package io.github.adambench.habbits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.adambench.habbits.data.SettingsRepository
import io.github.adambench.habbits.ui.manage.ManageScreen
import io.github.adambench.habbits.ui.manage.ManageScreenModel
import io.github.adambench.habbits.ui.settings.SettingsScreen
import io.github.adambench.habbits.ui.stats.StatsScreen
import io.github.adambench.habbits.ui.stats.StatsScreenModel
import kotlinx.datetime.LocalDate

private enum class Screen { Day, Manage, Settings, Stats }

/**
 * Two screens and a flag rather than a navigation library: the app has one
 * destination beyond the day view, and a graph would be more moving parts than
 * the problem has.
 */
@Composable
fun AppRoot(
    dayModel: DayScreenModel,
    manageModel: ManageScreenModel,
    statsModel: StatsScreenModel,
    settingsRepository: SettingsRepository,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onImport: (() -> Unit)? = null,
    onPickSyncFolder: (() -> Unit)? = null,
    onSyncNow: (suspend () -> String)? = null,
    onRemindersChanged: (() -> Unit)? = null,
) {
    var screen by remember { mutableStateOf(Screen.Day) }

    when (screen) {
        Screen.Day -> DayScreen(
            model = dayModel,
            modifier = modifier,
            onImport = onImport,
            onManage = { screen = Screen.Manage },
            onStats = {
                // Recompute on entry: the day view may have changed things.
                statsModel.refresh()
                screen = Screen.Stats
            },
        )

        Screen.Stats -> StatsScreen(
            model = statsModel,
            onDone = { screen = Screen.Day },
            modifier = modifier,
        )

        Screen.Manage -> ManageScreen(
            model = manageModel,
            onDone = { screen = Screen.Day },
            onSettings = { screen = Screen.Settings },
            modifier = modifier,
        )

        Screen.Settings -> SettingsScreen(
            repository = settingsRepository,
            today = today,
            onDone = { screen = Screen.Manage },
            modifier = modifier,
            onPickSyncFolder = onPickSyncFolder,
            onSyncNow = onSyncNow,
            onRemindersChanged = onRemindersChanged,
        )
    }
}
