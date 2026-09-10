package dev.adambench.habbits.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val displayName: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
    Black("Black"),
}

@Serializable
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Material You on Android; ignored where there is no system palette. */
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
)
