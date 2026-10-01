package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.dao.LoggedSetRow
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.exerciseUnitOf

fun ExerciseCatalogEntity.toDomain(): CatalogExercise =
    CatalogExercise(id = id, name = name, unit = exerciseUnitOf(unit))

fun LoggedSetRow.toDomain(): LoggedSet = LoggedSet(
    set = SessionSet(
        id = id,
        sessionId = sessionId,
        catalogId = catalogId,
        weight = weight,
        extraWeight = extraWeight,
        reps = reps,
        unit = exerciseUnitOf(unit)
    ),
    exerciseName = exerciseName,
    finishedAt = finishedAt
)
