package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.ExerciseWithCatalog
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

class ObserveWorkoutUseCaseTest {

    private fun workout(id: Int, name: String, vararg exercises: Pair<String, Int>) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = 0L),
        exercises = exercises.mapIndexed { index, (exerciseName, order) ->
            ExerciseWithCatalog(
                exercise = ExerciseEntity(
                    id = id * 10 + index, workoutId = id.toLong(), name = exerciseName, weight = 60.0,
                    sets = 4, reps = 8, restTimeMillis = 120_000L, orderInWorkout = order, catalogId = 1L
                ),
                catalog = null
            )
        }
    )

    @Test
    fun `тренировка по id, упражнения по порядку, и новый состав после правки`() = runTest {
        val all = MutableStateFlow(listOf(workout(1, "Ноги", "Присед" to 2, "Выпады" to 1), workout(2, "Спина")))
        val repo = mockk<WorkoutRepository>()
        every { repo.getAllWorkoutsWithExercise() } returns all
        val observe = ObserveWorkoutUseCase(repo)

        val first = observe(1).first()
        assertEquals("Ноги", first?.name)
        assertEquals(listOf("Выпады", "Присед"), first?.exercises?.map { it.name })

        all.value = listOf(workout(1, "Ноги", "Жим ногами" to 1))
        assertEquals(listOf("Жим ногами"), observe(1).first()?.exercises?.map { it.name })
    }

    @Test
    fun `удалённой тренировки нет`() = runTest {
        val repo = mockk<WorkoutRepository>()
        every { repo.getAllWorkoutsWithExercise() } returns MutableStateFlow(listOf(workout(2, "Спина")))

        assertNull(ObserveWorkoutUseCase(repo)(1).first())
    }
}
