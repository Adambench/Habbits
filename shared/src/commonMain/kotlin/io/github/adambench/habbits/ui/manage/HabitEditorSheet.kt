package io.github.adambench.habbits.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.HabitType
import io.github.adambench.habbits.domain.Weekdays
import io.github.adambench.habbits.ui.headerLabel
import io.github.adambench.habbits.ui.theme.accent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Two letters, because one leaves T and S ambiguous. */
private val WEEKDAY_LABELS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    habit: Habit,
    isNew: Boolean,
    today: LocalDate,
    onChange: (Habit) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // What was there when the sheet opened, so a swipe or a stray tap outside
    // cannot quietly throw away edits.
    val original = remember(habit.id) { habit }
    val isDirty by rememberUpdatedState(habit != original)
    var confirmDiscard by remember { mutableStateOf(false) }

    // Counting is a choice in its own right, not merely "a unit was typed": the
    // unit box is empty at the moment it is chosen.
    var counting by remember(habit.id) { mutableStateOf(habit.isMeasured) }
    val unitMissing = counting && habit.unit.isNullOrBlank()
    val canSave = habit.label.isNotBlank() && !unitMissing

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && isDirty) {
                confirmDiscard = true
                false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        EditorHeader(
            title = if (isNew) "New habit" else "Edit habit",
            canSave = canSave,
            onCancel = onDismiss,
            onSave = onSave,
        )

        // The header stays put while the form scrolls under it, so Save is
        // always in reach.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SummaryCard(
                text = when {
                    habit.label.isBlank() -> "Give the habit a name to save it."
                    unitMissing -> "Say what you are counting, or switch to tick off."
                    else -> habit.sentence(today)
                },
                isHint = !canSave,
            )

            Section("Name") {
                OutlinedTextField(
                    value = habit.label,
                    onValueChange = { onChange(habit.copy(label = it)) },
                    placeholder = { Text("e.g. Morning adhkar") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = habit.description.orEmpty(),
                    onValueChange = { onChange(habit.copy(description = it.ifBlank { null })) },
                    label = { Text("Note (optional)") },
                    supportingText = { Text("Shown when you press and hold the habit.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Time of day", help = "Which part of the day it is listed under.") {
                ChipFlow(
                    options = Category.entries,
                    selected = habit.category,
                    label = { it.displayName },
                    onSelect = { onChange(habit.copy(category = it)) },
                )
            }

            Section("Repeats") {
                Segmented(
                    options = FrequencyType.entries,
                    selected = habit.frequencyType,
                    label = {
                        when (it) {
                            FrequencyType.Daily -> "Every day"
                            FrequencyType.Weekly -> "Some days"
                            FrequencyType.Interval -> "Every few days"
                        }
                    },
                    onSelect = { onChange(habit.withFrequency(it, today)) },
                )
                when (habit.frequencyType) {
                    FrequencyType.Daily -> Unit
                    FrequencyType.Weekly -> WeekdayPicker(
                        days = habit.recurringDays,
                        onToggle = { iso ->
                            val next = if (iso in habit.recurringDays) {
                                habit.recurringDays.minus(iso)
                            } else {
                                habit.recurringDays.plus(iso)
                            }
                            onChange(habit.copy(recurringDays = next))
                        },
                    )
                    FrequencyType.Interval -> IntervalPicker(habit, today, onChange)
                }
            }

            Section("Tracking") {
                Segmented(
                    options = listOf(false, true),
                    selected = counting,
                    label = { if (it) "Count an amount" else "Tick off" },
                    onSelect = { count ->
                        counting = count
                        if (!count) onChange(habit.copy(unit = null))
                    },
                )
                if (counting) {
                    CountFields(habit, unitMissing, onChange)
                } else {
                    Help("One tap marks it done for the day.")
                }
            }

            Section("Colour", help = "Colours the card, and groups the habit in stats.") {
                ChipFlow(
                    options = HabitType.entries,
                    selected = habit.type,
                    label = { it.name },
                    tint = { it.accent() },
                    onSelect = { onChange(habit.copy(type = it)) },
                )
            }

            if (!isNew) {
                Section("Status") {
                    HabitStatus.entries.forEach { status ->
                        StatusOption(
                            status = status,
                            selected = habit.status == status,
                            onSelect = { onChange(habit.copy(status = status)) },
                        )
                    }
                }

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Delete habit",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this habit have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

/**
 * Switching to an interval with nothing to count from would leave the habit
 * showing every day, which is not what anyone choosing "every few days" means.
 */
private fun Habit.withFrequency(type: FrequencyType, today: LocalDate): Habit = when (type) {
    FrequencyType.Interval -> copy(
        frequencyType = type,
        intervalDays = intervalDays?.takeIf { it >= 2 } ?: 2,
        intervalStart = intervalStart ?: today,
    )
    else -> copy(frequencyType = type)
}

@Composable
private fun EditorHeader(title: String, canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = canSave, onClick = onSave)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSave) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The habit read back in a sentence, or what still stands in the way of saving. */
@Composable
private fun SummaryCard(text: String, isHint: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isHint) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHint) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
            .padding(14.dp),
    )
}

@Composable
private fun Section(title: String, help: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (help != null) Help(help)
        }
        content()
    }
}

@Composable
private fun Help(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CountFields(habit: Habit, unitMissing: Boolean, onChange: (Habit) -> Unit) {
    val unit = habit.unit?.trim().orEmpty().ifBlank { "units" }
    OutlinedTextField(
        value = habit.unit.orEmpty(),
        onValueChange = { onChange(habit.copy(unit = it.ifBlank { null })) },
        label = { Text("What are you counting?") },
        placeholder = { Text("pages, raka'ats, minutes") },
        isError = unitMissing,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        NumberField(
            value = habit.defaultValue,
            label = "Ticking logs",
            onValueChange = { onChange(habit.copy(defaultValue = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = habit.step,
            label = "Buttons change by",
            onValueChange = { onChange(habit.copy(step = it ?: Habit.DEFAULT_STEP)) },
            modifier = Modifier.weight(1f),
        )
    }
    val logged = habit.defaultValue?.takeIf { it > 0 }
    Help(
        (if (logged != null) "Ticking the habit logs $logged $unit. " else "Ticking the habit logs no amount. ") +
            "Tap the amount on its card for buttons that add or take away ${habit.step}.",
    )
}

@Composable
private fun IntervalPicker(habit: Habit, today: LocalDate, onChange: (Habit) -> Unit) {
    val every = habit.effectiveInterval
    val start = habit.intervalStart ?: today
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperRow(
            label = "Every",
            value = "$every days",
            canDecrease = every > 2,
            onDecrease = { onChange(habit.copy(intervalDays = every - 1)) },
            onIncrease = { onChange(habit.copy(intervalDays = every + 1)) },
        )
        StepperRow(
            label = "Starting",
            value = if (start == today) "Today" else start.headerLabel(),
            canDecrease = true,
            onDecrease = { onChange(habit.copy(intervalStart = start.plus(DatePeriod(days = -1)))) },
            onIncrease = { onChange(habit.copy(intervalStart = start.plus(DatePeriod(days = 1)))) },
        )
        habit.nextDue(today)?.let { next ->
            Help(
                if (next == today) {
                    "Due today, then every $every days."
                } else {
                    "Next due ${next.headerLabel()}, then every $every days."
                },
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        StepButton("−", enabled = canDecrease, onClick = onDecrease)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("+", enabled = true, onClick = onIncrease)
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun StatusOption(status: HabitStatus, selected: Boolean, onSelect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A radio dot, so the three read as one choice rather than three toggles.
        Box(
            Modifier
                .size(20.dp)
                .border(2.dp, if (selected) accent else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = status.label(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = when (status) {
                    HabitStatus.Active -> "Shows up on the days it is due."
                    HabitStatus.Sleeping -> "Hidden from your day for now. Wake it any time."
                    HabitStatus.Archived -> "Retired. Its history stays in your stats."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberField(
    value: Int?,
    label: String,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            // Ignore anything that is not a number rather than silently zeroing.
            if (text.isBlank()) onValueChange(null)
            else text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A row of joined buttons for a choice between a few options. */
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> ChipFlow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    tint: (@Composable (T) -> Color)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val swatch = tint?.invoke(option)
            val accent = swatch ?: MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The colour itself, visible before it is chosen.
                if (swatch != null && !isSelected) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(swatch))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekdayPicker(days: Weekdays, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WEEKDAY_LABELS.forEachIndexed { index, letters ->
                val iso = index + 1
                val isOn = iso in days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isOn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable { onToggle(iso) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letters,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Help(
            if (days.isEmpty) {
                "No days picked yet, so it shows every day until you choose some."
            } else {
                "Tap the days it should show on."
            },
        )
    }
}
