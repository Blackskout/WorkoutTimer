package ru.hopes.workouttimer.presentation.screen.workoutPreview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveWorkoutUseCase
import ru.hopes.workouttimer.presentation.session.AddResult
import ru.hopes.workouttimer.presentation.session.RunningWorkout
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPreviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val press = Exercise(id = 5, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, order = 1, catalogId = 50L)
    private val back = Workout(id = 2, name = "Спина", exercises = listOf(press), lastUseAt = 0L)

    private val workout = MutableStateFlow<Workout?>(back)
    private val lastSets = MutableStateFlow<List<LoggedSet>>(emptyList())
    private val durations = MutableStateFlow<Map<Int, Long>>(mapOf(2 to 3_120_000L))
    private val running = MutableStateFlow<RunningWorkout?>(null)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.runningWorkout } returns running
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(observe: ObserveWorkoutUseCase = mockk { every { this@mockk(2) } returns workout }): WorkoutPreviewViewModel {
        val catalog = mockk<ExerciseCatalogRepository> { every { observeLastSessionSets() } returns lastSets }
        val getDurations = mockk<GetLastSessionDurationsUseCase> { every { this@mockk() } returns durations }
        return WorkoutPreviewViewModel(observe, catalog, getDurations, manager).also { it.load(2) }
    }

    private fun kg(sessionId: Long, catalogId: Long, weight: Double, reps: Int) =
        SessionSet(id = 0, sessionId = sessionId, catalogId = catalogId, weight = weight, extraWeight = 0.0, reps = reps, unit = ExerciseUnit.KG)

    @Test
    fun `без сессии — начать`() {
        val state = viewModel().state.value

        assertTrue(state.isLoaded)
        assertEquals(PreviewBottom.Start, state.bottom)
        assertFalse(state.canAddToday)
        assertEquals(3_120_000L, state.lastDurationMillis)
    }

    @Test
    fun `идёт эта тренировка — вернуться`() {
        running.value = RunningWorkout(2, "Спина", isLoading = false, addedExerciseIds = emptySet())

        assertEquals(PreviewBottom.Return, viewModel().state.value.bottom)
    }

    @Test
    fun `идёт эта тренировка — сделанное сегодня по упражнениям`() {
        running.value = RunningWorkout(2, "Спина", isLoading = false, addedExerciseIds = emptySet(), doneSets = mapOf(5 to 2))

        assertEquals(mapOf(5 to 2), viewModel().state.value.todayDone)
    }

    @Test
    fun `без сессии или при другой тренировке сегодняшнего прогресса нет`() {
        assertNull(viewModel().state.value.todayDone)

        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet(), doneSets = mapOf(5 to 2))
        assertNull(viewModel().state.value.todayDone)
    }

    @Test
    fun `идёт другая тренировка — подсказка и добавление`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet())

        val state = viewModel().state.value

        assertEquals(PreviewBottom.RunningOther("Ноги"), state.bottom)
        assertTrue(state.canAddToday)
    }

    @Test
    fun `пока сессия загружается — ничего`() {
        running.value = RunningWorkout(1, "", isLoading = true, addedExerciseIds = emptySet())

        assertEquals(PreviewBottom.None, viewModel().state.value.bottom)
    }

    @Test
    fun `сессия закончилась — снова начать`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet())
        val vm = viewModel()

        running.value = null // Finished, Error и выход дают null

        assertEquals(PreviewBottom.Start, vm.state.value.bottom)
    }

    @Test
    fun `пустую тренировку не начать`() {
        workout.value = back.copy(exercises = emptyList())

        assertEquals(PreviewBottom.None, viewModel().state.value.bottom)
    }

    @Test
    fun `удалённая тренировка — не найдена`() {
        workout.value = null

        val state = viewModel().state.value

        assertTrue(state.isLoaded)
        assertNull(state.workout)
        assertEquals(PreviewBottom.None, state.bottom)
    }

    @Test
    fun `новый состав после правки виден сразу`() {
        val vm = viewModel()

        workout.value = back.copy(exercises = listOf(press, press.copy(id = 6, name = "Разводка")))

        assertEquals(listOf("Жим лёжа", "Разводка"), vm.state.value.workout?.exercises?.map { it.name })
    }

    @Test
    fun `прошлый раз — по записи справочника`() {
        lastSets.value = listOf(
            LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 60.0, reps = 8), "Жим лёжа", finishedAt = 1_000L),
            LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 62.5, reps = 6), "Жим лёжа", finishedAt = 1_000L)
        )

        val lastTime = viewModel().state.value.lastTime[50L]

        assertEquals(1_000L, lastTime?.finishedAt)
        assertEquals(listOf(60.0, 62.5), lastTime?.sets?.map { it.weight })
    }

    @Test
    fun `прошлый раз — подходы самой поздней сессии`() {
        val grouped = lastTimeByCatalog(
            listOf(
                LoggedSet(kg(sessionId = 3, catalogId = 50, weight = 55.0, reps = 8), "Жим лёжа", finishedAt = 500L),
                LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 60.0, reps = 8), "Жим лёжа", finishedAt = 1_000L),
                LoggedSet(kg(sessionId = 7, catalogId = 51, weight = 20.0, reps = 12), "Разводка", finishedAt = 1_000L)
            )
        )

        assertEquals(LastTime(1_000L, listOf(kg(7, 50, 60.0, 8))), grouped[50L])
        assertEquals(listOf(20.0), grouped[51L]?.sets?.map { it.weight })
    }

    @Test
    fun `добавленное упражнение отмечено`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = setOf(5))

        assertEquals(setOf(5), viewModel().state.value.addedExerciseIds)
    }

    @Test
    fun `добавление идёт в менеджер`() {
        every { manager.addExerciseToday(press) } returns AddResult.ADDED

        assertEquals(AddResult.ADDED, viewModel().addToday(press))
        verify { manager.addExerciseToday(press) }
    }

    @Test
    fun `сбой чтения — сообщение, а не падение`() {
        val failing = mockk<ObserveWorkoutUseCase> {
            every { this@mockk(2) } returns flow { throw IllegalStateException("disk I/O error") }
        }

        val state = viewModel(observe = failing).state.value

        assertTrue(state.isLoaded)
        assertTrue(state.loadFailed)
    }
}
