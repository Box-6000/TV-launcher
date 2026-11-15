package com.launch.tv.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

fun DrawScope.drawPaletteGradient(palette: Palette) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF10131C),
                palette.accent.copy(alpha = 0.72f),
                Color(0xFF07080D),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    drawCircle(
        color = palette.accentSoft.copy(alpha = 0.34f),
        radius = size.minDimension * 0.42f,
        center = Offset(size.width * 0.16f, size.height * 0.10f),
    )
    drawCircle(
        color = palette.accent.copy(alpha = 0.26f),
        radius = size.minDimension * 0.5f,
        center = Offset(size.width * 0.9f, size.height * 0.94f),
    )
}
