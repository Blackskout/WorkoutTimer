package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises", indices = [Index("catalogId")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val orderInWorkout: Int,
    val note: String = "",
    // Без умолчания в Kotlin намеренно: каждый путь вставки обязан проставить
    // запись справочника. DEFAULT 0 в базе нужен только для ALTER TABLE.
    @ColumnInfo(defaultValue = "0") val catalogId: Long,
    @ColumnInfo(defaultValue = "0") val extraWeight: Double = 0.0
)
