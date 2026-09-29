package io.github.adambench.habbits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * A handful of line icons drawn directly rather than pulled from an icon
 * library: the app needs four, and a dependency for four glyphs is a poor
 * trade. Each takes its colour from [LocalContentColor], like a Material icon.
 */

/** Three bars of a chart. */
@Composable
fun StatsIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val width = s * 0.17f
        val base = s * 0.84f
        listOf(0.16f to 0.38f, 0.415f to 0.66f, 0.67f to 0.5f).forEach { (x, height) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(s * x, base - s * height),
                size = Size(width, s * height),
                cornerRadius = CornerRadius(width * 0.3f),
            )
        }
    }
}

/** A bulleted list: the habits themselves. */
@Composable
fun HabitsIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.085f
        listOf(0.28f, 0.5f, 0.72f).forEach { y ->
            drawCircle(color, radius = s * 0.06f, center = Offset(s * 0.2f, s * y))
            drawLine(
                color = color,
                start = Offset(s * 0.36f, s * y),
                end = Offset(s * 0.84f, s * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Two sliders: settings. */
@Composable
fun SettingsIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.085f
        listOf(0.34f to 0.34f, 0.66f to 0.64f).forEach { (y, knob) ->
            drawLine(
                color = color,
                start = Offset(s * 0.16f, s * y),
                end = Offset(s * 0.84f, s * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(color, radius = s * 0.1f, center = Offset(s * knob, s * y))
        }
    }
}

/** A plus sign. */
@Composable
fun PlusIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.1f
        drawLine(color, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.5f, s * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.8f, s * 0.5f), stroke, StrokeCap.Round)
    }
}
