package com.lemytho.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lemytho.app.data.local.WordRepository
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.di.AppContainer
import com.lemytho.app.engine.GameEngine
import com.lemytho.app.engine.Victory
import com.lemytho.app.engine.VoteOutcome
import com.lemytho.app.engine.WordGuessMatcher
import com.lemytho.app.net.ConnectionManager
import com.lemytho.app.net.HostSession
import com.lemytho.app.net.LobbyMember
import com.lemytho.app.net.Protocol
import com.lemytho.app.net.ServerEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(
    private val gameEngine: GameEngine,
    private val wordRepository: WordRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Session de diffusion vers les invités (null en jeu local). */
    private var hostSession: HostSession? = null

    init {
        viewModelScope.launch {
            wordRepository.getCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            connectionManager.events.collect { event ->
                if (event is ServerEvent.GameEvent) {
                    handleGuestEvent(event)
                }
            }
        }
    }

    fun navigate(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    // --- Setup ---

    fun setPlayerCount(count: Int) {
        _uiState.update { it.copy(playerCount = count) }
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setThreePlayerIsUnknown(isUnknown: Boolean) {
        _uiState.update { it.copy(threePlayerIsUnknown = isUnknown) }
    }

    fun startPlayerEntry() {
        _uiState.update {
            it.copy(
                playerNames = funnyNamesFor(it.playerCount),
                currentScreen = Screen.Players
            )
        }
    }

    // --- Players ---

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { state ->
            if (index !in state.playerNames.indices) {
                state
            } else {
                state.copy(
                    playerNames = state.playerNames.toMutableList().also { it[index] = name }
                )
            }
        }
    }

    /** Régénère aléatoirement les pseudos amusants, sans toucher au nombre de joueurs. */
    fun shufflePlayerNames() {
        _uiState.update { state ->
            state.copy(playerNames = funnyNamesFor(state.playerCount))
        }
    }

    /** Tire [count] pseudos amusants distincts, mélangés aléatoirement. */
    private fun funnyNamesFor(count: Int): List<String> =
        FunnyNames.NAMES.shuffled().take(count)

    fun startGame() {
        viewModelScope.launch {
            val state = _uiState.value
            val names = state.playerNames.map { it.trim() }
            if (names.any { it.isEmpty() }) return@launch

            val wordPair = wordRepository.getRandomPair(state.selectedCategory) ?: return@launch
            val roles = gameEngine.assignRoles(names.size, state.threePlayerIsUnknown)
            val players = names.mapIndexed { index, name ->
                Player(id = index + 1, pseudo = name, role = roles[index], assignedWord = "")
            }
            val withWords = gameEngine.assignWords(players, wordPair)

            _uiState.update {
                it.copy(
                    players = withWords,
                    wordPair = wordPair,
                    revealIndex = 0,
                    turnNumber = 1,
                    clueOrder = emptyList(),
                    votePhase = VotePhase.IDLE,
                    votes = emptyMap(),
                    tiedCandidates = emptySet(),
                    elimination = null,
                    pendingUnknownGuess = null,
                    unknownGuessCorrect = null,
                    result = null,
                    finalScores = emptyMap(),
                    currentScreen = Screen.Reveal
                )
            }
        }
    }

    /** Relance une partie avec la même configuration (noms + catégorie conservés). */
    fun replay() {
        _uiState.update { it.copy(roundNumber = it.roundNumber + 1) }
        startGame()
    }

    // --- Reveal ---

    fun nextReveal() {
        _uiState.update { state ->
            val next = state.revealIndex + 1
            if (next >= state.players.size) {
                startNewClueRound(state.copy(currentScreen = Screen.GameBoard))
            } else {
                state.copy(revealIndex = next)
            }
        }
    }

    // --- Boucle de jeu : indices ---

    /**
     * Démarre une nouvelle phase d'indices : ordre calculé parmi les joueurs
     * actifs, premier joueur aléatoire puis ordre d'inscription en boucle.
     */
    private fun startNewClueRound(state: GameUiState): GameUiState {
        val activeIds = state.players
            .filter { it.status == PlayerStatus.ACTIVE }
            .map { it.id }
        return state.copy(
            clueOrder = if (activeIds.isEmpty()) emptyList() else gameEngine.clueOrder(activeIds)
        )
    }

    // --- Boucle de jeu : vote ---

    fun startVote() {
        _uiState.update { state ->
            if (state.result != null || state.votePhase != VotePhase.IDLE) return@update state
            val active = activePlayers(state)
            if (active.size < 2) return@update state
            if (state.multiplayerHost) {
                val newState = state.copy(
                    votePhase = VotePhase.VOTING,
                    votes = emptyMap(),
                    tiedCandidates = emptySet()
                )
                broadcastBoard(newState)
                hostSession?.sendPhase(Protocol.PHASE_VOTE)
                newState
            } else {
                state.copy(
                    votePhase = VotePhase.VOTING,
                    voteOrder = active.map { it.id },
                    currentVoterIndex = 0,
                    votes = emptyMap(),
                    tiedCandidates = emptySet()
                )
            }
        }
    }

    fun castVote(targetId: Int) {
        _uiState.update { state ->
            if (state.votePhase == VotePhase.IDLE) return@update state
            val voterId = state.voteOrder.getOrNull(state.currentVoterIndex) ?: return@update state
            val newVotes = state.votes + (voterId to targetId)
            val nextIndex = state.currentVoterIndex + 1
            if (nextIndex < state.voteOrder.size) {
                state.copy(votes = newVotes, currentVoterIndex = nextIndex)
            } else {
                resolveRound(state.copy(votes = newVotes))
            }
        }
    }

    private fun activePlayers(state: GameUiState): List<Player> =
        state.players.filter { it.status == PlayerStatus.ACTIVE }

    private fun resolveRound(state: GameUiState): GameUiState {
        val candidates = if (state.votePhase == VotePhase.SECOND_ROUND) {
            state.tiedCandidates
        } else {
            activePlayers(state).map { it.id }.toSet()
        }
        return when (val outcome = gameEngine.resolveVote(state.votes, candidates)) {
            is VoteOutcome.Eliminated -> applyElimination(state, outcome.targetId)
            is VoteOutcome.Tie -> {
                if (state.votePhase == VotePhase.SECOND_ROUND) {
                    // Égalité persistante : élimination aléatoire parmi les ex æquo.
                    applyElimination(state, gameEngine.randomElimination(outcome.candidateIds))
                } else {
                    // Second tour : seuls les joueurs non-concernés par l'égalité revotent.
                    val tiedIds = outcome.candidateIds
                    val secondVoters = activePlayers(state)
                        .map { it.id }
                        .filter { it !in tiedIds }
                    if (secondVoters.isEmpty()) {
                        // Cas dégénéré : tous les actifs sont ex æquo → tirage au sort direct.
                        applyElimination(state, gameEngine.randomElimination(tiedIds))
                    } else {
                        state.copy(
                            votePhase = VotePhase.SECOND_ROUND,
                            tiedCandidates = tiedIds,
                            voteOrder = secondVoters,
                            currentVoterIndex = 0,
                            votes = emptyMap()
                        )
                    }
                }
            }
        }
    }

    private fun applyElimination(state: GameUiState, targetId: Int): GameUiState {
        val target = state.players.firstOrNull { it.id == targetId } ?: return state
        val updatedPlayers = state.players.map { player ->
            if (player.id == targetId) player.copy(status = PlayerStatus.ELIMINATED) else player
        }
        val base = state.copy(
            players = updatedPlayers,
            votePhase = VotePhase.IDLE,
            votes = emptyMap(),
            tiedCandidates = emptySet(),
            elimination = EliminationEvent(target.id, target.pseudo, target.role),
            currentScreen = Screen.Elimination
        )
        return if (target.role == Role.UNKNOWN) {
            // l'Inconnu éliminé : ultime tentative avant de clôturer la partie.
            base.copy(pendingUnknownGuess = targetId)
        } else {
            finalizeVictory(base)
        }
    }

    // --- Ultime tentative de l'Inconnu ---

    fun resolveUnknownGuess(guess: String) {
        val state = _uiState.value
        val unknownId = state.pendingUnknownGuess ?: return
        val trimmed = guess.trim().take(WordGuessMatcher.MAX_GUESS_LENGTH)
        if (trimmed.isEmpty()) return
        val citizenWord = state.wordPair?.citizenWord.orEmpty()
        val validated = WordGuessMatcher.matchesCitizenWord(trimmed, citizenWord)
        val base = state.copy(
            pendingUnknownGuess = null,
            unknownGuessCorrect = validated
        )
        val resolved = if (validated) {
            val victory = Victory.Unknown(setOf(unknownId), byGuess = true)
            val roundScores = gameEngine.computeScores(state.players, victory)
            base.copy(
                result = victory,
                finalScores = roundScores,
                totalScores = accumulateScores(state.totalScores, roundScores),
                elimination = base.elimination?.copy(
                    guessResolved = true,
                    guessCorrect = true
                )
            )
        } else {
            val next = finalizeVictory(base)
            next.copy(
                elimination = next.elimination?.copy(
                    guessResolved = true,
                    guessCorrect = false
                )
            )
        }
        _uiState.value = resolved
        // Cas de l'Inconnu : le résultat n'est connu qu'après la résolution de la
        // devinette. On le diffuse ici (l'écran d'élimination est déjà affiché).
        if (resolved.multiplayerHost) {
            if (resolved.result != null) {
                hostSession?.sendResult(resolved.players, resolved.result, resolved.totalScores)
                hostSession?.sendPhase(Protocol.PHASE_RESULT)
            } else {
                // Devinette fausse et partie encore en cours : les invités affichent
                // « Ce n'était pas le mot des Citoyens. »
                resolved.elimination?.let { e ->
                    hostSession?.sendElimination(
                        playerId = e.playerId,
                        pseudo = e.pseudo,
                        role = e.role,
                        turnNumber = resolved.turnNumber,
                        guessResolved = true
                    )
                }
            }
        }
    }

    private fun finalizeVictory(state: GameUiState): GameUiState {
        val victory = gameEngine.determineWinner(state.players)
        return if (victory is Victory.Ongoing) {
            // Tour suivant : nouvelle phase d'indices (premier joueur aléatoire).
            startNewClueRound(state.copy(turnNumber = state.turnNumber + 1))
        } else {
            val roundScores = gameEngine.computeScores(state.players, victory)
            state.copy(
                result = victory,
                finalScores = roundScores,
                totalScores = accumulateScores(state.totalScores, roundScores)
            )
        }
    }

    /** Quitte l'écran d'élimination : retour au plateau (ou aux résultats si la partie est finie). */
    fun continueAfterElimination() {
        _uiState.update { state ->
            val next = state.copy(
                elimination = null,
                currentScreen = Screen.GameBoard
            )
            // Le résultat final est déjà diffusé (broadcastResolved) : ici on ne
            // synchronise que la reprise du jeu (tour suivant) pour les invités.
            if (state.multiplayerHost && state.result == null) {
                broadcastBoard(next)
                hostSession?.sendPhase(Protocol.PHASE_CLUE)
            }
            next
        }
    }

    /** Additionne les points d'une manche au cumul total, par identifiant de joueur. */
    private fun accumulateScores(total: Map<Int, Int>, round: Map<Int, Int>): Map<Int, Int> {
        val merged = total.toMutableMap()
        round.forEach { (id, points) ->
            merged[id] = (merged[id] ?: 0) + points
        }
        return merged
    }

    // --- Multijoueur : hôte autoritaire ---

    fun startHostGame(members: List<LobbyMember>, category: String?, threePlayerIsUnknown: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val isReplay = hostSession != null
            val playing = members.filter { (it.ready || it.isHost) && it.connected }
            if (playing.size < 3) return@launch
            val wordPair = wordRepository.getRandomPair(category) ?: return@launch
            val roles = gameEngine.assignRoles(playing.size, threePlayerIsUnknown)
            val players = playing.mapIndexed { index, member ->
                Player(id = member.playerId, pseudo = member.pseudo, role = roles[index], assignedWord = "")
            }
            val withWords = gameEngine.assignWords(players, wordPair)
            val session = hostSession ?: HostSession(connectionManager)
            hostSession = session
            session.sendStart(wordPair.category)
            withWords.forEach { player ->
                session.sendPrivate(player.id, player.role, player.assignedWord)
            }
            connectionManager.notifyGameStarted()
            _uiState.update { st ->
                st.copy(
                    players = withWords,
                    wordPair = wordPair,
                    revealIndex = 0,
                    roundNumber = if (isReplay) st.roundNumber + 1 else 1,
                    turnNumber = 1,
                    clueOrder = emptyList(),
                    votePhase = VotePhase.IDLE,
                    votes = emptyMap(),
                    tiedCandidates = emptySet(),
                    elimination = null,
                    pendingUnknownGuess = null,
                    unknownGuessCorrect = null,
                    result = null,
                    finalScores = emptyMap(),
                    totalScores = if (isReplay) st.totalScores else emptyMap(),
                    multiplayerHost = true,
                    revealAcks = emptySet(),
                    selectedCategory = category,
                    currentScreen = Screen.Reveal
                )
            }
        }
    }

    /** L'hôte a vu son rôle : enregistre son ack et attend les invités. */
    fun hostRevealDone() {
        if (!_uiState.value.multiplayerHost) return
        applyHostAck(Protocol.HOST_PLAYER_ID)
    }

    /** L'hôte vote depuis son propre appareil. */
    fun hostCastVote(targetId: Int) {
        _uiState.update { state ->
            if (!state.multiplayerHost) return@update state
            registerHostVote(state, Protocol.HOST_PLAYER_ID, targetId)
        }
    }

    /** L'hôte retourne au salon après une manche (il s'est déclaré prêt). */
    fun hostReturnToLobby() {
        _uiState.update { state ->
            state.copy(
                currentScreen = Screen.Multiplayer,
                multiplayerHost = false
            )
        }
    }

    // --- Entrées des invités (routées vers la logique hôte) ---

    private fun handleGuestEvent(event: ServerEvent.GameEvent) {
        when (event.name) {
            Protocol.EVENT_PLAYER_REVEAL -> onGuestReveal(event.data.optInt("playerId"))
            Protocol.EVENT_PLAYER_VOTE -> onGuestVote(event.data.optInt("playerId"), event.data.optInt("targetId"))
            Protocol.EVENT_PLAYER_GUESS -> onGuestGuess(
                event.data.optInt("playerId"),
                event.data.optString("text")
            )
            Protocol.EVENT_PLAYER_DISCONNECTED -> onPlayerDisconnected(event.data.optInt("playerId"))
        }
    }

    /**
     * Un invité s'est déconnecté en cours de partie. On le retire/élimine pour
     * ne pas bloquer la manche : silencieusement pendant la révélation (le rôle
     * n'est pas encore joué), avec révélation du rôle sinon (option A).
     * Si le nombre de joueurs tombe sous 3, la partie est annulée.
     */
    private fun onPlayerDisconnected(playerId: Int) {
        if (!_uiState.value.multiplayerHost) return
        _uiState.update { state ->
            val target = state.players.firstOrNull { it.id == playerId } ?: return@update state
            if (target.status == PlayerStatus.ELIMINATED) return@update state

            if (state.currentScreen == Screen.Reveal) {
                val remaining = state.players.filter { it.id != playerId }
                if (remaining.size < 3) return@update abortGame(state)
                val base = state.copy(
                    players = remaining,
                    revealAcks = state.revealAcks - playerId
                )
                if (base.revealAcks.size >= base.players.size) startCluePhase(base) else base
            } else {
                val updatedPlayers = state.players.map { p ->
                    if (p.id == playerId) p.copy(status = PlayerStatus.ELIMINATED) else p
                }
                val remainingActive = updatedPlayers.count { it.status == PlayerStatus.ACTIVE }
                if (remainingActive < 3) return@update abortGame(state)
                val base = state.copy(
                    players = updatedPlayers,
                    votePhase = VotePhase.IDLE,
                    votes = emptyMap(),
                    tiedCandidates = emptySet(),
                    elimination = EliminationEvent(target.id, target.pseudo, target.role),
                    pendingUnknownGuess = null,
                    currentScreen = Screen.Elimination
                )
                val next = finalizeVictory(base)
                broadcastResolved(next)
                next
            }
        }
    }

    /** Annule la partie (plus assez de joueurs) et renvoie tout le monde au salon. */
    private fun abortGame(state: GameUiState): GameUiState {
        hostSession?.sendCancelled()
        hostSession = null
        return GameUiState(categories = state.categories).copy(
            currentScreen = Screen.Multiplayer,
            hostAborted = true
        )
    }

    /** L'écran a traité le retour au salon après annulation : on remet le signal à zéro. */
    fun acknowledgeAbort() {
        _uiState.update { it.copy(hostAborted = false) }
    }

    private fun onGuestReveal(playerId: Int) {
        if (!_uiState.value.multiplayerHost) return
        applyHostAck(playerId)
    }

    /** Enregistre un ack de révélation puis diffuse la progression aux invités. */
    private fun applyHostAck(playerId: Int) {
        _uiState.update { state ->
            if (!state.multiplayerHost) return@update state
            onHostAck(state, playerId)
        }
        val state = _uiState.value
        if (state.multiplayerHost && state.currentScreen == Screen.Reveal) {
            hostSession?.sendRevealAck(state.revealAcks.size, state.players.size)
        }
    }

    private fun onGuestVote(playerId: Int, targetId: Int) {
        _uiState.update { state ->
            if (!state.multiplayerHost) return@update state
            registerHostVote(state, playerId, targetId)
        }
    }

    private fun onGuestGuess(playerId: Int, text: String) {
        val state = _uiState.value
        if (!state.multiplayerHost) return
        if (state.pendingUnknownGuess != playerId) return
        resolveUnknownGuess(text)
    }

    // --- Helpers hôte ---

    private fun onHostAck(state: GameUiState, playerId: Int): GameUiState {
        val acks = state.revealAcks + playerId
        return if (acks.size >= state.players.size) {
            startCluePhase(state.copy(revealAcks = acks))
        } else {
            state.copy(revealAcks = acks)
        }
    }

    private fun startCluePhase(state: GameUiState): GameUiState {
        val newState = startNewClueRound(state.copy(currentScreen = Screen.GameBoard))
        broadcastBoard(newState)
        hostSession?.sendPhase(Protocol.PHASE_CLUE)
        return newState
    }

    private fun registerHostVote(state: GameUiState, playerId: Int, targetId: Int): GameUiState {
        if (state.votePhase == VotePhase.IDLE) return state
        val expectedVoters = if (state.votePhase == VotePhase.SECOND_ROUND) {
            state.voteOrder.toSet()
        } else {
            activePlayers(state).map { it.id }.toSet()
        }
        if (playerId !in expectedVoters) return state
        val newVotes = state.votes + (playerId to targetId)
        return if (newVotes.keys.containsAll(expectedVoters)) {
            resolveRoundHost(state.copy(votes = newVotes))
        } else {
            state.copy(votes = newVotes)
        }
    }

    private fun resolveRoundHost(state: GameUiState): GameUiState {
        val resolved = resolveRound(state)
        broadcastResolved(resolved)
        return resolved
    }

    private fun broadcastResolved(state: GameUiState) {
        state.elimination?.let { elimination ->
            hostSession?.sendElimination(
                elimination.playerId, elimination.pseudo, elimination.role, state.turnNumber
            )
        }
        when {
            state.result != null -> {
                hostSession?.sendResult(state.players, state.result, state.totalScores)
                hostSession?.sendPhase(Protocol.PHASE_RESULT)
            }
            state.votePhase == VotePhase.SECOND_ROUND -> {
                broadcastBoard(state)
                hostSession?.sendPhase(Protocol.PHASE_VOTE)
            }
        }
    }

    private fun broadcastBoard(state: GameUiState) {
        hostSession?.sendBoard(
            players = state.players,
            clueOrder = state.clueOrder,
            roundNumber = state.roundNumber,
            turnNumber = state.turnNumber,
            category = state.wordPair?.category,
            votePhase = state.votePhase,
            currentVoterId = null,
            tiedCandidates = state.tiedCandidates
        )
    }

    fun resetGame() {
        hostSession = null
        _uiState.update { state ->
            GameUiState(categories = state.categories)
        }
    }
}

/** Factory manuelle : injecte GameEngine et WordRepository sans Hilt. */
class GameViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(
                gameEngine = container.gameEngine,
                wordRepository = container.wordRepository,
                connectionManager = container.connectionManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
