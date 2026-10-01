package ru.hopes.workouttimer.presentation.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

@OptIn(ExperimentalCoroutinesApi::class)
class MiniWorkoutBarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    private val running = MutableStateFlow<RunningWorkout?>(null)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)
    private val press = Exercise(id = 1, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L, order = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.session } returns session
        every { manager.runningWorkout } returns running
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun resting(left: Long) = WorkoutSession.Present(
        sessionId = 1L, workoutId = 3, workoutName = "Ноги", exercises = listOf(SessionExercise(press)),
        exerciseIndex = 0, phase = WorkoutExecutionState.Rest(press, 2, restTimeMillis = left)
    )

    @Test
    fun `плашка повторяет сессию`() {
        val vm = MiniWorkoutBarViewModel(manager)
        assertNull(vm.barState.value)
        assertFalse(vm.visible.value)

        session.value = resting(78_000L)

        assertEquals(MiniBarStatus.Resting(78_000L, 120_000L), vm.barState.value?.status)
        assertTrue(vm.visible.value)
    }

    @Test
    fun `тики отдыха не меняют видимость`() = runTest {
        session.value = resting(78_000L)
        val vm = MiniWorkoutBarViewModel(manager)
        val seen = mutableListOf<Boolean>()
        val job = launch(dispatcher) { vm.visible.collect { seen += it } }

        session.value = resting(77_800L)
        session.value = resting(77_600L)

        assertEquals(listOf(true), seen)
        job.cancel()
    }

    @Test
    fun `возврат засчитывает действие и отдаёт идущую тренировку`() {
        running.value = RunningWorkout(3, "Ноги", isLoading = false, addedExerciseIds = emptySet())
        val vm = MiniWorkoutBarViewModel(manager)

        assertEquals(3, vm.prepareReturn())
        verify { manager.registerInteraction(any()) }
    }

    @Test
    fun `без сессии возвращаться некуда`() {
        val vm = MiniWorkoutBarViewModel(manager)

        assertNull(vm.prepareReturn())
        verify(exactly = 0) { manager.registerInteraction(any()) }
    }

    @Test
    fun `крестик выходит без сохранения`() {
        MiniWorkoutBarViewModel(manager).abandon()

        verify { manager.abandon() }
    }
}
