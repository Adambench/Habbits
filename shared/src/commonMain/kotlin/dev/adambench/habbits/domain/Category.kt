package dev.adambench.habbits.domain

/**
 * The eight slots the day is divided into, in the order they occur.
 *
 * Ordinal order is display order, and [storageId] is the string the Obsidian
 * vault used, so imported habits keep their placement.
 */
enum class Category(val storageId: String, val displayName: String) {
    Anytime("Anytime", "Anytime"),
    BeforeFajr("Before Fajr", "Before Fajr"),
    Fajr("Fajr", "Fajr"),
    Shuruq("Shuruq", "Shuruq"),
    Dhuhr("Dhuhr", "Dhuhr"),
    Asr("Asr", "Asr"),
    Maghrib("Maghrib", "Maghrib"),
    Isha("Isha", "Isha"),
    ;

    companion object {
        private val byStorageId = entries.associateBy { it.storageId }

        /** Unknown values fall back to [Anytime] rather than dropping the habit. */
        fun fromStorageId(id: String?): Category = byStorageId[id] ?: Anytime
    }
}
