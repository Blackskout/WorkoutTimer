package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WidgetWorkout
import ru.hopes.workouttimer.domain.model.exerciseUnitOf

fun WorkoutWithExercises.toDomain(): Workout {
    val exercisesDomain = exercises
        .sortedBy { it.exercise.orderInWorkout }
        .map { (e, catalog) ->
            Exercise(
                id = e.id,
                name = e.name,
                sets = e.sets,
                reps = e.reps,
                timeMillis = e.restTimeMillis,
                order = e.orderInWorkout,
                weight = e.weight,
                note = e.note,
                catalogId = e.catalogId,
                unit = exerciseUnitOf(catalog?.unit),
                extraWeight = e.extraWeight
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
        lastUseAt = workout.lastUseAt,
        lastDurationMillis = lastDurationMillis
    )
}

