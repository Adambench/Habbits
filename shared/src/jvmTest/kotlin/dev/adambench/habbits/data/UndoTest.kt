package dev.adambench.habbits.data

import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Undo has to put an entry back exactly as it was, including its value. */
class UndoTest {

    private val date = LocalDate(2026, 9, 10)
    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-undo-${System.nanoTime()}")
    private val db = createHabbitsDatabase(dir.apply { mkdirs() })
    private var clock = 1_700_000_000_000L
    private val repository = HabitRepository(
        habitDao = db.habitDao(),
        entryDao = db.entryDao(),
        clock = HlcGenerator("test") { clock++ },
        now = { clock },
    )

    @AfterTest
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun seed() {
        repository.saveHabit(Habit(id = "tahajjud", label = "Tahajjud", unit = "Raka'ats", defaultValue = 2, step = 2))
        repository.saveHabit(Habit(id = "adkar", label = "Adkar"))
    }

    @Test
    fun undoing_a_completion_removes_the_row_again() = runTest {
        seed()
        repository.toggle(date, "adkar")
        assertTrue(repository.isCompleted(date, "adkar"))

        repository.restore(date, "adkar", wasCompleted = false, value = null)
        assertFalse(repository.isCompleted(date, "adkar"), "undo must delete, not write a zero")
    }

    @Test
    fun undoing_an_un_completion_restores_the_measured_value() = runTest {
        seed()
        repository.toggle(date, "tahajjud")
        repository.incrementValue(date, "tahajjud", 6) // now 8
        val before = repository.valueOf(date, "tahajjud")
        assertEquals(8, before)

        repository.toggle(date, "tahajjud") // un-completes, deleting the row
        assertFalse(repository.isCompleted(date, "tahajjud"))

        repository.restore(date, "tahajjud", wasCompleted = true, value = before)
        assertTrue(repository.isCompleted(date, "tahajjud"))
        assertEquals(8, repository.valueOf(date, "tahajjud"), "the amount comes back, not the default")
    }

    @Test
    fun a_plain_check_restores_without_inventing_a_number() = runTest {
        seed()
        repository.toggle(date, "adkar")
        assertEquals(null, repository.valueOf(date, "adkar"))

        repository.toggle(date, "adkar")
        repository.restore(date, "adkar", wasCompleted = true, value = null)
        assertTrue(repository.isCompleted(date, "adkar"))
        assertEquals(null, repository.valueOf(date, "adkar"))
    }
}
