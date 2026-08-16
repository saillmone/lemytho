package com.opencover.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.opencover.app.data.local.WordRepository
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.data.model.Role
import com.opencover.app.di.AppContainer
import com.opencover.app.engine.GameEngine
import com.opencover.app.engine.Victory
import com.opencover.app.engine.VoteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(
    private val gameEngine: GameEngine,
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wordRepository.getCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
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

    fun setThreePlayerIsMrWhite(isMrWhite: Boolean) {
        _uiState.update { it.copy(threePlayerIsMrWhite = isMrWhite) }
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
        FUNNY_NAMES.shuffled().take(count)

    fun startGame() {
        viewModelScope.launch {
            val state = _uiState.value
            val names = state.playerNames.map { it.trim() }
            if (names.any { it.isEmpty() }) return@launch

            val wordPair = wordRepository.getRandomPair(state.selectedCategory) ?: return@launch
            val roles = gameEngine.assignRoles(names.size, state.threePlayerIsMrWhite)
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
                    pendingMrWhiteGuess = null,
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
            state.copy(
                votePhase = VotePhase.VOTING,
                voteOrder = active.map { it.id },
                currentVoterIndex = 0,
                votes = emptyMap(),
                tiedCandidates = emptySet()
            )
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
        return if (target.role == Role.MR_WHITE) {
            // Mr White éliminé : ultime tentative avant de clôturer la partie.
            base.copy(pendingMrWhiteGuess = targetId)
        } else {
            finalizeVictory(base)
        }
    }

    // --- Ultime tentative de Mr White ---

    fun resolveMrWhiteGuess(validated: Boolean) {
        _uiState.update { state ->
            val mrWhiteId = state.pendingMrWhiteGuess ?: return@update state
            val base = state.copy(pendingMrWhiteGuess = null)
            if (validated) {
                val victory = Victory.MrWhite(setOf(mrWhiteId), byGuess = true)
                val roundScores = gameEngine.computeScores(state.players, victory)
                base.copy(
                    result = victory,
                    finalScores = roundScores,
                    totalScores = accumulateScores(state.totalScores, roundScores)
                )
            } else {
                finalizeVictory(base)
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
            state.copy(
                elimination = null,
                currentScreen = Screen.GameBoard
            )
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

    fun resetGame() {
        _uiState.update { state ->
            GameUiState(categories = state.categories)
        }
    }
}

/** Pseudos amusants (thème espion/enquête), utilisés pour le préremplissage rapide. */
private val FUNNY_NAMES = listOf(
    "Sherlock", "Columbo", "Mata Hari", "Arsène", "Le Fouineur",
    "Hercule", "Miss Marple", "Le Corbeau", "Tête Brûlée", "L'Indic",
    "La Taupe", "Double Jeu", "Mr X", "La Silhouette", "L'Ombre",
    "Baron Noir", "Professeur", "La Belette", "Cervelle", "L'Espionne"
)

/** Factory manuelle : injecte GameEngine et WordRepository sans Hilt. */
class GameViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(container.gameEngine, container.wordRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
