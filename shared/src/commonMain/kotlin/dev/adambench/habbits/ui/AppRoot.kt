package dev.adambench.habbits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.adambench.habbits.data.SettingsRepository
import dev.adambench.habbits.ui.manage.ManageScreen
import dev.adambench.habbits.ui.manage.ManageScreenModel
import dev.adambench.habbits.ui.settings.SettingsScreen
import kotlinx.datetime.LocalDate

private enum class Screen { Day, Manage, Settings }

/**
 * Two screens and a flag rather than a navigation library: the app has one
 * destination beyond the day view, and a graph would be more moving parts than
 * the problem has.
 */
@Composable
fun AppRoot(
    dayModel: DayScreenModel,
    manageModel: ManageScreenModel,
    settingsRepository: SettingsRepository,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onImport: (() -> Unit)? = null,
) {
    var screen by remember { mutableStateOf(Screen.Day) }

    when (screen) {
        Screen.Day -> DayScreen(
            model = dayModel,
            modifier = modifier,
            onImport = onImport,
            onManage = { screen = Screen.Manage },
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
        )
    }
}
