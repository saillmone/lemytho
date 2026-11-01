package com.lemytho.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Texte posé sur un fond arrondi translucide : l'assombrissement ne porte que
 * sur le texte (pas sur l'image de fond), pour en garantir la lisibilité.
 */
@Composable
fun ScrimText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    color: Color = Color.White
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = color
    )
}

/**
 * Bouton-lien unifié : texte posé sur un fond arrondi translucide, capsule
 * courte (ne s'étire pas en largeur). Utilisé pour les liens « Retour » /
 * « Quitter » sur l'ensemble des écrans, afin de garantir un style homogène.
 */
@Composable
fun ScrimTextButton(
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

/** Consigne d'étape du plateau : scrim + filet cyan. */
@Composable
fun BoardStatusText(text: String) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/** Pastille de présence : anneau = pas prêt, vert = en ligne, gris = hors ligne. */
@Composable
fun PresenceDot(
    connected: Boolean,
    filled: Boolean = connected,
    modifier: Modifier = Modifier
) {
    val online = Color(0xFF2E7D32)
    val offline = Color(0xFF6B7280)
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .then(
                when {
                    !connected -> Modifier.background(offline)
                    filled -> Modifier.background(online)
                    else -> Modifier.border(2.dp, online, CircleShape)
                }
            )
    )
}
