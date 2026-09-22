package ru.hopes.workouttimer.presentation.screen.creation

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
}
