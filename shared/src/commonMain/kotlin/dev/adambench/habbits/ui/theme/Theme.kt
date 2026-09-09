package dev.adambench.habbits.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColors = lightColorScheme(primary = SeedIndigo)
private val DarkColors = darkColorScheme(primary = SeedIndigoDark)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = (if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HabbitsTypography,
            content = content,
        )
    }
}
