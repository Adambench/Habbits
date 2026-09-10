package dev.adambench.habbits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReorderTest {

    private fun habit(id: String, category: Category, order: Int) =
        Habit(id = id, label = id, category = category, sortOrder = order)

    /** Two in Before Fajr, one in Fajr — the shape the real vault has. */
    private val habits = listOf(
        habit("tahajjud", Category.BeforeFajr, 0),
        habit("chafrWaWitr", Category.BeforeFajr, 1),
        habit("twoRBF", Category.Fajr, 2),
    )

    private fun ids(list: List<Habit>) = list.map { it.id }

    @Test
    fun moving_up_swaps_with_the_neighbour_above() {
        val moved = habits.movedBy("chafrWaWitr", -1)
        assertEquals(listOf("chafrWaWitr", "tahajjud", "twoRBF"), ids(moved))
        assertEquals(Category.BeforeFajr, moved.first { it.id == "chafrWaWitr" }.category)
    }

    @Test
    fun moving_down_swaps_with_the_neighbour_below() {
        val moved = habits.movedBy("tahajjud", 1)
        assertEquals(listOf("chafrWaWitr", "tahajjud", "twoRBF"), ids(moved))
    }

    @Test
    fun moving_off_the_bottom_of_a_category_enters_the_next_one() {
        val moved = habits.movedBy("chafrWaWitr", 1)
        val subject = moved.first { it.id == "chafrWaWitr" }
        assertEquals(Category.Fajr, subject.category, "carried into the next category")
        assertEquals(listOf("tahajjud", "chafrWaWitr", "twoRBF"), ids(moved))
    }

    @Test
    fun moving_off_the_top_of_a_category_enters_the_end_of_the_previous_one() {
        val moved = habits.movedBy("twoRBF", -1)
        val subject = moved.first { it.id == "twoRBF" }
        assertEquals(Category.BeforeFajr, subject.category)
        assertEquals(listOf("tahajjud", "chafrWaWitr", "twoRBF"), ids(moved))
    }

    @Test
    fun sort_order_is_renumbered_without_gaps() {
        val moved = habits.movedBy("tahajjud", 1)
        assertEquals(listOf(0, 1, 2), moved.map { it.sortOrder })
    }

    @Test
    fun moving_past_the_start_of_the_day_does_nothing() {
        val first = listOf(habit("only", Category.Anytime, 0))
        assertSame(first, first.movedBy("only", -1))
    }

    @Test
    fun moving_past_the_end_of_the_day_does_nothing() {
        val last = listOf(habit("only", Category.Isha, 0))
        assertSame(last, last.movedBy("only", 1))
    }

    @Test
    fun an_unknown_id_or_direction_is_a_no_op() {
        assertSame(habits, habits.movedBy("nope", 1))
        assertSame(habits, habits.movedBy("tahajjud", 0))
    }

    @Test
    fun crossing_an_empty_category_lands_in_it_rather_than_skipping() {
        // Anytime -> Before Fajr, where Before Fajr holds nothing.
        val list = listOf(habit("a", Category.Anytime, 0), habit("b", Category.Fajr, 1))
        val moved = list.movedBy("a", 1)
        assertEquals(Category.BeforeFajr, moved.first { it.id == "a" }.category)
    }
}

class ReorderToTest {

    private fun habit(id: String, category: Category, order: Int) =
        Habit(id = id, label = id, category = category, sortOrder = order)

    private val habits = listOf(
        habit("a", Category.BeforeFajr, 0),
        habit("b", Category.BeforeFajr, 1),
        habit("c", Category.BeforeFajr, 2),
        habit("d", Category.Fajr, 3),
    )

    private fun ids(list: List<Habit>) = list.map { it.id }

    @Test
    fun dragging_downwards_lands_after_the_target() {
        assertEquals(listOf("b", "c", "a", "d"), ids(habits.movedTo("a", "c")))
    }

    @Test
    fun dragging_upwards_lands_before_the_target() {
        assertEquals(listOf("c", "a", "b", "d"), ids(habits.movedTo("c", "a")))
    }

    @Test
    fun dragging_into_another_category_reassigns_it() {
        val moved = habits.movedTo("a", "d")
        assertEquals(Category.Fajr, moved.first { it.id == "a" }.category)
        assertEquals(listOf("b", "c", "d", "a"), ids(moved))
    }

    @Test
    fun sort_order_stays_dense() {
        assertEquals(listOf(0, 1, 2, 3), habits.movedTo("a", "c").map { it.sortOrder })
    }

    @Test
    fun dropping_a_habit_on_itself_changes_nothing() {
        assertSame(habits, habits.movedTo("a", "a"))
    }

    @Test
    fun an_unknown_id_is_a_no_op() {
        assertSame(habits, habits.movedTo("a", "zzz"))
        assertSame(habits, habits.movedTo("zzz", "a"))
    }
}
