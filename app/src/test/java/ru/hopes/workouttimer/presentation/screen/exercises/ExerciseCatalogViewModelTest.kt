package ru.hopes.workouttimer.presentation.screen.exercises

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.usecase.ChangeCatalogUnitUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogSummariesUseCase
import ru.hopes.workouttimer.domain.usecase.RenameCatalogExerciseUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCatalogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)
    private val bench = CatalogExercise(2L, "Жим лёжа", ExerciseUnit.KG)
    private val untitled = CatalogExercise(3L, "Без названия", ExerciseUnit.KG)

    private fun viewModel(
        entries: List<CatalogExercise> = listOf(untitled, bench, squat),
        rename: RenameCatalogExerciseUseCase = mockk(relaxed = true),
        changeUnit: ChangeCatalogUnitUseCase = mockk(relaxed = true)
    ): ExerciseCatalogViewModel {
        val summaries = mockk<ObserveCatalogSummariesUseCase>()
        every { summaries() } returns flowOf(entries.map { CatalogSummary(it, lastBest = null, lastDoneAt = null) })
        return ExerciseCatalogViewModel(summaries, rename, changeUnit)
    }

    @Test
    fun `пустой запрос показывает весь справочник, а не одну запись Без названия`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateQuery(" \u00A0 ")
        testScheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.rows.size)
        assertEquals(" \u00A0 ", vm.state.value.query)
    }

    @Test
    fun `поиск не зависит от регистра и ё`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateQuery("ЛЕЖА")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(bench), vm.state.value.rows.map { it.exercise })
    }

    @Test
    fun `пустой справочник отличается от пустого результата поиска`() = runTest(dispatcher) {
        val empty = viewModel(entries = emptyList())
        testScheduler.advanceUntilIdle()
        assertTrue(empty.state.value.isLoaded)
        assertTrue(empty.state.value.isCatalogEmpty)

        val searched = viewModel()
        searched.updateQuery("становая")
        testScheduler.advanceUntilIdle()
        assertFalse(searched.state.value.isCatalogEmpty)
        assertEquals(emptyList<CatalogSummary>(), searched.state.value.rows)
    }

    @Test
    fun `занятое название оставляет диалог открытым с ошибкой`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(1L, "жим лёжа") } returns RenameResult.NAME_TAKEN
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("жим лёжа")
        testScheduler.advanceUntilIdle()

        assertEquals(squat, vm.state.value.renameTarget)
        assertTrue(vm.state.value.renameTaken)

        vm.clearRenameTaken()
        assertFalse(vm.state.value.renameTaken)
    }

    @Test
    fun `успешное переименование закрывает диалог`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(1L, "Присед со штангой") } returns RenameResult.RENAMED
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("Присед со штангой")
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.renameTarget)
        coVerify(exactly = 1) { rename(1L, "Присед со штангой") }
    }

    @Test
    fun `сбой переименования — снекбар и закрытый диалог`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(any(), any()) } throws IllegalStateException("db")
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("Присед со штангой")
        testScheduler.advanceUntilIdle()

        assertEquals(R.string.catalog_error_rename, vm.state.value.errorMessage)
        assertNull(vm.state.value.renameTarget)

        vm.dismissError()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `сбой смены единицы — снекбар`() = runTest(dispatcher) {
        val changeUnit = mockk<ChangeCatalogUnitUseCase>()
        coEvery { changeUnit(any(), any()) } throws IllegalStateException("db")
        val vm = viewModel(changeUnit = changeUnit)

        vm.changeUnit(squat, ExerciseUnit.PLATE)
        testScheduler.advanceUntilIdle()

        assertEquals(R.string.catalog_error_unit, vm.state.value.errorMessage)
    }

    @Test
    fun `выбор текущей единицы ничего не пишет`() = runTest(dispatcher) {
        val changeUnit = mockk<ChangeCatalogUnitUseCase>(relaxed = true)
        val vm = viewModel(changeUnit = changeUnit)

        vm.changeUnit(squat, ExerciseUnit.KG)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { changeUnit(any(), any()) }
    }
}
