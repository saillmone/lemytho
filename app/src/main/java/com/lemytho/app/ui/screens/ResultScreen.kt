package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemytho.app.R
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory

@Composable
fun ResultScreen(
    players: List<Player>,
    result: Victory,
    scores: Map<Int, Int>,
    onReplay: () -> Unit,
    onReset: () -> Unit,
    isHost: Boolean = false,
    onHostReady: () -> Unit = {}
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
                text = victoryTitle(result, players),
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
                val ranked = players.sortedByDescending { scores[it.id] ?: 0 }
                itemsIndexed(ranked, key = { _, player -> player.id }) { index, player ->
                    val points = scores[player.id] ?: 0
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

            if (isHost) {
                Button(
                    onClick = onHostReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Prêt pour la prochaine manche", fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = onReplay,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rejouer", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            ScrimTextButton(
                text = "Quitter",
                onClick = { if (isHost) showQuitConfirm = true else onReset() }
            )
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quitter le salon ?") },
            text = {
                Text("En quittant, tu fermes le salon et tous les joueurs seront éjectés.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirm = false
                    onReset()
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

private fun victoryTitle(result: Victory, players: List<Player>): String = when (result) {
    Victory.Ongoing -> "Partie en cours"
    Victory.Citizen -> {
        val n = players.count { it.role == Role.CITIZEN }
        if (n <= 1) "Victoire du Citoyen" else "Victoire des Citoyens"
    }
    Victory.Impostor -> {
        val n = players.count { it.role == Role.IMPOSTOR }
        if (n <= 1) "Victoire de l'Imposteur" else "Victoire des Imposteurs"
    }
    is Victory.Unknown -> "Victoire de l'Inconnu"
    Victory.Combined -> {
        val nI = players.count { it.role == Role.IMPOSTOR }
        val prefix = if (nI <= 1) "Victoire de l'Imposteur" else "Victoire des Imposteurs"
        "$prefix et de l'Inconnu"
    }
}

@Composable
private fun roleIcon(role: Role): ImageVector = when (role) {
    Role.CITIZEN -> Icons.Filled.Person
    Role.IMPOSTOR -> Icons.Filled.Masks
    Role.UNKNOWN -> Icons.Filled.VisibilityOff
}

private fun roleLabel(role: Role): String = when (role) {
    Role.CITIZEN -> "Citoyen"
    Role.IMPOSTOR -> "Imposteur"
    Role.UNKNOWN -> "Inconnu"
}

@Composable
private fun roleColor(role: Role) = when (role) {
    Role.CITIZEN -> MaterialTheme.colorScheme.primary
    Role.IMPOSTOR -> MaterialTheme.colorScheme.secondary
    Role.UNKNOWN -> MaterialTheme.colorScheme.tertiary
}

private fun victorySubtitle(result: Victory, players: List<Player>): String = when (result) {
    Victory.Ongoing -> ""
    Victory.Citizen -> "Tous les Imposteurs et les Inconnus ont été éliminés."
    Victory.Impostor -> "Au moins un Imposteur a survécu jusqu'à la fin."
    Victory.Combined -> {
        val impostors = players.count { it.role == Role.IMPOSTOR }
        val unknowns = players.count { it.role == Role.UNKNOWN }
        val impostorLabel = if (impostors <= 1) "l'Imposteur" else "les Imposteurs"
        val unknownLabel = if (unknowns <= 1) "l'Inconnu" else "les Inconnus"
        "Les Citoyens sont éliminés : $impostorLabel et $unknownLabel gagnent ensemble."
    }
    is Victory.Unknown -> if (result.byGuess) {
        val winner = players.firstOrNull { it.id == result.winnerIds.firstOrNull() }
        "${winner?.pseudo ?: "l'Inconnu"} a deviné le mot exact !"
    } else {
        "l'Inconnu a survécu jusqu'à la fin."
    }
}
