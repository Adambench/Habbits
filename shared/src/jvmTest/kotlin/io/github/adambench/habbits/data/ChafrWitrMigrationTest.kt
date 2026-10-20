package io.github.adambench.habbits.data

import io.github.adambench.habbits.AppContainer
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.SettingsStore
import io.github.adambench.habbits.sync.FileSyncStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChafrWitrMigrationTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "habbits-migration-${System.nanoTime()}")
    private val syncDir = File(root, "shared").apply { mkdirs() }
    private val devices = mutableListOf<AppContainer>()

    private val witr = Habit(id = "chafrWaWitr", label = "Chafr and Witr", category = Category.BeforeFajr, sortOrder = 1)
    private val tahajjud = Habit(id = "tahajjud", label = "Tahajjud", category = Category.BeforeFajr, sortOrder = 0)
    private val prayedNights = listOf(LocalDate(2026, 10, 10), LocalDate(2026, 10, 11), LocalDate(2026, 10, 14))

    private class MemorySettings : SettingsStore {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private fun device(name: String, synced: Boolean = true, startClock: Long = 1_000L): AppContainer {
        var clock = startClock
        val dir = File(root, name).apply { mkdirs() }
        return AppContainer(
            database = createHabbitsDatabase(dir),
            settingsStore = MemorySettings(),
            deviceId = name,
            syncStoreFor = { FileSyncStore(File(it), name) },
            now = { clock++ },
        ).also {
            if (synced) it.bindSync(syncDir.absolutePath)
            devices += it
        }
    }

    /** Logged the old way: each night's Witr stored on the following morning. */
    private suspend fun AppContainer.logOldWay() {
        repository.saveHabit(tahajjud)
        repository.saveHabit(witr)
        prayedNights.forEach {
            repository.toggle(it.plusDays(1), witr.id)
            repository.toggle(it.plusDays(1), tahajjud.id)
        }
    }

    private suspend fun AppContainer.witrDays(): List<LocalDate> =
        database.entryDao().getAllForHabit(witr.id).map { LocalDate.fromEpochDays(it.date) }

    private suspend fun AppContainer.tahajjudDays(): List<LocalDate> =
        database.entryDao().getAllForHabit(tahajjud.id).map { LocalDate.fromEpochDays(it.date) }

    private suspend fun AppContainer.witrHabit(): Habit = repository.getHabits().first { it.id == witr.id }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

    @AfterTest
    fun tearDown() {
        devices.forEach { it.close() }
        root.deleteRecursively()
    }

    @Test
    fun history_moves_back_a_day_and_the_habit_moves_to_the_end_of_isha() = runTest {
        val phone = device("phone", synced = false)
        phone.repository.saveHabit(Habit(id = "isha", label = "Isha", category = Category.Isha, sortOrder = 5))
        phone.logOldWay()

        phone.runMigrations()

        assertEquals(prayedNights, phone.witrDays())
        assertEquals(Category.Isha, phone.witrHabit().category)
        assertEquals(witr.id, phone.repository.getHabits().last().id, "last in Isha")
        assertEquals(prayedNights.map { it.plusDays(1) }, phone.tahajjudDays(), "other habits untouched")
    }

    @Test
    fun running_again_changes_nothing_even_if_moved_back_by_hand() = runTest {
        val phone = device("phone", synced = false)
        phone.logOldWay()
        phone.runMigrations()

        phone.repository.saveHabit(phone.witrHabit().copy(category = Category.BeforeFajr))
        phone.runMigrations()

        assertEquals(prayedNights, phone.witrDays())
    }

    @Test
    fun a_second_device_takes_the_result_instead_of_shifting_again() = runTest {
        val phone = device("phone")
        val laptop = device("laptop")
        phone.logOldWay()
        phone.flushSync()
        laptop.syncNow()

        phone.runMigrations()
        // The laptop has the old data locally and has not synced since.
        laptop.runMigrations()
        phone.syncNow()

        assertEquals(prayedNights, laptop.witrDays())
        assertEquals(prayedNights, phone.witrDays())
        assertEquals(Category.Isha, laptop.witrHabit().category)
    }

    @Test
    fun a_stale_category_from_an_old_version_does_not_trigger_a_second_shift() = runTest {
        val phone = device("phone")
        // A clock well ahead, so its stale write wins the merge.
        val laptop = device("laptop", startClock = 1_000_000L)
        phone.logOldWay()
        phone.flushSync()
        laptop.syncNow()

        phone.runMigrations()
        // An un-updated laptop syncs, then drags the habit back to Before Fajr.
        laptop.syncNow()
        laptop.repository.applyOrder(
            laptop.repository.getHabits().map { if (it.id == witr.id) it.copy(category = Category.BeforeFajr) else it },
        )
        laptop.flushSync()

        // The phone sees Before Fajr again, but the marker holds.
        phone.syncNow()
        assertEquals(Category.BeforeFajr, phone.witrHabit().category, "precondition: the stale write won")
        phone.settingsRepository.update(phone.settingsRepository.settings.value.copy(completedMigrations = emptySet()))
        phone.runMigrations()

        assertEquals(prayedNights, phone.witrDays())
    }

    @Test
    fun a_device_without_the_habit_yet_tries_again_later() = runTest {
        val phone = device("phone", synced = false)
        phone.runMigrations()
        assertTrue(phone.settingsRepository.settings.value.completedMigrations.isEmpty())

        phone.logOldWay()
        phone.runMigrations()
        assertEquals(prayedNights, phone.witrDays())
    }
}
