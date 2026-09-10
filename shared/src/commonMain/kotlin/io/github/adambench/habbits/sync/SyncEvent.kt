package io.github.adambench.habbits.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One change, as written to a device's log.
 *
 * Every event carries the hybrid logical clock of the write that produced it,
 * which is what lets logs from different devices merge to the same result
 * regardless of the order they arrive in.
 */
@Serializable
sealed interface SyncEvent {
    val hlc: String

    /** What this event is about. Later events on the same key win. */
    val key: String
}

@Serializable
@SerialName("entry")
data class EntrySet(
    val habit: String,
    val date: Long,
    val value: Int? = null,
    override val hlc: String,
) : SyncEvent {
    override val key: String get() = "e:$date:$habit"
}

/**
 * A tombstone. Absence cannot be represented by silence, or a device that was
 * offline when the entry was cleared would resurrect it on the next merge.
 */
@Serializable
@SerialName("entryCleared")
data class EntryCleared(
    val habit: String,
    val date: Long,
    override val hlc: String,
) : SyncEvent {
    override val key: String get() = "e:$date:$habit"
}

@Serializable
@SerialName("habit")
data class HabitSaved(
    val habit: ExportHabit,
    override val hlc: String,
) : SyncEvent {
    override val key: String get() = "h:${habit.id}"
}

@Serializable
@SerialName("habitDeleted")
data class HabitDeleted(
    val id: String,
    override val hlc: String,
) : SyncEvent {
    override val key: String get() = "h:$id"
}
