package io.github.adambench.habbits.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.adambench.habbits.domain.HabitType

/**
 * Card accent per habit type, carried over from the Obsidian tracker and
 * retuned for legible contrast on both light and dark surfaces.
 *
 * The type itself is a domain concept; this is only its appearance.
 */
private val LightAccents = mapOf(
    HabitType.Dua to Color(0xFF1F8C4D),
    HabitType.Adkar to Color(0xFF7D46A0),
    HabitType.Prayer to Color(0xFF2A72A8),
    HabitType.Reading to Color(0xFF9A7A0B),
    HabitType.Other to Color(0xFF6C7A7C),
)

private val DarkAccents = mapOf(
    HabitType.Dua to Color(0xFF4ECB80),
    HabitType.Adkar to Color(0xFFBC8AD8),
    HabitType.Prayer to Color(0xFF6BB2E4),
    HabitType.Reading to Color(0xFFDCBB45),
    HabitType.Other to Color(0xFF9AABAD),
)

fun HabitType.accent(darkTheme: Boolean): Color =
    (if (darkTheme) DarkAccents else LightAccents).getValue(this)

@Composable
fun HabitType.accent(): Color = accent(LocalDarkTheme.current)

internal val SeedIndigo = Color(0xFF3F4E96)
internal val SeedIndigoDark = Color(0xFF93A2E8)

/**
 * Sequential ramp for the completion heatmap: one hue, light to dark, with the
 * anchor flipped in dark mode. Steps were chosen for even perceptual spacing —
 * OKLab lightness moves in roughly equal increments, so the reader sees the
 * order in the colour rather than guessing it.
 *
 * Index 0 is the "nothing due" neutral and is deliberately not part of the ramp.
 */
private val HeatLight = listOf(
    Color(0xFFE8EAF2), // L 93.8
    Color(0xFFC2CAE8), // L 84.2
    Color(0xFF8E9BD3), // L 70.0
    Color(0xFF5F6FB4), // L 56.0
    Color(0xFF3F4E96), // L 44.9
)

private val HeatDark = listOf(
    Color(0xFF1C2030), // L 24.7
    Color(0xFF2F3A66), // L 36.1
    Color(0xFF47559A), // L 47.2
    Color(0xFF6577C6), // L 59.1
    Color(0xFF93A2E8), // L 72.8
)

/** [ratio] of 0 returns the neutral step, not the lightest ramp colour. */
fun heatColor(ratio: Float, darkTheme: Boolean): Color {
    val ramp = if (darkTheme) HeatDark else HeatLight
    val step = when {
        ratio <= 0f -> 0
        ratio < 0.34f -> 1
        ratio < 0.67f -> 2
        ratio < 1f -> 3
        else -> 4
    }
    return ramp[step]
}

fun heatLegend(darkTheme: Boolean): List<Color> = if (darkTheme) HeatDark else HeatLight
