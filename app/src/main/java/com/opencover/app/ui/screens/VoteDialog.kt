package com.opencover.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.opencover.app.data.model.Player

/**
 * Modale de vote (tour séquentiel ouvert). Non fermable : le joueur courant
 * doit voter pour passer au suivant.
 */
@Composable
fun VoteDialog(
    currentVoter: Player,
    targets: List<Player>,
    isSecondRound: Boolean,
    isSelf: Boolean = false,
    onCastVote: (Int) -> Unit
) {
    Dialog(onDismissRequest = { /* vote obligatoire : pas de fermeture */ }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isSecondRound) "Égalité !" else "Vote",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isSecondRound) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Re-votez pour départager les ex æquo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Qui est l'intrus d'après ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append(if (isSelf) "toi" else currentVoter.pseudo)
                        }
                        append(" ?")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                targets.forEach { target ->
                    Button(
                        onClick = { onCastVote(target.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(target.pseudo, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
