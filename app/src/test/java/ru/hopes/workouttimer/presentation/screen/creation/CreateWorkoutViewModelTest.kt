package ru.hopes.workouttimer.presentation.screen.creation

import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun catalogOf(entries: List<CatalogExercise>) =
        mockk<ObserveCatalogUseCase>().also { every { it() } returns flowOf(entries) }

    private fun viewModel(
        add: AddWorkoutUseCase = mockk(relaxed = true),
        get: GetWorkoutByIdUseCase = mockk(relaxed = true),
        update: UpdateWorkoutUseCase = mockk(relaxed = true),
        catalog: List<CatalogExercise> = emptyList()
    ) = CreateWorkoutViewModel(add, get, update, catalogOf(catalog))

    private val catalog = listOf(
        CatalogExercise(1, "Присед", ExerciseUnit.KG),
        CatalogExercise(2, "Фронтальный присед", ExerciseUnit.KG),
        CatalogExercise(3, "Присед сумо", ExerciseUnit.PLATE),
        CatalogExercise(4, "Жим лёжа", ExerciseUnit.KG),
        CatalogExercise(5, "Без названия", ExerciseUnit.KG)
    )

    /** Добавляет упражнение с названием [name] и возвращает его из состояния. */
    private fun CreateWorkoutViewModel.exerciseNamed(name: String): ExerciseItem {
        processCommand(CreateWorkoutCommand.AddExercise())
        val item = state.value.exercises.last().copy(name = name)
        processCommand(CreateWorkoutCommand.UpdateExercise(item.id, item))
        return item
    }

    @Test
    fun `новая тренировка сохраняется с нулевым lastUseAt`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>(relaxed = true)
        val vm = viewModel(add = add)

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val exerciseId = vm.state.value.exercises.first().id
        vm.processCommand(
            CreateWorkoutCommand.UpdateExercise(
                exerciseId,
                vm.state.value.exercises.first().copy(name = "Жим ног")
            )
        )
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { add(capture(saved)) }
        assertEquals(0L, saved.captured.lastUseAt)
    }

    @Test
    fun `перестановка меняет порядок упражнений в состоянии`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 2))

        assertEquals(
            listOf(ids[1], ids[2], ids[0]),
            vm.state.value.exercises.map { it.id }
        )
    }

    @Test
    fun `перестановка вверх работает симметрично`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 2, to = 0))

        assertEquals(
            listOf(ids[2], ids[0], ids[1]),
            vm.state.value.exercises.map { it.id }
        )
    }

    @Test
    fun `перестановка вне диапазона не ломает список`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val ids = vm.state.value.exercises.map { it.id }

        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 5))

        assertEquals(ids, vm.state.value.exercises.map { it.id })
    }

    @Test
    fun `после перестановки order сохраняется непрерывным от единицы`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>(relaxed = true)
        val vm = viewModel(add = add)
        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        repeat(3) { vm.processCommand(CreateWorkoutCommand.AddExercise()) }
        vm.state.value.exercises.forEachIndexed { index, item ->
            vm.processCommand(
                CreateWorkoutCommand.UpdateExercise(item.id, item.copy(name = "Упр $index"))
            )
        }
        vm.processCommand(CreateWorkoutCommand.MoveExercise(from = 0, to = 2))
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { add(capture(saved)) }
        assertEquals(listOf(1, 2, 3), saved.captured.exercises.map { it.order })
        assertEquals("Упр 1", saved.captured.exercises.first().name)
    }

    @Test
    fun `лист нового упражнения закрыт без названия — упражнения нет и выход без вопроса`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val id = vm.state.value.exercises.last().id

        vm.processCommand(CreateWorkoutCommand.CloseExercise(id))

        assertTrue(vm.state.value.exercises.isEmpty())
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `лист закрыт с названием — упражнение остаётся`() = runTest(dispatcher) {
        val vm = viewModel()
        val item = vm.exerciseNamed("Присед")

        vm.processCommand(CreateWorkoutCommand.CloseExercise(item.id))

        assertEquals(listOf("Присед"), vm.state.value.exercises.map { it.name })
    }

    @Test
    fun `свежезагруженное состояние не считается изменённым`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(false, vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `изменение имени помечает состояние как несохранённое`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))

        assertEquals(true, vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `сохранение изменений загруженной тренировки идёт через update, а не add`() = runTest(dispatcher) {
        val get = mockk<GetWorkoutByIdUseCase>(relaxed = true)
        val update = mockk<UpdateWorkoutUseCase>(relaxed = true)
        val existing = Workout(
            id = 7,
            name = "Ноги",
            exercises = listOf(
                ru.hopes.workouttimer.domain.model.Exercise(
                    id = 1,
                    name = "Присед",
                    weight = 60.0,
                    sets = 4,
                    reps = 10,
                    timeMillis = 90_000L,
                    order = 1
                )
            ),
            lastUseAt = 12345L
        )
        coEvery { get(7) } returns existing
        val vm = viewModel(get = get, update = update)

        vm.loadWorkout(7)
        testScheduler.advanceUntilIdle()
        assertEquals(false, vm.state.value.hasUnsavedChanges)

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги (изменено)"))
        assertEquals(true, vm.state.value.hasUnsavedChanges)

        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { update(capture(saved)) }
        assertEquals(7, saved.captured.id)
        assertEquals(12345L, saved.captured.lastUseAt)
        assertEquals("Ноги (изменено)", saved.captured.name)
    }

    @Test
    fun `сбой сохранения оставляет редактор открытым и сообщает об ошибке`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>()
        coEvery { add(any()) } throws IllegalStateException("db")
        val vm = viewModel(add = add)

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val exerciseId = vm.state.value.exercises.first().id
        vm.processCommand(
            CreateWorkoutCommand.UpdateExercise(
                exerciseId,
                vm.state.value.exercises.first().copy(name = "Присед")
            )
        )
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isFinished)
        assertTrue(vm.state.value.saveFailed)

        vm.processCommand(CreateWorkoutCommand.DismissSaveError)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `подсказки не зависят от регистра и ищут по вхождению, начало — первым`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("ПРИС")

        assertEquals(
            listOf("Присед", "Присед сумо", "Фронтальный присед"),
            vm.state.value.suggestionsFor(item).map { it.name }
        )
    }

    @Test
    fun `ё и е в запросе не различаются`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("жим леж")

        assertEquals(listOf("Жим лёжа"), vm.state.value.suggestionsFor(item).map { it.name })
    }

    @Test
    fun `подсказок не больше пяти`() = runTest(dispatcher) {
        val many = (1..7).map { CatalogExercise(it.toLong(), "Тяга $it", ExerciseUnit.KG) }
        val vm = viewModel(catalog = many)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("тяга")

        assertEquals(listOf("Тяга 1", "Тяга 2", "Тяга 3", "Тяга 4", "Тяга 5"), vm.state.value.suggestionsFor(item).map { it.name })
    }

    @Test
    fun `точное совпадение с записью скрывает подсказки`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed(" присед ")

        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(item))
    }

    @Test
    fun `пустое поле не подсказывает даже при записи Без названия`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(vm.exerciseNamed("")))
        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(vm.exerciseNamed(" \u00A0 ")))
    }

    @Test
    fun `единица упражнения берётся из справочника по названию`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        assertEquals(ExerciseUnit.PLATE, vm.state.value.unitOf(vm.exerciseNamed("присед  СУМО")))
        assertEquals(ExerciseUnit.KG, vm.state.value.unitOf(vm.exerciseNamed("Новое упражнение")))
    }

    @Test
    fun `загрузка тренировки не теряет справочник`() = runTest(dispatcher) {
        val get = mockk<GetWorkoutByIdUseCase>()
        coEvery { get(7) } returns Workout(
            id = 7, name = "Ноги", lastUseAt = 1L,
            exercises = listOf(Exercise(id = 1, name = "Присед сумо", weight = 5.0, sets = 3, reps = 10, order = 1))
        )
        val vm = viewModel(get = get, catalog = catalog)
        testScheduler.advanceUntilIdle()

        vm.loadWorkout(7)
        testScheduler.advanceUntilIdle()

        assertEquals(catalog, vm.state.value.catalog)
        assertEquals(ExerciseUnit.PLATE, vm.state.value.unitOf(vm.state.value.exercises.single()))
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `добавка к плите переживает открытие и сохранение тренировки`() = runTest(dispatcher) {
        val get = mockk<GetWorkoutByIdUseCase>()
        coEvery { get(7) } returns Workout(
            id = 7, name = "Спина", lastUseAt = 1L,
            exercises = listOf(
                Exercise(id = 1, name = "Присед сумо", weight = 5.0, sets = 3, reps = 10, order = 1, extraWeight = 2.5)
            )
        )
        val update = mockk<UpdateWorkoutUseCase>(relaxed = true)
        val vm = viewModel(get = get, update = update, catalog = catalog)
        vm.loadWorkout(7)
        testScheduler.advanceUntilIdle()

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Спина Б"))
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { update(capture(saved)) }
        assertEquals(2.5, saved.captured.exercises.single().extraWeight, 0.0)
    }
}
