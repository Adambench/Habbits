package dev.adambench.habbits

import dev.adambench.habbits.data.HabbitsDatabase
import dev.adambench.habbits.data.HabitRepository
import dev.adambench.habbits.data.SettingsRepository
import dev.adambench.habbits.sync.HlcGenerator
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.DayScreenModel
import dev.adambench.habbits.ui.manage.ManageScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Wiring for the whole app. Manual rather than a DI framework: there are about
 * six objects here, and annotation processing would cost build time for nothing.
 */
class AppContainer(
    val database: HabbitsDatabase,
    settingsStore: dev.adambench.habbits.domain.SettingsStore,
    deviceId: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val clock = HlcGenerator(deviceId, now)

    val repository: HabitRepository = HabitRepository(
        habitDao = database.habitDao(),
        entryDao = database.entryDao(),
        clock = clock,
        now = now,
    )

    /** Local calendar date — the unit habits are logged against. */
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val settingsRepository: SettingsRepository = SettingsRepository(settingsStore)

    fun nowTime(): LocalTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

    fun dayScreenModel(scope: CoroutineScope): DayScreenModel =
        DayScreenModel(repository, settingsRepository, scope, ::today, ::nowTime)

    fun importer(): VaultImporter = VaultImporter(database, clock, now)

    fun manageScreenModel(scope: CoroutineScope): ManageScreenModel =
        ManageScreenModel(repository, scope) { "habit_${now()}" }

    fun close() = database.close()
}
