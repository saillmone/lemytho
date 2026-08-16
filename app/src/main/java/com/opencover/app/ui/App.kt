package com.opencover.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.ui.screens.GameBoardScreen
import com.opencover.app.ui.screens.HomeScreen
import com.opencover.app.ui.screens.PlayersScreen
import com.opencover.app.ui.screens.ResultScreen
import com.opencover.app.ui.screens.RevealScreen
import com.opencover.app.ui.screens.SetupScreen
import com.opencover.app.ui.screens.EliminationScreen
import com.opencover.app.ui.screens.MultiplayerNavHost
import com.opencover.app.ui.screens.RevealOwnScreen
import com.opencover.app.ui.multiplayer.MultiplayerViewModel
import com.opencover.app.net.Protocol

/**
 * Racine Compose : observe l'état et affiche l'écran courant.
 * Navigation par état (zéro dépendance de navigation) : l'écran affiché
 * est piloté par [GameUiState.currentScreen].
 */
@Composable
fun OpenCoverAppRoot(
    viewModel: GameViewModel,
    multiplayerViewModel: MultiplayerViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state.currentScreen) {
            Screen.Home -> HomeScreen(
                onNewGame = { viewModel.navigate(Screen.Setup) },
                onMultiplayer = { viewModel.navigate(Screen.Multiplayer) }
            )

            Screen.Setup -> SetupScreen(
                playerCount = state.playerCount,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                threePlayerIsMrWhite = state.threePlayerIsMrWhite,
                onPlayerCountChange = viewModel::setPlayerCount,
                onCategoryChange = viewModel::setCategory,
                onThreePlayerIsMrWhiteChange = viewModel::setThreePlayerIsMrWhite,
                onNext = viewModel::startPlayerEntry,
                onBack = { viewModel.navigate(Screen.Home) }
            )

            Screen.Players -> PlayersScreen(
                playerNames = state.playerNames,
                onNameChange = viewModel::updatePlayerName,
                onShuffleNames = viewModel::shufflePlayerNames,
                onBack = { viewModel.navigate(Screen.Setup) },
                onStart = viewModel::startGame
            )

            Screen.Reveal -> {
                if (state.multiplayerHost) {
                    val host = state.players.firstOrNull { it.id == Protocol.HOST_PLAYER_ID }
                    if (host != null) {
                    RevealOwnScreen(
                        player = host,
                        waiting = Protocol.HOST_PLAYER_ID in state.revealAcks,
                        onDone = viewModel::hostRevealDone
                    )
                    }
                } else {
                    val player = state.players.getOrNull(state.revealIndex)
                    if (player != null) {
                        RevealScreen(
                            currentPlayer = player,
                            playerIndex = state.revealIndex,
                            totalPlayers = state.players.size,
                            onNext = viewModel::nextReveal
                        )
                    } else {
                        ComingSoon("Révélation")
                    }
                }
            }

            Screen.GameBoard -> {
                val result = state.result
                if (result != null) {
                    ResultScreen(
                        players = state.players,
                        result = result,
                        scores = state.totalScores,
                        onReplay = viewModel::replay,
                        onReset = if (state.multiplayerHost) {
                            {
                                viewModel.resetGame()
                                multiplayerViewModel.quit()
                                viewModel.navigate(Screen.Home)
                            }
                        } else {
                            viewModel::resetGame
                        },
                        isHost = state.multiplayerHost,
                        onHostReady = if (state.multiplayerHost) {
                            {
                                multiplayerViewModel.setReady(true)
                                multiplayerViewModel.goToHost()
                                viewModel.hostReturnToLobby()
                            }
                        } else {
                            {}
                        }
                    )
                } else {
                    val currentVoter = if (state.multiplayerHost) {
                        val host = state.players.firstOrNull { it.id == Protocol.HOST_PLAYER_ID }
                        val canVote = host != null &&
                            host.status == PlayerStatus.ACTIVE &&
                            Protocol.HOST_PLAYER_ID !in state.votes &&
                            (state.votePhase != VotePhase.SECOND_ROUND ||
                                Protocol.HOST_PLAYER_ID !in state.tiedCandidates)
                        if (canVote) host else null
                    } else {
                        val currentVoterId = state.voteOrder.getOrNull(state.currentVoterIndex)
                        state.players.firstOrNull { it.id == currentVoterId }
                    }
                    GameBoardScreen(
                        players = state.players,
                        category = state.wordPair?.category,
                        clueOrder = state.clueOrder,
                        roundNumber = state.roundNumber,
                        turnNumber = state.turnNumber,
                        votePhase = state.votePhase,
                        currentVoter = currentVoter,
                        tiedCandidates = state.tiedCandidates,
                        selfVote = state.multiplayerHost,
                        selfId = if (state.multiplayerHost) Protocol.HOST_PLAYER_ID else null,
                        onStartVote = viewModel::startVote,
                        onCastVote = if (state.multiplayerHost) viewModel::hostCastVote else viewModel::castVote
                    )
                }
            }

            Screen.Elimination -> {
                val elimination = state.elimination
                if (elimination != null) {
                    val pendingGuess = state.pendingMrWhiteGuess
                        ?.let { id -> state.players.firstOrNull { it.id == id } }
                    EliminationScreen(
                        elimination = elimination,
                        result = state.result,
                        turnNumber = state.turnNumber,
                        pendingMrWhiteGuess = pendingGuess,
                        isSelf = state.multiplayerHost && elimination.playerId == Protocol.HOST_PLAYER_ID,
                        onContinue = viewModel::continueAfterElimination,
                        onResolveMrWhiteGuess = viewModel::resolveMrWhiteGuess
                    )
                } else {
                    ComingSoon("Élimination")
                }
            }

            Screen.Multiplayer -> {
                val mpState by multiplayerViewModel.uiState.collectAsStateWithLifecycle()
                MultiplayerNavHost(
                    state = mpState,
                    onBack = {
                        multiplayerViewModel.quit()
                        viewModel.navigate(Screen.Home)
                    },
                    onUpdateServerUrl = multiplayerViewModel::updateServerUrl,
                    onUpdatePseudo = multiplayerViewModel::updatePseudo,
                    onStartHosting = multiplayerViewModel::startHosting,
                    onGoToJoin = multiplayerViewModel::goToJoin,
                    onJoinLobby = multiplayerViewModel::joinLobby,
                    onSetReady = multiplayerViewModel::setReady,
                    onGoToHostSetup = multiplayerViewModel::goToHostSetup,
                    onSetCategory = multiplayerViewModel::setCategory,
                    onSetThreePlayerIsMrWhite = multiplayerViewModel::setThreePlayerIsMrWhite,
                    onLaunchGame = {
                        viewModel.startHostGame(
                            mpState.members,
                            mpState.selectedCategory,
                            mpState.threePlayerIsMrWhite
                        )
                    },
                    onGuestRevealDone = multiplayerViewModel::guestRevealDone,
                    onGuestCastVote = multiplayerViewModel::guestCastVote,
                    onGuestSeeResults = multiplayerViewModel::guestSeeResults,
                    onGuestMarkReady = multiplayerViewModel::guestMarkReady
                )
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
