package io.github.adambench.habbits.sync

/**
 * A hybrid logical clock: wall-clock millis, a counter, and the device id.
 *
 * Ordering by raw wall-clock time is not safe across devices — a phone whose
 * clock runs a few minutes fast would win every conflict permanently. The
 * counter breaks ties within the same millisecond, and the device id makes the
 * order total so that every device merges to the same result.
 *
 * The wire form is fixed-width and lexicographically ordered, so logs can be
 * sorted as plain strings: `000001757394751442-000003-3f9a`.
 */
data class Hlc(
    val millis: Long,
    val counter: Int,
    val nodeId: String,
) : Comparable<Hlc> {

    override fun compareTo(other: Hlc): Int {
        millis.compareTo(other.millis).let { if (it != 0) return it }
        counter.compareTo(other.counter).let { if (it != 0) return it }
        return nodeId.compareTo(other.nodeId)
    }

    fun encode(): String = buildString {
        append(millis.toString().padStart(MILLIS_WIDTH, '0'))
        append('-')
        append(counter.toString().padStart(COUNTER_WIDTH, '0'))
        append('-')
        append(nodeId)
    }

    override fun toString(): String = encode()

    companion object {
        private const val MILLIS_WIDTH = 18
        private const val COUNTER_WIDTH = 6

        fun decode(encoded: String): Hlc? {
            val first = encoded.indexOf('-')
            if (first < 0) return null
            val second = encoded.indexOf('-', first + 1)
            if (second < 0) return null
            val millis = encoded.substring(0, first).toLongOrNull() ?: return null
            val counter = encoded.substring(first + 1, second).toIntOrNull() ?: return null
            val nodeId = encoded.substring(second + 1)
            if (nodeId.isEmpty()) return null
            return Hlc(millis, counter, nodeId)
        }
    }
}

/**
 * Issues monotonically increasing timestamps for one device.
 *
 * Not thread-safe on its own; the repository serialises writes. Merge logic
 * that consumes remote clocks arrives with M6.5.
 */
class HlcGenerator(
    private val nodeId: String,
    private val now: () -> Long,
) {
    private var lastMillis = 0L
    private var counter = 0

    fun next(): Hlc {
        val wall = now()
        if (wall > lastMillis) {
            lastMillis = wall
            counter = 0
        } else {
            // Clock stalled or stepped backwards: keep issuing increasing
            // stamps rather than emitting a duplicate or going back in time.
            counter++
        }
        return Hlc(lastMillis, counter, nodeId)
    }
}
