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
import com.opencover.app.ui.screens.GameBoardScreen
import com.opencover.app.ui.screens.HomeScreen
import com.opencover.app.ui.screens.PlayersScreen
import com.opencover.app.ui.screens.ResultScreen
import com.opencover.app.ui.screens.RevealScreen
import com.opencover.app.ui.screens.SetupScreen
import com.opencover.app.ui.screens.EliminationScreen

/**
 * Racine Compose : observe l'état et affiche l'écran courant.
 * Navigation par état (zéro dépendance de navigation) : l'écran affiché
 * est piloté par [GameUiState.currentScreen].
 */
@Composable
fun OpenCoverAppRoot(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state.currentScreen) {
            Screen.Home -> HomeScreen(onNewGame = { viewModel.navigate(Screen.Setup) })

            Screen.Setup -> SetupScreen(
                playerCount = state.playerCount,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                threePlayerIsMrWhite = state.threePlayerIsMrWhite,
                onPlayerCountChange = viewModel::setPlayerCount,
                onCategoryChange = viewModel::setCategory,
                onThreePlayerIsMrWhiteChange = viewModel::setThreePlayerIsMrWhite,
                onNext = viewModel::startPlayerEntry
            )

            Screen.Players -> PlayersScreen(
                playerNames = state.playerNames,
                onNameChange = viewModel::updatePlayerName,
                onShuffleNames = viewModel::shufflePlayerNames,
                onBack = { viewModel.navigate(Screen.Setup) },
                onStart = viewModel::startGame
            )

            Screen.Reveal -> {
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

            Screen.GameBoard -> {
                val result = state.result
                if (result != null) {
                    ResultScreen(
                        players = state.players,
                        result = result,
                        scores = state.totalScores,
                        onReplay = viewModel::replay,
                        onReset = viewModel::resetGame
                    )
                } else {
                    val currentVoterId = state.voteOrder.getOrNull(state.currentVoterIndex)
                    val currentVoter = state.players.firstOrNull { it.id == currentVoterId }
                    GameBoardScreen(
                        players = state.players,
                        category = state.wordPair?.category,
                        clueOrder = state.clueOrder,
                        roundNumber = state.roundNumber,
                        turnNumber = state.turnNumber,
                        votePhase = state.votePhase,
                        currentVoter = currentVoter,
                        tiedCandidates = state.tiedCandidates,
                        onStartVote = viewModel::startVote,
                        onCastVote = viewModel::castVote
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
                        onContinue = viewModel::continueAfterElimination,
                        onResolveMrWhiteGuess = viewModel::resolveMrWhiteGuess
                    )
                } else {
                    ComingSoon("Élimination")
                }
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
