package dev.adambench.habbits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.adambench.habbits.ui.HabbitsApp
import dev.adambench.habbits.ui.theme.HabbitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HabbitsTheme {
                HabbitsApp()
            }
        }
    }
}
