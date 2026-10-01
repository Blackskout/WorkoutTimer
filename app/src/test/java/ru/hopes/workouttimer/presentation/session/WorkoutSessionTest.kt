package ru.hopes.workouttimer.presentation.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

class WorkoutSessionTest {

    private val push = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, order = 1)

    private fun present(phase: WorkoutExecutionState, exercises: List<SessionExercise> = listOf(SessionExercise(push))) =
        WorkoutSession.Present(
            sessionId = 1L, workoutId = 7, workoutName = "Ноги",
            exercises = exercises, exerciseIndex = 0, phase = phase
        )

    @Test
    fun `тренировка идёт в Loading, Rest и Active`() {
        assertTrue(WorkoutExecutionState.Loading.isRunning)
        assertTrue(WorkoutExecutionState.Rest(push, 2).isRunning)
        assertTrue(WorkoutExecutionState.Active(push, 1).isRunning)
        assertFalse(WorkoutExecutionState.Error.isRunning)
        assertFalse(WorkoutExecutionState.Finished(1_000L).isRunning)
    }

    @Test
    fun `в процессе — только Rest и Active`() {
        assertFalse(WorkoutExecutionState.Loading.isInProgress)
        assertTrue(WorkoutExecutionState.Rest(push, 2).isInProgress)
        assertTrue(WorkoutExecutionState.Active(push, 1).isInProgress)
    }

    @Test
    fun `сводки нет без идущей сессии`() {
        assertNull(runningWorkoutOf(WorkoutSession.None))
        assertNull(runningWorkoutOf(present(WorkoutExecutionState.Error)))
        assertNull(runningWorkoutOf(present(WorkoutExecutionState.Finished(1_000L))))
    }

    @Test
    fun `сводка не зависит от остатка отдыха`() {
        val a = runningWorkoutOf(present(WorkoutExecutionState.Rest(push, 2, restTimeMillis = 50_000L)))
        val b = runningWorkoutOf(present(WorkoutExecutionState.Rest(push, 2, restTimeMillis = 49_800L)))
        assertEquals(RunningWorkout(7, "Ноги", isLoading = false, addedExerciseIds = emptySet()), a)
        assertEquals(a, b)
    }

    @Test
    fun `сводка загрузки помечена и перечисляет добавленные упражнения`() {
        assertTrue(runningWorkoutOf(present(WorkoutExecutionState.Loading))!!.isLoading)
        val added = SessionExercise(push.copy(id = 42), addedToday = true)
        val summary = runningWorkoutOf(present(WorkoutExecutionState.Active(push, 1), listOf(SessionExercise(push), added)))
        assertEquals(setOf(42), summary!!.addedExerciseIds)
    }
}
