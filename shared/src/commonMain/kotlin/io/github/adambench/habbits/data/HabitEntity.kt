package io.github.adambench.habbits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** [io.github.adambench.habbits.domain.Category] ordinal; also the display order. */
    @ColumnInfo(name = "category")
    val category: Int,

    /** [io.github.adambench.habbits.domain.HabitType] ordinal. */
    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "unit")
    val unit: String? = null,

    @ColumnInfo(name = "default_value")
    val defaultValue: Int? = null,

    @ColumnInfo(name = "step")
    val step: Int,

    /** [io.github.adambench.habbits.domain.FrequencyType] ordinal. */
    @ColumnInfo(name = "frequency_type")
    val frequencyType: Int,

    /** 7-bit weekday mask, bit 0 = Monday. */
    @ColumnInfo(name = "recurring_days")
    val recurringDays: Int,

    @ColumnInfo(name = "interval_days")
    val intervalDays: Int? = null,

    /** Epoch day, not a timestamp. */
    @ColumnInfo(name = "interval_start")
    val intervalStart: Long? = null,

    /** [io.github.adambench.habbits.domain.HabitStatus] ordinal. */
    @ColumnInfo(name = "status")
    val status: Int,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Hybrid logical clock of the write that produced this row. */
    @ColumnInfo(name = "hlc")
    val hlc: String,
)
