package ru.hopes.workouttimer.presentation.screen.workoutHistory

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionSetsUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadHistory exposes workout name and sessions`() = runTest {
        val workout = Workout(id = 3, name = "Leg day", exercises = emptyList(), lastUseAt = 0L)
        val sessions = listOf(
            WorkoutSession(id = 1, workoutId = 3, startedAt = 1_000L, finishedAt = 4_000L, durationMillis = 3_000L)
        )
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(3) } returns workout
        val getWorkoutSessionsUseCase = mockk<GetWorkoutSessionsUseCase>()
        every { getWorkoutSessionsUseCase(3) } returns flowOf(sessions)

        val getSets = mockk<GetWorkoutSessionSetsUseCase>()
        every { getSets(3) } returns flowOf(emptyMap())

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getSets)
        viewModel.loadHistory(3)

        val state = viewModel.state.value
        assertEquals("Leg day", state.workoutName)
        assertEquals(sessions, state.sessions)
    }

    @Test
    fun `loadHistory exposes sets grouped by session`() = runTest {
        val sets = mapOf(
            1L to listOf(
                SessionExerciseSets(
                    catalogId = 7L,
                    exerciseName = "Присед",
                    sets = listOf(SessionSet(1L, 1L, 7L, 60.0, 0.0, 8, ExerciseUnit.KG))
                )
            )
        )
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(3) } returns null
        val getWorkoutSessionsUseCase = mockk<GetWorkoutSessionsUseCase>()
        every { getWorkoutSessionsUseCase(3) } returns flowOf(emptyList())
        val getSets = mockk<GetWorkoutSessionSetsUseCase>()
        every { getSets(3) } returns flowOf(sets)

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getSets)
        viewModel.loadHistory(3)

        assertEquals(sets, viewModel.state.value.setsBySession)
    }

    @Test
    fun `setsBySession is null until the sets flow emits`() = runTest {
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(3) } returns null
        val getWorkoutSessionsUseCase = mockk<GetWorkoutSessionsUseCase>()
        every { getWorkoutSessionsUseCase(3) } returns flowOf(emptyList())
        val getSets = mockk<GetWorkoutSessionSetsUseCase>()
        every { getSets(3) } returns kotlinx.coroutines.flow.emptyFlow()

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getSets)
        viewModel.loadHistory(3)

        assertEquals(null, viewModel.state.value.setsBySession)
    }
}
