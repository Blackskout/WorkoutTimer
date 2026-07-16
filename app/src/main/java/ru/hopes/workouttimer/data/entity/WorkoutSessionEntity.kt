package ru.hopes.workouttimer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
