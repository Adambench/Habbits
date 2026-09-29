package io.github.adambench.habbits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    /** Category then sort order: the day's running order, top to bottom. */
    @Query("SELECT * FROM habits ORDER BY category ASC, sort_order ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE status != :archived ORDER BY category ASC, sort_order ASC")
    fun observeNotArchived(archived: Int): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY category ASC, sort_order ASC")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Upsert
    suspend fun upsertAll(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Rows whose status history has never been worked out. */
    @Query("SELECT * FROM habits WHERE status_history IS NULL")
    suspend fun getWithoutStatusHistory(): List<HabitEntity>

    /**
     * Fills in a derived history without stamping a new clock: it is worked out
     * from data every device already has, so it is not a change to sync.
     */
    @Query("UPDATE habits SET status_history = :history WHERE id = :id")
    suspend fun setStatusHistory(id: String, history: String)

    @Query("UPDATE habits SET sort_order = :sortOrder, category = :category, hlc = :hlc WHERE id = :id")
    suspend fun updatePlacement(id: String, category: Int, sortOrder: Int, hlc: String)
}
