package io.github.adambench.habbits.domain

import kotlin.jvm.JvmInline

/**
 * A set of weekdays held as a 7-bit mask, bit 0 = Monday.
 *
 * ISO numbering (Monday = 1 … Sunday = 7) matches both `kotlinx.datetime`'s
 * `isoDayNumber` and the Luxon weekday numbers the vault stored, so imported
 * schedules need no remapping.
 */
@JvmInline
value class Weekdays(val mask: Int) {

    operator fun contains(isoDayNumber: Int): Boolean =
        isoDayNumber in 1..7 && (mask shr (isoDayNumber - 1)) and 1 == 1

    fun plus(isoDayNumber: Int): Weekdays =
        if (isoDayNumber in 1..7) Weekdays(mask or (1 shl (isoDayNumber - 1))) else this

    fun minus(isoDayNumber: Int): Weekdays =
        if (isoDayNumber in 1..7) Weekdays(mask and (1 shl (isoDayNumber - 1)).inv()) else this

    fun toIsoDayNumbers(): List<Int> = (1..7).filter { it in this }

    val isEmpty: Boolean get() = mask and ALL_MASK == 0

    companion object {
        private const val ALL_MASK = 0b111_1111

        val None = Weekdays(0)
        val All = Weekdays(ALL_MASK)

        fun of(isoDayNumbers: Iterable<Int>): Weekdays =
            Weekdays(isoDayNumbers.fold(0) { acc, d ->
                if (d in 1..7) acc or (1 shl (d - 1)) else acc
            })
    }
}
