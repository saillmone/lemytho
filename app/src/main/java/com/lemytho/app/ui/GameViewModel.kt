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
import com.lemytho.app.net.GameProtocol
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

    /** Spectateurs déjà syncés cette manche (évite un game:sync à chaque lobby:update). */
    private val syncedSpectatorIds = mutableSetOf<Int>()

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
                    noElimination = null,
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
                    tiedCandidates = emptySet(),
                    skippedVoters = emptySet(),
                    noElimination = null
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
                    tiedCandidates = emptySet(),
                    noElimination = null
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
                        .filter { it !in tiedIds && it !in state.skippedVoters }
                    if (secondVoters.isEmpty()) {
                        // Tous ex æquo, ou plus personne hors skipped pour revoter.
                        skipEliminationRound(state, NoElimination.TIE)
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
        val voteReveals = voteRevealsFrom(state.votes, state.players)
        val base = state.copy(
            players = updatedPlayers,
            votePhase = VotePhase.IDLE,
            votes = emptyMap(),
            tiedCandidates = emptySet(),
            skippedVoters = emptySet(),
            noElimination = null,
            elimination = EliminationEvent(
                playerId = target.id,
                pseudo = target.pseudo,
                role = target.role,
                votes = voteReveals
            ),
            currentScreen = Screen.Elimination
        )
        return if (target.role == Role.UNKNOWN) {
            // l'Inconnu éliminé : ultime tentative avant de clôturer la partie.
            base.copy(pendingUnknownGuess = targetId)
        } else {
            finalizeVictory(base)
        }
    }

    private fun voteRevealsFrom(votes: Map<Int, Int>, players: List<Player>): List<VoteReveal> {
        val names = players.associate { it.id to it.pseudo }
        return votes.mapNotNull { (voterId, targetId) ->
            val voter = names[voterId] ?: return@mapNotNull null
            val target = names[targetId] ?: return@mapNotNull null
            VoteReveal(voterPseudo = voter, targetPseudo = target)
        }.sortedBy { it.voterPseudo.lowercase() }
    }

    // --- Ultime tentative de l'Inconnu ---

    fun resolveUnknownGuess(guess: String, forcedFail: Boolean = false) {
        val state = _uiState.value
        val unknownId = state.pendingUnknownGuess ?: return
        val trimmed = guess.trim().take(WordGuessMatcher.MAX_GUESS_LENGTH)
        if (!forcedFail && trimmed.isEmpty()) return
        val citizenWord = state.wordPair?.citizenWord.orEmpty()
        val validated = !forcedFail && WordGuessMatcher.matchesCitizenWord(trimmed, citizenWord)
        val guessText = if (validated) {
            citizenWord.ifBlank { trimmed }
        } else {
            trimmed
        }
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
                    guessCorrect = true,
                    guessText = guessText
                )
            )
        } else {
            val next = finalizeVictory(base)
            next.copy(
                elimination = next.elimination?.copy(
                    guessResolved = true,
                    guessCorrect = false,
                    guessText = guessText
                )
            )
        }
        _uiState.value = resolved
        // Cas de l'Inconnu : le résultat n'est connu qu'après la résolution de la
        // devinette. On le diffuse ici (l'écran d'élimination est déjà affiché).
        // Elimination AVANT résultat : les invités vident guestResult à chaque
        // game:elimination.
        if (resolved.multiplayerHost) {
            resolved.elimination?.let { e ->
                hostSession?.sendElimination(
                    playerId = e.playerId,
                    pseudo = e.pseudo,
                    role = e.role,
                    turnNumber = resolved.turnNumber,
                    guessResolved = e.guessResolved,
                    guessCorrect = e.guessCorrect,
                    guessText = e.guessText,
                    votes = e.votes
                )
            }
            if (resolved.result != null) {
                hostSession?.sendResult(resolved.players, resolved.result, resolved.totalScores)
                hostSession?.sendPhase(Protocol.PHASE_RESULT)
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
            session.sendStart(wordPair.category, withWords.map { it.id })
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
                    offlinePlayerIds = emptySet(),
                    skippedVoters = emptySet(),
                    noElimination = null,
                    selectedCategory = category,
                    currentScreen = Screen.Reveal
                )
            }
            val guests = members.filter {
                it.connected && it.playerId != Protocol.HOST_PLAYER_ID
            }
            guests.forEach { sendSyncTo(it.playerId) }
            syncedSpectatorIds.clear()
            val playingIds = withWords.map { it.id }.toSet()
            syncedSpectatorIds.addAll(
                guests.filter { it.playerId !in playingIds }.map { it.playerId }
            )
        }
    }

    /**
     * Envoie game:sync aux nouveaux sièges hors manche. No-op s'il n'y a
     * personne de nouveau (ready / présence d'un joueur en jeu).
     */
    fun syncSpectators(members: List<LobbyMember>) {
        val s = _uiState.value
        if (!s.multiplayerHost || !isHostRoundScreen(s)) return
        val playingIds = s.players.map { it.id }.toSet()
        members.filter { member ->
            member.connected &&
                member.playerId != Protocol.HOST_PLAYER_ID &&
                member.playerId !in playingIds &&
                member.playerId !in syncedSpectatorIds
        }.forEach { member ->
            sendSyncTo(member.playerId)
            syncedSpectatorIds.add(member.playerId)
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
        notifyRoundOver()
        connectionManager.endRoundOnServer()
        syncedSpectatorIds.clear()
        _uiState.update { state ->
            state.copy(
                currentScreen = Screen.Multiplayer,
                multiplayerHost = false,
                offlinePlayerIds = emptySet(),
                skippedVoters = emptySet()
            )
        }
    }

    /** Hôte : arrête la manche et renvoie tout le monde au salon. */
    fun cancelRound() {
        if (!_uiState.value.multiplayerHost) return
        _uiState.update { abortGame(it, Protocol.CANCEL_HOST_CANCELLED) }
    }

    /**
     * Après un `lobby:create` repris : si la ViewModel n'a plus la manche,
     * on annule côté invités ; sinon on renvoie l'état à tout le monde.
     */
    fun onHostSeatResumed(serverInRound: Boolean) {
        val s = _uiState.value
        val localInRound = s.multiplayerHost && isHostRoundScreen(s)
        if (localInRound) {
            syncAllGuests()
            return
        }
        if (serverInRound) {
            (hostSession ?: HostSession(connectionManager))
                .sendCancelled(Protocol.CANCEL_ROUND_LOST)
            connectionManager.endRoundOnServer()
        }
    }

    /**
     * Hôte : ne plus attendre un joueur hors ligne (ack / vote / guess).
     * 0 bulletin et plus personne à attendre → tour d'indices suivant.
     */
    fun skipWait(playerId: Int) {
        val s = _uiState.value
        if (!s.multiplayerHost) return
        if (playerId !in s.offlinePlayerIds) return
        if (s.currentScreen == Screen.Elimination && s.pendingUnknownGuess == playerId) {
            resolveUnknownGuess("", forcedFail = true)
            return
        }
        if (s.currentScreen == Screen.Reveal && playerId !in s.revealAcks) {
            applyHostAck(playerId)
            return
        }
        if (s.votePhase == VotePhase.IDLE) return
        _uiState.update { state ->
            if (playerId !in state.offlinePlayerIds) return@update state
            val next = state.copy(skippedVoters = state.skippedVoters + playerId)
            val expected = expectedVoters(next)
            when {
                expected.isEmpty() && next.votes.isEmpty() ->
                    skipEliminationRound(next, NoElimination.NO_VOTES)
                next.votes.keys.containsAll(expected) || expected.isEmpty() -> resolveRoundHost(next)
                else -> next
            }
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
            Protocol.EVENT_PLAYER_PRESENCE -> onPresence(
                event.data.optInt("playerId"),
                event.data.optBoolean("connected")
            )
            Protocol.EVENT_PLAYER_LEFT -> onPlayerLeft(event.data.optInt("playerId"))
        }
    }

    /**
     * Fil coupé ou revenu. Ne change pas le statut de jeu ; met à jour les
     * badges et, au retour, envoie game:sync.
     */
    private fun onPresence(playerId: Int, connected: Boolean) {
        val s = _uiState.value
        if (!s.multiplayerHost) return
        val inRound = isHostRoundScreen(s)
        if (playerId != Protocol.HOST_PLAYER_ID) {
            _uiState.update {
                val offline = if (connected) it.offlinePlayerIds - playerId else it.offlinePlayerIds + playerId
                val skipped = if (connected) it.skippedVoters - playerId else it.skippedVoters
                it.copy(offlinePlayerIds = offline, skippedVoters = skipped)
            }
            if (inRound) broadcastBoard(_uiState.value)
        }
        if (connected && inRound) {
            if (playerId == Protocol.HOST_PLAYER_ID) syncAllGuests()
            else sendSyncTo(playerId)
        }
    }

    /**
     * Départ franc en cours de partie. On le retire/élimine : silencieusement
     * pendant la révélation, avec révélation du rôle sinon (option A).
     * Si le nombre de joueurs tombe sous 3, la partie est annulée.
     */
    private fun onPlayerLeft(playerId: Int) {
        if (!_uiState.value.multiplayerHost) return
        _uiState.update { state ->
            if (!isHostRoundScreen(state) || state.result != null) {
                return@update state
            }
            val target = state.players.firstOrNull { it.id == playerId } ?: return@update state
            if (target.status == PlayerStatus.ELIMINATED) return@update state
            val cleaned = state.copy(
                offlinePlayerIds = state.offlinePlayerIds - playerId,
                skippedVoters = state.skippedVoters - playerId
            )

            if (cleaned.currentScreen == Screen.Reveal) {
                val remaining = cleaned.players.filter { it.id != playerId }
                if (remaining.size < 3) {
                    return@update abortGame(cleaned, Protocol.CANCEL_NOT_ENOUGH_PLAYERS)
                }
                val base = cleaned.copy(
                    players = remaining,
                    revealAcks = cleaned.revealAcks - playerId
                )
                if (base.revealAcks.size >= base.players.size) startCluePhase(base) else base
            } else {
                val updatedPlayers = cleaned.players.map { p ->
                    if (p.id == playerId) p.copy(status = PlayerStatus.ELIMINATED) else p
                }
                val remainingActive = updatedPlayers.count { it.status == PlayerStatus.ACTIVE }
                if (remainingActive < 3) {
                    return@update abortGame(cleaned, Protocol.CANCEL_NOT_ENOUGH_PLAYERS)
                }
                val base = cleaned.copy(
                    players = updatedPlayers,
                    votePhase = VotePhase.IDLE,
                    votes = emptyMap(),
                    tiedCandidates = emptySet(),
                    skippedVoters = emptySet(),
                    noElimination = null,
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

    /** Annule la partie et renvoie tout le monde au salon. */
    private fun abortGame(state: GameUiState, reason: String): GameUiState {
        syncedSpectatorIds.clear()
        hostSession?.sendCancelled(reason)
        connectionManager.endRoundOnServer()
        hostSession = null
        return GameUiState(categories = state.categories).copy(
            currentScreen = Screen.Multiplayer,
            hostAbortReason = reason
        )
    }

    /** L'écran a traité le retour au salon après annulation : on remet le signal à zéro. */
    fun acknowledgeAbort() {
        _uiState.update { it.copy(hostAbortReason = null) }
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

    private fun expectedVoters(state: GameUiState): Set<Int> {
        val base = if (state.votePhase == VotePhase.SECOND_ROUND) {
            state.voteOrder.toSet()
        } else {
            activePlayers(state).map { it.id }.toSet()
        }
        return base - state.skippedVoters
    }

    private fun registerHostVote(state: GameUiState, playerId: Int, targetId: Int): GameUiState {
        if (state.votePhase == VotePhase.IDLE) return state
        val expected = expectedVoters(state)
        if (playerId !in expected) return state
        val newVotes = state.votes + (playerId to targetId)
        return if (newVotes.keys.containsAll(expected)) {
            resolveRoundHost(state.copy(votes = newVotes))
        } else {
            val next = state.copy(votes = newVotes)
            broadcastBoard(next)
            next
        }
    }

    private fun skipEliminationRound(state: GameUiState, reason: NoElimination): GameUiState {
        val next = startNewClueRound(
            state.copy(
                votePhase = VotePhase.IDLE,
                votes = emptyMap(),
                tiedCandidates = emptySet(),
                skippedVoters = emptySet(),
                elimination = null,
                noElimination = reason,
                currentScreen = Screen.GameBoard,
                turnNumber = state.turnNumber + 1
            )
        )
        broadcastBoard(next)
        hostSession?.sendPhase(Protocol.PHASE_CLUE)
        return next
    }

    private fun resolveRoundHost(state: GameUiState): GameUiState {
        val resolved = resolveRound(state)
        // skipEliminationRound a déjà diffusé board + PHASE_CLUE.
        if (resolved.noElimination != null) return resolved
        broadcastResolved(resolved)
        return resolved
    }

    private fun broadcastResolved(state: GameUiState) {
        state.elimination?.let { elimination ->
            hostSession?.sendPhase(Protocol.PHASE_ELIMINATION)
            hostSession?.sendElimination(
                playerId = elimination.playerId,
                pseudo = elimination.pseudo,
                role = elimination.role,
                turnNumber = state.turnNumber,
                guessResolved = elimination.guessResolved,
                guessCorrect = elimination.guessCorrect,
                guessText = elimination.guessText,
                votes = elimination.votes
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
            tiedCandidates = state.tiedCandidates,
            offlinePlayerIds = state.offlinePlayerIds,
            votedPlayerIds = votedPlayerIdsOf(state),
            noElimination = state.noElimination
        )
    }

    private fun isHostRoundScreen(state: GameUiState): Boolean =
        state.currentScreen == Screen.Reveal ||
            state.currentScreen == Screen.GameBoard ||
            state.currentScreen == Screen.Elimination

    private fun notifyRoundOver() {
        val s = _uiState.value
        if (!s.multiplayerHost) return
        val payload = GameProtocol.syncPayload(
            inRound = false,
            participating = false,
            phase = Protocol.PHASE_RESULT
        )
        val ids = (s.players.map { it.id } + syncedSpectatorIds)
            .toSet()
            .filter { it != Protocol.HOST_PLAYER_ID }
        ids.forEach { hostSession?.sendSync(it, payload) }
    }

    private fun currentPhase(state: GameUiState): String = when {
        state.result != null -> Protocol.PHASE_RESULT
        state.currentScreen == Screen.Elimination -> Protocol.PHASE_ELIMINATION
        state.currentScreen == Screen.Reveal -> Protocol.PHASE_REVEAL
        state.votePhase != VotePhase.IDLE -> Protocol.PHASE_VOTE
        else -> Protocol.PHASE_CLUE
    }

    private fun syncAllGuests() {
        val s = _uiState.value
        s.players.filter { it.id != Protocol.HOST_PLAYER_ID }.forEach { sendSyncTo(it.id) }
    }

    private fun sendSyncTo(playerId: Int) {
        val s = _uiState.value
        if (!s.multiplayerHost || !isHostRoundScreen(s)) return
        val participating = s.players.any { it.id == playerId }
        val player = s.players.find { it.id == playerId }
        val phase = currentPhase(s)
        val board = GameProtocol.boardPayload(
            players = s.players,
            clueOrder = s.clueOrder,
            roundNumber = s.roundNumber,
            turnNumber = s.turnNumber,
            category = s.wordPair?.category,
            votePhase = s.votePhase,
            currentVoterId = null,
            tiedCandidates = s.tiedCandidates,
            offlinePlayerIds = s.offlinePlayerIds,
            votedPlayerIds = votedPlayerIdsOf(s),
            noElimination = s.noElimination
        )
        val elimination = s.elimination?.let {
            GameProtocol.eliminationPayload(
                playerId = it.playerId,
                pseudo = it.pseudo,
                role = it.role,
                turnNumber = s.turnNumber,
                guessResolved = it.guessResolved,
                guessCorrect = it.guessCorrect,
                guessText = it.guessText,
                votes = it.votes
            )
        }
        val result = s.result?.let { GameProtocol.resultPayload(s.players, it, s.totalScores) }
        val payload = GameProtocol.syncPayload(
            inRound = true,
            participating = participating,
            phase = phase,
            privateRole = player?.role,
            privateWord = player?.assignedWord,
            board = board,
            revealAcked = s.revealAcks.size,
            revealTotal = s.players.size,
            alreadyRevealed = playerId in s.revealAcks,
            alreadyVoted = s.votes.containsKey(playerId),
            elimination = if (s.currentScreen == Screen.Elimination) elimination else null,
            pendingUnknownGuess = s.pendingUnknownGuess != null &&
                s.currentScreen == Screen.Elimination &&
                s.elimination?.guessResolved != true,
            result = result
        )
        hostSession?.sendSync(playerId, payload)
    }

    private fun votedPlayerIdsOf(state: GameUiState): List<Int> =
        if (state.votePhase == VotePhase.IDLE) emptyList() else state.votes.keys.toList()

    fun resetGame() {
        hostSession = null
        syncedSpectatorIds.clear()
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
