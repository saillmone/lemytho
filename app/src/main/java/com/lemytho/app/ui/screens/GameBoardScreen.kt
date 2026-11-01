package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemytho.app.R
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.ui.NoElimination
import com.lemytho.app.ui.VotePhase
import com.lemytho.app.ui.noEliminationHint
import com.lemytho.app.ui.theme.CitizenCyan
import com.lemytho.app.ui.theme.ImpostorRed

@Composable
fun GameBoardScreen(
    players: List<Player>,
    category: String?,
    clueOrder: List<Int>,
    roundNumber: Int,
    turnNumber: Int,
    votePhase: VotePhase,
    currentVoter: Player?,
    tiedCandidates: Set<Int>,
    selfVote: Boolean = false,
    selfId: Int? = null,
    hasVoted: Boolean = false,
    offlinePlayerIds: Set<Int> = emptySet(),
    votedPlayerIds: Set<Int> = emptySet(),
    noElimination: NoElimination? = null,
    onStartVote: () -> Unit,
    onCastVote: (Int) -> Unit
) {
    // Rang de passage (1-indexé) par joueur actif, selon l'ordre des indices.
    val rankById = clueOrder.withIndex().associate { (index, id) -> id to (index + 1) }
    val orderedPlayers = players.sortedBy { player ->
        when (player.status) {
            PlayerStatus.ACTIVE -> rankById[player.id] ?: Int.MAX_VALUE
            PlayerStatus.ELIMINATED -> Int.MAX_VALUE + player.id
        }
    }

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
                    text = "Manche $roundNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ScrimText(
                    text = "Tour $turnNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (category != null) {
                Spacer(Modifier.height(8.dp))
                ScrimText(
                    text = "Catégorie : $category",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(16.dp))

            val statusText = when {
                hasVoted -> "Vote enregistré, en attente des autres…"
                votePhase == VotePhase.IDLE ->
                    noEliminationHint(noElimination)
                        ?: "Donnez vos indices dans l'ordre, discutez… puis votez !"
                votePhase == VotePhase.SECOND_ROUND -> "Égalité : re-vote pour départager."
                else -> "Vote en cours…"
            }
            BoardStatusText(text = statusText)

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(orderedPlayers, key = { it.id }) { player ->
                    PlayerCard(
                        player,
                        order = rankById[player.id],
                        selfId = selfId,
                        offline = player.id in offlinePlayerIds,
                        hasVoted = votePhase != VotePhase.IDLE && player.id in votedPlayerIds
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStartVote,
                enabled = votePhase == VotePhase.IDLE,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lancer le vote", fontSize = 18.sp)
            }
        }
    }

    if (votePhase != VotePhase.IDLE && currentVoter != null) {
        val targets = if (votePhase == VotePhase.SECOND_ROUND) {
            players.filter { it.id in tiedCandidates && it.id != currentVoter.id }
        } else {
            players.filter { it.status == PlayerStatus.ACTIVE && it.id != currentVoter.id }
        }
        VoteDialog(
            currentVoter = currentVoter,
            targets = targets,
            isSecondRound = votePhase == VotePhase.SECOND_ROUND,
            isSelf = selfVote,
            onCastVote = onCastVote
        )
    }
}

@Composable
private fun PlayerCard(
    player: Player,
    order: Int? = null,
    selfId: Int? = null,
    offline: Boolean = false,
    hasVoted: Boolean = false
) {
    val eliminated = player.status == PlayerStatus.ELIMINATED
    val label = buildString {
        if (order != null) append("$order. ")
        append(player.pseudo)
        if (player.id == selfId) append(" (toi)")
        if (hasVoted) append(" (a voté)")
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selfId != null) {
                    PresenceDot(connected = !offline)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (eliminated) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            if (eliminated) {
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

private fun roleLabel(role: Role): String = when (role) {
    Role.CITIZEN -> "Citoyen"
    Role.IMPOSTOR -> "Imposteur"
    Role.UNKNOWN -> "Inconnu"
}

private fun roleIcon(role: Role): ImageVector = when (role) {
    Role.CITIZEN -> Icons.Filled.Person
    Role.IMPOSTOR -> Icons.Filled.Masks
    Role.UNKNOWN -> Icons.Filled.VisibilityOff
}

@Composable
private fun roleColor(role: Role) = when (role) {
    Role.CITIZEN -> MaterialTheme.colorScheme.primary
    Role.IMPOSTOR -> MaterialTheme.colorScheme.secondary
    Role.UNKNOWN -> MaterialTheme.colorScheme.tertiary
}

/**
 * Bandeau hôte : joueurs hors ligne, « Ne plus attendre », « Annuler la manche ».
 */
@Composable
fun HostRoundOverlay(
    players: List<Player>,
    offlinePlayerIds: Set<Int>,
    skippableIds: Set<Int>,
    onSkipWait: (Int) -> Unit,
    onCancelRound: () -> Unit,
    content: @Composable () -> Unit
) {
    var skipTarget by remember { mutableStateOf<Player?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    val skippable = players.filter {
        it.id in skippableIds && it.status == PlayerStatus.ACTIVE
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (skippable.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                StatusBanner(accent = ImpostorRed) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        skippable.forEach { player ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Hors ligne : ${player.pseudo}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = { skipTarget = player }) {
                                    Text("Ne plus attendre")
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
            IconButton(
                onClick = { confirmCancel = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Annuler la manche",
                    tint = Color.White
                )
            }
        }
    }
    skipTarget?.let { player ->
        AlertDialog(
            onDismissRequest = { skipTarget = null },
            title = { Text("Ne plus attendre ?") },
            text = {
                Text("La table n'attendra plus ${player.pseudo} pour cette étape.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onSkipWait(player.id)
                    skipTarget = null
                }) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { skipTarget = null }) { Text("Annuler") }
            }
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Annuler la manche ?") },
            text = {
                Text("Retour au salon. Les scores de cette manche ne seront pas comptés. Le salon reste ouvert.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancelRound()
                }) { Text("Annuler la manche") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Revenir") }
            }
        )
    }
}

@Composable
fun StatusBanner(
    accent: Color = CitizenCyan,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
