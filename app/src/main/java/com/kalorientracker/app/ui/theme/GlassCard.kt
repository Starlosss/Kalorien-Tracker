package com.kalorientracker.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The only glass surface in the app: faint translucency, a hairline light edge and a soft top
 * reflection. Use it for the one or two focal elements of a screen, not for every card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.075f),
                    0.35f to Color.White.copy(alpha = 0.035f),
                    1f to Color.White.copy(alpha = 0.02f),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.22f),
                    0.5f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.10f),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Plain surface for everything that is not a focal element. */
@Composable
fun PlainCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), shape)
            .padding(contentPadding),
        content = content,
    )
}
