package io.github.adambench.habbits.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.stats.HabitStat
import io.github.adambench.habbits.ui.headerLabel
import io.github.adambench.habbits.ui.theme.LocalDarkTheme
import io.github.adambench.habbits.ui.theme.accent
import io.github.adambench.habbits.ui.theme.heatColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailSheet(stat: HabitStat, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dark = LocalDarkTheme.current
    val habit = stat.habit

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(habit.type.accent()),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = habit.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // The window disambiguates the morning/evening pairs, which
                    // otherwise appear twice under identical names.
                    Text(
                        text = buildString {
                            append(habit.category.displayName)
                            habit.unit?.let { append(" · measured in $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            habit.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("${(stat.rate * 100).toInt()}%", "Rate", Modifier.weight(1f))
                Metric("${stat.currentStreak}", "Streak", Modifier.weight(1f))
                Metric("${stat.longestStreak}", "Best", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("${stat.done}/${stat.due}", "Done", Modifier.weight(1f))
                Metric("${stat.longestGap}", "Worst gap", Modifier.weight(1f))
                Metric(
                    value = stat.recoveryRate?.let { "${(it * 100).toInt()}%" } ?: "—",
                    label = "Bounce back",
                    modifier = Modifier.weight(1f),
                )
            }

            // The single most useful sentence on the screen: what the pattern is,
            // not just how much of it there was.
            Text(
                text = verdict(stat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            stat.total?.let {
                Text(
                    text = "$it ${habit.unit.orEmpty()} logged in this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "HISTORY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    stat.days.chunked(7).forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { day ->
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            when {
                                                !day.due -> MaterialTheme.colorScheme.surface
                                                day.done -> heatColor(1f, dark)
                                                else -> heatColor(0f, dark)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stat.lastDone?.let { "Last done ${it.headerLabel()}" } ?: "Never done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Plain-language reading of the pattern behind the numbers. */
private fun verdict(stat: HabitStat): String = when {
    stat.due == 0 -> "Not due at all in this window."
    stat.done == 0 -> "Never done in this window — ${stat.due} chances missed."
    stat.isAbandoned ->
        "Dropped: ${stat.currentGap} due days since it was last done, though it ran " +
            "${stat.longestStreak} in a row at its best."
    stat.rate >= 0.9f -> "Solid — done ${(stat.rate * 100).toInt()}% of the days it came up."
    stat.longestGap <= 2 && stat.rate >= 0.5f ->
        "Patchy but never dropped: the longest gap was only ${stat.longestGap} due days."
    (stat.recoveryRate ?: 0f) < 0.3f ->
        "Misses tend to stick — after a miss it came back the next day only " +
            "${((stat.recoveryRate ?: 0f) * 100).toInt()}% of the time. " +
            "That is where a reminder would help most."
    else ->
        "Intermittent: ${stat.done} of ${stat.due} days, worst gap ${stat.longestGap}, " +
            "bouncing back ${((stat.recoveryRate ?: 0f) * 100).toInt()}% of the time."
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
