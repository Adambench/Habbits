package io.github.adambench.habbits.sync

/**
 * What an import actually wrote, for the reconciliation gate.
 *
 * [skipped] counts rows the importer refused rather than silently dropped —
 * unparseable dates, or entries naming a habit that does not exist.
 */
data class ImportReport(
    val habits: Int,
    val entries: Int,
    val days: Int,
    val archived: Int,
    val skipped: List<String> = emptyList(),
) {
    val hasProblems: Boolean get() = skipped.isNotEmpty()

    /** Whether the database matches what the file claimed it contained. */
    fun matches(stats: ExportStats?): Boolean =
        stats == null || (stats.habits == habits && stats.entries == entries && stats.days == days)
}
