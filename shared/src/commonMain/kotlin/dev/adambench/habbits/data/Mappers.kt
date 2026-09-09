package dev.adambench.habbits.data

import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.FrequencyType
import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.domain.HabitType
import dev.adambench.habbits.domain.Weekdays
import kotlinx.datetime.LocalDate

/**
 * `entries` is already a `List`, so this indexes it directly rather than
 * allocating an array per row — this runs 3,187 times during import.
 */
private fun <T : Enum<T>> List<T>.atOrFirst(ordinal: Int): T =
    getOrNull(ordinal) ?: first()

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    label = label,
    description = description,
    category = Category.entries.atOrFirst(category),
    type = HabitType.entries.atOrFirst(type),
    unit = unit,
    defaultValue = defaultValue,
    step = step,
    frequencyType = FrequencyType.entries.atOrFirst(frequencyType),
    recurringDays = Weekdays(recurringDays),
    intervalDays = intervalDays,
    intervalStart = intervalStart?.let { LocalDate.fromEpochDays(it) },
    status = HabitStatus.entries.atOrFirst(status),
    sortOrder = sortOrder,
)

fun Habit.toEntity(hlc: String, createdAt: Long): HabitEntity = HabitEntity(
    id = id,
    label = label,
    description = description,
    category = category.ordinal,
    type = type.ordinal,
    unit = unit,
    defaultValue = defaultValue,
    step = step,
    frequencyType = frequencyType.ordinal,
    recurringDays = recurringDays.mask,
    intervalDays = intervalDays,
    intervalStart = intervalStart?.toEpochDays(),
    status = status.ordinal,
    sortOrder = sortOrder,
    createdAt = createdAt,
    hlc = hlc,
)
