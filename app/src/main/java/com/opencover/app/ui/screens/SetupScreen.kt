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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencover.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    playerCount: Int,
    categories: List<String>,
    selectedCategory: String?,
    threePlayerIsMrWhite: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onThreePlayerIsMrWhiteChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.setup_bg),
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
                text = "Configuration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(32.dp))

            ScrimText(
                text = "Nombre de joueurs",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = { if (playerCount > 3) onPlayerCountChange(playerCount - 1) }) {
                    Text("−", fontSize = 20.sp)
                }
                ScrimText(
                    text = "$playerCount",
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                OutlinedButton(onClick = { if (playerCount < 20) onPlayerCountChange(playerCount + 1) }) {
                    Text("+", fontSize = 20.sp)
                }
            }

            if (playerCount == 3) {
                Spacer(Modifier.height(24.dp))
                ScrimText(
                    text = "Rôle du 3e joueur",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RoleChoiceButton(
                        label = "Mr White",
                        selected = threePlayerIsMrWhite,
                        onClick = { onThreePlayerIsMrWhiteChange(true) },
                        modifier = Modifier.weight(1f)
                    )
                    RoleChoiceButton(
                        label = "Infiltré",
                        selected = !threePlayerIsMrWhite,
                        onClick = { onThreePlayerIsMrWhiteChange(false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            ScrimText(
                text = "Catégorie",
                style = MaterialTheme.typography.titleMedium
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory ?: "Toutes catégories",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Catégorie") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Toutes catégories") },
                        onClick = {
                            onCategoryChange(null)
                            expanded = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                onCategoryChange(category)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Suivant", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RoleChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
