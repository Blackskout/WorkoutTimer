package ru.hopes.workouttimer.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

class SkipWorkoutUseCaseTest {

    @Test
    fun `возвращает прежний lastUseAt и ставит текущее время`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(3) } returns 1_000L
        val before = System.currentTimeMillis()

        val previous = SkipWorkoutUseCase(repo)(workoutId = 3)

        assertEquals(1_000L, previous)
        coVerify {
            repo.setLastUseAt(
                workoutId = 3,
                timestamp = match { it >= before }
            )
        }
    }

    @Test
    fun `не пишет сессию в историю`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(3) } returns 1_000L

        SkipWorkoutUseCase(repo)(workoutId = 3)

        coVerify(exactly = 0) {
            repo.addWorkoutSession(any(), any(), any(), any())
        }
    }

    @Test
    fun `для несуществующей тренировки возвращает null и ничего не пишет`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        coEvery { repo.getLastUseAt(99) } returns null

        assertNull(SkipWorkoutUseCase(repo)(workoutId = 99))

        coVerify(exactly = 0) { repo.setLastUseAt(any(), any()) }
    }

    @Test
    fun `отмена возвращает прежнее значение`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)

        UndoSkipWorkoutUseCase(repo)(workoutId = 3, previousLastUseAt = 1_000L)

        coVerify { repo.setLastUseAt(3, 1_000L) }
    }
}
