package io.github.adambench.habbits.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.adambench.habbits.AppContainer
import io.github.adambench.habbits.domain.PrayerClock
import io.github.adambench.habbits.domain.ReminderPlanner
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Arms a single alarm for the next reminder, which re-arms the following one
 * when it fires.
 *
 * The alarm is exact and allowed while idle. An inexact alarm is held back
 * while the phone dozes — on a nightstand or a desk that is hours, which in
 * practice meant reminders never arrived. Prayer reminders are what exact
 * alarms exist for, and `USE_EXACT_ALARM` is granted at install for them.
 *
 * The alarm carries which windows it is for, rather than the receiver working
 * it out from the clock: a window with no prayer time, or a reminder set to go
 * off before its window opens, fires while a different window is live.
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001

    const val EXTRA_CATEGORIES = "categories"
    const val EXTRA_DATE = "date"
    const val EXTRA_AT = "at"

    /**
     * Arms the next reminder after [after], or after now.
     *
     * When a reminder fires late it passes its own time here, so any window
     * that fell due in the meantime still fires instead of being skipped.
     */
    fun reschedule(context: Context, container: AppContainer, after: LocalDateTime? = null) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return

        val settings = container.settingsRepository.settings.value
        if (!settings.reminders.enabled || settings.reminders.activeCount == 0) {
            alarms.cancel(pendingIntent(context, Intent()))
            return
        }

        val zone = TimeZone.currentSystemDefault()
        val from = after ?: Clock.System.now().toLocalDateTime(zone)
        val batch = ReminderPlanner.nextBatch(settings.reminders, from) { date ->
            PrayerClock.timesFor(date, settings, zone)
        }
        if (batch.isEmpty()) {
            alarms.cancel(pendingIntent(context, Intent()))
            return
        }

        val at = batch.first().at
        val triggerAt = at.toInstant(zone).toEpochMilliseconds()
        val intent = pendingIntent(
            context,
            Intent()
                .putExtra(EXTRA_CATEGORIES, batch.map { it.category.ordinal }.toIntArray())
                .putExtra(EXTRA_DATE, at.date.toEpochDays())
                .putExtra(EXTRA_AT, triggerAt),
        )
        runCatching {
            if (canScheduleExact(alarms)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        }
    }

    fun canScheduleExact(alarms: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Extras do not take part in matching, so every call here names the same alarm. */
    private fun pendingIntent(context: Context, extras: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtras(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
