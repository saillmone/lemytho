package com.lemytho.app.ui.screens

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lemytho.app.ui.VoteReveal

/** Bouton centré + overlay plein écran pour relire les votes du tour. */
@Composable
fun ShowVotesControl(
    votes: List<VoteReveal>,
    modifier: Modifier = Modifier
) {
    if (votes.isEmpty()) return
    var show by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedButton(
            onClick = { show = true },
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White
            ),
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f))
        ) {
            Text(
                text = "Afficher les votes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        ScrimText(
            text = "Tu ne pourras plus revoir qui a voté pour qui.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
    if (show) {
        // Les insets Compose sont nuls *dans* un Dialog (autre fenêtre).
        // On les lit ici, dans la fenêtre Activity.
        val density = LocalDensity.current
        val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val bottomInset = maxOf(
            with(density) { WindowInsets.navigationBars.getBottom(this).toDp() },
            56.dp
        )
        VotesOverlay(
            votes = votes,
            topInset = topInset,
            bottomInset = bottomInset,
            onDismiss = { show = false }
        )
    }
}

@Composable
private fun VotesOverlay(
    votes: List<VoteReveal>,
    topInset: Dp,
    bottomInset: Dp,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 24.dp + topInset,
                        bottom = 24.dp + bottomInset
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Votes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(24.dp))
                VoteRevealList(
                    votes = votes,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp + topInset, end = 12.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = Color.Black
                )
            }
        }
    }
}

/** Liste des votes du tour décisif. */
@Composable
fun VoteRevealList(
    votes: List<VoteReveal>,
    modifier: Modifier = Modifier
) {
    if (votes.isEmpty()) return
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(votes, key = { "${it.voterPseudo}->${it.targetPseudo}" }) { vote ->
            ScrimText(
                text = "${vote.voterPseudo} → ${vote.targetPseudo}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
