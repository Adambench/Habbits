package io.github.adambench.habbits.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Desktop has no system-wide accent palette to follow, so use the app's own. */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? = null
