package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.AddWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExecutionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
        workoutRepository: WorkoutRepository,
        addWorkoutSessionUseCase: AddWorkoutSessionUseCase
    ): WorkoutExecutionViewModel {
        return WorkoutExecutionViewModel(
            context = mockk<Context>(relaxed = true),
            soundPlayer = mockk<SoundPlayer>(relaxed = true),
            getWorkoutByIdUseCase = getWorkoutByIdUseCase,
            vibrationManager = mockk<VibrationManager>(relaxed = true),
            wakeLockHelper = mockk<WakeLockHelper>(relaxed = true),
            workoutRepository = workoutRepository,
            addWorkoutSessionUseCase = addWorkoutSessionUseCase
        )
    }

    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            addWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot)
            )
        } just Runs

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished()

        val state = viewModel.uiState.value
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(durationSlot.captured, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any())
        }
    }

    @Test
    fun `loading a workout does not save a session by itself`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        coVerify(exactly = 0) {
            addWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any(), durationMillis = any())
        }
    }

    @Test
    fun `a gap under the threshold excludes nothing`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 1L) // ненулевая база: 0L совпал бы с "ещё не было взаимодействий"
        viewModel.registerInteraction(now = 1L + 5 * 60 * 1000L) // 5 минут, меньше порога в 10

        assertEquals(0L, viewModel.excludedIdleMillis)
    }

    @Test
    fun `a gap over the threshold excludes only the excess`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 1L)
        viewModel.registerInteraction(now = 1L + 45 * 60 * 1000L) // 45 минут простоя

        assertEquals(35 * 60 * 1000L, viewModel.excludedIdleMillis) // исключены только 45 - 10 = 35 минут
    }

    @Test
    fun `multiple gaps over the threshold accumulate`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 1L)
        viewModel.registerInteraction(now = 1L + 20 * 60 * 1000L) // гэп 20 мин -> исключено 10 мин
        viewModel.registerInteraction(now = 1L + 20 * 60 * 1000L + 30 * 60 * 1000L) // ещё гэп 30 мин -> исключено ещё 20 мин

        assertEquals(30 * 60 * 1000L, viewModel.excludedIdleMillis) // 10 + 20 = 30 минут суммарно
    }

    @Test
    fun `loadWorkout resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.registerInteraction(now = 1L)
        viewModel.registerInteraction(now = 1L + 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, viewModel.excludedIdleMillis)

        viewModel.loadWorkout(1) // повторная загрузка = новая сессия

        assertEquals(0L, viewModel.excludedIdleMillis)
    }

    @Test
    fun `onExerciseFinished registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.onExerciseFinished()

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `skipRest registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.skipRest()

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `moveToSelectedExercise registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.moveToSelectedExercise(exercise)

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `updateExerciseNote registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        coEvery { workoutRepository.updateExerciseNote(1, "note") } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.updateExerciseNote(1, "note")

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `automatic rest completion does not register an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest, регистрирует взаимодействие
        val afterRealInteraction = viewModel.lastInteractionAt

        viewModel.onRestFinished()

        assertEquals(afterRealInteraction, viewModel.lastInteractionAt)
    }

    @Test
    fun `loading a workout schedules the idle reminder for the first exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        assertTrue(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `moving to rest cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest

        assertFalse(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `skipping rest re-schedules the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // -> Rest
        viewModel.skipRest() // -> Active

        assertTrue(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `finishing the workout cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        coEvery {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any())
        } just Runs

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // единственное упражнение, единственный подход -> Finished

        assertFalse(viewModel.isIdleReminderJobActive)
    }
}
