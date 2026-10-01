package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Фактический подход сессии. Порядок внутри сессии — порядок id: номера
 * подхода нет, между упражнениями можно прыгать и возвращаться.
 * RESTRICT на справочник не даёт автоочистке удалить запись с историей.
 */
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("catalogId")]
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val catalogId: Long,
    val weight: Double,
    @ColumnInfo(defaultValue = "0") val extraWeight: Double = 0.0,
    val reps: Int,
    val unit: String
)
