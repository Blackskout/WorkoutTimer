package ru.hopes.workouttimer.domain.model

data class WorkoutSession(
    val id: Int = 0,
    val workoutId: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
