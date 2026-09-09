package dev.adambench.habbits

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.adambench.habbits.ui.HabbitsApp
import dev.adambench.habbits.ui.theme.HabbitsTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Habbits",
        state = rememberWindowState(size = DpSize(440.dp, 880.dp)),
    ) {
        HabbitsTheme {
            HabbitsApp()
        }
    }
}
