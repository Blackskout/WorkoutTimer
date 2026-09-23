package ru.hopes.workouttimer.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.LastSessionDuration
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WidgetUpdater

class WorkoutRepositoryImplTest {

    private class RecordingWidgetUpdater : WidgetUpdater {
        var updateCount = 0
            private set

        override suspend fun requestUpdate() {
            updateCount++
        }
    }

    @Test
    fun `addWorkoutSession stores the provided duration and inserts entity with converted workoutId`() = runTest {
        val dao = mockk<WorkoutDao>()
        val entitySlot = slot<WorkoutSessionEntity>()
        coEvery { dao.insertSession(capture(entitySlot)) } just io.mockk.Runs
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

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
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

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
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

        val durations = repo.getLastSessionDurations().first()

        assertEquals(mapOf(7 to 5_500L, 9 to 2_000L), durations)
    }

    @Test
    fun `updateWorkout does not touch lastUseAt`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

        repo.updateWorkout(
            Workout(id = 3, name = "Ноги", exercises = emptyList(), lastUseAt = 999L)
        )

        coVerify { dao.updateWorkout(id = 3, name = "Ноги") }
    }

    @Test
    fun `updateLastUseAt requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.updateLastUseAt(7)

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `addWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.addWorkout(Workout(id = 0, name = "Ноги", exercises = emptyList(), lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `deleteWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.deleteWorkout(WorkoutEntity(id = 3, name = "Ноги", lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `updateWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.updateWorkout(Workout(id = 3, name = "Ноги", exercises = emptyList(), lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `addWorkoutSession does not request a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.addWorkoutSession(workoutId = 7, startedAt = 1L, finishedAt = 2L, durationMillis = 1L)

        // Сессия всегда пишется в паре с updateLastUseAt(), второй пуш был бы лишним
        assertEquals(0, updater.updateCount)
    }

    @Test
    fun `setLastUseAt пишет переданное время и обновляет виджет`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val widgetUpdater = mockk<WidgetUpdater>(relaxed = true)
        val repo = WorkoutRepositoryImpl(dao, widgetUpdater)

        repo.setLastUseAt(workoutId = 5, timestamp = 1_700_000_000_000L)

        coVerify { dao.updateLastUseAt(5, 1_700_000_000_000L) }
        coVerify { widgetUpdater.requestUpdate() }
    }

    @Test
    fun `getLastUseAt возвращает null для несуществующей тренировки`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        coEvery { dao.getWorkoutById(42) } returns null
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        assertNull(repo.getLastUseAt(42))
    }

    @Test
    fun `getLastUseAt возвращает сохранённое время`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        coEvery { dao.getWorkoutById(7) } returns WorkoutEntity(
            id = 7,
            name = "Ноги",
            lastUseAt = 555L
        )
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        assertEquals(555L, repo.getLastUseAt(7))
    }

    @Test
    fun `updateExerciseWeightAndReps доходит до DAO целиком`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        repo.updateExerciseWeightAndReps(exerciseId = 3, weight = 82.5, reps = 6)

        coVerify(exactly = 1) { dao.updateExerciseWeightAndReps(id = 3, weight = 82.5, reps = 6) }
    }
}
