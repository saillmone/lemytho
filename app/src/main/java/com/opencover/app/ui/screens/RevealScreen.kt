package com.opencover.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencover.app.R
import com.opencover.app.data.model.Player
import com.opencover.app.data.model.Role
import com.opencover.app.ui.theme.SpecialElite

@Composable
fun RevealScreen(
    currentPlayer: Player,
    playerIndex: Int,
    totalPlayers: Int,
    onNext: () -> Unit
) {
    val isLast = playerIndex == totalPlayers - 1
    val isMrWhite = currentPlayer.role == Role.MR_WHITE

    var isRevealed by remember { mutableStateOf(false) }

    // Fond identique pour tous tant que rien n'est révélé : l'image Mr White
    // n'apparaît en plein écran que pendant l'appui d'un joueur Mr White.
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
            ScrimCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Révélation",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Joueur ${playerIndex + 1} / $totalPlayers",
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentPlayer.pseudo,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            // Zone « maintenir pour révéler » : occupe tout l'espace restant
            // au-dessus du bouton pour que le doigt ne masque pas la révélation.
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
                        ScrimCard(modifier = Modifier.offset(y = (-88).dp)) {
                            Text(
                                text = "Tu es Mr White",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Tu ne reçois pas de mot.\nÉcoute les autres et devine le mot civil.",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        ScrimCard {
                            Text(
                                text = "Ton mot secret :",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = currentPlayer.assignedWord,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontFamily = SpecialElite
                                ),
                                color = Color.White,
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

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isLast) "Commencer la partie" else "Joueur suivant",
                    fontSize = 18.sp
                )
            }
        }
    }
}

/**
 * Rectangle arrondi translucide : l'assombrissement ne porte que sur le texte
 * qu'il contient, sans voiler l'image de fond plein écran.
 */
@Composable
private fun ScrimCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
