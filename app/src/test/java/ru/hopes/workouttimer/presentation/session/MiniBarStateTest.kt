package ru.hopes.workouttimer.presentation.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

class MiniBarStateTest {

    private val press = Exercise(id = 1, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L, order = 1)

    private fun present(phase: WorkoutExecutionState, restOver: Boolean = false) = WorkoutSession.Present(
        sessionId = 1L, workoutId = 3, workoutName = "Ноги",
        exercises = listOf(SessionExercise(press)), exerciseIndex = 0, phase = phase, restOver = restOver
    )

    @Test
    fun `плашки нет без сессии в отдыхе или подходе`() {
        assertNull(miniBarStateOf(WorkoutSession.None))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Loading)))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Error)))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Finished(1_000L))))
    }

    @Test
    fun `отдых — остаток и полное время`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Rest(press, 2, restTimeMillis = 78_000L)))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.Resting(78_000L, 120_000L)), state)
    }

    @Test
    fun `подход — номер, число подходов и упражнение`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Active(press, 2)))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.Working(2, 4, "Жим лёжа")), state)
    }

    @Test
    fun `отдых закончился сам — пора делать подход`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Active(press, 2), restOver = true))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.RestOver(2)), state)
    }

    @Test
    fun `кольцо — доля оставшегося отдыха, пустое в подходе, полное после отдыха`() {
        assertEquals(0.5f, ringFraction(MiniBarStatus.Resting(60_000L, 120_000L)), 0.0001f)
        assertEquals(0f, ringFraction(MiniBarStatus.Resting(10_000L, 0L)), 0.0001f)
        assertEquals(1f, ringFraction(MiniBarStatus.Resting(130_000L, 120_000L)), 0.0001f)
        assertEquals(0f, ringFraction(MiniBarStatus.Working(1, 4, "Жим лёжа")), 0.0001f)
        assertEquals(1f, ringFraction(MiniBarStatus.RestOver(2)), 0.0001f)
    }
}
