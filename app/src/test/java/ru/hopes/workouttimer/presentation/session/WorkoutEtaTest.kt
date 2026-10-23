package ru.hopes.workouttimer.presentation.session

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

class WorkoutEtaTest {

    private fun row(id: Int, sets: Int, done: Int = 0, restMillis: Long = 60_000L, addedToday: Boolean = false) =
        SessionExercise(
            exercise = Exercise(id = id, name = "E$id", weight = 10.0, sets = sets, reps = 8, timeMillis = restMillis, order = id),
            addedToday = addedToday,
            doneSets = done
        )

    private fun active(row: SessionExercise, set: Int) =
        WorkoutExecutionState.Active(exercise = row.exercise, currentSet = set, totalSets = row.exercise.sets)

    @Test
    fun `without closed sets the estimate comes from the template`() {
        val rows = listOf(row(1, sets = 3, restMillis = 90_000L), row(2, sets = 2, restMillis = 60_000L))

        val eta = workoutEtaOf(rows, 0, active(rows[0], 1), elapsedMillis = 0L, closedSets = 0)

        assertEquals(5, eta.remainingSets)
        assertEquals(3 * (90_000L + SET_WORK_MILLIS) + 2 * (60_000L + SET_WORK_MILLIS), eta.remainingMillis)
    }

    @Test
    fun `with closed sets the estimate follows today's pace`() {
        // 2 подхода за 6 минут → 3 мин на подход, осталось 1 + 3.
        val rows = listOf(row(1, sets = 3, done = 2), row(2, sets = 3))

        val eta = workoutEtaOf(rows, 0, active(rows[0], 3), elapsedMillis = 360_000L, closedSets = 2)

        assertEquals(4, eta.remainingSets)
        assertEquals(4 * 180_000L, eta.remainingMillis)
        assertEquals(360_000L, eta.elapsedMillis)
    }

    @Test
    fun `during rest the next set still counts`() {
        val rows = listOf(row(1, sets = 3, done = 1))
        val rest = WorkoutExecutionState.Rest(exercise = rows[0].exercise, currentSet = 2)

        assertEquals(2, workoutEtaOf(rows, 0, rest, 60_000L, closedSets = 1).remainingSets)
    }

    @Test
    fun `a repeat round of a done exercise counts from the set on screen`() {
        val rows = listOf(row(1, sets = 2, done = 2), row(2, sets = 1))

        val eta = workoutEtaOf(rows, 0, active(rows[0], 1), 60_000L, closedSets = 2)

        assertEquals(3, eta.remainingSets)
    }

    @Test
    fun `exercises added today are counted`() {
        val rows = listOf(row(1, sets = 1, done = 1), row(2, sets = 2, addedToday = true))

        assertEquals(2, workoutEtaOf(rows, 1, active(rows[1], 1), 60_000L, closedSets = 1).remainingSets)
    }
}
