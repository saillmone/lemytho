package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemytho.app.R

@Composable
fun PlayersScreen(
    playerNames: List<String>,
    onNameChange: (Int, String) -> Unit,
    onShuffleNames: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    val allFilled = playerNames.isNotEmpty() && playerNames.all { it.isNotBlank() }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.players_bg),
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
                text = "Joueurs",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            ScrimText(
                text = "Saisis le pseudo de chaque joueur",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onShuffleNames,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mélanger les pseudos")
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(playerNames) { index, name ->
                    OutlinedTextField(
                        value = name,
                        onValueChange = { onNameChange(index, it) },
                        label = { Text("Joueur ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStart,
                enabled = allFilled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lancer la partie", fontSize = 18.sp)
            }

            ScrimTextButton(
                text = "Retour",
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ScrimTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = textAlign,
        color = Color.White
    )
}
