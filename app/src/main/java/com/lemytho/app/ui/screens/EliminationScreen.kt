package com.lemytho.app.ui.screens

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
import com.lemytho.app.R
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.ui.EliminationEvent

/**
 * Écran intermédiaire affiché après un vote : révèle le joueur éliminé et son rôle,
 * puis annonce la suite (fin de partie, tour suivant ou ultime tentative de l'Inconnu).
 */
@Composable
fun EliminationScreen(
    elimination: EliminationEvent,
    result: Victory?,
    turnNumber: Int,
    pendingUnknownGuess: Player?,
    isSelf: Boolean = false,
    onContinue: () -> Unit,
    onResolveUnknownGuess: (Boolean) -> Unit
) {
    val background = when (elimination.role) {
        Role.CITIZEN -> R.drawable.eliminated_citizen
        Role.IMPOSTOR -> R.drawable.eliminated_impostor
        Role.UNKNOWN -> R.drawable.eliminated_unknown
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
                text = nextStepText(result, pendingUnknownGuess, turnNumber, isSelf),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                enabled = pendingUnknownGuess == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (result != null) "Voir les résultats" else "Continuer",
                    fontSize = 18.sp
                )
            }
        }
    }

    if (pendingUnknownGuess != null) {
        UnknownGuessDialog(
            player = pendingUnknownGuess,
            isSelf = isSelf,
            onResolve = onResolveUnknownGuess
        )
    }
}

private fun nextStepText(
    result: Victory?,
    pendingUnknownGuess: Player?,
    turnNumber: Int,
    isSelf: Boolean
): String =
    when {
        result != null -> "La partie est terminée."
        pendingUnknownGuess != null -> if (isSelf) {
            "Tu as une dernière chance de deviner le mot des Citoyens."
        } else {
            "${pendingUnknownGuess.pseudo} a une dernière chance de deviner le mot des Citoyens."
        }
        else -> "Début du tour $turnNumber"
    }

private fun rolePhrase(role: Role): String = when (role) {
    Role.CITIZEN -> "un Citoyen"
    Role.IMPOSTOR -> "un Imposteur"
    Role.UNKNOWN -> "l'Inconnu"
}

@Composable
private fun UnknownGuessDialog(
    player: Player,
    isSelf: Boolean,
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
                    text = if (isSelf) {
                        "Tu as été éliminé. En tant qu'Inconnu, tu disposes d'une dernière tentative pour deviner le mot exact des Citoyens."
                    } else {
                        "${player.pseudo} a été éliminé. En tant qu'Inconnu, il dispose d'une dernière tentative pour deviner le mot exact des Citoyens."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onResolve(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSelf) "Tu as trouvé le mot" else "Il a trouvé le mot")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onResolve(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSelf) "Tu t'es trompé" else "Il s'est trompé")
                }
            }
        }
    }
}
