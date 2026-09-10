package io.github.adambench.habbits.sync

import io.github.adambench.habbits.data.HabbitsDatabase
import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.domain.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two devices, two databases, two log files, one shared folder — the actual
 * claim the sync design makes.
 */
class SyncConvergenceTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "habbits-sync-${System.nanoTime()}")
    private val syncDir = File(root, "shared").apply { mkdirs() }
    private val date = LocalDate(2026, 9, 10)
    private val epochDay = date.toEpochDays()

    private val devices = mutableListOf<Device>()

    inner class Device(val name: String, startClock: Long) {
        val dir = File(root, name).apply { mkdirs() }
        val db: HabbitsDatabase = createHabbitsDatabase(dir)
        val store = FileSyncStore(syncDir, name)
        val journal = BufferedSyncJournal(store, flushThreshold = 1_000)
        var clock = startClock
        val repository = HabitRepository(
            habitDao = db.habitDao(),
            entryDao = db.entryDao(),
            clock = HlcGenerator(name) { clock++ },
            now = { clock },
            journal = journal,
        )
        val merger = SyncMerger(db, store)

        suspend fun push() = journal.flush()
        suspend fun pull() = merger.merge()

        suspend fun snapshot(): Pair<List<String>, List<String>> {
            val habits = db.habitDao().getAll().sortedBy { it.id }
                .map { "${it.id}|${it.label}|${it.category}|${it.status}|${it.sortOrder}" }
            val entries = db.entryDao().getDay(epochDay).sortedBy { it.habitId }
                .map { "${it.date}|${it.habitId}|${it.value}" }
            return habits to entries
        }
    }

    private fun device(name: String, startClock: Long) =
        Device(name, startClock).also { devices += it }

    @AfterTest
    fun tearDown() {
        devices.forEach { it.db.close() }
        root.deleteRecursively()
    }

    @Test
    fun two_devices_reach_the_same_state() = runTest {
        val phone = device("phone", 1_000)
        val laptop = device("laptop", 1_000)

        // Same habits set up on the phone and shared across.
        phone.repository.saveHabit(Habit(id = "tahajjud", label = "Tahajjud", unit = "Raka'ats", defaultValue = 2, step = 2))
        phone.repository.saveHabit(Habit(id = "adkar", label = "Adkar al-Sabah"))
        phone.push()
        laptop.pull()

        // Each device logs something different, neither having seen the other.
        phone.repository.toggle(date, "tahajjud")
        laptop.repository.toggle(date, "adkar")
        phone.push()
        laptop.push()

        // Exchange.
        phone.pull()
        laptop.pull()

        assertEquals(phone.snapshot(), laptop.snapshot(), "devices diverged")
        assertTrue(phone.repository.isCompleted(date, "adkar"), "the laptop's log reached the phone")
        assertTrue(laptop.repository.isCompleted(date, "tahajjud"), "the phone's log reached the laptop")
    }

    @Test
    fun the_later_write_wins_a_genuine_conflict() = runTest {
        val phone = device("phone", 1_000)
        val laptop = device("laptop", 1_000)

        phone.repository.saveHabit(Habit(id = "quran", label = "Quran", unit = "verses", defaultValue = 100, step = 50))
        phone.push()
        laptop.pull()

        // Both edit the same entry on the same day. The laptop's clock is later.
        phone.clock = 5_000
        phone.repository.toggle(date, "quran")
        phone.repository.incrementValue(date, "quran", 100) // 200 on the phone

        laptop.clock = 9_000
        laptop.repository.toggle(date, "quran")
        laptop.repository.incrementValue(date, "quran", 300) // 400 on the laptop

        phone.push(); laptop.push()
        phone.pull(); laptop.pull()

        assertEquals(400, phone.repository.valueOf(date, "quran"), "the later write should win")
        assertEquals(phone.snapshot(), laptop.snapshot())
    }

    @Test
    fun a_clearing_is_not_resurrected_by_an_offline_device() = runTest {
        val phone = device("phone", 1_000)
        val laptop = device("laptop", 1_000)

        phone.repository.saveHabit(Habit(id = "duha", label = "Duha"))
        phone.repository.toggle(date, "duha")
        phone.push()
        laptop.pull()
        assertTrue(laptop.repository.isCompleted(date, "duha"))

        // The phone un-completes it later; the laptop has an older completion.
        phone.clock = 8_000
        phone.repository.toggle(date, "duha")
        phone.push()
        laptop.pull()

        assertFalse(
            laptop.repository.isCompleted(date, "duha"),
            "without a tombstone the stale completion would come back",
        )
        assertEquals(phone.snapshot(), laptop.snapshot())
    }

    @Test
    fun merging_is_idempotent_and_order_independent() = runTest {
        val phone = device("phone", 1_000)
        val laptop = device("laptop", 2_000)

        phone.repository.saveHabit(Habit(id = "a", label = "A"))
        laptop.repository.saveHabit(Habit(id = "b", label = "B"))
        phone.repository.toggle(date, "a")
        phone.push(); laptop.push()

        laptop.pull()
        laptop.repository.toggle(date, "b")
        laptop.push()

        phone.pull()
        val once = phone.snapshot()
        phone.pull()
        phone.pull()
        assertEquals(once, phone.snapshot(), "re-merging must not change anything")

        laptop.pull()
        assertEquals(phone.snapshot(), laptop.snapshot())
    }

    @Test
    fun a_corrupt_line_is_counted_and_skipped() = runTest {
        val phone = device("phone", 1_000)
        phone.repository.saveHabit(Habit(id = "a", label = "A"))
        phone.push()

        File(syncDir, "device-broken.jsonl").writeText("{ not json\n{\"type\":\"unknown\"}\n")

        val report = phone.pull()
        assertTrue(report.malformed >= 1, "malformed lines should be reported, not silently dropped")
        assertEquals(1, report.habits, "the good data still merged")
    }

    @Test
    fun compaction_shrinks_the_log_without_changing_the_result() = runTest {
        val phone = device("phone", 1_000)
        phone.repository.saveHabit(Habit(id = "a", label = "A", unit = "x", defaultValue = 1, step = 1))
        phone.repository.toggle(date, "a")
        repeat(20) { phone.repository.incrementValue(date, "a", 1) }
        phone.push()

        val before = phone.snapshot()
        val linesBefore = phone.store.readAllLogs().size
        val kept = phone.journal.compactOwnLog()

        assertTrue(kept < linesBefore, "compaction should drop superseded lines: $kept vs $linesBefore")

        // A fresh device merging only the compacted log must land in the same place.
        val fresh = device("fresh", 50_000)
        fresh.pull()
        assertEquals(before.second, fresh.snapshot().second, "entries survived compaction")
    }
}
