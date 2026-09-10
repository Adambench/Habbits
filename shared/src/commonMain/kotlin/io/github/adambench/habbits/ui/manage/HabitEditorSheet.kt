package io.github.adambench.habbits.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.HabitType
import io.github.adambench.habbits.domain.Weekdays
import io.github.adambench.habbits.ui.theme.accent
import kotlinx.datetime.LocalDate

private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    habit: Habit,
    isNew: Boolean,
    onChange: (Habit) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = if (isNew) "New habit" else "Edit habit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = habit.label,
                onValueChange = { onChange(habit.copy(label = it)) },
                label = { Text("Label") },
                singleLine = true,
                isError = habit.label.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = habit.description.orEmpty(),
                onValueChange = { onChange(habit.copy(description = it.ifBlank { null })) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("When in the day")
            ChipFlow(
                options = Category.entries,
                selected = habit.category,
                label = { it.displayName },
                onSelect = { onChange(habit.copy(category = it)) },
            )

            FieldLabel("Type")
            ChipFlow(
                options = HabitType.entries,
                selected = habit.type,
                label = { it.name },
                tint = { it.accent() },
                onSelect = { onChange(habit.copy(type = it)) },
            )

            FieldLabel("Schedule")
            ChipFlow(
                options = FrequencyType.entries,
                selected = habit.frequencyType,
                label = {
                    when (it) {
                        FrequencyType.Daily -> "Every day"
                        FrequencyType.Weekly -> "Certain days"
                        FrequencyType.Interval -> "Every N days"
                    }
                },
                onSelect = { onChange(habit.copy(frequencyType = it)) },
            )

            when (habit.frequencyType) {
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

                FrequencyType.Interval -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NumberField(
                        value = habit.intervalDays,
                        label = "Every N days",
                        onValueChange = { onChange(habit.copy(intervalDays = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = habit.intervalStart?.toString().orEmpty(),
                        onValueChange = { text ->
                            val parsed = runCatching { LocalDate.parse(text) }.getOrNull()
                            onChange(habit.copy(intervalStart = parsed))
                        },
                        label = { Text("Starting") },
                        placeholder = { Text("2026-09-10") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                FrequencyType.Daily -> Unit
            }

            FieldLabel("Measurement")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = habit.unit.orEmpty(),
                    onValueChange = { onChange(habit.copy(unit = it.ifBlank { null })) },
                    label = { Text("Unit") },
                    placeholder = { Text("Raka'ats") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                )
                NumberField(
                    value = habit.defaultValue,
                    label = "Default",
                    onValueChange = { onChange(habit.copy(defaultValue = it)) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = habit.step,
                    label = "Step",
                    onValueChange = { onChange(habit.copy(step = it ?: Habit.DEFAULT_STEP)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Leave the unit empty for a habit that is simply done or not done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FieldLabel("Status")
            ChipFlow(
                options = HabitStatus.entries,
                selected = habit.status,
                label = {
                    when (it) {
                        HabitStatus.Active -> "Active"
                        HabitStatus.Sleeping -> "Sleeping"
                        HabitStatus.Archived -> "Archived"
                    }
                },
                onSelect = { onChange(habit.copy(status = it)) },
            )

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) {
                    ActionButton(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        filled = false,
                        modifier = Modifier.weight(1f),
                    ) { onDelete(habit.id) }
                }
                ActionButton(
                    text = "Save",
                    color = MaterialTheme.colorScheme.primary,
                    filled = true,
                    enabled = habit.label.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { onSave() }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

@Composable
private fun <T> ChipFlow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    tint: (@Composable (T) -> Color)? = null,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val accent = tint?.invoke(option) ?: MaterialTheme.colorScheme.primary
            Box(
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
                contentAlignment = Alignment.Center,
            ) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WEEKDAY_LABELS.forEachIndexed { index, letter ->
                val iso = index + 1
                val isOn = iso in days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(44.dp)
                        .clip(CircleShape)
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
                        text = letter,
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
        if (days.isEmpty) {
            Text(
                text = "No days chosen — the habit shows every day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
        )
    }
}
