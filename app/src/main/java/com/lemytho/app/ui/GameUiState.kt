package com.lemytho.app.ui

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
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

/** Un vote du tour décisif, affiché après clôture (doigt levé IRL). */
data class VoteReveal(
    val voterPseudo: String,
    val targetPseudo: String
)

/** Joueur éliminé affiché sur l'écran intermédiaire post-vote. */
data class EliminationEvent(
    val playerId: Int,
    val pseudo: String,
    val role: Role,
    val guessResolved: Boolean = false,
    val guessCorrect: Boolean = false,
    /** Saisie (échec) ou mot du paquet (succès) ; null tant que non résolu. */
    val guessText: String? = null,
    val votes: List<VoteReveal> = emptyList()
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

/** Personne n'est éliminé : égalité totale, ou 0 bulletin après skip-wait. */
enum class NoElimination {
    TIE,
    NO_VOTES
}

fun noEliminationHint(reason: NoElimination?): String? = when (reason) {
    NoElimination.TIE -> "Égalité : personne n'est éliminé. Nouvel indice."
    NoElimination.NO_VOTES -> "Personne n'est éliminé (aucun vote). Nouvel indice."
    null -> null
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
    // null = pas de devinette cette manche ; true/false = issue de la dernière tentative
    val unknownGuessCorrect: Boolean? = null,

    // Fin de partie
    val result: Victory? = null,
    val finalScores: Map<Int, Int> = emptyMap(),
    val totalScores: Map<Int, Int> = emptyMap(),

    // Multijoueur (mode hôte autoritaire)
    val multiplayerHost: Boolean = false,
    val revealAcks: Set<Int> = emptySet(),

    /** Joueurs de la manche dont le fil est coupé (pas éliminés). */
    val offlinePlayerIds: Set<Int> = emptySet(),

    /** Ids ignorés pour le tour de vote courant (« Ne plus attendre »). */
    val skippedVoters: Set<Int> = emptySet(),

    /** Skip d'élimination affiché tant que le prochain vote n'a pas commencé. */
    val noElimination: NoElimination? = null,

    // Raison d'annulation (`Protocol.CANCEL_*`) : l'hôte doit revenir au salon.
    val hostAbortReason: String? = null
)

/** Ids hors ligne encore bloquants pour l'étape courante (bouton skip). */
fun GameUiState.skippablePlayerIds(): Set<Int> {
    val offlineActive = players
        .filter { it.id in offlinePlayerIds && it.status == PlayerStatus.ACTIVE }
        .map { it.id }
        .toSet()
    return when {
        currentScreen == Screen.Reveal -> offlineActive - revealAcks
        currentScreen == Screen.Elimination -> {
            val pending = pendingUnknownGuess ?: return emptySet()
            if (pending in offlineActive) setOf(pending) else emptySet()
        }
        votePhase != VotePhase.IDLE -> offlineActive - skippedVoters - votes.keys
        else -> emptySet()
    }
}
