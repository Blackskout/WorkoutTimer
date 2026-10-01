package ru.hopes.workouttimer.presentation.session

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.FinishWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper

/**
 * Перенос WorkoutExecutionViewModelTest на менеджер сессии: те же сценарии и
 * ожидания. Отличия механические: start вместо loadWorkout, индекс упражнения
 * вместо exercise.id, фаза — из снимка session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // Область менеджера на том же планировщике, что и runTest: виртуальное время общее.
    private lateinit var scope: CoroutineScope

    private val context = mockk<Context>(relaxed = true)
    private val soundPlayer = mockk<SoundPlayer>(relaxed = true)
    private val vibrationManager = mockk<VibrationManager>(relaxed = true)
    private val wakeLockHelper = mockk<WakeLockHelper>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After
    fun tearDown() {
        // Без отмены напоминание о простое и таймер одного теста переживают его.
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun buildManager(
        getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
        workoutRepository: WorkoutRepository,
        finishWorkoutSessionUseCase: FinishWorkoutSessionUseCase
    ): WorkoutSessionManager = WorkoutSessionManager(
        context = context,
        soundPlayer = soundPlayer,
        getWorkoutByIdUseCase = getWorkoutByIdUseCase,
        vibrationManager = vibrationManager,
        wakeLockHelper = wakeLockHelper,
        workoutRepository = workoutRepository,
        finishWorkoutSessionUseCase = finishWorkoutSessionUseCase,
        scope = scope
    )

    /** Фаза текущей сессии; тест падает, если сессии нет. */
    private val WorkoutSessionManager.phase: WorkoutExecutionState
        get() = (session.value as WorkoutSession.Present).phase

    private fun singleSetWorkout(): Workout {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        return Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
    }

    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            finishWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot),
                sets = any()
            )
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished()

        val state = manager.phase
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(durationSlot.captured, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        }
    }

    // f5956c4 поменял порядок вызовов местами: пуш обновления виджета подвешен на
    // updateLastUseAt(), поэтому он обязан срабатывать уже после записи сессии в БД, иначе
    // виджет может перерисоваться со старой длительностью или без неё. До этого теста порядок
    // не был закреплён ничем, кроме комментария в коде.
    @Test
    fun `finishing the workout records the session before pushing lastUseAt`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        coEvery {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished()

        coVerifyOrder {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
            workoutRepository.updateLastUseAt(1)
        }
    }

    @Test
    fun `excluded idle time is subtracted from the saved duration and clamped at zero`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            finishWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot),
                sets = any()
            )
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        // Bank a large excluded gap using timestamps far in the "future" relative to real
        // wall-clock: the jump from start's real lastInteractionAt to farFutureBase is
        // itself well over the threshold and banks its own excess, and the subsequent 45-minute
        // gap banks a further 35 minutes on top -- the exact total isn't asserted, only that it's
        // large enough to exceed the (near-zero, in a fast unit test) raw duration below. The
        // automatic registerInteraction() inside onExerciseFinished() (which uses the real
        // current time, far in the past relative to farFutureBase) then computes a negative gap
        // and adds no further exclusion.
        val farFutureBase = System.currentTimeMillis() + 10_000_000L
        manager.registerInteraction(now = farFutureBase)
        manager.registerInteraction(now = farFutureBase + 45 * 60 * 1000L) // additional 45 min gap

        manager.onExerciseFinished()

        // Raw elapsed (real start time -> real finishedAt time) is near-zero in a fast unit
        // test, while the banked exclusion is 35 minutes, so the coerceAtLeast(0L) clamp must apply.
        assertEquals(0L, durationSlot.captured)
        val state = manager.phase
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(0L, (state as WorkoutExecutionState.Finished).durationMillis)
    }

    @Test
    fun `loading a workout does not save a session by itself`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        coVerify(exactly = 0) {
            finishWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        }
    }

    @Test
    fun `a gap under the threshold excludes nothing`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L) // ненулевая база: 0L совпал бы с "ещё не было взаимодействий"
        manager.registerInteraction(now = 1L + 5 * 60 * 1000L) // 5 минут, меньше порога в 10

        assertEquals(0L, manager.excludedIdleMillis)
    }

    @Test
    fun `a gap over the threshold excludes only the excess`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L) // 45 минут простоя

        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis) // исключены только 45 - 10 = 35 минут
    }

    @Test
    fun `multiple gaps over the threshold accumulate`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 20 * 60 * 1000L) // гэп 20 мин -> исключено 10 мин
        manager.registerInteraction(now = 1L + 20 * 60 * 1000L + 30 * 60 * 1000L) // ещё гэп 30 мин -> исключено ещё 20 мин

        assertEquals(30 * 60 * 1000L, manager.excludedIdleMillis) // 10 + 20 = 30 минут суммарно
    }

    @Test
    fun `start resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)

        manager.start(1) // повторный start = новая сессия

        assertEquals(0L, manager.excludedIdleMillis)
    }

    @Test
    fun `onExerciseFinished registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.onExerciseFinished()

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `skipRest registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.skipRest()

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `moveToExercise registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.moveToExercise(0)

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `updateExerciseNote registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        coEvery { workoutRepository.updateExerciseNote(1, "note") } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.updateExerciseNote(0, "note")

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `automatic rest completion does not register an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest, регистрирует взаимодействие
        val afterRealInteraction = manager.lastInteractionAt

        manager.onRestFinished()

        assertEquals(afterRealInteraction, manager.lastInteractionAt)
    }

    @Test
    fun `loading a workout schedules the idle reminder for the first exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        assertTrue(manager.isIdleReminderJobActive)
    }

    @Test
    fun `moving to rest cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest

        assertFalse(manager.isIdleReminderJobActive)
    }

    @Test
    fun `skipping rest re-schedules the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // -> Rest
        manager.skipRest() // -> Active

        assertTrue(manager.isIdleReminderJobActive)
    }

    @Test
    fun `finishing the workout cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        coEvery {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // единственное упражнение, единственный подход -> Finished

        assertFalse(manager.isIdleReminderJobActive)
    }

    @Test
    fun `isLastSetOfWorkout is false on an intermediate set`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1) // подход 1 из 2

        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is false on the last set of a non-final exercise`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1) // единственный подход первого из двух упражнений

        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is true on the last set of the last exercise`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.moveToExercise(1) // единственный подход последнего упражнения

        assertTrue(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is false while resting`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> Rest

        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `updateExerciseWeightAndReps stores the new values`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, mockk<FinishWorkoutSessionUseCase>())

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        coVerify(exactly = 1) {
            workoutRepository.updateExerciseWeightAndReps(exerciseId = 1, weight = 12.5, extraWeight = 0.0, reps = 8)
        }
    }

    // Плитки на экране выполнения берут числа из Active.weight/Active.reps, а не из
    // Active.exercise, поэтому правка обязана обновить и отдельные поля состояния тоже.
    @Test
    fun `updateExerciseWeightAndReps refreshes the tiles of the current exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        val state = manager.phase as WorkoutExecutionState.Active
        assertEquals(12.5, state.weight, 0.0)
        assertEquals(8, state.reps)
        assertEquals(12.5, state.exercise.weight, 0.0)
        assertEquals(8, state.exercise.reps)
    }

    @Test
    fun `updateExerciseWeightAndReps refreshes the exercise shown while resting`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> Rest
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        val state = manager.phase as WorkoutExecutionState.Rest
        assertEquals(12.5, state.exercise.weight, 0.0)
        assertEquals(8, state.exercise.reps)
    }

    @Test
    fun `updateExerciseWeightAndReps of another exercise reaches it only when its turn comes`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 20.0, sets = 1, reps = 6, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 1, weight = 25.0, extraWeight = 0.0, reps = 9)

        val stillFirst = manager.phase as WorkoutExecutionState.Active
        assertEquals(10.0, stillFirst.weight, 0.0)
        assertEquals(5, stillFirst.reps)

        manager.moveToExercise(1)

        val nowSecond = manager.phase as WorkoutExecutionState.Active
        assertEquals(25.0, nowSecond.weight, 0.0)
        assertEquals(9, nowSecond.reps)
    }

    private fun workoutOf(vararg exercises: Exercise) =
        Workout(id = 1, name = "Test", exercises = exercises.toList(), lastUseAt = 0L)

    private fun ex(id: Int, name: String, sets: Int, weight: Double = 50.0, reps: Int = 8) =
        Exercise(id = id, name = name, weight = weight, sets = sets, reps = reps,
            timeMillis = 1_000, order = id, catalogId = id.toLong() * 10)

    @Test
    fun `last set of the workout is recorded together with the session`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 2))
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, repo, finish)

        manager.start(1)
        manager.onExerciseFinished()   // подход 1 → отдых
        manager.skipRest()
        manager.onExerciseFinished()   // подход 2 — последний

        assertEquals(
            listOf(
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG),
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
            ),
            setsSlot.captured
        )
    }

    @Test
    fun `jumping to another exercise records only finished sets`() = runTest {
        val squat = ex(1, "Присед", sets = 3)
        val press = ex(2, "Жим", sets = 1)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(squat, press)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()            // присед, подход 1
        manager.moveToExercise(1)   // бросили присед
        manager.onExerciseFinished()            // жим — последнее упражнение, последний подход

        assertEquals(listOf("Присед", "Жим"), setsSlot.captured.map { it.exerciseName })
    }

    @Test
    fun `leaving without finishing writes no session`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 3))
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()

        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `edited tiles are recorded right away even if the db write is slow`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.updateExerciseWeightAndReps(any(), any(), any(), any()) } coAnswers { gate.await() }
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, repo, finish)

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 62.5, extraWeight = 0.0, reps = 6) // запись в БД висит
        manager.onExerciseFinished()

        assertEquals(62.5, setsSlot.captured.single().weight, 0.0)
        assertEquals(6, setsSlot.captured.single().reps)
        gate.complete(Unit)
    }

    @Test
    fun `double tap on the last set gives one session and one set`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        manager.onExerciseFinished()   // второй тап, пока запись висит
        gate.complete(Unit)

        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `failed finish keeps sets, reports error and retry does not duplicate the last set`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        assertTrue(manager.finishError.value)
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        manager.dismissFinishError()
        manager.onExerciseFinished()   // повтор

        assertEquals(2, attempts.size)
        assertEquals(1, attempts.last().size)
        assertTrue(manager.phase is WorkoutExecutionState.Finished)
    }

    @Test
    fun `failed finish then jumping back and finishing again keeps every tapped set`() = runTest {
        val a = ex(1, "Присед", sets = 1)
        val b = ex(2, "Жим", sets = 1)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(a, b)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()            // A → отдых перед B
        manager.skipRest()
        manager.onExerciseFinished()            // B, последний подход — запись падает
        assertTrue(manager.finishError.value)

        manager.dismissFinishError()
        manager.moveToExercise(0)       // ушли с последнего подхода
        manager.onExerciseFinished()            // A ещё раз
        manager.skipRest()
        manager.onExerciseFinished()            // B — запись проходит

        val setA = RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
        val setB = RecordedSet(20L, "Жим", 50.0, 0.0, 8, ExerciseUnit.KG)
        assertEquals(2, attempts.size)
        assertEquals(listOf(setA, setB), attempts.first())
        // Неудавшийся подход B остаётся в истории: он был сделан, а не потерян.
        assertEquals(listOf(setA, setB, setA, setB), attempts.last())
        assertTrue(manager.phase is WorkoutExecutionState.Finished)
    }

    @Test
    fun `failed finish then weight edit is recorded by the retry without growing the list`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        manager.dismissFinishError()
        manager.updateExerciseWeightAndReps(index = 0, weight = 70.0, extraWeight = 0.0, reps = 5)
        manager.onExerciseFinished()   // повтор

        assertEquals(1, attempts.last().size)
        assertEquals(70.0, attempts.last().single().weight, 0.0)
        assertEquals(5, attempts.last().single().reps)
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `plate extra weight edited on the tiles is recorded with the set`() = runTest {
        val plate = Exercise(
            id = 1, name = "Тяга блока", weight = 5.0, sets = 1, reps = 12, timeMillis = 1_000, order = 1,
            catalogId = 4L, unit = ExerciseUnit.PLATE
        )
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns Workout(id = 1, name = "Спина", exercises = listOf(plate), lastUseAt = 0L)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 6.0, extraWeight = 2.0, reps = 10)

        assertEquals(2.0, (manager.phase as WorkoutExecutionState.Active).extraWeight, 0.0)
        manager.onExerciseFinished()
        assertEquals(
            RecordedSet(4L, "Тяга блока", 6.0, 2.0, 10, ExerciseUnit.PLATE),
            setsSlot.captured.single()
        )
    }
}
