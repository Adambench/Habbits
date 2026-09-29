package io.github.adambench.habbits.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.adambench.habbits.AppContainer
import io.github.adambench.habbits.HabbitsApplication
import io.github.adambench.habbits.MainActivity
import io.github.adambench.habbits.R
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.PrayerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HabbitsApplication ?: return
        val container = app.container
        // The work touches the database, so the broadcast is kept alive rather
        // than blocking the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_FIRE) {
                    fire(context, container, intent)
                } else {
                    // Boot, an app update, or the clock or time zone changing:
                    // any of them can lose or invalidate the armed alarm.
                    ReminderScheduler.reschedule(context, container)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, container: AppContainer, intent: Intent) {
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0L)
        val categories = intent.getIntArrayExtra(ReminderScheduler.EXTRA_CATEGORIES)
            ?.toList()
            ?.mapNotNull { Category.entries.getOrNull(it) }
            .orEmpty()
        val date = intent.getLongExtra(ReminderScheduler.EXTRA_DATE, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let { LocalDate.fromEpochDays(it) }

        // A reminder hours late — the phone was off, say — is noise, not help.
        val stale = at > 0 && System.currentTimeMillis() - at > STALE_AFTER_MILLIS
        if (date != null && !stale) {
            categories.forEach { notifyWindow(context, container, date, it, isTest = false) }
        }

        // Planned from this reminder's own time, not from now, so a window that
        // fell due while this one was delayed still gets its turn.
        val after = if (at > 0) {
            Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault())
        } else {
            null
        }
        ReminderScheduler.reschedule(context, container, after)
    }

    companion object {
        const val ACTION_FIRE = "io.github.adambench.habbits.REMINDER"
        const val CHANNEL_ID = "habit_reminders"

        private const val STALE_AFTER_MILLIS = 2L * 60 * 60 * 1000
        private const val TEST_NOTIFICATION_ID = 999

        /**
         * Posts the reminder for [category] on [date], unless there is nothing
         * left to do and the user asked for quiet. Returns whether it posted.
         */
        suspend fun notifyWindow(
            context: Context,
            container: AppContainer,
            date: LocalDate,
            category: Category,
            isTest: Boolean,
        ): Boolean {
            val settings = container.settingsRepository.settings.value
            if (!isTest) {
                if (!settings.reminders.enabled) return false
                if (!settings.reminders.forCategory(category).enabled) return false
            }

            val outstanding = container.outstanding(date, category)
            if (!isTest && outstanding.isEmpty() && settings.reminders.silentWhenDone) return false

            return post(
                context = context,
                id = if (isTest) TEST_NOTIFICATION_ID else category.ordinal,
                title = buildString {
                    if (isTest) append("Test · ")
                    append(category.displayName)
                    if (outstanding.isNotEmpty()) append(" — ${outstanding.size} outstanding")
                },
                labels = outstanding.map { it.label },
            )
        }

        /**
         * Posts a reminder for whichever window is live right now, whatever the
         * settings say, so the whole path to the notification shade can be
         * checked on the spot.
         */
        suspend fun sendTest(context: Context, container: AppContainer): Boolean {
            val settings = container.settingsRepository.settings.value
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(zone)
            val live = PrayerClock.timesFor(today, settings, zone).windowAt(container.nowTime())
            return notifyWindow(context, container, today, live, isTest = true)
        }

        private fun post(context: Context, id: Int, title: String, labels: List<String>): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            ensureChannel(context)

            val body = when {
                labels.isEmpty() -> "Nothing left in this window."
                labels.size <= 3 -> labels.joinToString(", ")
                else -> labels.take(3).joinToString(", ") + " and ${labels.size - 3} more"
            }

            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(labels.joinToString("\n").ifEmpty { body }))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()

            return runCatching { manager.notify(id, notification) }.isSuccess
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Habit reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        "A nudge when a prayer window opens and something is still outstanding"
                },
            )
        }

        /** Whether the reminder channel has been switched off in system settings. */
        fun isChannelBlocked(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
            return channel.importance == NotificationManager.IMPORTANCE_NONE
        }
    }
}
