package ru.hopes.workouttimer.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.dao.ExerciseWithCatalog
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit

class WorkoutMapperTest {

    private fun entity(order: Int, extra: Double = 0.0) = ExerciseEntity(
        id = order, workoutId = 1L, name = "Тяга блока", weight = 5.0, sets = 3, reps = 12,
        restTimeMillis = 60_000L, orderInWorkout = order, catalogId = 4L, extraWeight = extra
    )

    private fun catalog(unit: String) =
        ExerciseCatalogEntity(id = 4L, name = "Тяга блока", nameKey = "тяга блока", unit = unit)

    private fun workout(vararg exercises: ExerciseWithCatalog) = WorkoutWithExercises(
        workout = WorkoutEntity(id = 1, name = "Спина", lastUseAt = 0L),
        exercises = exercises.toList()
    )

    @Test
    fun `единица и добавка берутся из справочника и шаблона`() {
        val exercise = workout(ExerciseWithCatalog(entity(1, extra = 2.5), catalog("PLATE")))
            .toDomain().exercises.single()
        assertEquals(ExerciseUnit.PLATE, exercise.unit)
        assertEquals(2.5, exercise.extraWeight, 0.0)
        assertEquals(4L, exercise.catalogId)
    }

    @Test
    fun `без записи справочника упражнение читается в кг`() {
        val exercise = workout(ExerciseWithCatalog(entity(1), catalog = null)).toDomain().exercises.single()
        assertEquals(ExerciseUnit.KG, exercise.unit)
    }

    @Test
    fun `упражнения идут в порядке orderInWorkout`() {
        val exercises = workout(
            ExerciseWithCatalog(entity(2), catalog("KG")),
            ExerciseWithCatalog(entity(1), catalog("KG"))
        ).toDomain().exercises
        assertEquals(listOf(1, 2), exercises.map { it.order })
    }
}
