package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.ui.LocalPalette

/** Translucent white card that lets the sky gradient show through. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = LocalPalette.current.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = secondaryContentColor(),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
@ReadOnlyComposable
fun secondaryContentColor(): Color = LocalContentColor.current.copy(alpha = 0.8f)
