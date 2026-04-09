package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.export.ExportExercise
import ru.hopes.workouttimer.domain.model.export.ExportWorkout

fun Workout.toExport(): ExportWorkout {
    return ExportWorkout(
        name = name,
        lastUseAt = lastUseAt,
        exercises = exercises.map { it.toExport() }
    )
}

fun Exercise.toExport(): ExportExercise {
    return ExportExercise(
        name = name,
        weight = weight,
        sets = sets,
        reps = reps,
        restTimeMillis = timeMillis,
        order = order,
        note = note
    )
}

fun ExportWorkout.toDomain(): Workout {
    return Workout(
        id = 0, // ID генерируется при вставке
        name = name,
        lastUseAt = lastUseAt,
        exercises = exercises.map { it.toDomain() }
    )
}

fun ExportExercise.toDomain(): Exercise {
    return Exercise(
        id = 0, // ID генерируется при вставке
        name = name,
        weight = weight,
        sets = sets,
        reps = reps,
        timeMillis = restTimeMillis,
        order = order,
        note = note
    )
}
