package dev.adambench.habbits.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.ui.theme.accent

@Composable
fun ManageScreen(
    model: ManageScreenModel,
    onDone: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val listState = rememberLazyListState()
    val habitKeys = state.sections.flatMapTo(mutableSetOf()) { section ->
        section.habits.map { it.id }
    }
    val reorder = rememberReorderState(listState) { from, to ->
        model.moveTo(from as String, to as String)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Habits",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.total} shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextAction("Times") { onSettings() }
                Spacer(Modifier.width(8.dp))
                TextAction("Add", filled = true) { model.createNew() }
                Spacer(Modifier.width(8.dp))
                TextAction("Done") { onDone() }
            }

            if (state.archivedCount > 0) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TextAction(
                        text = if (state.showArchived) {
                            "Hide archived"
                        } else {
                            "Show ${state.archivedCount} archived"
                        },
                    ) { model.toggleShowArchived() }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            ) {
                state.sections.forEach { section ->
                    item(key = "h-${section.category.name}") {
                        Text(
                            text = section.category.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(count = section.habits.size, key = { section.habits[it].id }) { i ->
                        val habit = section.habits[i]
                        val isDragged = reorder.draggedKey == habit.id
                        ManageRow(
                            habit = habit,
                            isDragged = isDragged,
                            onEdit = { model.edit(habit) },
                            onSleep = { model.toggleSleep(habit) },
                            onMove = { model.move(habit.id, it) },
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.placeRelative(0, reorder.offsetFor(habit.id))
                                    }
                                }
                                .reorderable(reorder, habit.id) { it in habitKeys },
                        )
                    }
                }
            }
        }
    }

    state.editing?.let { draft ->
        HabitEditorSheet(
            habit = draft,
            isNew = state.isNew,
            onChange = model::updateDraft,
            onSave = model::save,
            onDelete = model::delete,
            onDismiss = model::cancelEdit,
        )
    }
}

@Composable
private fun ManageRow(
    habit: Habit,
    isDragged: Boolean,
    onEdit: () -> Unit,
    onSleep: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimmed = habit.status != HabitStatus.Active
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isDragged) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                1.dp,
                if (isDragged) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(habit.type.accent().copy(alpha = if (dimmed) 0.4f else 1f)),
        )
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = habit.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dimmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = when (habit.status) {
                HabitStatus.Sleeping -> "Sleeping"
                HabitStatus.Archived -> "Archived"
                HabitStatus.Active -> habit.scheduleSummary()
            }
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "⠿",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(end = 2.dp),
        )
        IconAction(if (habit.status == HabitStatus.Sleeping) "☀" else "☾", onSleep)
        IconAction("↑") { onMove(-1) }
        IconAction("↓") { onMove(1) }
    }
}

/** A one-line description of when the habit is due, for the management list. */
private fun Habit.scheduleSummary(): String = when (frequencyType) {
    dev.adambench.habbits.domain.FrequencyType.Daily -> "Every day"
    dev.adambench.habbits.domain.FrequencyType.Weekly ->
        if (recurringDays.isEmpty) {
            "Every day"
        } else {
            recurringDays.toIsoDayNumbers()
                .joinToString(" ") { listOf("M", "T", "W", "T", "F", "S", "S")[it - 1] }
        }
    dev.adambench.habbits.domain.FrequencyType.Interval ->
        "Every ${intervalDays?.takeIf { it > 0 } ?: 1} days"
}

@Composable
private fun IconAction(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextAction(
    text: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
        )
    }
}
