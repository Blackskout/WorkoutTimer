package ru.hopes.workouttimer.presentation.screen.progress

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.usecase.ObserveExerciseHistoryUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val day = 86_400_000L
    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)

    private fun session(id: Long, daysAgo: Int, weight: Double, reps: Int) = ProgressSession(
        sessionId = id,
        finishedAt = System.currentTimeMillis() - daysAgo * day,
        sets = listOf(SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG))
    )

    // 200, 60 и 10 дней назад: в «1 мес» одна точка, в «3 мес» две, во «всё» три — в любом поясе.
    private val sessions = listOf(session(1, 200, 70.0, 3), session(2, 60, 60.0, 8), session(3, 10, 62.5, 6))

    private fun useCase(flow: Flow<ExerciseHistory?>): ObserveExerciseHistoryUseCase {
        val mock = mockk<ObserveExerciseHistoryUseCase>()
        every { mock(1L) } returns flow
        return mock
    }

    private fun loaded(flow: Flow<ExerciseHistory?> = flowOf(ExerciseHistory(squat, sessions))): ExerciseProgressViewModel {
        val vm = ExerciseProgressViewModel(useCase(flow))
        vm.load(1L)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `по умолчанию период 3 мес — две точки, изменение и лучший за всё время`() = runTest(dispatcher) {
        val state = loaded().state.value

        assertTrue(state.isLoaded)
        assertEquals(squat, state.exercise)
        assertEquals(ProgressPeriod.QUARTER, state.period)
        assertEquals(listOf(2L, 3L), state.summary!!.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(2.5), state.summary!!.delta)
        assertEquals(70.0, state.summary!!.best!!.weight, 0.0)
    }

    @Test
    fun `смена периода пересчитывает точки и снимает выбор, ушедший из периода`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectPoint(2L)
        assertEquals(2L, vm.state.value.selectedSessionId)

        vm.selectPeriod(ProgressPeriod.MONTH)

        assertEquals(ProgressPeriod.MONTH, vm.state.value.period)
        assertEquals(listOf(3L), vm.state.value.summary!!.points.map { it.sessionId })
        assertNull(vm.state.value.selectedSessionId)
    }

    @Test
    fun `выбор остаётся, если точка есть и в новом периоде`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectPoint(3L)

        vm.selectPeriod(ProgressPeriod.ALL)

        assertEquals(3L, vm.state.value.selectedSessionId)
        assertEquals(3, vm.state.value.summary!!.points.size)
    }

    @Test
    fun `повторный тап по точке и тап мимо снимают выбор`() = runTest(dispatcher) {
        val vm = loaded()

        vm.selectPoint(3L)
        vm.selectPoint(3L)
        assertNull(vm.state.value.selectedSessionId)

        vm.selectPoint(2L)
        vm.selectPoint(null)
        assertNull(vm.state.value.selectedSessionId)
    }

    @Test
    fun `несуществующая запись — загружено, упражнения нет`() = runTest(dispatcher) {
        val state = loaded(flowOf(null)).state.value

        assertTrue(state.isLoaded)
        assertFalse(state.loadFailed)
        assertNull(state.exercise)
        assertNull(state.summary)
    }

    @Test
    fun `сбой чтения — состояние ошибки, а не падение`() = runTest(dispatcher) {
        val state = loaded(flow { throw IllegalStateException("база недоступна") }).state.value

        assertTrue(state.isLoaded)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `повторный load того же упражнения не подписывается второй раз`() = runTest(dispatcher) {
        val observe = useCase(flowOf(ExerciseHistory(squat, sessions)))
        val vm = ExerciseProgressViewModel(observe)

        vm.load(1L)
        vm.load(1L)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { observe(1L) }
    }
}
