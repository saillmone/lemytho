package com.opencover.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.opencover.app.R
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.Role
import com.opencover.app.engine.Victory
import com.opencover.app.ui.EliminationEvent

/**
 * Écran intermédiaire affiché après un vote : révèle le joueur éliminé et son rôle,
 * puis annonce la suite (fin de partie, tour suivant ou ultime tentative de Mr White).
 */
@Composable
fun EliminationScreen(
    elimination: EliminationEvent,
    result: Victory?,
    turnNumber: Int,
    pendingMrWhiteGuess: Player?,
    isSelf: Boolean = false,
    onContinue: () -> Unit,
    onResolveMrWhiteGuess: (Boolean) -> Unit
) {
    val background = when (elimination.role) {
        Role.CIVIL -> R.drawable.eliminated_civil
        Role.UNDERCOVER -> R.drawable.eliminated_undercover
        Role.MR_WHITE -> R.drawable.eliminated_mrwhite
    }
    val title = if (isSelf) {
        "Tu étais ${rolePhrase(elimination.role)}, tu as été éliminé !"
    } else {
        "${elimination.pseudo} était ${rolePhrase(elimination.role)}, il a été éliminé !"
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            ScrimText(
                text = nextStepText(result, pendingMrWhiteGuess, turnNumber),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                enabled = pendingMrWhiteGuess == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (result != null) "Voir les résultats" else "Continuer",
                    fontSize = 18.sp
                )
            }
        }
    }

    if (pendingMrWhiteGuess != null) {
        MrWhiteGuessDialog(
            player = pendingMrWhiteGuess,
            onResolve = onResolveMrWhiteGuess
        )
    }
}

private fun nextStepText(result: Victory?, pendingMrWhiteGuess: Player?, turnNumber: Int): String =
    when {
        result != null -> "La partie est terminée."
        pendingMrWhiteGuess != null ->
            "${pendingMrWhiteGuess.pseudo} a une dernière chance de deviner le mot des Civils."
        else -> "Début du tour $turnNumber"
    }

private fun rolePhrase(role: Role): String = when (role) {
    Role.CIVIL -> "un Civil"
    Role.UNDERCOVER -> "un Infiltré"
    Role.MR_WHITE -> "Mr White"
}

@Composable
private fun MrWhiteGuessDialog(
    player: Player,
    onResolve: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = { /* décision obligatoire : pas de fermeture */ }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Ultime tentative",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${player.pseudo} a été éliminé. En tant que Mr White, il dispose d'une dernière tentative pour deviner le mot exact des Civils.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onResolve(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Il a trouvé le mot")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onResolve(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Il s'est trompé")
                }
            }
        }
    }
}
