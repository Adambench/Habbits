package dev.adambench.habbits.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [HabitEntity::class, EntryEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(HabbitsDatabaseConstructor::class)
abstract class HabbitsDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun entryDao(): EntryDao

    companion object {
        const val FILE_NAME = "habbits.db"
    }
}

/** Generated per platform by Room's KMP compiler. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object HabbitsDatabaseConstructor : RoomDatabaseConstructor<HabbitsDatabase> {
    override fun initialize(): HabbitsDatabase
}
