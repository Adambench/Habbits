package dev.adambench.habbits.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The interchange format shared by the vault importer and the app's own backup
 * export, so importing a vault and restoring a backup are one code path.
 */
@Serializable
data class HabbitsExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String? = null,
    val source: ExportSource? = null,
    val stats: ExportStats? = null,
    val habits: List<ExportHabit> = emptyList(),
    val entries: List<ExportEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "habbits-export"
        const val VERSION = 1
    }
}

@Serializable
data class ExportSource(
    val kind: String? = null,
    val vault: String? = null,
    @SerialName("trackerNote") val trackerNote: String? = null,
    @SerialName("dailyNotesPath") val dailyNotesPath: String? = null,
)

@Serializable
data class ExportStats(
    val habits: Int = 0,
    val entries: Int = 0,
    val days: Int = 0,
)

@Serializable
data class ExportHabit(
    val id: String,
    val label: String,
    val description: String? = null,
    val category: String? = null,
    val type: String? = null,
    val unit: String? = null,
    val defaultValue: Int? = null,
    val step: Int? = null,
    val frequencyType: String? = null,
    val recurringDays: List<Int>? = null,
    val intervalDays: Int? = null,
    val intervalStart: String? = null,
    val status: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class ExportEntry(
    /** ISO `yyyy-MM-dd`. */
    val date: String,
    val habitId: String,
    /** Null means a plain check; a number means a measured amount. */
    val value: Int? = null,
)
