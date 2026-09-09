package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

class GetWidgetWorkoutsUseCaseTest {

    private fun workoutWith(
        id: Int,
        name: String,
        lastUseAt: Long,
        exerciseCount: Int = 1
    ) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = (1..exerciseCount).map { index ->
            ExerciseEntity(
                id = index,
                workoutId = id.toLong(),
                name = "Упражнение $index",
                weight = 10.0,
                sets = 3,
                reps = 12,
                restTimeMillis = 120_000L,
                orderInWorkout = index,
                note = ""
            )
        }
    )

    private fun useCase(
        workouts: List<WorkoutWithExercises> = emptyList(),
        durations: Map<Int, Long> = emptyMap()
    ): GetWidgetWorkoutsUseCase {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flowOf(workouts)
        every { getDurations() } returns flowOf(durations)
        return GetWidgetWorkoutsUseCase(getAll, getDurations)
    }

    @Test
    fun `sorts workouts by lastUseAt ascending`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 1, name = "Старая", lastUseAt = 100L),
                workoutWith(id = 2, name = "Свежая", lastUseAt = 300L),
                workoutWith(id = 3, name = "Средняя", lastUseAt = 200L)
            ),
            durations = mapOf(1 to 1_000L, 2 to 1_000L, 3 to 1_000L)
        )().first()

        assertEquals(listOf("Старая", "Средняя", "Свежая"), result.map { it.name })
    }

    @Test
    fun `puts workouts without a single session above the rest`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 1, name = "Заброшенная", lastUseAt = 100L),
                workoutWith(id = 2, name = "Новая", lastUseAt = 300L),
                workoutWith(id = 3, name = "Вчерашняя", lastUseAt = 200L)
            ),
            // «Новой» нет в истории сессий, хотя её lastUseAt самый свежий: при создании
            // тренировки туда пишется now.
            durations = mapOf(1 to 1_000L, 3 to 1_000L)
        )().first()

        assertEquals(listOf("Новая", "Заброшенная", "Вчерашняя"), result.map { it.name })
    }

    @Test
    fun `puts the last session duration on the matching workout`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 7, name = "Ноги", lastUseAt = 100L),
                workoutWith(id = 9, name = "Спина", lastUseAt = 50L)
            ),
            durations = mapOf(7 to 3_120_000L)
        )().first()

        // «Спина» без сессии идёт первой, «Ноги» с длительностью — второй
        assertEquals("Спина", result[0].name)
        assertNull(result[0].lastDurationMillis)
        assertEquals(3_120_000L, result[1].lastDurationMillis)
    }

    @Test
    fun `counts exercises of each workout`() = runTest {
        val result = useCase(
            workouts = listOf(workoutWith(id = 1, name = "Ноги", lastUseAt = 1L, exerciseCount = 7))
        )().first()

        assertEquals(7, result[0].exerciseCount)
    }

    @Test
    fun `returns an empty list when there are no workouts`() = runTest {
        assertEquals(emptyList<Any>(), useCase()().first())
    }

    @Test
    fun `returns an empty list instead of failing when the source flow throws`() = runTest {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flow { throw IllegalStateException("db is gone") }
        every { getDurations() } returns flowOf(emptyMap())

        val result = GetWidgetWorkoutsUseCase(getAll, getDurations)().first()

        assertEquals(emptyList<Any>(), result)
    }
}
