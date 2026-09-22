package ru.hopes.workouttimer.presentation.screen.creation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CreateWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        add: AddWorkoutUseCase = mockk(relaxed = true),
        update: UpdateWorkoutUseCase = mockk(relaxed = true)
    ) = CreateWorkoutViewModel(add, mockk(relaxed = true), update)

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
        val vm = CreateWorkoutViewModel(mockk(relaxed = true), get, update)

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
}
