package dev.adambench.habbits.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.adambench.habbits.domain.HabitType

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
