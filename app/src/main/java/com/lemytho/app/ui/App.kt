package com.lemytho.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.ui.screens.GameBoardScreen
import com.lemytho.app.ui.screens.HostRoundOverlay
import com.lemytho.app.ui.screens.HomeScreen
import com.lemytho.app.ui.screens.PlayersScreen
import com.lemytho.app.ui.screens.ResultScreen
import com.lemytho.app.ui.screens.RevealScreen
import com.lemytho.app.ui.screens.RulesScreen
import com.lemytho.app.ui.screens.SetupScreen
import com.lemytho.app.ui.screens.EliminationScreen
import com.lemytho.app.ui.screens.MultiplayerNavHost
import com.lemytho.app.ui.screens.RevealOwnScreen
import com.lemytho.app.ui.multiplayer.MultiplayerViewModel
import com.lemytho.app.net.GameProtocol
import com.lemytho.app.net.Protocol

/**
 * Racine Compose : observe l'état et affiche l'écran courant.
 * Navigation par état (zéro dépendance de navigation) : l'écran affiché
 * est piloté par [GameUiState.currentScreen].
 */
@Composable
fun LeMythoAppRoot(
    viewModel: GameViewModel,
    multiplayerViewModel: MultiplayerViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mpState by multiplayerViewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    DisposableEffect(mpState.lobbyCode) {
        val window = (view.context as Activity).window
        if (mpState.lobbyCode != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.hostAbortReason) {
        val reason = state.hostAbortReason
        if (reason != null) {
            multiplayerViewModel.abortToLobby(GameProtocol.cancelledMessage(reason))
            viewModel.acknowledgeAbort()
        }
    }

    LaunchedEffect(mpState.members) {
        viewModel.syncSpectators(mpState.members)
    }

    LaunchedEffect(mpState.hostSeatResumedTick) {
        if (mpState.hostSeatResumedTick > 0) {
            viewModel.onHostSeatResumed(mpState.hostResumeInRound)
        }
    }

    LaunchedEffect(mpState.lobbyCode) {
        if (mpState.lobbyCode != null && state.currentScreen == Screen.Home) {
            viewModel.navigate(Screen.Multiplayer)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state.currentScreen) {
            Screen.Home -> HomeScreen(
                onNewGame = { viewModel.navigate(Screen.Setup) },
                onMultiplayer = { viewModel.navigate(Screen.Multiplayer) },
                onRules = { viewModel.navigate(Screen.Rules) }
            )

            Screen.Rules -> RulesScreen(
                onClose = { viewModel.navigate(Screen.Home) }
            )

            Screen.Setup -> SetupScreen(
                playerCount = state.playerCount,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                threePlayerIsUnknown = state.threePlayerIsUnknown,
                onPlayerCountChange = viewModel::setPlayerCount,
                onCategoryChange = viewModel::setCategory,
                onThreePlayerIsUnknownChange = viewModel::setThreePlayerIsUnknown,
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
                    HostRoundOverlay(
                        players = state.players,
                        offlinePlayerIds = state.offlinePlayerIds,
                        skippableIds = state.skippablePlayerIds(),
                        onSkipWait = viewModel::skipWait,
                        onCancelRound = viewModel::cancelRound
                    ) {
                    RevealOwnScreen(
                        player = host,
                        waiting = Protocol.HOST_PLAYER_ID in state.revealAcks,
                        ackedCount = state.revealAcks.size,
                        totalCount = state.players.size,
                        onDone = viewModel::hostRevealDone
                    )
                    }
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
                        unknownGuessCorrect = state.unknownGuessCorrect,
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
                    val board: @Composable () -> Unit = {
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
                        hasVoted = state.multiplayerHost && Protocol.HOST_PLAYER_ID in state.votes,
                        offlinePlayerIds = state.offlinePlayerIds,
                        votedPlayerIds = if (state.votePhase == VotePhase.IDLE) {
                            emptySet()
                        } else {
                            state.votes.keys
                        },
                        noElimination = state.noElimination,
                        onStartVote = viewModel::startVote,
                        onCastVote = if (state.multiplayerHost) viewModel::hostCastVote else viewModel::castVote
                    )
                    }
                    if (state.multiplayerHost) {
                        HostRoundOverlay(
                            players = state.players,
                            offlinePlayerIds = state.offlinePlayerIds,
                            skippableIds = state.skippablePlayerIds(),
                            onSkipWait = viewModel::skipWait,
                            onCancelRound = viewModel::cancelRound,
                            content = board
                        )
                    } else {
                        board()
                    }
                }
            }

            Screen.Elimination -> {
                val elimination = state.elimination
                if (elimination != null) {
                    val pendingGuess = state.pendingUnknownGuess
                        ?.let { id -> state.players.firstOrNull { it.id == id } }
                    val isTypingDevice = !state.multiplayerHost ||
                        elimination.playerId == Protocol.HOST_PLAYER_ID
                    val eliminationUi: @Composable () -> Unit = {
                    EliminationScreen(
                        elimination = elimination,
                        result = state.result,
                        turnNumber = state.turnNumber,
                        pendingUnknownGuess = pendingGuess,
                        canTypeGuess = pendingGuess != null && isTypingDevice,
                        useSelfCopy = isTypingDevice && elimination.role == Role.UNKNOWN,
                        myPseudo = if (state.multiplayerHost) {
                            state.players.firstOrNull { it.id == Protocol.HOST_PLAYER_ID }?.pseudo
                        } else {
                            null
                        },
                        onContinue = viewModel::continueAfterElimination,
                        onResolveUnknownGuess = viewModel::resolveUnknownGuess
                    )
                    }
                    if (state.multiplayerHost) {
                        HostRoundOverlay(
                            players = state.players,
                            offlinePlayerIds = state.offlinePlayerIds,
                            skippableIds = state.skippablePlayerIds(),
                            onSkipWait = viewModel::skipWait,
                            onCancelRound = viewModel::cancelRound,
                            content = eliminationUi
                        )
                    } else {
                        eliminationUi()
                    }
                } else {
                    ComingSoon("Élimination")
                }
            }

            Screen.Multiplayer -> {
                MultiplayerNavHost(
                    state = mpState,
                    onBack = {
                        multiplayerViewModel.quit()
                        viewModel.navigate(Screen.Home)
                    },
                    onUpdateServerUrl = multiplayerViewModel::updateServerUrl,
                    onResetServerUrl = multiplayerViewModel::resetServerUrl,
                    onUpdatePseudo = multiplayerViewModel::updatePseudo,
                    onRandomPseudo = multiplayerViewModel::randomPseudo,
                    onStartHosting = multiplayerViewModel::startHosting,
                    onGoToJoin = multiplayerViewModel::goToJoin,
                    onJoinLobby = multiplayerViewModel::joinLobby,
                    onSetReady = multiplayerViewModel::setReady,
                    onGoToHostSetup = multiplayerViewModel::goToHostSetup,
                    onBackToHostLobby = multiplayerViewModel::backToHostLobby,
                    onBackToMenu = multiplayerViewModel::backToMenu,
                    onSetCategory = multiplayerViewModel::setCategory,
                    onSetThreePlayerIsUnknown = multiplayerViewModel::setThreePlayerIsUnknown,
                    onLaunchGame = {
                        viewModel.startHostGame(
                            mpState.members,
                            mpState.selectedCategory,
                            mpState.threePlayerIsUnknown
                        )
                    },
                    onGuestRevealDone = multiplayerViewModel::guestRevealDone,
                    onGuestCastVote = multiplayerViewModel::guestCastVote,
                    onGuestSubmitGuess = multiplayerViewModel::guestSubmitGuess,
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
