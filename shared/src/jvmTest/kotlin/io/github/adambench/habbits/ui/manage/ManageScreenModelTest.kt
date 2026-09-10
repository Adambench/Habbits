package io.github.adambench.habbits.ui.manage

import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManageScreenModelTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-manage-${System.nanoTime()}")
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
        repository.saveHabit(Habit(id = "a", label = "Tahajjud", category = Category.BeforeFajr, sortOrder = 0))
        repository.saveHabit(Habit(id = "b", label = "Chafr and Witr", category = Category.BeforeFajr, sortOrder = 1))
        repository.saveHabit(Habit(id = "c", label = "Subh Congregation", category = Category.Fajr, sortOrder = 2))
        repository.saveHabit(
            Habit(id = "old", label = "Dua Yunus Asr", category = Category.Anytime, sortOrder = 3, status = HabitStatus.Archived),
        )
    }

    private fun model(scope: kotlinx.coroutines.CoroutineScope) =
        ManageScreenModel(repository, scope) { "habit_new" }

    @Test
    fun archived_habits_are_hidden_until_asked_for() = runTest {
        seed()
        val m = model(backgroundScope)
        val hidden = m.state.first { !it.isLoading }
        assertEquals(3, hidden.total)
        assertEquals(1, hidden.archivedCount, "the count is offered even while hidden")

        m.toggleShowArchived()
        val shown = m.state.first { it.showArchived }
        assertEquals(4, shown.total)
        assertTrue(shown.sections.flatMap { it.habits }.any { it.id == "old" })
    }

    @Test
    fun moving_a_habit_persists_the_new_order() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        m.move("b", -1)
        val after = m.state.first { s ->
            s.sections.firstOrNull()?.habits?.firstOrNull()?.id == "b" ||
                s.sections.any { it.habits.map { h -> h.id } == listOf("b", "a") }
        }
        val beforeFajr = after.sections.first { it.category == Category.BeforeFajr }
        assertEquals(listOf("b", "a"), beforeFajr.habits.map { it.id })
    }

    @Test
    fun moving_off_a_category_edge_changes_the_category() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        m.move("c", -1) // Fajr -> Before Fajr
        val after = m.state.first { s ->
            s.sections.none { it.category == Category.Fajr }
        }
        val beforeFajr = after.sections.first { it.category == Category.BeforeFajr }
        assertEquals(listOf("a", "b", "c"), beforeFajr.habits.map { it.id })
    }

    @Test
    fun sleeping_a_habit_keeps_it_in_the_list() = runTest {
        seed()
        val m = model(backgroundScope)
        val state = m.state.first { !it.isLoading }
        val habit = state.sections.flatMap { it.habits }.first { it.id == "a" }

        m.toggleSleep(habit)
        val after = m.state.first { s ->
            s.sections.flatMap { it.habits }.first { it.id == "a" }.status == HabitStatus.Sleeping
        }
        assertEquals(3, after.total, "sleeping hides it from the day view, not from here")
    }

    @Test
    fun a_new_habit_is_not_saved_without_a_label() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        m.createNew()
        val editing = m.state.first { it.editing != null }
        assertTrue(editing.isNew)

        m.save() // blank label
        assertEquals(3, m.state.first { !it.isLoading }.total, "nothing was written")

        m.updateDraft(editing.editing!!.copy(label = "Duha"))
        m.save()
        val after = m.state.first { it.total == 4 }
        assertTrue(after.sections.flatMap { it.habits }.any { it.label == "Duha" })
        assertNull(after.editing, "the sheet closes after a successful save")
    }

    @Test
    fun deleting_a_habit_removes_it() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        m.delete("a")
        val after = m.state.first { it.total == 2 }
        assertFalse(after.sections.flatMap { it.habits }.any { it.id == "a" })
    }
}
