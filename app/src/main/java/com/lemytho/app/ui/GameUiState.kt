package com.lemytho.app.ui

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.data.model.WordPair
import com.lemytho.app.engine.Victory

/** Les écrans de l'application. */
sealed interface Screen {
    data object Home : Screen
    data object Setup : Screen
    data object Players : Screen
    data object Reveal : Screen
    data object GameBoard : Screen
    data object Elimination : Screen
    data object Multiplayer : Screen
    data object Rules : Screen
}

/** Joueur éliminé affiché sur l'écran intermédiaire post-vote. */
data class EliminationEvent(
    val playerId: Int,
    val pseudo: String,
    val role: Role
)

/** Phase de vote en cours sur le plateau. */
enum class VotePhase {
    /** Pas de vote en cours. */
    IDLE,

    /** Premier tour : chaque joueur actif vote. */
    VOTING,

    /** Second tour : restreint aux ex æquo. */
    SECOND_ROUND
}

/**
 * État global de l'UI, exposé via StateFlow (source unique de vérité, MVVM strict).
 * Les écrans sont 100% stateless : ils lisent cet état et appellent le ViewModel.
 */
data class GameUiState(
    val currentScreen: Screen = Screen.Home,

    // Setup
    val playerCount: Int = 5,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val threePlayerIsUnknown: Boolean = true,

    // Players
    val playerNames: List<String> = emptyList(),

    // Partie en cours
    val players: List<Player> = emptyList(),
    val wordPair: WordPair? = null,
    val revealIndex: Int = 0,

    // Boucle de jeu (indices / vote / élimination)
    val roundNumber: Int = 1,
    val turnNumber: Int = 1,
    val clueOrder: List<Int> = emptyList(),
    val votePhase: VotePhase = VotePhase.IDLE,
    val voteOrder: List<Int> = emptyList(),
    val currentVoterIndex: Int = 0,
    val votes: Map<Int, Int> = emptyMap(),
    val tiedCandidates: Set<Int> = emptySet(),

    // Écran intermédiaire post-vote (joueur éliminé à afficher)
    val elimination: EliminationEvent? = null,

    // Ultime tentative de l'Inconnu (id du joueur en attente de devinette, sinon null)
    val pendingUnknownGuess: Int? = null,

    // Fin de partie
    val result: Victory? = null,
    val finalScores: Map<Int, Int> = emptyMap(),
    val totalScores: Map<Int, Int> = emptyMap(),

    // Multijoueur (mode hôte autoritaire)
    val multiplayerHost: Boolean = false,
    val revealAcks: Set<Int> = emptySet(),

    // La partie a été annulée (plus assez de joueurs) : l'hôte doit revenir au salon.
    val hostAborted: Boolean = false
)
