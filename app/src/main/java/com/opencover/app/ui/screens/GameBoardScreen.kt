package com.opencover.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencover.app.R
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.data.model.Role
import com.opencover.app.ui.VotePhase

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

            val statusText = when (votePhase) {
                VotePhase.IDLE -> "Donnez vos indices dans l'ordre, discutez… puis votez !"
                VotePhase.SECOND_ROUND -> "Égalité : re-vote pour départager."
                VotePhase.VOTING -> "Vote en cours…"
            }
            ScrimText(
                text = statusText,
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
                items(orderedPlayers, key = { it.id }) { player ->
                    PlayerCard(player, order = rankById[player.id], selfId = selfId)
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
private fun PlayerCard(player: Player, order: Int? = null, selfId: Int? = null) {
    val eliminated = player.status == PlayerStatus.ELIMINATED
    val label = buildString {
        if (order != null) append("$order. ")
        append(player.pseudo)
        if (player.id == selfId) append(" (toi)")
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
            if (eliminated) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = roleLabel(player.role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = roleColor(player.role)
                )
            }
        }
    }
}

private fun roleLabel(role: Role): String = when (role) {
    Role.CIVIL -> "Civil"
    Role.UNDERCOVER -> "Infiltré"
    Role.MR_WHITE -> "Mr White"
}

@Composable
private fun roleColor(role: Role) = when (role) {
    Role.CIVIL -> MaterialTheme.colorScheme.primary
    Role.UNDERCOVER -> MaterialTheme.colorScheme.secondary
    Role.MR_WHITE -> MaterialTheme.colorScheme.tertiary
}
