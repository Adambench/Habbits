package io.github.adambench.habbits.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.adambench.habbits.AppContainer
import io.github.adambench.habbits.domain.PrayerClock
import io.github.adambench.habbits.domain.ReminderPlanner
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Arms a single alarm for the next reminder, which re-arms the following one
 * when it fires.
 *
 * Uses an inexact window rather than an exact alarm: exact alarms need a
 * restricted permission an app like this cannot justify, and a prayer reminder
 * is just as useful a couple of minutes either side.
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001
    private const val WINDOW_MILLIS = 5L * 60 * 1000

    fun reschedule(context: Context, container: AppContainer) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)

        val settings = container.settingsRepository.settings.value
        if (!settings.reminders.enabled || settings.reminders.activeCount == 0) {
            alarms.cancel(intent)
            return
        }

        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val next = ReminderPlanner.next(settings.reminders, now) { date ->
            PrayerClock.timesFor(date, settings, zone)
        }
        if (next == null) {
            alarms.cancel(intent)
            return
        }

        val triggerAt = next.at.toInstant(zone).toEpochMilliseconds()
        runCatching { alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, intent) }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
