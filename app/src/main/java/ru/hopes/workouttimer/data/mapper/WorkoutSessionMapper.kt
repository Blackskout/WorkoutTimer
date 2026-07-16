package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.WorkoutSession

fun WorkoutSessionEntity.toDomain(): WorkoutSession {
    return WorkoutSession(
        id = id,
        workoutId = workoutId.toInt(),
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMillis = durationMillis
    )
}
