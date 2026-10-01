package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.session.SessionExercise
import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import ru.hopes.workouttimer.presentation.session.WorkoutSession
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExecutionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)
    private val store = ViewModelStore()

    private val push = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, order = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.session } returns session
        every { manager.finishError } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Через ViewModelStore: store.clear() вызывает настоящий onCleared().
    private fun viewModel(): WorkoutExecutionViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { WorkoutExecutionViewModel(manager) } }
    )[WorkoutExecutionViewModel::class]

    private fun present(id: Long, phase: WorkoutExecutionState) = WorkoutSession.Present(
        sessionId = id, workoutId = 1, workoutName = "Ноги",
        exercises = listOf(SessionExercise(push)), exerciseIndex = 0, phase = phase
    )

    @Test
    fun `leaving before start closes nothing`() {
        viewModel()

        store.clear()

        verify(exactly = 0) { manager.close(any()) }
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `leaving a finished session closes the session this screen started`() {
        every { manager.start(1) } returns 7L
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Finished(1_000L))

        store.clear()

        verify { manager.close(7L) }
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `leaving a running session only asks to close it, which keeps it running`() {
        every { manager.start(1) } returns 7L
        every { manager.isRunning } returns true
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Active(push, 1))

        store.clear() // экран свернули

        verify { manager.close(7L) } // менеджер сам ничего не сделает: тренировка в процессе
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `start on a finished session this screen owns does not start again`() {
        every { manager.start(1) } returns 7L
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Finished(1_000L))

        vm.start(1) // пересоздание активности на «ГОТОВО»

        verify(exactly = 1) { manager.start(1) }
    }

    @Test
    fun `retry starts a new load even for an owned session`() {
        every { manager.start(1) } returnsMany listOf(7L, 8L)
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Error)

        vm.retry(1)

        verify(exactly = 2) { manager.start(1) }
    }

    @Test
    fun `exit without saving abandons the session`() {
        viewModel().abandon()

        verify { manager.abandon() }
    }

    @Test
    fun `retry after an error owns the new session`() {
        every { manager.start(1) } returnsMany listOf(7L, 8L)
        val vm = viewModel()
        vm.start(1)
        vm.retry(1) // «Повторить»
        session.value = present(8L, WorkoutExecutionState.Error)

        store.clear()

        verify { manager.close(8L) }
        verify(exactly = 0) { manager.close(7L) }
    }

    @Test
    fun `ui state starts from the current snapshot`() {
        session.value = present(3L, WorkoutExecutionState.Rest(push, 2))

        val vm = viewModel()

        assertTrue(vm.uiState.value is WorkoutExecutionState.Rest)
    }

    @Test
    fun `ui state follows the manager and keeps the last phase when the session ends`() {
        val vm = viewModel()
        assertEquals(WorkoutExecutionState.Loading, vm.uiState.value)

        session.value = present(3L, WorkoutExecutionState.Active(push, 1))
        assertTrue(vm.uiState.value is WorkoutExecutionState.Active)

        session.value = WorkoutSession.None
        assertTrue(vm.uiState.value is WorkoutExecutionState.Active)
    }

    @Test
    fun `chrome shows the session name and position`() {
        session.value = present(3L, WorkoutExecutionState.Active(push, 1))

        val chrome = viewModel().chrome.value

        assertEquals("Ноги", chrome.workoutName)
        assertEquals(1, chrome.currentExerciseNumber)
        assertEquals(1, chrome.totalExercises)
    }
}
