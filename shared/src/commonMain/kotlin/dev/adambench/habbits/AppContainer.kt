package dev.adambench.habbits

import dev.adambench.habbits.data.HabbitsDatabase
import dev.adambench.habbits.data.HabitRepository
import dev.adambench.habbits.sync.HlcGenerator
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.DayScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Wiring for the whole app. Manual rather than a DI framework: there are about
 * six objects here, and annotation processing would cost build time for nothing.
 */
class AppContainer(
    val database: HabbitsDatabase,
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

    fun dayScreenModel(scope: CoroutineScope): DayScreenModel =
        DayScreenModel(repository, scope, ::today)

    fun importer(): VaultImporter = VaultImporter(database, clock, now)

    fun close() = database.close()
}
