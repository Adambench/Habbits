package io.github.adambench.habbits.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** A reminder that should fire at [at] for [category]. */
data class PlannedReminder(
    val category: Category,
    val at: LocalDateTime,
)

/**
 * Works out when reminders should next fire.
 *
 * Kept free of Android so the arithmetic — offsets, day rollover, windows that
 * have no prayer time — is testable without a device.
 */
object ReminderPlanner {

    /**
     * The next firing time for every enabled window, at or after [now].
     *
     * A window whose time has already passed today rolls to tomorrow, and
     * tomorrow's prayer times are used for it rather than today's, since they
     * differ by a few minutes every day.
     */
    fun plan(
        settings: ReminderSettings,
        now: LocalDateTime,
        timesFor: (LocalDate) -> DayPrayerTimes,
    ): List<PlannedReminder> {
        if (!settings.enabled) return emptyList()

        val today = now.date
        val tomorrow = today.plus(DatePeriod(days = 1))
        val todayTimes = timesFor(today)
        val tomorrowTimes = timesFor(tomorrow)

        return settings.windows
            .filter { it.enabled }
            .mapNotNull { window ->
                val todayAt = fireTime(window, todayTimes)
                val at = if (todayAt != null && todayAt > now.time) {
                    LocalDateTime(today, todayAt)
                } else {
                    val tomorrowAt = fireTime(window, tomorrowTimes) ?: return@mapNotNull null
                    LocalDateTime(tomorrow, tomorrowAt)
                }
                PlannedReminder(window.category, at)
            }
            .sortedBy { it.at }
    }

    /** The soonest reminder, which is the only one an alarm needs to hold. */
    fun next(
        settings: ReminderSettings,
        now: LocalDateTime,
        timesFor: (LocalDate) -> DayPrayerTimes,
    ): PlannedReminder? = plan(settings, now, timesFor).firstOrNull()

    private fun fireTime(window: WindowReminder, times: DayPrayerTimes): LocalTime? {
        val base = times[window.category]
            ?: return LocalTime(
                window.fallbackHour.coerceIn(0, 23),
                window.fallbackMinute.coerceIn(0, 59),
            )
        val total = base.hour * 60 + base.minute + window.offsetMinutes
        // Clamped rather than wrapped: a reminder pushed past midnight belongs
        // to a different day's window, and silently moving it would confuse.
        val clamped = total.coerceIn(0, 24 * 60 - 1)
        return LocalTime(clamped / 60, clamped % 60)
    }
}
