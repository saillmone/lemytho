package com.opencover.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencover.app.R
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.Role
import com.opencover.app.engine.Victory

@Composable
fun ResultScreen(
    players: List<Player>,
    result: Victory,
    scores: Map<Int, Int>,
    onReplay: () -> Unit,
    onReset: () -> Unit
) {
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
                text = victoryTitle(result),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            ScrimText(
                text = victorySubtitle(result, players),
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
                items(players, key = { it.id }) { player ->
                    val points = scores[player.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = roleIcon(player.role),
                            contentDescription = roleLabel(player.role),
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

            Button(
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rejouer", fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nouvelle partie")
            }
        }
    }
}

private fun victoryTitle(result: Victory): String = when (result) {
    Victory.Ongoing -> "Partie en cours"
    Victory.Civil -> "Victoire des Civils"
    Victory.Undercover -> "Victoire des Infiltrés"
    is Victory.MrWhite -> "Victoire de Mr White"
    Victory.Combined -> "Victoire des Infiltrés et de Mr White"
}

@Composable
private fun roleIcon(role: Role): ImageVector = when (role) {
    Role.CIVIL -> Icons.Filled.Person
    Role.UNDERCOVER -> Icons.Filled.Masks
    Role.MR_WHITE -> Icons.Filled.VisibilityOff
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

private fun victorySubtitle(result: Victory, players: List<Player>): String = when (result) {
    Victory.Ongoing -> ""
    Victory.Civil -> "Tous les Infiltrés et Mr White ont été éliminés."
    Victory.Undercover -> "Au moins un Infiltré a survécu jusqu'à la fin."
    Victory.Combined -> "Les Civils sont éliminés : Infiltrés et Mr White gagnent ensemble."
    is Victory.MrWhite -> if (result.byGuess) {
        val winner = players.firstOrNull { it.id == result.winnerIds.firstOrNull() }
        "${winner?.pseudo ?: "Mr White"} a deviné le mot exact !"
    } else {
        "Mr White a survécu jusqu'à la fin."
    }
}
