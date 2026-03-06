package ru.hopes.workouttimer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val orderInWorkout: Int
)