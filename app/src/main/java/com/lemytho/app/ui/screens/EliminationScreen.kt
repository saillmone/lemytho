package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lemytho.app.R
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.engine.WordGuessMatcher
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
    canTypeGuess: Boolean,
    useSelfCopy: Boolean,
    onContinue: () -> Unit,
    onResolveUnknownGuess: (String) -> Unit
) {
    val background = when (elimination.role) {
        Role.CITIZEN -> R.drawable.eliminated_citizen
        Role.IMPOSTOR -> R.drawable.eliminated_impostor
        Role.UNKNOWN -> R.drawable.eliminated_unknown
    }
    val title = if (useSelfCopy) {
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val messages = eliminationMessages(
                    result = result,
                    pendingUnknownGuess = pendingUnknownGuess,
                    elimination = elimination,
                    canTypeGuess = canTypeGuess,
                    useSelfCopy = useSelfCopy,
                    turnNumber = turnNumber
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-128).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (messages.verdict != null) {
                        val verdictPill = @Composable {
                            ScrimText(
                                text = messages.verdict,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (elimination.guessResolved && elimination.guessCorrect) {
                            GuessWinSparkles { verdictPill() }
                        } else {
                            verdictPill()
                        }
                    }
                    if (messages.followUp != null) {
                        if (messages.verdict != null) {
                            Spacer(Modifier.height(8.dp))
                        }
                        ScrimText(
                            text = messages.followUp,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                ShowVotesControl(
                    votes = elimination.votes,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

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

    if (pendingUnknownGuess != null && canTypeGuess) {
        UnknownGuessDialog(onSubmit = onResolveUnknownGuess)
    }
}

private data class EliminationMessages(
    val verdict: String?,
    val followUp: String?
)

private fun eliminationMessages(
    result: Victory?,
    pendingUnknownGuess: Player?,
    elimination: EliminationEvent,
    canTypeGuess: Boolean,
    useSelfCopy: Boolean,
    turnNumber: Int
): EliminationMessages {
    val over = "La partie est terminée."
    val nextTurn = "Début du tour $turnNumber"
    return when {
        pendingUnknownGuess != null && canTypeGuess ->
            EliminationMessages(
                verdict = "Tu as une dernière chance de deviner le mot des Citoyens.",
                followUp = null
            )
        pendingUnknownGuess != null ->
            EliminationMessages(
                verdict = "${pendingUnknownGuess.pseudo} tente de deviner le mot des Citoyens…",
                followUp = null
            )
        elimination.guessResolved && elimination.guessCorrect ->
            EliminationMessages(
                verdict = unknownGuessVerdict(
                    guessText = elimination.guessText,
                    correct = true,
                    useSelfCopy = useSelfCopy,
                    pseudo = elimination.pseudo
                ),
                followUp = over
            )
        elimination.guessResolved ->
            EliminationMessages(
                verdict = unknownGuessVerdict(
                    guessText = elimination.guessText,
                    correct = false,
                    useSelfCopy = useSelfCopy,
                    pseudo = elimination.pseudo
                ),
                followUp = if (result != null) over else nextTurn
            )
        result != null -> EliminationMessages(verdict = null, followUp = over)
        else -> EliminationMessages(verdict = null, followUp = nextTurn)
    }
}

private fun unknownGuessVerdict(
    guessText: String?,
    correct: Boolean,
    useSelfCopy: Boolean,
    pseudo: String
): String {
    val win = if (useSelfCopy) "tu gagnes la partie !" else "il gagne la partie !"
    val quoted = guessText?.takeIf { it.isNotBlank() }
    return when {
        quoted != null && correct -> "\"$quoted\" était le mot des Citoyens : $win"
        quoted != null -> "\"$quoted\" n'était pas le mot des Citoyens."
        correct && useSelfCopy -> "Tu as trouvé le mot des Citoyens : $win"
        correct -> "$pseudo a trouvé le mot des Citoyens : $win"
        else -> "Ce n'était pas le mot des Citoyens."
    }
}

private fun rolePhrase(role: Role): String = when (role) {
    Role.CITIZEN -> "un Citoyen"
    Role.IMPOSTOR -> "un Imposteur"
    Role.UNKNOWN -> "l'Inconnu"
}

@Composable
private fun UnknownGuessDialog(
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val trimmed = text.trim()
    val canSubmit = trimmed.isNotEmpty() && !submitted
    val submit = {
        if (canSubmit) {
            submitted = true
            onSubmit(trimmed)
        }
    }
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
                    text = "Tu as été éliminé. En tant qu'Inconnu, tu disposes d'une dernière tentative pour deviner le mot des Citoyens.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= WordGuessMatcher.MAX_GUESS_LENGTH) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !submitted,
                    label = { Text("Le mot des Citoyens") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = submit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Proposer le mot")
                }
            }
        }
    }
}
