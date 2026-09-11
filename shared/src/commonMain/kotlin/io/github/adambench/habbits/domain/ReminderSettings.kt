package io.github.adambench.habbits.domain

import kotlinx.serialization.Serializable

/**
 * A reminder for one prayer window.
 *
 * [offsetMinutes] is relative to the moment the window opens, so a reminder set
 * to Maghrib + 20 follows the sun through the year instead of being a fixed
 * clock time that drifts out of usefulness every few weeks.
 *
 * Windows with no computed time — Anytime and Before Fajr — fall back to
 * [fallbackHour]/[fallbackMinute] as an ordinary clock time.
 */
@Serializable
data class WindowReminder(
    val category: Category,
    val enabled: Boolean = false,
    val offsetMinutes: Int = 15,
    val fallbackHour: Int = 9,
    val fallbackMinute: Int = 0,
)

@Serializable
data class ReminderSettings(
    /** Off by default: reminders are the only thing that needs a permission. */
    val enabled: Boolean = false,
    /** Stay quiet when everything due in the window is already logged. */
    val silentWhenDone: Boolean = true,
    val windows: List<WindowReminder> = Category.entries.map { WindowReminder(it) },
) {
    fun forCategory(category: Category): WindowReminder =
        windows.firstOrNull { it.category == category } ?: WindowReminder(category)

    val activeCount: Int get() = if (!enabled) 0 else windows.count { it.enabled }
}
