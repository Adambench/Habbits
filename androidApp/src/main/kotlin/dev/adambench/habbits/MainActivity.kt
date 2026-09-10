package dev.adambench.habbits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.adambench.habbits.ui.DayScreen
import dev.adambench.habbits.ui.theme.HabbitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as HabbitsApplication).container
        val model = container.dayScreenModel(lifecycleScope)

        setContent {
            HabbitsTheme {
                DayScreen(model)
            }
        }
    }
}
