package ru.hopes.workouttimer.presentation.screen.workouts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsWithExerciseUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.SkipWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.UndoSkipWorkoutUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ListWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun workoutWith(id: Int, name: String, lastUseAt: Long) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = listOf(
            ExerciseEntity(
                id = id,
                workoutId = id.toLong(),
                name = "Упражнение",
                weight = 10.0,
                sets = 3,
                reps = 12,
                restTimeMillis = 120_000L,
                orderInWorkout = 1,
                note = ""
            )
        )
    )

    private fun viewModel(
        workouts: List<WorkoutWithExercises> = emptyList(),
        searchResults: List<WorkoutWithExercises> = workouts,
        durations: Map<Int, Long> = emptyMap(),
        skip: SkipWorkoutUseCase = mockk(relaxed = true),
        undoSkip: UndoSkipWorkoutUseCase = mockk(relaxed = true),
        delete: DeleteWorkoutUseCase = mockk(relaxed = true)
    ): ListWorkoutViewModel {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val search = mockk<SearchWorkoutsUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flowOf(workouts)
        every { search(any()) } returns flowOf(searchResults)
        every { getDurations() } returns flowOf(durations)
        return ListWorkoutViewModel(
            getAll,
            search,
            delete,
            getDurations,
            skip,
            undoSkip
        )
    }

    @Test
    fun `состояние соединяет тренировки с длительностями прошлых сессий`() = runTest(dispatcher) {
        val vm = viewModel(
            workouts = listOf(workoutWith(1, "Ноги", 0L)),
            durations = mapOf(1 to 3_120_000L)
        )
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.workouts.size)
        assertEquals("Ноги", state.workouts.first().workout.name)
        assertEquals(1, state.workouts.first().exercises.size)
        assertEquals(3_120_000L, state.lastSessionDurations[1])
    }

    @Test
    fun `пропуск запоминает прежнее время для отмены`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        coEvery { skip(7) } returns 555L
        val vm = viewModel(skip = skip)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
        testScheduler.advanceUntilIdle()

        val skipped = vm.state.value.skippedWorkout
        assertEquals(7, skipped?.id)
        assertEquals("Спина", skipped?.name)
        assertEquals(555L, skipped?.previousLastUseAt)
    }

    @Test
    fun `отмена возвращает прежнее время и гасит снекбар`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        val undo = mockk<UndoSkipWorkoutUseCase>(relaxed = true)
        coEvery { skip(7) } returns 555L
        val vm = viewModel(skip = skip, undoSkip = undo)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
        testScheduler.advanceUntilIdle()
        vm.undoSkip()
        testScheduler.advanceUntilIdle()

        coVerify { undo(7, 555L) }
        assertNull(vm.state.value.skippedWorkout)
    }

    @Test
    fun `пропуск несуществующей тренировки не показывает снекбар`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        coEvery { skip(9) } returns null
        val vm = viewModel(skip = skip)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 9, name = "Нет", lastUseAt = 0L))
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.skippedWorkout)
    }

    @Test
    fun `поиск переключает состояние на результаты SearchWorkoutsUseCase`() = runTest(dispatcher) {
        val queueWorkouts = listOf(workoutWith(1, "Ноги", 0L))
        val searchWorkouts = listOf(workoutWith(2, "Спина", 100L))
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        every { getAll() } returns flowOf(queueWorkouts)
        val search = mockk<SearchWorkoutsUseCase>()
        every { search(any()) } returns flowOf(searchWorkouts)
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getDurations() } returns flowOf(emptyMap())
        val vm = ListWorkoutViewModel(
            getAll,
            search,
            mockk(relaxed = true),
            getDurations,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        testScheduler.advanceUntilIdle()

        vm.updateSearchQuery("Спина")
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Спина", state.query)
        assertTrue(state.isSearching)
        assertEquals(1, state.workouts.size)
        assertEquals(2, state.workouts.first().workout.id)
        assertEquals("Спина", state.workouts.first().workout.name)
        verify { search("Спина") }
    }

    @Test
    fun `многословный запрос с пробелом внутри доходит до SearchWorkoutsUseCase целиком`() =
        runTest(dispatcher) {
            val queueWorkouts = listOf(workoutWith(1, "Ноги", 0L))
            val searchWorkouts = listOf(workoutWith(2, "Грудь и трицепс", 100L))
            val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
            every { getAll() } returns flowOf(queueWorkouts)
            val search = mockk<SearchWorkoutsUseCase>()
            every { search(any()) } returns flowOf(searchWorkouts)
            val getDurations = mockk<GetLastSessionDurationsUseCase>()
            every { getDurations() } returns flowOf(emptyMap())
            val vm = ListWorkoutViewModel(
                getAll,
                search,
                mockk(relaxed = true),
                getDurations,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
            testScheduler.advanceUntilIdle()

            // Каждый вызов строится из ТЕКУЩЕГО state.query + новый символ — именно
            // так реальный TextField вызывает onValueChange (старое значение поля
            // плюс то, что напечатали). Если бы тест передавал готовые литералы
            // ("Грудь", "Грудь ", "Грудь и"), он бы не отличал старое поведение от
            // нового: "Грудь и".trim() == "Грудь и" и под старым, триммящим сразу,
            // кодом тоже, так как в этой строке нет ни ведущих, ни хвостовых
            // пробелов. Баг проявляется только на промежуточном шаге, где триммится
            // именно хвостовой пробел.
            vm.updateSearchQuery(vm.state.value.query + "Грудь")
            testScheduler.advanceUntilIdle()

            vm.updateSearchQuery(vm.state.value.query + " ")
            testScheduler.advanceUntilIdle()
            // Ключевая проверка: хвостовой пробел должен остаться в поле. Старый
            // updateSearchQuery { newQuery.trim() } схлопывал "Грудь " в "Грудь" —
            // здесь тест ловит баг ещё до того, как символ "и" успел бы приклеиться
            // вплотную и превратить "Грудь и" в "Грудьи".
            assertEquals("Грудь ", vm.state.value.query)

            vm.updateSearchQuery(vm.state.value.query + "и")
            testScheduler.advanceUntilIdle()

            assertEquals("Грудь и", vm.state.value.query)
            verify { search("Грудь и") }
        }

    @Test
    fun `очистка поискового запроса возвращает очередь`() = runTest(dispatcher) {
        val queueWorkouts = listOf(workoutWith(1, "Ноги", 0L))
        val searchWorkouts = listOf(workoutWith(2, "Спина", 100L))
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        every { getAll() } returns flowOf(queueWorkouts)
        val search = mockk<SearchWorkoutsUseCase>()
        every { search(any()) } returns flowOf(searchWorkouts)
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getDurations() } returns flowOf(emptyMap())
        val vm = ListWorkoutViewModel(
            getAll,
            search,
            mockk(relaxed = true),
            getDurations,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        testScheduler.advanceUntilIdle()

        vm.updateSearchQuery("Спина")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isSearching)

        vm.updateSearchQuery("")
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSearching)
        assertEquals(1, state.workouts.size)
        assertEquals(1, state.workouts.first().workout.id)
    }

    @Test
    fun `отклонение снекбара гасит его без отмены пропуска`() = runTest(dispatcher) {
        val skip = mockk<SkipWorkoutUseCase>()
        val undo = mockk<UndoSkipWorkoutUseCase>(relaxed = true)
        coEvery { skip(7) } returns 555L
        val vm = viewModel(skip = skip, undoSkip = undo)
        testScheduler.advanceUntilIdle()

        vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.skippedWorkout)

        vm.dismissSkipUndo()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.skippedWorkout)
        coVerify(exactly = 0) { undo(any(), any()) }
    }

    // Ошибки БД. Если исключение не поймать внутри viewModelScope, оно уходит
    // в дефолтный обработчик потока — на Android это падение процесса, а не
    // тихий отказ. Под старым кодом эти тесты падают самим RuntimeException:
    // runTest сообщает о непойманном исключении из чужого скоупа.

    @Test
    fun `ошибка БД при удалении показывает сообщение вместо падения`() = runTest(dispatcher) {
        val delete = mockk<DeleteWorkoutUseCase>()
        coEvery { delete(any()) } throws RuntimeException("disk I/O error")
        val vm = viewModel(delete = delete)
        testScheduler.advanceUntilIdle()

        vm.deleteWorkout(WorkoutEntity(id = 3, name = "Ноги", lastUseAt = 0L))
        testScheduler.advanceUntilIdle()

        assertEquals("Не удалось удалить тренировку", vm.state.value.errorMessage)
    }

    @Test
    fun `ошибка БД при пропуске показывает сообщение и не предлагает отмену`() =
        runTest(dispatcher) {
            val skip = mockk<SkipWorkoutUseCase>()
            coEvery { skip(any()) } throws RuntimeException("database is locked")
            val vm = viewModel(skip = skip)
            testScheduler.advanceUntilIdle()

            vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
            testScheduler.advanceUntilIdle()

            assertEquals("Не удалось пропустить тренировку", vm.state.value.errorMessage)
            // Пропуск не состоялся — отменять нечего, снекбар с «Отменить» показывать нельзя.
            assertNull(vm.state.value.skippedWorkout)
        }

    @Test
    fun `ошибка БД при отмене пропуска показывает сообщение вместо падения`() =
        runTest(dispatcher) {
            val skip = mockk<SkipWorkoutUseCase>()
            coEvery { skip(7) } returns 555L
            val undo = mockk<UndoSkipWorkoutUseCase>()
            coEvery { undo(any(), any()) } throws RuntimeException("database is locked")
            val vm = viewModel(skip = skip, undoSkip = undo)
            testScheduler.advanceUntilIdle()

            vm.skipWorkout(WorkoutEntity(id = 7, name = "Спина", lastUseAt = 555L))
            testScheduler.advanceUntilIdle()
            vm.undoSkip()
            testScheduler.advanceUntilIdle()

            assertEquals("Не удалось отменить пропуск", vm.state.value.errorMessage)
        }

    @Test
    fun `отмена корутины не превращается в сообщение об ошибке`() = runTest(dispatcher) {
        val delete = mockk<DeleteWorkoutUseCase>()
        // CancellationException — это тоже Exception. Если ловить его наравне с
        // остальными, штатное закрытие экрана показывало бы ошибку.
        coEvery { delete(any()) } throws CancellationException("экран закрыт")
        val vm = viewModel(delete = delete)
        testScheduler.advanceUntilIdle()

        vm.deleteWorkout(WorkoutEntity(id = 3, name = "Ноги", lastUseAt = 0L))
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `показанное сообщение об ошибке гасится`() = runTest(dispatcher) {
        val delete = mockk<DeleteWorkoutUseCase>()
        coEvery { delete(any()) } throws RuntimeException("disk I/O error")
        val vm = viewModel(delete = delete)
        testScheduler.advanceUntilIdle()

        vm.deleteWorkout(WorkoutEntity(id = 3, name = "Ноги", lastUseAt = 0L))
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.errorMessage)

        vm.dismissError()

        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `nextWorkout и restOfQueue на границах очереди`() = runTest(dispatcher) {
        val emptyVm = viewModel(workouts = emptyList())
        testScheduler.advanceUntilIdle()
        assertNull(emptyVm.state.value.nextWorkout)
        assertTrue(emptyVm.state.value.restOfQueue.isEmpty())

        val onlyWorkout = workoutWith(1, "Ноги", 0L)
        val singleVm = viewModel(workouts = listOf(onlyWorkout))
        testScheduler.advanceUntilIdle()
        assertEquals(onlyWorkout, singleVm.state.value.nextWorkout)
        assertTrue(singleVm.state.value.restOfQueue.isEmpty())
    }
}
