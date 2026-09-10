package dev.adambench.habbits.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.adambench.habbits.domain.Appearance
import dev.adambench.habbits.domain.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColors = lightColorScheme(primary = SeedIndigo)
private val DarkColors = darkColorScheme(primary = SeedIndigoDark)

/**
 * True black rather than dark grey. On an OLED panel an unlit pixel draws no
 * power, which is worth having on a screen opened many times a day.
 */
private val BlackColors = darkColorScheme(
    primary = SeedIndigoDark,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF14161F),
    surfaceContainer = Color(0xFF0B0D13),
    surfaceContainerHigh = Color(0xFF12141C),
    surfaceContainerHighest = Color(0xFF171A23),
    outlineVariant = Color(0xFF2A2E3B),
)

/**
 * Android can follow the system Material You palette; desktop has no equivalent
 * system palette, so it falls back to the app's own seed colours.
 */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?

/**
 * The resolved dark/light mode, so habit accent colours can be picked without
 * each call site re-deriving it. `staticCompositionLocalOf` because it changes
 * about as often as the OS theme does.
 */
val LocalDarkTheme: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

@Composable
fun HabbitsTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Black -> true
    }
    val black = appearance.themeMode == ThemeMode.Black

    val scheme = when {
        // Material You cannot express a true-black ground, so an explicit
        // Black choice wins over the system palette.
        black -> BlackColors
        appearance.dynamicColor -> dynamicColorSchemeOrNull(darkTheme)
            ?: if (darkTheme) DarkColors else LightColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HabbitsTypography,
            content = content,
        )
    }
}
