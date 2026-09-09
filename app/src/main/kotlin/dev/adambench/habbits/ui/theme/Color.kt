package dev.adambench.habbits.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Habit type colours, carried over from the Obsidian tracker and retuned for
 * legible contrast on both light and dark surfaces.
 */
enum class HabitType(val light: Color, val dark: Color) {
    Dua(Color(0xFF1F8C4D), Color(0xFF4ECB80)),
    Adkar(Color(0xFF7D46A0), Color(0xFFBC8AD8)),
    Prayer(Color(0xFF2A72A8), Color(0xFF6BB2E4)),
    Reading(Color(0xFF9A7A0B), Color(0xFFDCBB45)),
    Other(Color(0xFF6C7A7C), Color(0xFF9AABAD)),
}

internal val SeedIndigo = Color(0xFF3F4E96)
internal val SeedIndigoDark = Color(0xFF93A2E8)
