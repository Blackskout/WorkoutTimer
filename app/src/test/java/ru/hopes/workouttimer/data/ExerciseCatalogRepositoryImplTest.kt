package ru.hopes.workouttimer.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.LoggedSetRow
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet

class ExerciseCatalogRepositoryImplTest {

    @Test
    fun `справочник отдаётся доменными записями, неизвестная единица — кг`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeCatalog() } returns flowOf(
            listOf(
                ExerciseCatalogEntity(id = 1, name = "Тяга блока", nameKey = "тяга блока", unit = "PLATE"),
                ExerciseCatalogEntity(id = 2, name = "Присед", nameKey = "присед", unit = "???")
            )
        )

        val catalog = ExerciseCatalogRepositoryImpl(dao).observeCatalog().first()

        assertEquals(
            listOf(CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE), CatalogExercise(2, "Присед", ExerciseUnit.KG)),
            catalog
        )
    }

    @Test
    fun `подходы тренировки отдаются с названием и временем сессии`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeSetsForWorkout(3L) } returns flowOf(
            listOf(LoggedSetRow(5, 9, 1, "Тяга блока", 5.0, 2.0, 12, "PLATE", 7_000L))
        )

        val sets = ExerciseCatalogRepositoryImpl(dao).observeSetsForWorkout(3).first()

        assertEquals(
            listOf(LoggedSet(SessionSet(5, 9, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), "Тяга блока", 7_000L)),
            sets
        )
    }

    @Test
    fun `запись справочника по id отдаётся доменной, отсутствующая — null`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeCatalogEntry(1L) } returns flowOf(
            ExerciseCatalogEntity(id = 1, name = "Тяга блока", nameKey = "тяга блока", unit = "PLATE")
        )
        every { dao.observeCatalogEntry(2L) } returns flowOf(null)
        val repo = ExerciseCatalogRepositoryImpl(dao)

        assertEquals(CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE), repo.observeExercise(1L).first())
        assertNull(repo.observeExercise(2L).first())
    }

    @Test
    fun `подходы упражнения отдаются с названием и временем сессии`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeSetsForCatalog(1L) } returns flowOf(
            listOf(LoggedSetRow(5, 9, 1, "Тяга блока", 5.0, 2.0, 12, "PLATE", 7_000L))
        )

        val sets = ExerciseCatalogRepositoryImpl(dao).observeSetsForExercise(1L).first()

        assertEquals(
            listOf(LoggedSet(SessionSet(5, 9, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), "Тяга блока", 7_000L)),
            sets
        )
    }
}
