package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WidgetWorkout

fun WorkoutWithExercises.toDomain(): Workout {
    val exercisesDomain = exercises
        .sortedBy { it.orderInWorkout }
        .map { e ->
            Exercise(
                id = e.id,
                name = e.name,
                sets = e.sets,
                reps = e.reps,
                timeMillis = e.restTimeMillis,
                order = e.orderInWorkout,
                weight = e.weight,
                note = e.note
            )
        }
    return Workout(
        id = workout.id,
        name = workout.name,
        lastUseAt = workout.lastUseAt,
        exercises = exercisesDomain
    )
}

fun WorkoutWithExercises.toWidgetWorkout(lastDurationMillis: Long?): WidgetWorkout {
    return WidgetWorkout(
        id = workout.id,
        name = workout.name,
        exerciseCount = exercises.size,
        lastDurationMillis = lastDurationMillis
    )
}

