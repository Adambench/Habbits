package io.github.adambench.habbits.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.sync.BufferedSyncJournal
import io.github.adambench.habbits.sync.FileSyncStore
import io.github.adambench.habbits.sync.HlcGenerator
import io.github.adambench.habbits.sync.SyncMerger
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MoveAcrossMidnightTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "habbits-midnight-${System.nanoTime()}")
    private val syncDir = File(root, "shared").apply { mkdirs() }
    private val devices = mutableListOf<Device>()

    private val witr = Habit(id = "chafrWaWitr", label = "Chafr and Witr", category = Category.BeforeFajr, sortOrder = 1)
    private val tahajjud = Habit(id = "tahajjud", label = "Tahajjud", category = Category.BeforeFajr, sortOrder = 0)
    private val isha = Habit(id = "isha", label = "Isha", category = Category.Isha, sortOrder = 2)
    private val nights = listOf(LocalDate(2026, 10, 10), LocalDate(2026, 10, 11), LocalDate(2026, 10, 14))
    private val mornings = nights.map { LocalDate.fromEpochDays(it.toEpochDays() + 1) }

    /** Each device has its own database and log, sharing one folder, as under Syncthing. */
    inner class Device(name: String) {
        val db = createHabbitsDatabase(File(root, name).apply { mkdirs() })
        val store = FileSyncStore(syncDir, name)
        val journal = BufferedSyncJournal(store, flushThreshold = 1_000)
        var clock = 1_000L
        val repository = HabitRepository(
            habitDao = db.habitDao(),
            entryDao = db.entryDao(),
            clock = HlcGenerator(name) { clock++ },
            now = { clock },
            journal = journal,
            transaction = { block -> db.useWriterConnection { it.immediateTransaction { block() } } },
        )

        suspend fun sync() {
            journal.flush()
            SyncMerger(db, store).merge()
        }

        suspend fun days(id: String) = db.entryDao().getAllForHabit(id).map { LocalDate.fromEpochDays(it.date) }

        suspend fun habit(id: String) = repository.getHabits().first { it.id == id }
    }

    private fun device(name: String) = Device(name).also { devices += it }

    /** Logged the Before Fajr way: each night's prayer on the following morning. */
    private suspend fun Device.logBeforeFajr() {
        listOf(tahajjud, witr, isha).forEach { repository.saveHabit(it) }
        mornings.forEach {
            repository.toggle(it, witr.id)
            repository.toggle(it, tahajjud.id)
        }
    }

    @AfterTest
    fun tearDown() {
        devices.forEach { it.db.close() }
        root.deleteRecursively()
    }

    @Test
    fun before_fajr_to_isha_moves_history_a_day_earlier_and_the_habit_last() = runTest {
        val phone = device("phone")
        phone.logBeforeFajr()

        phone.repository.moveAcrossMidnight(witr.id)

        assertEquals(nights, phone.days(witr.id))
        assertEquals(Category.Isha, phone.habit(witr.id).category)
        assertEquals(witr.id, phone.repository.getHabits().last().id, "last in Isha")
        assertEquals(mornings, phone.days(tahajjud.id), "other habits untouched")
    }

    @Test
    fun moving_back_is_an_exact_undo() = runTest {
        val phone = device("phone")
        phone.logBeforeFajr()

        phone.repository.moveAcrossMidnight(witr.id)
        phone.repository.moveAcrossMidnight(witr.id)

        assertEquals(mornings, phone.days(witr.id))
        assertEquals(Category.BeforeFajr, phone.habit(witr.id).category)
    }

    @Test
    fun other_windows_are_left_alone() = runTest {
        val phone = device("phone")
        phone.repository.saveHabit(Habit(id = "fajr", label = "Fajr", category = Category.Fajr))
        phone.repository.toggle(mornings[0], "fajr")

        assertFalse(phone.repository.moveAcrossMidnight("fajr"))
        assertEquals(listOf(mornings[0]), phone.days("fajr"))
    }

    @Test
    fun the_other_device_gets_the_move_and_its_undo_through_the_folder() = runTest {
        val phone = device("phone")
        val laptop = device("laptop")
        phone.logBeforeFajr()
        phone.sync()
        laptop.sync()

        phone.repository.moveAcrossMidnight(witr.id)
        phone.sync()
        laptop.sync()
        assertEquals(nights, laptop.days(witr.id))
        assertEquals(Category.Isha, laptop.habit(witr.id).category)

        // Undone from the other device this time.
        laptop.clock = phone.clock
        laptop.repository.moveAcrossMidnight(witr.id)
        laptop.sync()
        phone.sync()
        assertEquals(mornings, phone.days(witr.id))
        assertEquals(Category.BeforeFajr, phone.habit(witr.id).category)
    }
}
