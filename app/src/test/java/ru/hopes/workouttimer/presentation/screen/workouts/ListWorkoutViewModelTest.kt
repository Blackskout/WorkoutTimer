package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.lifecycle.SavedStateHandle
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
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ListWorkoutViewModelTest {

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
    fun `state combines workouts with their last session durations`() = runTest {
        val workouts = listOf(WorkoutEntity(id = 1, name = "Leg day", lastUseAt = 0L))
        val getAllWorkoutsUseCase = mockk<GetAllWorkoutsUseCase>()
        every { getAllWorkoutsUseCase() } returns flowOf(workouts)
        val getLastSessionDurationsUseCase = mockk<GetLastSessionDurationsUseCase>()
        every { getLastSessionDurationsUseCase() } returns flowOf(mapOf(1 to 2_500L))

        val viewModel = ListWorkoutViewModel(
            getAllWorkoutsUseCase = getAllWorkoutsUseCase,
            searchWorkoutsUseCase = mockk(relaxed = true),
            addWorkoutUseCase = mockk(relaxed = true),
            getWorkoutByIdUseCase = mockk(relaxed = true),
            deleteWorkoutUseCase = mockk(relaxed = true),
            getLastSessionDurationsUseCase = getLastSessionDurationsUseCase,
            savedStateHandle = SavedStateHandle()
        )

        val state = viewModel.state.value

        assertEquals(workouts, state.workouts)
        assertEquals(mapOf(1 to 2_500L), state.lastSessionDurations)
    }
}
