package com.lemytho.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
