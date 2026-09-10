package io.github.adambench.habbits.domain

enum class FrequencyType(val storageId: String) {
    /** Every day. */
    Daily("daily"),

    /** Only on selected weekdays. */
    Weekly("weekly"),

    /** Every N days counting from a start date. */
    Interval("interval"),
    ;

    companion object {
        private val byStorageId = entries.associateBy { it.storageId }

        fun fromStorageId(id: String?): FrequencyType = byStorageId[id] ?: Daily
    }
}
