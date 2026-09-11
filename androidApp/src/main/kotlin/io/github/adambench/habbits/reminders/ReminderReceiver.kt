package io.github.adambench.habbits.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.adambench.habbits.AppContainer
import io.github.adambench.habbits.HabbitsApplication
import io.github.adambench.habbits.MainActivity
import io.github.adambench.habbits.R
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.PrayerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HabbitsApplication ?: return
        val container = app.container
        // The work touches the database, so the broadcast is kept alive rather
        // than blocking the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
                    notifyDueWindow(context, container)
                }
                ReminderScheduler.reschedule(context, container)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyDueWindow(context: Context, container: AppContainer) {
        val settings = container.settingsRepository.settings.value
        if (!settings.reminders.enabled) return

        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val times = PrayerClock.timesFor(today, settings, zone)
        // Whichever window is live is the one this alarm belongs to.
        val category = times.windowAt(container.nowTime())
        if (!settings.reminders.forCategory(category).enabled) return

        val outstanding = container.outstanding(today, category)
        if (outstanding.isEmpty() && settings.reminders.silentWhenDone) return

        post(context, category, outstanding.map { it.label })
    }

    private fun post(context: Context, category: Category, labels: List<String>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val body = when {
            labels.isEmpty() -> "Nothing left in this window."
            labels.size <= 3 -> labels.joinToString(", ")
            else -> labels.take(3).joinToString(", ") + " and ${labels.size - 3} more"
        }

        val open = PendingIntent.getActivity(
            context,
            category.ordinal,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (labels.isEmpty()) {
                    category.displayName
                } else {
                    "${category.displayName} — ${labels.size} outstanding"
                },
            )
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { manager.notify(category.ordinal, notification) }
    }

    companion object {
        const val ACTION_FIRE = "io.github.adambench.habbits.REMINDER"
        const val CHANNEL_ID = "habit_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    }
}
