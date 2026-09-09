package dev.adambench.habbits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The eight slots the day is divided into, in order. Carried over verbatim from
 * the Obsidian tracker so imported habits keep their placement.
 */
val CATEGORIES = listOf(
    "Anytime",
    "Before Fajr",
    "Fajr",
    "Shuruq",
    "Dhuhr",
    "Asr",
    "Maghrib",
    "Isha",
)

@Composable
fun HabbitsApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Habbits",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Toolchain verified — M0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CATEGORIES.forEach { category ->
                    CategoryRow(category)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
