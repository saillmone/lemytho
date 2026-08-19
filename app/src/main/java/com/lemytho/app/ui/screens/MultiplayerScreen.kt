package com.lemytho.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemytho.app.R
import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.roleDistributionLabel
import com.lemytho.app.net.ConnectionStatus
import com.lemytho.app.net.LobbyMember
import com.lemytho.app.ui.multiplayer.MultiplayerScreen
import com.lemytho.app.ui.multiplayer.MultiplayerUiState

/**
 * Racine du mode multijoueur : bascule entre les sous-écrans selon l'état.
 * Tous les écrans sont stateless (MVVM strict).
 */
@Composable
fun MultiplayerNavHost(
    state: MultiplayerUiState,
    onBack: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onResetServerUrl: () -> Unit,
    onUpdatePseudo: (String) -> Unit,
    onRandomPseudo: () -> Unit,
    onStartHosting: () -> Unit,
    onGoToJoin: () -> Unit,
    onJoinLobby: (String) -> Unit,
    onSetReady: (Boolean) -> Unit,
    onGoToHostSetup: () -> Unit,
    onBackToHostLobby: () -> Unit,
    onBackToMenu: () -> Unit,
    onSetCategory: (String?) -> Unit,
    onSetThreePlayerIsUnknown: (Boolean) -> Unit,
    onLaunchGame: () -> Unit,
    onGuestRevealDone: () -> Unit,
    onGuestCastVote: (Int) -> Unit,
    onGuestSeeResults: () -> Unit,
    onGuestMarkReady: () -> Unit
) {
    when (state.screen) {
        MultiplayerScreen.Menu -> MultiplayerMenuScreen(
            pseudo = state.myPseudo,
            serverUrl = state.serverUrl,
            error = state.error,
            connectionStatus = state.connectionStatus,
            onUpdatePseudo = onUpdatePseudo,
            onRandomPseudo = onRandomPseudo,
            onUpdateServerUrl = onUpdateServerUrl,
            onResetServerUrl = onResetServerUrl,
            onStartHosting = onStartHosting,
            onGoToJoin = onGoToJoin,
            onBack = onBack
        )

        MultiplayerScreen.HostLobby -> HostLobbyScreen(
            code = state.lobbyCode,
            members = state.members,
            myPlayerId = state.myPlayerId,
            serverUrl = state.serverUrl,
            error = state.error,
            onGoToHostSetup = onGoToHostSetup,
            onBack = onBack
        )

        MultiplayerScreen.HostSetup -> HostSetupScreen(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            memberCount = state.members.count { (it.ready || it.isHost) && it.connected },
            threePlayerIsUnknown = state.threePlayerIsUnknown,
            onCategoryChange = onSetCategory,
            onThreePlayerIsUnknownChange = onSetThreePlayerIsUnknown,
            onLaunch = onLaunchGame,
            onBack = onBackToHostLobby
        )

        MultiplayerScreen.JoinLobby -> JoinLobbyScreen(
            pseudo = state.myPseudo,
            error = state.error,
            initialCode = state.joinCode,
            onUpdatePseudo = onUpdatePseudo,
            onRandomPseudo = onRandomPseudo,
            onJoinLobby = onJoinLobby,
            onBack = onBackToMenu
        )

        MultiplayerScreen.Waiting -> WaitingScreen(
            code = state.lobbyCode,
            members = state.members,
            myPlayerId = state.myPlayerId,
            error = state.error,
            onSetReady = onSetReady,
            onBack = onBack
        )

        MultiplayerScreen.GuestReveal -> {
            val myId = state.myPlayerId
            val role = state.myRole
            if (myId != null && role != null) {
                RevealOwnScreen(
                    player = Player(
                        id = myId,
                        pseudo = state.myPseudo,
                        role = role,
                        assignedWord = state.myWord ?: ""
                    ),
                    waiting = state.revealConfirmed,
                    ackedCount = state.revealAcked,
                    totalCount = state.revealTotal,
                    onDone = onGuestRevealDone
                )
            }
        }

        MultiplayerScreen.GuestBoard -> {
            val board = state.board
            val myId = state.myPlayerId
            if (board != null && myId != null) {
                GuestBoardScreen(
                    board = board,
                    myId = myId,
                    hasVoted = state.hasVoted,
                    onCastVote = onGuestCastVote
                )
            }
        }

        MultiplayerScreen.GuestElimination -> {
            val elimination = state.elimination
            if (elimination != null) {
                GuestEliminationScreen(
                    elimination = elimination,
                    isMe = elimination.playerId == state.myPlayerId,
                    hasResult = state.guestResult != null,
                    onSeeResults = onGuestSeeResults
                )
            }
        }

        MultiplayerScreen.GuestResult -> {
            val result = state.guestResult
            if (result != null) {
                GuestResultScreen(
                    result = result,
                    wantsReplay = state.wantsReplay,
                    onReady = onGuestMarkReady,
                    onQuit = onBack
                )
            }
        }
    }
}

// --- Menu (choix hôte / invité) ---

@Composable
private fun MultiplayerMenuScreen(
    pseudo: String,
    serverUrl: String,
    error: String?,
    connectionStatus: ConnectionStatus,
    onUpdatePseudo: (String) -> Unit,
    onRandomPseudo: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onResetServerUrl: () -> Unit,
    onStartHosting: () -> Unit,
    onGoToJoin: () -> Unit,
    onBack: () -> Unit
) {
    MultiplayerBackground(backgroundRes = R.drawable.setup_bg) {
        Spacer(Modifier.height(40.dp))

        ScrimText(
            text = "Multijoueur",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pseudo,
            onValueChange = { onUpdatePseudo(it.take(24)) },
            singleLine = true,
            label = { Text("Ton pseudo") },
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = onRandomPseudo,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Pseudo aléatoire")
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onGoToJoin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rejoindre une partie", fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onStartHosting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Créer une partie", fontSize = 18.sp)
        }

        ConnectionStatusText(connectionStatus)

        Spacer(Modifier.height(24.dp))

        var showAdvanced by remember { mutableStateOf(false) }

        if (showAdvanced) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onUpdateServerUrl,
                singleLine = true,
                label = { Text("Adresse du serveur") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onResetServerUrl,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Rétablir l'URL par défaut")
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrimTextButton(text = "Retour", onClick = onBack)
            ScrimTextButton(
                text = if (showAdvanced) "Masquer" else "Paramètres avancés",
                onClick = { showAdvanced = !showAdvanced }
            )
        }
    }
}

// --- Salon de l'hôte ---

@Composable
private fun HostLobbyScreen(
    code: String?,
    members: List<LobbyMember>,
    myPlayerId: Int?,
    serverUrl: String,
    error: String?,
    onGoToHostSetup: () -> Unit,
    onBack: () -> Unit
) {
    var showNotReadyConfirm by remember { mutableStateOf(false) }
    var showQuitConfirm by remember { mutableStateOf(false) }
    val readyCount = members.count { it.ready }
    val allReady = members.all { it.ready }
    val playingCount = members.count { it.ready || it.isHost }
    val context = LocalContext.current

    MultiplayerBackground(backgroundRes = R.drawable.players_bg) {
        Spacer(Modifier.height(40.dp))

        ScrimText(
            text = "Salon créé",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        ScrimText(
            text = "Partage ce code :",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(12.dp))

        ScrimText(
            text = code ?: "----",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val message = buildInvitationMessage(code, serverUrl)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Partager l'invitation"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Partager le lien", fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        if (readyCount < 3) {
            ScrimText(
                text = "En attente d'au moins 3 joueurs prêts…",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ScrimText(
                text = "Répartition : ${roleLabelForLobby(playingCount)}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))

        ScrimText(
            text = "Joueurs (${members.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        MemberList(members = members, myPlayerId = myPlayerId)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { if (allReady) onGoToHostSetup() else showNotReadyConfirm = true },
            enabled = readyCount >= 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configurer la partie", fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))

        ScrimTextButton(
            text = "Quitter",
            onClick = { showQuitConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
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
                    onBack()
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

    if (showNotReadyConfirm) {
        AlertDialog(
            onDismissRequest = { showNotReadyConfirm = false },
            title = { Text("Joueurs non prêts") },
            text = {
                Text("Tous les joueurs ne sont pas prêts. Lancer la partie avec seulement les joueurs prêts ?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotReadyConfirm = false
                    onGoToHostSetup()
                }) {
                    Text("Lancer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotReadyConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// --- Configuration de la partie (hôte) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostSetupScreen(
    categories: List<String>,
    selectedCategory: String?,
    memberCount: Int,
    threePlayerIsUnknown: Boolean,
    onCategoryChange: (String?) -> Unit,
    onThreePlayerIsUnknownChange: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onBack: () -> Unit
) {
    MultiplayerBackground(backgroundRes = R.drawable.setup_bg) {
        Spacer(Modifier.height(40.dp))

        ScrimText(
            text = "Configuration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        ScrimText(
            text = "Choisis une thématique de mots",
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

        if (memberCount == 3) {
            Spacer(Modifier.height(24.dp))
            ScrimText(
                text = "Rôle du 3ème joueur",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RoleChoiceButton(
                    label = "Inconnu",
                    selected = threePlayerIsUnknown,
                    onClick = { onThreePlayerIsUnknownChange(true) },
                    modifier = Modifier.weight(1f)
                )
                RoleChoiceButton(
                    label = "Imposteur",
                    selected = !threePlayerIsUnknown,
                    onClick = { onThreePlayerIsUnknownChange(false) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ScrimText(
            text = if (memberCount >= 3) {
                "Répartition : ${roleDistributionLabel(memberCount, threePlayerIsUnknown)}"
            } else {
                "En attente d'au moins 3 joueurs prêts…"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onLaunch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lancer la partie", fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))

        ScrimTextButton(
            text = "Retour",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// --- Saisie du code (invité) ---

@Composable
private fun JoinLobbyScreen(
    pseudo: String,
    error: String?,
    initialCode: String = "",
    onUpdatePseudo: (String) -> Unit,
    onRandomPseudo: () -> Unit,
    onJoinLobby: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }

    MultiplayerBackground(backgroundRes = R.drawable.players_bg) {
        Spacer(Modifier.height(40.dp))

        ScrimText(
            text = "Rejoindre une partie",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pseudo,
            onValueChange = { onUpdatePseudo(it.take(24)) },
            singleLine = true,
            label = { Text("Ton pseudo") },
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = onRandomPseudo,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Pseudo aléatoire")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.uppercase().filter { it.isLetterOrDigit() }.take(4)
            },
            singleLine = true,
            label = { Text("Code du salon") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onJoinLobby(code) },
            enabled = code.length == 4,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rejoindre", fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        ScrimTextButton(
            text = "Retour",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// --- Attente du lancement (invité) ---

@Composable
private fun WaitingScreen(
    code: String?,
    members: List<LobbyMember>,
    myPlayerId: Int?,
    error: String?,
    onSetReady: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val myReady = members.firstOrNull { it.playerId == myPlayerId }?.ready == true
    val readyCount = members.count { it.ready }
    val playingCount = members.count { it.ready || it.isHost }
    var showQuitConfirm by remember { mutableStateOf(false) }

    MultiplayerBackground(backgroundRes = R.drawable.players_bg) {
        Spacer(Modifier.height(40.dp))

        ScrimText(
            text = "En attente…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        ScrimText(
            text = "Le Maître du Jeu va lancer la partie (salon ${code ?: ""})",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(16.dp))

        if (myReady) {
            OutlinedButton(
                onClick = { onSetReady(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Je ne suis pas prêt", fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = { onSetReady(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Je suis prêt", fontSize = 18.sp)
            }
        }

        if (readyCount < 3) {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = "En attente d'au moins 3 joueurs prêts…",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = "Répartition : ${roleLabelForLobby(playingCount)}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ScrimText(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))

        ScrimText(
            text = "Joueurs (${members.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        MemberList(members = members, myPlayerId = myPlayerId)

        Spacer(Modifier.height(16.dp))

        ScrimTextButton(
            text = "Quitter",
            onClick = { showQuitConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quitter le salon ?") },
            text = {
                Text("Tu vas quitter le salon et être retiré de la partie.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirm = false
                    onBack()
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

// --- Composants partagés ---

/**
 * Libellé de répartition affiché dans le lobby. À 3 joueurs, le choix
 * l'Inconnu / Imposteur n'est pas encore fait : on présente les deux possibilités.
 */
private fun roleLabelForLobby(playingCount: Int): String =
    if (playingCount == 3) "2 Citoyens · 1 Inconnu ou 1 Imposteur"
    else roleDistributionLabel(playingCount)

@Composable
private fun MultiplayerBackground(
    backgroundRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
private fun MemberList(members: List<LobbyMember>, myPlayerId: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            val suffix = when {
                member.playerId == myPlayerId -> " (toi)"
                member.isHost -> " (Maître du Jeu)"
                else -> ""
            }
            val disconnected = if (!member.connected) " (déconnecté)" else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (member.ready) Color(0xFF2E7D32) else Color(0xFFC62828))
                )
                Spacer(Modifier.width(8.dp))
                ScrimText(
                    text = "${member.pseudo}$suffix$disconnected",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusText(status: ConnectionStatus) {
    // « Hors ligne » est l'état par défaut sur cet écran : sans intérêt, on le masque.
    if (status is ConnectionStatus.Disconnected) return
    val label = when (status) {
        is ConnectionStatus.Disconnected -> "Hors ligne"
        is ConnectionStatus.Connecting -> "Connexion…"
        is ConnectionStatus.Connected -> "Connecté"
        is ConnectionStatus.Error -> "Connexion impossible"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ScrimText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (status is ConnectionStatus.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
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

/** Message d'invitation : contient le code ET le lien cliquable (https). */
private fun buildInvitationMessage(code: String?, serverUrl: String): String {
    val c = code ?: ""
    // Lien https universellement cliquable (mail/SMS) : la landing page /join
    // fait le pont vers l'app (deep link) ou le client web. Un schéma custom
    // lemytho:// ne serait pas reconnu comme lien cliquable par les clients mail.
    val link = "${serverUrl.trimEnd('/')}/join?code=$c"
    return "Rejoins ma partie Le Mytho !\nCode : $c\n$link"
}
