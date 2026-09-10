package dev.adambench.habbits.domain

/**
 * Moves one habit one place up or down the day.
 *
 * The list is treated as the eight category groups laid end to end, so moving
 * off the top of a group carries the habit into the bottom of the previous
 * category — the behaviour the Obsidian tracker had. Moving past either end of
 * the whole day is a no-op rather than a wrap-around.
 *
 * @param direction -1 for earlier in the day, +1 for later.
 * @return the habits in their new order, with [Habit.category] and
 *   [Habit.sortOrder] updated to match their positions.
 */
fun List<Habit>.movedBy(habitId: String, direction: Int): List<Habit> {
    if (direction != -1 && direction != 1) return this

    val groups = Category.entries.associateWith { category ->
        filter { it.category == category }.toMutableList()
    }

    val habit = firstOrNull { it.id == habitId } ?: return this
    val group = groups.getValue(habit.category)
    val indexInGroup = group.indexOfFirst { it.id == habitId }
    if (indexInGroup < 0) return this

    val categoryIndex = habit.category.ordinal
    val target = indexInGroup + direction

    if (target in group.indices) {
        // Simple swap with the neighbour inside the same category.
        group[indexInGroup] = group[target].also { group[target] = group[indexInGroup] }
    } else {
        val newCategoryIndex = categoryIndex + direction
        val newCategory = Category.entries.getOrNull(newCategoryIndex)
            ?: return this // already at the very start or end of the day
        group.removeAt(indexInGroup)
        val destination = groups.getValue(newCategory)
        val moved = habit.copy(category = newCategory)
        if (direction == -1) destination.add(moved) else destination.add(0, moved)
    }

    return Category.entries
        .flatMap { groups.getValue(it) }
        .mapIndexed { index, h -> h.copy(sortOrder = index) }
}
