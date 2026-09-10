package dev.adambench.habbits.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.adambench.habbits.ui.theme.accent

/** 48dp keeps every tap target within the accessibility minimum. */
private val MIN_TARGET = 48.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    row: HabitRow,
    isExpanded: Boolean,
    showDescription: Boolean,
    hapticsEnabled: Boolean,
    onToggle: () -> Unit,
    onExpandToggle: () -> Unit,
    onLongPress: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = row.habit
    val accent = habit.type.accent()
    val completed = row.isCompleted
    val haptics = LocalHapticFeedback.current

    val container by animateColorAsState(
        targetValue = if (completed) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "cardBackground",
    )
    val outline = if (completed) accent else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(1.dp, outline, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                onLongClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onLongPress()
                },
            )
            .semantics {
                stateDescription = if (completed) "Completed" else "Not completed"
                contentDescription = buildString {
                    append(habit.label)
                    if (habit.isMeasured) append(", ${row.value} ${habit.unit.orEmpty()}")
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TARGET)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colour ribbon: the only cue for type while a habit is incomplete.
            if (!completed) {
                Spacer(
                    Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Spacer(Modifier.width(9.dp))
            }

            CheckCircle(completed = completed, accent = accent)
            Spacer(Modifier.width(12.dp))

            Text(
                text = habit.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (completed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (completed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (habit.isMeasured) {
                Spacer(Modifier.width(8.dp))
                ValuePill(
                    value = row.value,
                    unit = habit.unit.orEmpty(),
                    accent = accent,
                    expanded = isExpanded,
                    onClick = onExpandToggle,
                )
            }
        }

        // 28 of the imported habits carry a description the day view never
        // showed. Long-press reveals it without cluttering the list.
        AnimatedVisibility(visible = showDescription && !habit.description.isNullOrBlank()) {
            Text(
                text = habit.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }

        AnimatedVisibility(visible = isExpanded && habit.isMeasured) {
            StepperRow(step = habit.step, accent = accent, onStep = onStep)
        }
    }
}

@Composable
private fun CheckCircle(completed: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (completed) accent else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (completed) accent else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // A tick as well as the fill, so state never rests on colour alone.
        if (completed) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun ValuePill(
    value: Int,
    unit: String,
    accent: Color,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (expanded) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (unit.isBlank()) "$value" else "$value $unit",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun StepperRow(step: Int, accent: Color, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepButton("-${step * 2}", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) {
            onStep(-step * 2)
        }
        StepButton("-$step", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) {
            onStep(-step)
        }
        StepButton("+$step", accent, Modifier.weight(1f)) { onStep(step) }
        StepButton("+${step * 2}", accent, Modifier.weight(1f)) { onStep(step * 2) }
    }
}

@Composable
private fun StepButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
