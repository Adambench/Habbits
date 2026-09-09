package dev.adambench.habbits.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlcTest {

    @Test
    fun encoding_round_trips() {
        val hlc = Hlc(millis = 1_757_394_751_442, counter = 3, nodeId = "3f9a")
        assertEquals(hlc, Hlc.decode(hlc.encode()))
    }

    @Test
    fun encoded_form_sorts_the_same_as_the_value() {
        val a = Hlc(1_000, 0, "aaaa")
        val b = Hlc(1_000, 1, "aaaa")
        val c = Hlc(2_000, 0, "aaaa")

        // Fixed-width padding is what lets logs be merged by plain string sort.
        assertTrue(a.encode() < b.encode())
        assertTrue(b.encode() < c.encode())
        assertTrue(a < b && b < c)
    }

    @Test
    fun device_id_breaks_ties_so_ordering_is_total() {
        val phone = Hlc(1_000, 0, "aaaa")
        val laptop = Hlc(1_000, 0, "bbbb")
        assertTrue(phone < laptop)
        assertTrue(phone.encode() < laptop.encode())
    }

    @Test
    fun malformed_input_decodes_to_null() {
        assertNull(Hlc.decode(""))
        assertNull(Hlc.decode("nonsense"))
        assertNull(Hlc.decode("123-456"))
        assertNull(Hlc.decode("abc-1-node"))
        assertNull(Hlc.decode("1-2-"))
        assertNotNull(Hlc.decode(Hlc(1, 2, "n").encode()))
    }

    @Test
    fun generator_advances_with_the_wall_clock() {
        var now = 1_000L
        val gen = HlcGenerator("node") { now }
        val first = gen.next()
        now = 2_000L
        val second = gen.next()
        assertTrue(first < second)
        assertEquals(0, second.counter)
    }

    @Test
    fun generator_stays_monotonic_when_the_clock_stalls() {
        val gen = HlcGenerator("node") { 5_000L }
        val stamps = List(4) { gen.next() }
        assertEquals(stamps.sorted(), stamps)
        assertEquals(stamps.distinct().size, stamps.size)
    }

    @Test
    fun generator_stays_monotonic_when_the_clock_steps_backwards() {
        // NTP correction or a manual clock change must not let a later write
        // sort before an earlier one.
        var now = 10_000L
        val gen = HlcGenerator("node") { now }
        val before = gen.next()
        now = 1_000L
        val after = gen.next()
        assertTrue(after > before, "$after should sort after $before")
    }
}
