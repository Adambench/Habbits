package dev.adambench.habbits.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import dev.adambench.habbits.domain.Category
import kotlinx.datetime.LocalDate

@Composable
fun DayScreen(
    model: DayScreenModel,
    modifier: Modifier = Modifier,
    /** Supplied where the platform can open a file picker; null hides the action. */
    onImport: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()

    // Which measured habit has its stepper open. One at a time, and it closes
    // when the day changes so a stale row cannot stay expanded.
    var expandedHabitId by remember { mutableStateOf<String?>(null) }
    val selected = state.selectedDate
    remember(selected) { expandedHabitId = null }

    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            DayHeader(
                state = state,
                onPrevious = { model.shiftDay(-1) },
                onNext = { model.shiftDay(1) },
                onToday = { model.goToToday() },
            )

            WeekStrip(
                days = state.week,
                onSelect = { model.selectDate(it) },
            )

            Spacer(Modifier.height(4.dp))

            if (state.sections.isEmpty()) {
                EmptyDay(
                    isLoading = state.isLoading,
                    hasAnyHabits = state.hasAnyHabits,
                    onImport = onImport,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp,
                    ),
                ) {
                    state.sections.forEach { section ->
                        item(key = "header-${section.category.name}") {
                            CategoryHeader(section.category, section.completed, section.rows.size)
                        }
                        items(
                            count = section.rows.size,
                            key = { i -> section.rows[i].habit.id },
                        ) { i ->
                            val row = section.rows[i]
                            Box(Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                                HabitCard(
                                    row = row,
                                    isExpanded = expandedHabitId == row.habit.id,
                                    onToggle = {
                                        expandedHabitId = null
                                        model.toggle(row.habit.id)
                                    },
                                    onExpandToggle = {
                                        expandedHabitId =
                                            if (expandedHabitId == row.habit.id) null else row.habit.id
                                    },
                                    onStep = { model.step(row.habit.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    state: DayUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.selectedDate.headerLabel(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (state.isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (state.isToday) {
                Text(
                    text = "${state.completed} of ${state.total} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Back to today",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToday)
                        .padding(vertical = 2.dp),
                )
            }
        }

        NavArrow("‹", onPrevious)
        ProgressRing(completed = state.completed, total = state.total)
        NavArrow("›", onNext)
    }
}

@Composable
private fun NavArrow(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(days: List<DayChip>, onSelect: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { chip ->
            DayChipView(chip, Modifier.weight(1f)) { onSelect(chip.date) }
        }
    }
}

@Composable
private fun DayChipView(chip: DayChip, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val selectedBg = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (chip.isSelected) selectedBg else MaterialTheme.colorScheme.surface)
            .then(
                if (chip.isToday && !chip.isSelected) {
                    Modifier.border(1.dp, selectedBg, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = chip.date.weekdayInitial(),
            style = MaterialTheme.typography.labelSmall,
            color = if (chip.isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "${chip.date.day}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chip.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (chip.isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Completion dot: filled once every habit due that day is done.
        val complete = chip.total > 0 && chip.completed == chip.total
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        chip.isSelected -> MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (chip.ratio > 0f) 1f else 0.25f,
                        )
                        complete -> MaterialTheme.colorScheme.primary
                        chip.ratio > 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        )
    }
}

@Composable
private fun CategoryHeader(category: Category, completed: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The timeline rail dot, carried over from the Obsidian tracker.
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$completed/$total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDay(isLoading: Boolean, hasAnyHabits: Boolean, onImport: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = when {
                    isLoading -> "Loading…"
                    !hasAnyHabits -> "No habits yet."
                    else -> "Nothing scheduled for this day."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isLoading && !hasAnyHabits && onImport != null) {
                Text(
                    text = "Import a backup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onImport)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
