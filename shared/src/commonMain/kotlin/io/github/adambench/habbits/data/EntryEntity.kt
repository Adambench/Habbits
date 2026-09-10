package io.github.adambench.habbits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One logged completion.
 *
 * The presence of the row *is* the completion, matching the legacy semantics
 * where un-completing deleted the key outright. [value] is null for a plain
 * check and holds the number for a measured habit — the faithful mapping of the
 * vault's `true` versus integer values.
 */
@Entity(
    tableName = "entries",
    primaryKeys = ["date", "habit_id"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habit_id", "date"])],
)
data class EntryEntity(
    /** Epoch day from a local date, so it is immune to timezone and DST drift. */
    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "habit_id")
    val habitId: String,

    @ColumnInfo(name = "value")
    val value: Int? = null,

    @ColumnInfo(name = "logged_at")
    val loggedAt: Long,

    @ColumnInfo(name = "hlc")
    val hlc: String,
)
