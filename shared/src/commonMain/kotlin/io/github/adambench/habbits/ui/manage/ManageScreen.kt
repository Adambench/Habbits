package io.github.adambench.habbits.ui.manage

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.ui.PlusIcon
import io.github.adambench.habbits.ui.SettingsIcon
import io.github.adambench.habbits.ui.theme.accent
import kotlinx.datetime.LocalDate

@Composable
fun ManageScreen(
    model: ManageScreenModel,
    today: LocalDate,
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
    var confirmDelete by remember { mutableStateOf<Habit?>(null) }

    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item(key = "title") {
                ManageHeader(
                    isEmpty = state.total == 0 && !state.isLoading,
                    onNew = model::createNew,
                    onSettings = onSettings,
                    onDone = onDone,
                )
            }

            state.sections.forEach { section ->
                item(key = "h-${section.category.name}") {
                    SectionHeader(
                        title = section.category.displayName,
                        count = section.habits.size,
                    )
                }
                items(count = section.habits.size, key = { section.habits[it].id }) { i ->
                    val habit = section.habits[i]
                    val isDragged = reorder.draggedKey == habit.id
                    ManageRow(
                        habit = habit,
                        isDragged = isDragged,
                        canMoveUp = !(section == state.sections.first() && i == 0),
                        canMoveDown = !(section == state.sections.last() && i == section.habits.lastIndex),
                        onEdit = { model.edit(habit) },
                        onMove = { model.move(habit.id, it) },
                        onStatus = { model.setStatus(habit, it) },
                        onDelete = { confirmDelete = habit },
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

            if (state.archived.isNotEmpty()) {
                item(key = "archived-header") {
                    ArchivedHeader(
                        count = state.archived.size,
                        expanded = state.showArchived,
                        onToggle = model::toggleShowArchived,
                    )
                }
                if (state.showArchived) {
                    items(count = state.archived.size, key = { "a-" + state.archived[it].id }) { i ->
                        val habit = state.archived[i]
                        ManageRow(
                            habit = habit,
                            isDragged = false,
                            canMoveUp = false,
                            canMoveDown = false,
                            onEdit = { model.edit(habit) },
                            onMove = {},
                            onStatus = { model.setStatus(habit, it) },
                            onDelete = { confirmDelete = habit },
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
            today = today,
            onChange = model::updateDraft,
            onSave = model::save,
            onDelete = { confirmDelete = draft },
            onDismiss = model::cancelEdit,
        )
    }

    confirmDelete?.let { habit ->
        DeleteDialog(
            habit = habit,
            onDelete = {
                model.delete(habit.id)
                confirmDelete = null
            },
            onArchive = {
                model.setStatus(habit, HabitStatus.Archived)
                model.cancelEdit()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ManageHeader(isEmpty: Boolean, onNew: () -> Unit, onSettings: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Habits",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Prayer times, reminders, sync and appearance live one level down.
            HeaderAction("Settings", onClick = onSettings) {
                SettingsIcon(size = 18.dp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            HeaderAction("Done", onClick = onDone)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isEmpty) {
                "No habits yet. Add your first below."
            } else {
                "Tap a habit to change it. Press and hold, then drag, to reorder."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNew),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PlusIcon(size = 18.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "New habit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Archived · $count",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Retired habits. Their history is kept for stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ManageRow(
    habit: Habit,
    isDragged: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onStatus: (HabitStatus) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = habit.status == HabitStatus.Active
    val accent = habit.type.accent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
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
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onEdit)
            .heightIn(min = 60.dp)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same colour ribbon as the day view's cards, so the two read as one.
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = if (active) 1f else 0.35f)),
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = habit.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!active) {
                    StatusBadge(habit.status)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${habit.scheduleText()} · ${habit.trackingText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        RowMenu(
            habit = habit,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onEdit = onEdit,
            onMove = onMove,
            onStatus = onStatus,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun StatusBadge(status: HabitStatus) {
    Text(
        text = status.label(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Every action on a row, named in words. The previous row carried four bare
 * glyphs; a menu costs one more tap and needs no legend.
 */
@Composable
private fun RowMenu(
    habit: Habit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onStatus: (HabitStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { open = true }
                .semantics { contentDescription = "More actions for ${habit.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { action: () -> Unit -> { open = false; action() } }
            MenuItem("Edit", close(onEdit))
            if (canMoveUp) MenuItem("Move up", close { onMove(-1) })
            if (canMoveDown) MenuItem("Move down", close { onMove(1) })
            HorizontalDivider()
            when (habit.status) {
                HabitStatus.Active -> {
                    MenuItem("Put to sleep", close { onStatus(HabitStatus.Sleeping) })
                    MenuItem("Archive", close { onStatus(HabitStatus.Archived) })
                }
                HabitStatus.Sleeping -> {
                    MenuItem("Wake up", close { onStatus(HabitStatus.Active) })
                    MenuItem("Archive", close { onStatus(HabitStatus.Archived) })
                }
                HabitStatus.Archived -> MenuItem("Restore", close { onStatus(HabitStatus.Active) })
            }
            MenuItem("Delete…", close(onDelete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    DropdownMenuItem(text = { Text(text, color = color) }, onClick = onClick)
}

/**
 * Deleting erases every logged day, which no undo brings back, so it asks
 * first and offers archiving as the gentler way to be rid of a habit.
 */
@Composable
private fun DeleteDialog(
    habit: Habit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “${habit.label.ifBlank { "this habit" }}”?") },
        text = {
            Text(
                if (habit.status == HabitStatus.Archived) {
                    "This erases every day you logged it, and cannot be undone."
                } else {
                    "This erases every day you logged it, and cannot be undone. " +
                        "Archive it instead to stop seeing it but keep its history."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (habit.status != HabitStatus.Archived) {
                    TextButton(onClick = onArchive) { Text("Archive") }
                }
            }
        },
    )
}

internal fun HabitStatus.label(): String = when (this) {
    HabitStatus.Active -> "Active"
    HabitStatus.Sleeping -> "Sleeping"
    HabitStatus.Archived -> "Archived"
}
