package dev.adambench.habbits.domain

/** Habit categories by kind, which drive the card colour. */
enum class HabitType(val storageId: String) {
    Dua("dua"),
    Adkar("adkar"),
    Prayer("prayer"),
    Reading("reading"),
    Other("other"),
    ;

    companion object {
        private val byStorageId = entries.associateBy { it.storageId }

        fun fromStorageId(id: String?): HabitType = byStorageId[id] ?: Other
    }
}
