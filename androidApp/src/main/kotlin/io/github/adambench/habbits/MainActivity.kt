package io.github.adambench.habbits

import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.adambench.habbits.reminders.ReminderReceiver
import io.github.adambench.habbits.reminders.ReminderScheduler
import io.github.adambench.habbits.ui.AppRoot
import io.github.adambench.habbits.ui.settings.ReminderProblem
import io.github.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HabbitsApplication).container }

    /** What is stopping reminders on this device, if anything. */
    private enum class ReminderBlock(val problem: ReminderProblem) {
        Notifications(
            ReminderProblem(
                "Notifications for Habbits are off in system settings, so no reminder can appear.",
                "Open notification settings",
            ),
        ),
        Channel(
            ReminderProblem(
                "The \"Habit reminders\" category is switched off in system settings.",
                "Open notification settings",
            ),
        ),
        ExactAlarms(
            ReminderProblem(
                "\"Alarms & reminders\" is not allowed, so reminders can arrive late while the phone is idle.",
                "Allow",
            ),
        ),
    }

    private val reminderBlock = MutableStateFlow<ReminderBlock?>(null)
    private val reminderProblem = MutableStateFlow<ReminderProblem?>(null)

    private fun refreshReminderProblem() {
        val block = when {
            !container.settingsRepository.settings.value.reminders.enabled -> null
            !NotificationManagerCompat.from(this).areNotificationsEnabled() -> ReminderBlock.Notifications
            ReminderReceiver.isChannelBlocked(this) -> ReminderBlock.Channel
            getSystemService(AlarmManager::class.java)
                ?.let { !ReminderScheduler.canScheduleExact(it) } == true -> ReminderBlock.ExactAlarms
            else -> null
        }
        reminderBlock.value = block
        reminderProblem.value = block?.problem
    }

    private fun openReminderFix() {
        val intent = when (reminderBlock.value) {
            ReminderBlock.Channel -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderReceiver.CHANNEL_ID)
            ReminderBlock.ExactAlarms -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
            } else {
                null
            }
            ReminderBlock.Notifications, null -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } ?: return
        runCatching { startActivity(intent) }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val dayModel = container.dayScreenModel(lifecycleScope)
        val manageModel = container.manageScreenModel(lifecycleScope)
        val statsModel = container.statsScreenModel(lifecycleScope)

        setContent {
            // The system picker grants read access to exactly one file, so the
            // app still needs no storage permission.
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                lifecycleScope.launch {
                    val result = runCatching {
                        val text = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        } ?: error("could not read the selected file")
                        container.importer().import(text)
                    }
                    val message = result.fold(
                        onSuccess = { r ->
                            "Imported ${r.entries} completions across ${r.days} days" +
                                if (r.hasProblems) " (${r.skipped.size} skipped)" else ""
                        },
                        onFailure = { "Import failed: ${it.message}" },
                    )
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                // Persist the grant, or access is lost as soon as the app restarts.
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val repo = container.settingsRepository
                val current = repo.settings.value
                repo.update(
                    current.copy(
                        sync = current.sync.copy(
                            enabled = true,
                            folder = uri.toString(),
                            folderLabel = uri.lastPathSegment ?: uri.toString(),
                        ),
                    ),
                )
                container.bindSync(uri.toString())
            }

            // Asked for only when reminders are switched on, never at launch.
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                val repo = container.settingsRepository
                val current = repo.settings.value
                if (granted) {
                    ReminderReceiver.ensureChannel(this@MainActivity)
                    ReminderScheduler.reschedule(this@MainActivity, container)
                    refreshReminderProblem()
                } else {
                    // Without the permission a reminder can never appear, so the
                    // setting is turned back off rather than silently doing nothing.
                    repo.update(current.copy(reminders = current.reminders.copy(enabled = false)))
                    Toast.makeText(
                        this@MainActivity,
                        "Reminders need notification permission",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            val settings by container.settingsRepository.settings.collectAsState()
            HabbitsTheme(appearance = settings.appearance) {
                AppRoot(
                    dayModel = dayModel,
                    manageModel = manageModel,
                    statsModel = statsModel,
                    settingsRepository = container.settingsRepository,
                    today = container.today(),
                    onImport = { picker.launch(arrayOf("application/json", "*/*")) },
                    onPickSyncFolder = { folderPicker.launch(null) },
                    onRemindersChanged = {
                        val enabled = container.settingsRepository.settings.value.reminders.enabled
                        if (enabled && !hasNotificationPermission()) {
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReminderReceiver.ensureChannel(this@MainActivity)
                            ReminderScheduler.reschedule(this@MainActivity, container)
                            refreshReminderProblem()
                        }
                    },
                    reminderProblem = reminderProblem,
                    onFixReminders = ::openReminderFix,
                    onTestReminder = {
                        val posted = ReminderReceiver.sendTest(this@MainActivity, container)
                        refreshReminderProblem()
                        if (posted) {
                            "Sent. If nothing appeared, check Do Not Disturb and the notification settings."
                        } else {
                            "Could not post it: notifications are blocked for Habbits."
                        }
                    },
                    onSyncNow = {
                        val report = withContext(Dispatchers.IO) { container.syncNow() }
                        if (report == null) {
                            "No sync folder configured"
                        } else {
                            "Merged ${report.eventsApplied} changes — " +
                                "${report.habits} habits, ${report.entries} completions" +
                                if (report.malformed > 0) " (${report.malformed} bad lines)" else ""
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from system settings is how most problems get fixed.
        refreshReminderProblem()
    }

    override fun onPause() {
        super.onPause()
        // Prayer times shift daily, so the next alarm is re-armed whenever the
        // app is left rather than only when a reminder fires.
        ReminderScheduler.reschedule(this, container)
        // Leaving the app is the natural moment to put pending events on disk.
        container.flushSync()
    }
}
