package com.lemytho.app.ui.multiplayer

import com.lemytho.app.BuildConfig
import com.lemytho.app.data.model.Role
import com.lemytho.app.net.BoardSnapshot
import com.lemytho.app.net.ConnectionStatus
import com.lemytho.app.net.EliminationSnapshot
import com.lemytho.app.net.LobbyMember
import com.lemytho.app.net.ResultSnapshot

/** Sous-écrans du mode multijoueur. */
sealed interface MultiplayerScreen {
    data object Menu : MultiplayerScreen
    data object HostLobby : MultiplayerScreen
    data object HostSetup : MultiplayerScreen
    data object JoinLobby : MultiplayerScreen
    data object Waiting : MultiplayerScreen
    data object GuestReveal : MultiplayerScreen
    data object GuestBoard : MultiplayerScreen
    data object GuestElimination : MultiplayerScreen
    data object GuestResult : MultiplayerScreen
}

/**
 * État dédié au multijoueur. Séparé de [com.lemytho.app.ui.GameUiState]
 * pour ne pas alourdir l'état du jeu local.
 */
data class MultiplayerUiState(
    val screen: MultiplayerScreen = MultiplayerScreen.Menu,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isHost: Boolean = false,
    val myPseudo: String = "",
    val myPlayerId: Int? = null,
    val lobbyCode: String? = null,
    val joinCode: String = "",
    val members: List<LobbyMember> = emptyList(),
    val error: String? = null,
    val serverUrl: String = BuildConfig.SERVER_URL,

    // Setup hôte (choix de catégorie avant lancement)
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val threePlayerIsUnknown: Boolean = true,

    // Jeu invité (projection publique + informations privées)
    val myRole: Role? = null,
    val myWord: String? = null,
    val revealConfirmed: Boolean = false,
    // Progression de la révélation : X confirmations sur Y joueurs.
    val revealAcked: Int = 0,
    val revealTotal: Int = 0,
    val board: BoardSnapshot? = null,
    val elimination: EliminationSnapshot? = null,
    val guestResult: ResultSnapshot? = null,
    val hasVoted: Boolean = false,
    val guessSubmitted: Boolean = false,
    val unknownGuessCorrect: Boolean? = null,

    // Manche en cours : l'invité participe-t-il à la manche actuelle ?
    val inRound: Boolean = false,

    // L'invité a demandé à rejouer (en attente de la relance de l'hôte).
    val wantsReplay: Boolean = false
)
