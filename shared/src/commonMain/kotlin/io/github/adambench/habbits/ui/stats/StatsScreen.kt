package io.github.adambench.habbits.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.stats.HabitStat
import io.github.adambench.habbits.domain.stats.StatsRange
import io.github.adambench.habbits.domain.stats.StatsSummary
import io.github.adambench.habbits.ui.headerLabel
import io.github.adambench.habbits.ui.weekdayShort
import kotlinx.datetime.LocalDate

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun StatsScreen(
    model: StatsScreenModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by model.state.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var detailHabitId by remember { mutableStateOf<String?>(null) }

    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Pill("Done", filled = false, onClick = onDone)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { range ->
                    Pill(
                        text = range.label,
                        filled = range == s.range,
                        onClick = { model.select(range) },
                    )
                }
            }

            if (s.isLoading) {
                Text("Working…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            if (s.due == 0 && s.totalLogged == 0) {
                Text(
                    text = "Nothing logged in this window yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // Headline figures. These are the point, so they are tiles rather
            // than a chart of four bars.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${(s.rate * 100).toInt()}%", "Completed", Modifier.weight(1f))
                Tile("${s.currentStreak}", "Day streak", Modifier.weight(1f))
                Tile("${s.perfectDays}", "Perfect days", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${s.done}", "Done", Modifier.weight(1f))
                Tile("${s.due - s.done}", "Missed", Modifier.weight(1f))
                Tile("${s.longestStreak}", "Best streak", Modifier.weight(1f))
            }

            Section("Daily completion") {
                CompletionHeatmap(
                    days = s.days,
                    selected = selectedDay,
                    onSelect = { selectedDay = if (selectedDay == it) null else it },
                )
                val picked = selectedDay?.let { day -> s.days.firstOrNull { it.date == day } }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = picked?.let {
                        "${it.date.headerLabel()} — ${it.done} of ${it.due} done"
                    } ?: "Tap a day for its detail",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (s.weeks.size >= 2) {
                Section("By week") {
                    RateBars(
                        entries = s.weeks.map { it.start.let { d -> "${d.day}" } to it.rate },
                        labelEvery = if (s.weeks.size > 14) 4 else 2,
                    )
                }
            }

            Section("By day of the week") {
                RateBars(entries = s.weekdays.map { WEEKDAYS[it.isoDayNumber - 1] to it.rate })
            }

            if (s.categories.isNotEmpty()) {
                Section("By time of day") {
                    s.categories.forEach { c ->
                        RateRow(c.category.displayName, c.rate, "${c.done}/${c.due}")
                    }
                }
            }

            Section("Every habit") {
                // Duplicate labels are real here: the morning and evening pairs
                // share names, so the window is shown whenever a name repeats.
                val duplicated = s.habits.groupBy { it.habit.label }
                    .filterValues { it.size > 1 }
                    .keys
                s.habits.forEach { stat ->
                    HabitRow(
                        stat = stat,
                        showCategory = stat.habit.label in duplicated,
                        onClick = { detailHabitId = stat.habit.id },
                    )
                }
            }

            detailHabitId?.let { id ->
                s.habits.firstOrNull { it.habit.id == id }?.let { stat ->
                    HabitDetailSheet(stat = stat, onDismiss = { detailHabitId = null })
                }
            }

            if (s.excludedHabits > 0) {
                Text(
                    text = "${s.excludedHabits} sleeping or archived habits are left out of " +
                        "these rates, since they were not expected on any day. They account " +
                        "for ${s.excludedLogged} of the ${s.totalLogged} completions logged " +
                        "in this window.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column { content() }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun HabitRow(stat: HabitStat, showCategory: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (showCategory) {
                    "${stat.habit.label} · ${stat.habit.category.displayName}"
                } else {
                    stat.habit.label
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stat.currentStreak > 0) {
                Text(
                    text = "${stat.currentStreak}d",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "${(stat.rate * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(stat.rate.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildString {
                append("${stat.done}/${stat.due} days")
                stat.total?.let { append(" · $it ${stat.habit.unit.orEmpty()}") }
                if (stat.longestStreak > 0) append(" · best ${stat.longestStreak}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) accent else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (filled) accent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
