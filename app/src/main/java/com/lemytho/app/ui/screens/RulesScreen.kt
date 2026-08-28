package com.lemytho.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lemytho.app.R

/** Une page de règles : image de fond, titre, sous-titre et description. */
private data class RulesPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val background: Int
)

private val rulesPages = listOf(
    RulesPage(
        title = "Le but du jeu",
        subtitle = "",
        description = "Chaque joueur reçoit un rôle secret. Débattez, puis votez pour " +
            "éliminer les intrus. Les Citoyens gagnent en les démasquant, les intrus en " +
            "survivant jusqu'au bout.",
        background = R.drawable.home_bg
    ),
    RulesPage(
        title = "Citoyen",
        subtitle = "Tu reçois un mot secret, identique pour tous les Citoyens.",
        description = "Au début, tu ignores ton rôle. Compare les indices des autres " +
            "pour déduire qui sont les intrus et démasque-les.",
        background = R.drawable.rules_citizen
    ),
    RulesPage(
        title = "Imposteur",
        subtitle = "Tu reçois un mot secret, proche de celui des Citoyens.",
        description = "Au début, tu ignores ton rôle. En comparant les indices, " +
            "tu comprendras que ton mot diffère : reste alors caché jusqu'au bout.",
        background = R.drawable.rules_impostor
    ),
    RulesPage(
        title = "l'Inconnu",
        subtitle = "Tu ne reçois aucun mot.",
        description = "Tu ne reçois pas de mot. Tu connais ton rôle dès le départ : " +
            "devine le mot des Citoyens sans te faire repérer.",
        background = R.drawable.rules_unknown
    )
)

/**
 * Règles du jeu : pages défilables (swipe), image de fond par rôle, croix de
 * fermeture en haut à droite et indicateurs de page en bas.
 */
@Composable
fun RulesScreen(onClose: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { rulesPages.size })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val page = rulesPages[index]
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = page.background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(72.dp))
                    ScrimText(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (page.subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        ScrimText(
                            text = page.subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 300.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ScrimText(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Croix de fermeture en haut à droite.
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Fermer",
                tint = Color.White
            )
        }

        // Indicateurs de page en bas.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(rulesPages.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                            CircleShape
                        )
                )
            }
        }
    }
}
