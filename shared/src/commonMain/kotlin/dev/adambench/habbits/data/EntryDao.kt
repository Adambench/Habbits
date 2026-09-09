package dev.adambench.habbits.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    /** The whole day in one indexed lookup on the primary key. */
    @Query("SELECT * FROM entries WHERE date = :date")
    fun observeDay(date: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date = :date")
    suspend fun getDay(date: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: Long, to: Long): Flow<List<EntryEntity>>

    /** Backs streaks and the M8 dashboard; served by the (habit_id, date) index. */
    @Query("SELECT * FROM entries WHERE habit_id = :habitId ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentForHabit(habitId: String, limit: Int): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE date = :date AND habit_id = :habitId")
    suspend fun get(date: Long, habitId: String): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    @Query("SELECT COUNT(DISTINCT date) FROM entries")
    suspend fun distinctDayCount(): Int

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    /** Un-completing removes the row, exactly as the legacy tracker did. */
    @Query("DELETE FROM entries WHERE date = :date AND habit_id = :habitId")
    suspend fun delete(date: Long, habitId: String)
}
