package ru.hopes.workouttimer.presentation.session

import android.content.Context
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import ru.hopes.workouttimer.presentation.service.WorkoutAlerts
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
    private val workoutAlerts = mockk<WorkoutAlerts>(relaxed = true)

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
        workoutAlerts = workoutAlerts,
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
    fun `start after abandon resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(getWorkoutByIdUseCase, mockk(relaxed = true), mockk())
        manager.start(1)
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)

        manager.abandon()
        manager.start(1) // новая сессия

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
        manager.onExerciseFinished() // первое сделано → отдых перед последним
        manager.skipRest()

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
        manager.onExerciseFinished()            // жим — последний в списке, но присед не доделан
        assertFalse(setsSlot.isCaptured)
        assertEquals(2, (manager.phase as WorkoutExecutionState.Rest).currentSet)

        manager.skipRest()
        manager.onExerciseFinished()            // присед, подход 2
        manager.skipRest()
        manager.onExerciseFinished()            // присед, подход 3 — всё сделано

        assertEquals(listOf("Присед", "Жим", "Присед", "Присед"), setsSlot.captured.map { it.exerciseName })
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
        manager.onExerciseFinished()            // A ещё раз; B уже сделан — запись проходит

        val setA = RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
        val setB = RecordedSet(20L, "Жим", 50.0, 0.0, 8, ExerciseUnit.KG)
        assertEquals(2, attempts.size)
        assertEquals(listOf(setA, setB), attempts.first())
        // Неудавшийся подход B остаётся в истории: он был сделан, а не потерян.
        assertEquals(listOf(setA, setB, setA), attempts.last())
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

    // --- Жизненный цикл сессии (спека, «Тестирование» → новые тесты менеджера) ---

    private fun twoSetWorkout(id: Int = 1, name: String = "Test") = Workout(
        id = id, name = name, lastUseAt = 0L,
        exercises = listOf(Exercise(id = id * 10, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 60_000, order = 1))
    )

    private fun managerWith(vararg workouts: Workout, finish: FinishWorkoutSessionUseCase = mockk(relaxed = true)): WorkoutSessionManager {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(any()) } returns null
        workouts.forEach { w -> coEvery { getWorkout(w.id) } returns w }
        return buildManager(getWorkout, mockk(relaxed = true), finish)
    }

    @Test
    fun `a loaded workout is running`() = runTest {
        val manager = managerWith(singleSetWorkout())
        assertFalse(manager.isRunning)

        manager.start(1)

        assertTrue(manager.isRunning)
    }

    @Test
    fun `a workout that failed to load is not running`() = runTest {
        val manager = managerWith()

        manager.start(1)

        assertTrue(manager.phase is WorkoutExecutionState.Error)
        assertFalse(manager.isRunning)
    }

    @Test
    fun `finishing the workout stops it running`() = runTest {
        val manager = managerWith(singleSetWorkout())
        manager.start(1)

        manager.onExerciseFinished()

        assertTrue(manager.phase is WorkoutExecutionState.Finished)
        assertFalse(manager.isRunning)
    }

    @Test
    fun `resting and loading count as running`() = runTest {
        val gate = CompletableDeferred<Workout?>()
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), mockk(relaxed = true))

        manager.start(1)
        assertTrue(manager.phase is WorkoutExecutionState.Loading)
        assertTrue(manager.isRunning)

        gate.complete(twoSetWorkout())
        manager.onExerciseFinished()
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertTrue(manager.isRunning)
    }

    @Test
    fun `start of the same workout while running resets nothing`() = runTest {
        val manager = managerWith(twoSetWorkout())
        val id = manager.start(1)
        manager.onExerciseFinished() // подход 1 → отдых
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)

        val again = manager.start(1) // пересоздание активности: LaunchedEffect снова зовёт start

        assertEquals(id, again)
        assertEquals(1, manager.recordedSets.size)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)
    }

    @Test
    fun `start of another workout while running is ignored`() = runTest {
        val manager = managerWith(twoSetWorkout(1, "Ноги"), twoSetWorkout(2, "Спина"))
        val id = manager.start(1)

        val other = manager.start(2)

        assertEquals(id, other)
        val session = manager.session.value as WorkoutSession.Present
        assertEquals(1, session.workoutId)
        assertEquals("Ноги", session.workoutName)
    }

    @Test
    fun `start from Finished or Error begins a new session`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val first = manager.start(1)
        manager.onExerciseFinished() // Finished

        val second = manager.start(1)
        assertNotEquals(first, second)
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        val failing = managerWith()
        val broken = failing.start(5) // Error
        assertNotEquals(broken, failing.start(5))
    }

    @Test
    fun `abandon releases everything, writes nothing and leaves no session`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(twoSetWorkout(), finish = finish)
        manager.start(1)
        manager.onExerciseFinished() // отдых: таймер, wakelock, уведомление
        assertTrue(manager.isRestTimerActive)

        manager.abandon()

        assertEquals(WorkoutSession.None, manager.session.value)
        assertFalse(manager.isRestTimerActive)
        assertFalse(manager.isIdleReminderJobActive)
        assertTrue(manager.recordedSets.isEmpty())
        assertFalse(manager.finishError.value)
        verify { wakeLockHelper.release() }
        verify { soundPlayer.release() }
        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `close does nothing while the workout is in progress`() = runTest {
        val manager = managerWith(twoSetWorkout())
        val id = manager.start(1)

        manager.close(id) // Active
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        manager.onExerciseFinished()
        manager.close(id) // Rest
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
    }

    @Test
    fun `close ends a finished or failed session`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val finishedId = manager.start(1)
        manager.onExerciseFinished()
        manager.close(finishedId)
        assertEquals(WorkoutSession.None, manager.session.value)

        val failing = managerWith()
        val errorId = failing.start(5)
        failing.close(errorId)
        assertEquals(WorkoutSession.None, failing.session.value)
    }

    @Test
    fun `close during loading cancels the load`() = runTest {
        val gate = CompletableDeferred<Workout?>()
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), mockk(relaxed = true))
        val id = manager.start(1)

        manager.close(id)
        gate.complete(singleSetWorkout())

        assertEquals(WorkoutSession.None, manager.session.value)
    }

    @Test
    fun `close with a stale sessionId leaves the new session alone`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val old = manager.start(1)
        manager.onExerciseFinished() // Finished
        val fresh = manager.start(1)

        manager.close(old) // onCleared старой записи навигации приходит позже
        assertEquals(fresh, (manager.session.value as WorkoutSession.Present).sessionId)

        val failing = managerWith()
        val staleError = failing.start(5)
        val freshError = failing.start(5)
        failing.close(staleError)
        assertEquals(freshError, (failing.session.value as WorkoutSession.Present).sessionId)
    }

    @Test
    fun `abandon during a slow note write cancels the write`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.updateExerciseNote(any(), any()) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.updateExerciseNote(0, "колени наружу") // запись висит

        manager.abandon()
        manager.start(1)
        gate.complete(Unit)

        val session = manager.session.value as WorkoutSession.Present
        assertEquals("", session.exercises[0].exercise.note)
    }

    @Test
    fun `abandon while the finish is being written keeps the session closed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns singleSetWorkout()
        val manager = buildManager(getWorkout, repo, finish)
        manager.start(1)
        manager.onExerciseFinished() // запись висит, isFinishing

        manager.abandon()
        gate.complete(Unit)

        assertEquals(WorkoutSession.None, manager.session.value)
        assertFalse(manager.finishError.value)
        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { repo.updateLastUseAt(1) }
    }

    @Test
    fun `running summary follows start and end but not rest ticks`() = runTest {
        val manager = managerWith(twoSetWorkout(1, "Ноги"))
        assertNull(manager.runningWorkout.value)

        manager.start(1)
        assertEquals(RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet()), manager.runningWorkout.value)

        manager.onExerciseFinished() // отдых, таймер тикает каждые 200 мс
        val resting = manager.runningWorkout.value
        testScheduler.advanceTimeBy(1_000)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertSame(resting, manager.runningWorkout.value)

        manager.abandon()
        assertNull(manager.runningWorkout.value)
    }

    // --- «Пора: подход N» на плашке ---

    private val WorkoutSessionManager.restOver: Boolean
        get() = (session.value as WorkoutSession.Present).restOver

    @Test
    fun `rest that runs out by itself raises restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished() // отдых
        assertFalse(manager.restOver)

        manager.onRestFinished()

        assertTrue(manager.phase is WorkoutExecutionState.Active)
        assertTrue(manager.restOver)
    }

    @Test
    fun `skipping rest does not raise restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()

        manager.skipRest()

        assertFalse(manager.restOver)
    }

    @Test
    fun `finishing a set or jumping to an exercise lowers restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        manager.onRestFinished()
        assertTrue(manager.restOver)

        manager.onExerciseFinished() // последний подход → Finished
        assertFalse(manager.restOver)

        val other = managerWith(twoSetWorkout())
        other.start(1)
        other.onExerciseFinished()
        other.onRestFinished()
        other.moveToExercise(0)
        assertFalse(other.restOver)
    }

    // --- «+ в сегодняшнюю» ---

    // Упражнение другой тренировки (workoutId = 2), строка шаблона id = 21.
    private val fromBack = Exercise(
        id = 21, name = "Тяга блока", weight = 5.0, sets = 2, reps = 12, timeMillis = 60_000,
        order = 1, catalogId = 210L, unit = ExerciseUnit.PLATE, extraWeight = 2.0, note = "локти вниз"
    )

    private val WorkoutSessionManager.snapshot: WorkoutSession.Present
        get() = session.value as WorkoutSession.Present

    @Test
    fun `added exercise goes to the end without moving the current position`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished() // отдых перед подходом 2

        val result = manager.addExerciseToday(fromBack)

        assertEquals(AddResult.ADDED, result)
        assertEquals(listOf(false, true), manager.snapshot.exercises.map { it.addedToday })
        assertEquals(fromBack, manager.snapshot.exercises.last().exercise)
        assertEquals(0, manager.snapshot.exerciseIndex)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertEquals(setOf(21), manager.runningWorkout.value?.addedExerciseIds)
    }

    @Test
    fun `the same source row is added only once`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.addExerciseToday(fromBack)

        val again = manager.addExerciseToday(fromBack)

        assertEquals(AddResult.ALREADY_ADDED, again)
        assertEquals(2, manager.snapshot.exercises.size)
    }

    @Test
    fun `nothing is added without a workout in progress`() = runTest {
        val idle = managerWith(singleSetWorkout())
        assertEquals(AddResult.NO_SESSION, idle.addExerciseToday(fromBack))

        val finished = managerWith(singleSetWorkout())
        finished.start(1)
        finished.onExerciseFinished() // Finished
        assertEquals(AddResult.NO_SESSION, finished.addExerciseToday(fromBack))

        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val finishing = managerWith(singleSetWorkout(), finish = finish)
        finishing.start(1)
        finishing.onExerciseFinished() // запись висит
        assertEquals(AddResult.NO_SESSION, finishing.addExerciseToday(fromBack))
        assertEquals(1, finishing.snapshot.exercises.size)
        gate.complete(Unit)
    }

    @Test
    fun `adding an exercise registers an interaction`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.addExerciseToday(fromBack)

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `adding on the last set moves the end of the workout`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(singleSetWorkout(), finish = finish)
        manager.start(1)
        assertTrue(manager.isLastSetOfWorkout)

        manager.addExerciseToday(fromBack)
        assertFalse(manager.isLastSetOfWorkout)

        manager.onExerciseFinished()

        val rest = manager.phase as WorkoutExecutionState.Rest
        assertEquals("Тяга блока", rest.exercise.name)
        assertEquals(1, manager.snapshot.exerciseIndex)
        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sets of the added exercise are saved with the running workout`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(1, any(), any(), any(), capture(setsSlot)) } returns Unit
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val manager = buildManager(getWorkout, repo, finish)
        manager.start(1)
        manager.addExerciseToday(fromBack.copy(sets = 1))

        manager.onExerciseFinished() // присед → отдых перед тягой
        manager.skipRest()
        manager.onExerciseFinished() // тяга — последний подход

        assertEquals(
            listOf(
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG),
                RecordedSet(210L, "Тяга блока", 5.0, 2.0, 12, ExerciseUnit.PLATE)
            ),
            setsSlot.captured
        )
        coVerify(exactly = 1) { repo.updateLastUseAt(1) }
        coVerify(exactly = 0) { repo.updateLastUseAt(2) }
    }

    @Test
    fun `edits of an added exercise stay in the session`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.addExerciseToday(fromBack)

        manager.updateExerciseWeightAndReps(index = 1, weight = 6.0, extraWeight = 0.0, reps = 10)
        manager.updateExerciseNote(1, "без рывков")

        val added = manager.snapshot.exercises[1].exercise
        assertEquals(6.0, added.weight, 0.0)
        assertEquals("без рывков", added.note)
        coVerify(exactly = 0) { repo.updateExerciseWeightAndReps(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.updateExerciseNote(any(), any()) }
    }

    @Test
    fun `edits of a template row still go to the template`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.addExerciseToday(fromBack)

        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 6)
        manager.updateExerciseNote(0, "медленно")

        coVerify(exactly = 1) { repo.updateExerciseWeightAndReps(10, 12.5, 0.0, 6) }
        coVerify(exactly = 1) { repo.updateExerciseNote(10, "медленно") }
    }

    @Test
    fun `index addressing keeps an added copy apart from a template row of the same catalog entry`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        // Та же запись справочника, что у строки шаблона, но другая исходная строка.
        val sameCatalog = fromBack.copy(id = 99, name = "Push", catalogId = 0L, unit = ExerciseUnit.KG, extraWeight = 0.0)
        manager.addExerciseToday(sameCatalog)

        manager.updateExerciseWeightAndReps(index = 1, weight = 40.0, extraWeight = 0.0, reps = 3)
        assertEquals(10.0, manager.snapshot.exercises[0].exercise.weight, 0.0)

        manager.moveToExercise(1)
        val active = manager.phase as WorkoutExecutionState.Active
        assertEquals(99, active.exercise.id)
        assertEquals(40.0, active.weight, 0.0)
    }

    @Test
    fun `after a failed finish the first set of an added exercise is appended`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)
        manager.start(1)
        manager.onExerciseFinished() // последний подход — запись падает
        assertTrue(manager.finishError.value)
        manager.dismissFinishError()

        manager.addExerciseToday(fromBack) // два подхода тяги
        manager.onExerciseFinished() // присед ещё раз — заменяет неудачный, ведёт на отдых
        manager.skipRest()
        manager.onExerciseFinished() // тяга, подход 1 — дописывается
        manager.skipRest()
        manager.onExerciseFinished() // тяга, подход 2 — последний, запись проходит

        val squat = RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
        val row = RecordedSet(210L, "Тяга блока", 5.0, 2.0, 12, ExerciseUnit.PLATE)
        assertEquals(2, attempts.size)
        assertEquals(listOf(squat, row, row), attempts.last())
        assertNotNull(manager.phase as? WorkoutExecutionState.Finished)
    }

    @Test
    fun `rest finishing keeps its own alert`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        clearMocks(workoutAlerts)

        manager.onRestFinished()

        verify(exactly = 0) { workoutAlerts.dismiss() }
    }

    @Test
    fun `rest finishing in background posts the alert`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        every { workoutAlerts.isAppInForeground() } returns false
        clearMocks(context, answers = false)

        manager.onRestFinished()

        // ACTION_STOP таймера и ACTION_SHOW_FINISHED
        verify(exactly = 2) { context.startService(any()) }
    }

    @Test
    fun `rest finishing with the app on screen posts no alert`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        every { workoutAlerts.isAppInForeground() } returns true
        clearMocks(context, answers = false)

        manager.onRestFinished()

        // Только ACTION_STOP таймера
        verify(exactly = 1) { context.startService(any()) }
        assertNotNull(manager.phase as? WorkoutExecutionState.Active)
    }

    @Test
    fun `continuing the workout dismisses stale alerts`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        manager.onRestFinished()
        clearMocks(workoutAlerts)

        manager.onExerciseFinished()

        verify { workoutAlerts.dismiss() }
    }

    @Test
    fun `abandoning the workout dismisses stale alerts`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        manager.onRestFinished()
        clearMocks(workoutAlerts)

        manager.abandon()

        verify { workoutAlerts.dismiss() }
    }

    // --- Прогресс по упражнениям: переходы по шторке не путают сделанное с пропущенным ---

    private val WorkoutSessionManager.doneSets: List<Int>
        get() = snapshot.exercises.map { it.doneSets }

    private val WorkoutSessionManager.active: WorkoutExecutionState.Active
        get() = phase as WorkoutExecutionState.Active

    @Test
    fun `finished sets are counted per exercise`() = runTest {
        val manager = managerWith(workoutOf(ex(1, "Присед", sets = 3), ex(2, "Жим", sets = 2)))
        manager.start(1)

        manager.onExerciseFinished()
        manager.skipRest()
        manager.onExerciseFinished()

        assertEquals(listOf(2, 0), manager.doneSets)
    }

    @Test
    fun `jumping ahead leaves skipped exercises unstarted`() = runTest {
        val manager = managerWith(workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 1), ex(3, "C", sets = 1)))
        manager.start(1)

        manager.moveToExercise(2)

        assertEquals(listOf(0, 0, 0), manager.doneSets)
    }

    @Test
    fun `returning to a started exercise continues from the next set`() = runTest {
        val manager = managerWith(workoutOf(ex(1, "Присед", sets = 4), ex(2, "Жим", sets = 1)))
        manager.start(1)
        manager.onExerciseFinished()
        manager.skipRest()
        manager.onExerciseFinished() // присед: 2 из 4

        manager.moveToExercise(1)
        manager.moveToExercise(0)

        assertEquals("Присед", manager.active.exercise.name)
        assertEquals(3, manager.active.currentSet)
    }

    @Test
    fun `returning to a finished exercise starts a repeat round from set 1`() = runTest {
        val manager = managerWith(workoutOf(ex(1, "Присед", sets = 1), ex(2, "Жим", sets = 2)))
        manager.start(1)
        manager.onExerciseFinished() // присед сделан → отдых перед жимом

        manager.moveToExercise(0)

        assertEquals(1, manager.active.currentSet)
        manager.onExerciseFinished() // повтор не превращает счётчик в «2 из 1»
        assertEquals(listOf(1, 0), manager.doneSets)
    }

    @Test
    fun `after an exercise the next unfinished one below comes next`() = runTest {
        val manager = managerWith(
            workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 1), ex(3, "C", sets = 1), ex(4, "D", sets = 1))
        )
        manager.start(1)
        manager.moveToExercise(2)
        manager.onExerciseFinished() // C → D
        assertEquals("D", (manager.phase as WorkoutExecutionState.Rest).exercise.name)
        assertEquals(3, manager.snapshot.exerciseIndex)
    }

    @Test
    fun `after the last exercise in the list the first skipped one comes next`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 1), ex(3, "C", sets = 1)), finish = finish)
        manager.start(1)

        manager.moveToExercise(2)
        assertFalse(manager.isLastSetOfWorkout)
        manager.onExerciseFinished() // C — последнее в списке, но A и B не сделаны

        val rest = manager.phase as WorkoutExecutionState.Rest
        assertEquals("A", rest.exercise.name)
        assertEquals(0, manager.snapshot.exerciseIndex)
        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the next exercise resumes where it was left`() = runTest {
        val manager = managerWith(workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 3)))
        manager.start(1)
        manager.moveToExercise(1)
        manager.onExerciseFinished() // B: 1 из 3
        manager.moveToExercise(0)

        manager.onExerciseFinished() // A сделано → B, подход 2

        val rest = manager.phase as WorkoutExecutionState.Rest
        assertEquals("B", rest.exercise.name)
        assertEquals(2, rest.currentSet)
    }

    @Test
    fun `the workout ends only when every exercise is done`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 1)), finish = finish)
        manager.start(1)
        manager.moveToExercise(1)
        manager.onExerciseFinished() // B → A
        manager.skipRest()

        assertTrue(manager.isLastSetOfWorkout)
        manager.onExerciseFinished()

        assertTrue(manager.phase is WorkoutExecutionState.Finished)
        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a repeat round of the only unfinished exercise is not the end`() = runTest {
        // A сделано, B нет: повтор A на последнем подходе ведёт к B, а не к записи.
        val manager = managerWith(workoutOf(ex(1, "A", sets = 1), ex(2, "B", sets = 1)))
        manager.start(1)
        manager.onExerciseFinished()
        manager.moveToExercise(0)

        assertFalse(manager.isLastSetOfWorkout)
        manager.onExerciseFinished()

        assertEquals("B", (manager.phase as WorkoutExecutionState.Rest).exercise.name)
    }

    // --- Суперсеты: пара на сегодня, A → переход → B → отдых → A ---

    private val WorkoutSessionManager.rest: WorkoutExecutionState.Rest
        get() = phase as WorkoutExecutionState.Rest

    /** Бицепс 3 подхода (отдых 90 с) и икры (отдых 60 с), ещё одно упражнение в конце. */
    private fun supersetWorkout(calvesSets: Int = 3, bicepsSets: Int = 3) = workoutOf(
        ex(1, "Бицепс", sets = bicepsSets).copy(timeMillis = 90_000),
        ex(2, "Икры", sets = calvesSets).copy(timeMillis = 60_000),
        ex(3, "Пресс", sets = 1)
    )

    @Test
    fun `pairing keeps the current phase and position`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)

        assertTrue(manager.pairWith(1))

        assertEquals(listOf(Superset(lead = 0, second = 1)), manager.snapshot.supersets)
        assertEquals(0, manager.snapshot.exerciseIndex)
        assertEquals(1, manager.active.currentSet)
    }

    @Test
    fun `a lead set is followed by a short transition to the same set of the second`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)
        manager.pairWith(1)

        manager.onExerciseFinished()

        val rest = manager.rest
        assertTrue(rest.isTransition)
        assertEquals("Икры", rest.exercise.name)
        assertEquals(1, rest.currentSet)
        assertEquals(WorkoutSessionManager.TRANSITION_MILLIS, rest.totalRestTimeMillis)
        assertEquals(1, manager.snapshot.exerciseIndex)
    }

    @Test
    fun `a second set is followed by the longer of both rests back to the lead`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)
        manager.pairWith(1)
        manager.onExerciseFinished() // бицепс 1
        manager.skipRest()

        manager.onExerciseFinished() // икры 1

        val rest = manager.rest
        assertFalse(rest.isTransition)
        assertEquals("Бицепс", rest.exercise.name)
        assertEquals(2, rest.currentSet)
        assertEquals(90_000L, rest.totalRestTimeMillis)
        assertEquals(0, manager.snapshot.exerciseIndex)
    }

    @Test
    fun `when the lead runs out the second goes on alone with its own rest`() = runTest {
        val manager = managerWith(supersetWorkout(calvesSets = 3, bicepsSets = 1))
        manager.start(1)
        manager.pairWith(1)
        manager.onExerciseFinished() // бицепс 1 — бицепс сделан
        assertTrue(manager.rest.isTransition)
        manager.skipRest()

        manager.onExerciseFinished() // икры 1

        val rest = manager.rest
        assertFalse(rest.isTransition)
        assertEquals("Икры", rest.exercise.name)
        assertEquals(2, rest.currentSet)
        assertEquals(60_000L, rest.totalRestTimeMillis)
    }

    @Test
    fun `when the second runs out the lead goes on alone with its own rest`() = runTest {
        val manager = managerWith(supersetWorkout(calvesSets = 1, bicepsSets = 3))
        manager.start(1)
        manager.pairWith(1)
        manager.onExerciseFinished() // бицепс 1
        manager.skipRest()
        manager.onExerciseFinished() // икры 1 — икры сделаны → отдых → бицепс 2
        manager.skipRest()

        manager.onExerciseFinished() // бицепс 2

        val rest = manager.rest
        assertFalse(rest.isTransition)
        assertEquals("Бицепс", rest.exercise.name)
        assertEquals(3, rest.currentSet)
        assertEquals(90_000L, rest.totalRestTimeMillis)
    }

    @Test
    fun `after the pair is done the workout moves on and records every set`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = managerWith(supersetWorkout(calvesSets = 1, bicepsSets = 1), finish = finish)
        manager.start(1)
        manager.pairWith(1)

        manager.onExerciseFinished() // бицепс
        manager.skipRest()
        manager.onExerciseFinished() // икры — пара сделана
        assertEquals("Пресс", manager.rest.exercise.name)
        manager.skipRest()
        manager.onExerciseFinished()

        assertEquals(listOf("Бицепс", "Икры", "Пресс"), setsSlot.captured.map { it.exerciseName })
    }

    @Test
    fun `pairing is refused for itself, a finished or an already paired exercise`() = runTest {
        val manager = managerWith(
            workoutOf(ex(1, "A", sets = 2), ex(2, "B", sets = 1), ex(3, "C", sets = 1), ex(4, "D", sets = 1))
        )
        manager.start(1)
        manager.moveToExercise(1)
        manager.onExerciseFinished() // B сделано → отдых перед C
        manager.moveToExercise(0)

        assertFalse(manager.pairWith(0))   // само с собой
        assertFalse(manager.pairWith(1))   // B сделано
        assertTrue(manager.pairWith(2))
        manager.moveToExercise(3)
        assertFalse(manager.pairWith(2))   // C уже в паре
        assertFalse(manager.pairWith(0))   // A уже в паре
        assertEquals(listOf(Superset(lead = 0, second = 2)), manager.snapshot.supersets)
    }

    @Test
    fun `nothing is paired without a workout in progress`() = runTest {
        val manager = managerWith(supersetWorkout())

        assertFalse(manager.pairWith(1))
    }

    @Test
    fun `unpairing brings back the plain order`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)
        manager.pairWith(1)

        manager.unpair()
        manager.onExerciseFinished()

        assertTrue(manager.snapshot.supersets.isEmpty())
        val rest = manager.rest
        assertFalse(rest.isTransition)
        assertEquals("Бицепс", rest.exercise.name)
    }

    @Test
    fun `unpairing works from either exercise of the pair`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)
        manager.pairWith(1)
        manager.moveToExercise(1)

        manager.unpair()

        assertTrue(manager.snapshot.supersets.isEmpty())
    }

    @Test
    fun `pairing registers an interaction`() = runTest {
        val manager = managerWith(supersetWorkout())
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 10_000_000L
        manager.registerInteraction(now = farFuture)

        manager.pairWith(1)

        assertTrue(manager.lastInteractionAt < farFuture)
    }
}
