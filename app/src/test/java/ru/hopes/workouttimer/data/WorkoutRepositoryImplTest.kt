package ru.hopes.workouttimer.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.dao.LastSessionDuration
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

class WorkoutRepositoryImplTest {

    @Test
    fun `addWorkoutSession stores the provided duration and inserts entity with converted workoutId`() = runTest {
        val dao = mockk<WorkoutDao>()
        val entitySlot = slot<WorkoutSessionEntity>()
        coEvery { dao.insertSession(capture(entitySlot)) } just io.mockk.Runs
        val repo = WorkoutRepositoryImpl(dao)

        repo.addWorkoutSession(workoutId = 7, startedAt = 1_000L, finishedAt = 6_500L, durationMillis = 4_000L)

        assertEquals(7L, entitySlot.captured.workoutId)
        assertEquals(1_000L, entitySlot.captured.startedAt)
        assertEquals(6_500L, entitySlot.captured.finishedAt)
        assertEquals(4_000L, entitySlot.captured.durationMillis)
    }

    @Test
    fun `getSessionsForWorkout maps entities to domain sessions`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.getSessionsForWorkout(7L) } returns flowOf(
            listOf(
                WorkoutSessionEntity(id = 1, workoutId = 7L, startedAt = 100L, finishedAt = 200L, durationMillis = 100L)
            )
        )
        val repo = WorkoutRepositoryImpl(dao)

        val sessions = repo.getSessionsForWorkout(7).first()

        assertEquals(1, sessions.size)
        assertEquals(7, sessions[0].workoutId)
        assertEquals(100L, sessions[0].durationMillis)
    }

    @Test
    fun `getLastSessionDurations maps to a workoutId-to-duration map`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.getLastSessionDurations() } returns flowOf(
            listOf(
                LastSessionDuration(workoutId = 7L, durationMillis = 5_500L),
                LastSessionDuration(workoutId = 9L, durationMillis = 2_000L)
            )
        )
        val repo = WorkoutRepositoryImpl(dao)

        val durations = repo.getLastSessionDurations().first()

        assertEquals(mapOf(7 to 5_500L, 9 to 2_000L), durations)
    }
}
