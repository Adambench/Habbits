package dev.adambench.habbits.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = SeedIndigo)
private val DarkColors = darkColorScheme(primary = SeedIndigoDark)

/**
 * Android can follow the system Material You palette; desktop has no equivalent
 * system palette, so it falls back to the app's own seed colours.
 */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?

@Composable
fun HabbitsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = (if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = scheme,
        typography = HabbitsTypography,
        content = content,
    )
}
