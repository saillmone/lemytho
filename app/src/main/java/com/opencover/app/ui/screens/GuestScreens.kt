package com.opencover.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencover.app.R
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.data.model.Role
import com.opencover.app.engine.Victory
import com.opencover.app.net.BoardSnapshot
import com.opencover.app.net.EliminationSnapshot
import com.opencover.app.net.ResultSnapshot
import com.opencover.app.ui.VotePhase
import com.opencover.app.ui.theme.SpecialElite

/**
 * Écran de révélation individuelle, partagé par l'hôte et les invités :
 * chaque joueur ne voit que SON rôle et SON mot (projection privée).
 */
@Composable
fun RevealOwnScreen(
    player: Player,
    waiting: Boolean,
    ackedCount: Int? = null,
    totalCount: Int? = null,
    onDone: () -> Unit
) {
    val isMrWhite = player.role == Role.MR_WHITE
    var isRevealed by remember { mutableStateOf(false) }

    // Le fond Mr White n'apparaît en plein écran que pendant l'appui.
    val backgroundId = if (isMrWhite && isRevealed) R.drawable.mr_white_bg
    else R.drawable.reveal_word_bg

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = backgroundId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            ScrimText(
                text = "Révélation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = player.pseudo,
                style = MaterialTheme.typography.titleLarge
            )

            if (waiting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScrimText(
                            text = "En attente des autres joueurs…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (ackedCount != null && totalCount != null) {
                            Spacer(Modifier.height(8.dp))
                            ScrimText(
                                text = "Prêts : $ackedCount / $totalCount",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Zone « maintenir pour révéler » : le doigt ne masque pas le mot.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isRevealed = true
                                    tryAwaitRelease()
                                    isRevealed = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRevealed) {
                        if (isMrWhite) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(y = (-88).dp)
                            ) {
                                ScrimText(
                                    text = "Tu es Mr White",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                ScrimText(
                                    text = "Tu ne reçois pas de mot.\nÉcoute les autres et devine le mot civil.",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ScrimText(
                                    text = "Ton mot secret :",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(12.dp))
                                ScrimText(
                                    text = player.assignedWord,
                                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = SpecialElite),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        ScrimText(
                            text = "Touche l'écran pour révéler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                enabled = !waiting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Je suis prêt pour la suite", fontSize = 18.sp)
            }
        }
    }
}

/**
 * Plateau public vu par un invité. Aucune information secrète : les rôles ne
 * sont affichés que pour les joueurs éliminés.
 */
@Composable
fun GuestBoardScreen(
    board: BoardSnapshot,
    myId: Int,
    hasVoted: Boolean,
    onCastVote: (Int) -> Unit
) {
    val rankById = board.clueOrder.withIndex().associate { (index, id) -> id to (index + 1) }
    val orderedPlayers = board.players.sortedBy { player ->
        when (player.status) {
            PlayerStatus.ACTIVE -> rankById[player.playerId] ?: Int.MAX_VALUE
            PlayerStatus.ELIMINATED -> Int.MAX_VALUE + player.playerId
        }
    }
    val shouldVote = shouldVote(board, myId)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.game_board_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            ScrimText(
                text = "Plateau de jeu",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrimText(
                    text = "Manche ${board.roundNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ScrimText(
                    text = "Tour ${board.turnNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (board.category != null) {
                Spacer(Modifier.height(8.dp))
                ScrimText(
                    text = "Catégorie : ${board.category}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(16.dp))

            ScrimText(
                text = guestStatusText(board.votePhase, hasVoted, shouldVote),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(orderedPlayers, key = { it.playerId }) { player ->
                    GuestPlayerCard(player, order = rankById[player.playerId], isMe = player.playerId == myId)
                }
            }
        }
    }

    if (shouldVote && !hasVoted) {
        val me = board.players.firstOrNull { it.playerId == myId } ?: return
        val targets = if (board.votePhase == VotePhase.SECOND_ROUND) {
            board.players.filter { it.playerId in board.tiedCandidates && it.playerId != myId }
        } else {
            board.players.filter { it.status == PlayerStatus.ACTIVE && it.playerId != myId }
        }
        VoteDialog(
            currentVoter = Player(id = myId, pseudo = me.pseudo, role = Role.CIVIL, assignedWord = ""),
            targets = targets.map { Player(id = it.playerId, pseudo = it.pseudo, role = Role.CIVIL, assignedWord = "") },
            isSecondRound = board.votePhase == VotePhase.SECOND_ROUND,
            isSelf = true,
            onCastVote = onCastVote
        )
    }
}

private fun shouldVote(board: BoardSnapshot, myId: Int): Boolean = when (board.votePhase) {
    VotePhase.IDLE -> false
    VotePhase.VOTING -> board.players.any { it.playerId == myId && it.status == PlayerStatus.ACTIVE }
    VotePhase.SECOND_ROUND -> myId !in board.tiedCandidates
}

private fun guestStatusText(votePhase: VotePhase, hasVoted: Boolean, shouldVote: Boolean): String = when {
    hasVoted -> "Vote enregistré, en attente des autres…"
    votePhase == VotePhase.IDLE -> "Donnez vos indices dans l'ordre, discutez… puis votez !"
    shouldVote && votePhase == VotePhase.SECOND_ROUND -> "Égalité : re-vote pour départager."
    shouldVote -> "À toi de voter !"
    else -> "Vote en cours…"
}

@Composable
private fun GuestPlayerCard(player: com.opencover.app.net.PublicPlayer, order: Int?, isMe: Boolean) {
    val eliminated = player.status == PlayerStatus.ELIMINATED
    val label = buildString {
        if (order != null) append("$order. ")
        append(player.pseudo)
        if (isMe) append(" (toi)")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (eliminated) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (eliminated) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            if (eliminated && player.role != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = roleIcon(player.role),
                        contentDescription = roleLabel(player.role),
                        tint = roleColor(player.role),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = roleLabel(player.role),
                        style = MaterialTheme.typography.bodyMedium,
                        color = roleColor(player.role)
                    )
                }
            }
        }
    }
}

/** Élimination vue par un invité : rôle révélé, phrase personnalisée si c'est lui. */
@Composable
fun GuestEliminationScreen(
    elimination: EliminationSnapshot,
    isMe: Boolean,
    hasResult: Boolean,
    onSeeResults: () -> Unit
) {
    val background = when (elimination.role) {
        Role.CIVIL -> R.drawable.eliminated_civil
        Role.UNDERCOVER -> R.drawable.eliminated_undercover
        Role.MR_WHITE -> R.drawable.eliminated_mrwhite
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            ScrimText(
                text = eliminationPhrase(elimination, isMe),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            if (!hasResult) {
                val preStepText = when {
                    elimination.guessResolved -> "Début du tour ${elimination.turnNumber}"
                    elimination.role == Role.MR_WHITE -> if (isMe) {
                        "Tu as une dernière chance de deviner le mot des Civils."
                    } else {
                        "${elimination.pseudo} a une dernière chance de deviner le mot des Civils."
                    }
                    else -> "Début du tour ${elimination.turnNumber}"
                }
                ScrimText(
                    text = preStepText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
            }

            ScrimText(
                text = nextStepText(hasResult),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (hasResult) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSeeResults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Voir les résultats", fontSize = 18.sp)
                }
            }
        }
    }
}

private fun eliminationPhrase(elimination: EliminationSnapshot, isMe: Boolean): String {
    val role = rolePhrase(elimination.role)
    return if (isMe) {
        "Tu étais $role, tu as été éliminé !"
    } else {
        "${elimination.pseudo} était $role, il a été éliminé !"
    }
}

private fun nextStepText(hasResult: Boolean): String = when {
    hasResult -> "La partie est terminée."
    else -> "En attente du Maître du Jeu…"
}

/** Résultat final vu par un invité (rôles de tous + scores cumulés). */
@Composable
fun GuestResultScreen(
    result: ResultSnapshot,
    wantsReplay: Boolean,
    onReady: () -> Unit,
    onQuit: () -> Unit
) {
    var showQuitConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.results_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            ScrimText(
                text = victoryTitle(result.victory, result.players),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            ScrimText(
                text = victorySubtitle(result.victory, result.players),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            ScrimText(
                text = "Score total",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                val ranked = result.players.sortedByDescending { result.totalScores[it.id] ?: 0 }
                itemsIndexed(ranked, key = { _, player -> player.id }) { index, player ->
                    val points = result.totalScores[player.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RankBadge(rank = index + 1)
                        Icon(
                            imageVector = roleIcon(player.role),
                            contentDescription = null,
                            tint = roleColor(player.role),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = player.pseudo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "+$points",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (points > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (points > 0) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (wantsReplay) {
                ScrimText(
                    text = "En attente du Maître du Jeu…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Button(
                    onClick = onReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prêt pour la prochaine manche", fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            ScrimTextButton(
                text = "Quitter",
                onClick = { showQuitConfirm = true }
            )
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quitter le salon ?") },
            text = {
                Text("Tu vas quitter le salon et être retiré de la partie.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirm = false
                    onQuit()
                }) {
                    Text("Quitter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
// --- Helpers (dupliqués volontairement pour des écrans stateless autonomes) ---

@Composable
private fun ScrimTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White
    )
}

@Composable
private fun RankBadge(rank: Int) {
    if (rank !in 1..3) {
        Spacer(Modifier.width(28.dp))
        return
    }
    val (background, foreground) = when (rank) {
        1 -> Color(0xFFD4AF37) to Color(0xFF3A2E00) // or
        2 -> Color(0xFFB8B8B8) to Color(0xFF2A2A2A) // argent
        else -> Color(0xFFCD7F32) to Color(0xFF331C00) // bronze
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
    }
    Spacer(Modifier.width(8.dp))
}

private fun rolePhrase(role: Role): String = when (role) {
    Role.CIVIL -> "un Civil"
    Role.UNDERCOVER -> "un Infiltré"
    Role.MR_WHITE -> "Mr White"
}

private fun roleLabel(role: Role): String = when (role) {
    Role.CIVIL -> "Civil"
    Role.UNDERCOVER -> "Infiltré"
    Role.MR_WHITE -> "Mr White"
}

private fun victoryTitle(result: Victory, players: List<Player>): String = when (result) {
    Victory.Ongoing -> "Partie en cours"
    Victory.Civil -> {
        val n = players.count { it.role == Role.CIVIL }
        if (n <= 1) "Victoire du Civil" else "Victoire des Civils"
    }
    Victory.Undercover -> {
        val n = players.count { it.role == Role.UNDERCOVER }
        if (n <= 1) "Victoire de l'Infiltré" else "Victoire des Infiltrés"
    }
    is Victory.MrWhite -> "Victoire de Mr White"
    Victory.Combined -> {
        val nU = players.count { it.role == Role.UNDERCOVER }
        val prefix = if (nU <= 1) "Victoire de l'Infiltré" else "Victoire des Infiltrés"
        "$prefix et de Mr White"
    }
}

private fun victorySubtitle(result: Victory, players: List<Player>): String = when (result) {
    Victory.Ongoing -> ""
    Victory.Civil -> "Tous les Infiltrés et Mr White ont été éliminés."
    Victory.Undercover -> "Au moins un Infiltré a survécu jusqu'à la fin."
    Victory.Combined -> {
        val undercovers = players.count { it.role == Role.UNDERCOVER }
        val mrWhites = players.count { it.role == Role.MR_WHITE }
        val undercoverLabel = if (undercovers <= 1) "l'Infiltré" else "les Infiltrés"
        val mrWhiteLabel = if (mrWhites <= 1) "Mr White" else "les Mr White"
        "Les Civils sont éliminés : $undercoverLabel et $mrWhiteLabel gagnent ensemble."
    }
    is Victory.MrWhite -> if (result.byGuess) {
        val winner = players.firstOrNull { it.id == result.winnerIds.firstOrNull() }
        "${winner?.pseudo ?: "Mr White"} a deviné le mot exact !"
    } else {
        "Mr White a survécu jusqu'à la fin."
    }
}

private fun roleIcon(role: Role): ImageVector = when (role) {
    Role.CIVIL -> Icons.Filled.Person
    Role.UNDERCOVER -> Icons.Filled.Masks
    Role.MR_WHITE -> Icons.Filled.VisibilityOff
}

@Composable
private fun roleColor(role: Role) = when (role) {
    Role.CIVIL -> MaterialTheme.colorScheme.primary
    Role.UNDERCOVER -> MaterialTheme.colorScheme.secondary
    Role.MR_WHITE -> MaterialTheme.colorScheme.tertiary
}
