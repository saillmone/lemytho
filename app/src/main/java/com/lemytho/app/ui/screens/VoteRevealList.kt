package com.lemytho.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lemytho.app.data.model.Role
import com.lemytho.app.ui.VoteReveal

/** Liste des votes du tour décisif. `heightIn` avant `verticalScroll` (enfant d'une colonne scrollable). */
@Composable
fun VoteRevealList(
    votes: List<VoteReveal>,
    eliminatedPseudo: String,
    eliminatedRole: Role,
    myPseudo: String?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 240.dp
) {
    if (votes.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        votes.forEach { vote ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoteParty(
                    pseudo = vote.voterPseudo,
                    eliminatedPseudo = eliminatedPseudo,
                    eliminatedRole = eliminatedRole,
                    myPseudo = myPseudo
                )
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                VoteParty(
                    pseudo = vote.targetPseudo,
                    eliminatedPseudo = eliminatedPseudo,
                    eliminatedRole = eliminatedRole,
                    myPseudo = myPseudo
                )
            }
        }
    }
}

@Composable
private fun VoteParty(
    pseudo: String,
    eliminatedPseudo: String,
    eliminatedRole: Role,
    myPseudo: String?
) {
    val eliminated = pseudo == eliminatedPseudo
    val label = if (myPseudo != null && pseudo == myPseudo) "$pseudo (moi)" else pseudo
    val color = if (eliminated) voteRoleColor(eliminatedRole) else Color.White
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (eliminated) {
            Icon(
                imageVector = voteRoleIcon(eliminatedRole),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (eliminated) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textDecoration = if (eliminated) TextDecoration.LineThrough else null
        )
    }
}

private fun voteRoleIcon(role: Role): ImageVector = when (role) {
    Role.CITIZEN -> Icons.Filled.Person
    Role.IMPOSTOR -> Icons.Filled.Masks
    Role.UNKNOWN -> Icons.Filled.VisibilityOff
}

@Composable
private fun voteRoleColor(role: Role) = when (role) {
    Role.CITIZEN -> MaterialTheme.colorScheme.primary
    Role.IMPOSTOR -> MaterialTheme.colorScheme.secondary
    Role.UNKNOWN -> MaterialTheme.colorScheme.tertiary
}
