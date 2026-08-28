package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemytho.app.BuildConfig
import com.lemytho.app.R
import com.lemytho.app.ui.theme.UnknownWhite
import com.lemytho.app.ui.theme.SpecialElite

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onMultiplayer: () -> Unit,
    onRules: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.home_bg),
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
            // Contenu placé en haut de l'écran (≈ 20 % depuis le haut).
            Spacer(Modifier.weight(0.25f))

            Text(
                text = "Le Mytho",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = SpecialElite,
                    letterSpacing = 1.sp
                ),
                color = UnknownWhite,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Jeu de rôles cachés",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Jouer sur un seul écran", fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onMultiplayer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Jouer sur plusieurs écrans", fontSize = 18.sp)
            }

            Spacer(Modifier.weight(1f))

            ScrimText(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        // Accès aux règles : petite icône ronde en haut à droite.
        IconButton(
            onClick = onRules,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = "Règles",
                tint = Color.White
            )
        }
    }
}
