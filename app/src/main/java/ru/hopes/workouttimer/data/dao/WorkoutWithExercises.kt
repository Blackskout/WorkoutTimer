package ru.hopes.workouttimer.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId",
        entity = ExerciseEntity::class
    )
    val exercises: List<ExerciseWithCatalog>
)

/**
 * Упражнение вместе с записью справочника: единица живёт только там. catalog —
 * null, если инвариант catalogId > 0 когда-нибудь нарушится; тогда упражнение
 * читается в кг, а не роняет экран.
 */
data class ExerciseWithCatalog(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "catalogId", entityColumn = "id")
    val catalog: ExerciseCatalogEntity?
)
