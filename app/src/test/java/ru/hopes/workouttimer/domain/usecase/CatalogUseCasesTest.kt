package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository

class CatalogUseCasesTest {

    private fun logged(id: Long, sessionId: Long, catalogId: Long, name: String, weight: Double, reps: Int, finishedAt: Long = 1_000L) =
        LoggedSet(SessionSet(id, sessionId, catalogId, weight, 0.0, reps, ExerciseUnit.KG), name, finishedAt)

    @Test
    fun `сводка соединяет справочник с лучшим подходом последней сессии`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeCatalog() } returns flowOf(
            listOf(CatalogExercise(1L, "Присед", ExerciseUnit.KG), CatalogExercise(2L, "Жим", ExerciseUnit.KG))
        )
        every { repo.observeLastSessionSets() } returns flowOf(
            listOf(logged(1, 5, 1, "Присед", 60.0, 8, 9_000L), logged(2, 5, 1, "Присед", 62.5, 6, 9_000L))
        )

        val result = ObserveCatalogSummariesUseCase(repo)().first()

        assertEquals(listOf("Жим", "Присед"), result.map { it.exercise.name })
        assertNull(result[0].lastBest)
        assertEquals(62.5, result[1].lastBest!!.weight, 0.0)
        assertEquals(9_000L, result[1].lastDoneAt)
    }

    @Test
    fun `подходы тренировки группируются по сессиям, внутри — по упражнениям`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeSetsForWorkout(3) } returns flowOf(
            listOf(
                logged(1, 10, 1, "Присед", 60.0, 8),
                logged(2, 10, 2, "Выпады", 20.0, 10),
                logged(3, 10, 1, "Присед", 62.5, 6),
                logged(4, 11, 1, "Присед", 65.0, 5)
            )
        )

        val bySession = GetWorkoutSessionSetsUseCase(repo)(3).first()

        assertEquals(setOf(10L, 11L), bySession.keys)
        assertEquals(listOf("Присед", "Выпады"), bySession.getValue(10L).map { it.exerciseName })
        assertEquals(listOf(8, 6), bySession.getValue(10L)[0].sets.map { it.reps })
        assertEquals(listOf(5), bySession.getValue(11L).single().sets.map { it.reps })
    }

    @Test
    fun `история упражнения соединяет запись с её сессиями по времени`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeExercise(1L) } returns flowOf(CatalogExercise(1L, "Присед", ExerciseUnit.KG))
        every { repo.observeSetsForExercise(1L) } returns flowOf(
            listOf(
                logged(1, 10, 1, "Присед", 60.0, 8, 1_000L),
                logged(2, 10, 1, "Присед", 62.5, 6, 1_000L),
                logged(3, 11, 1, "Присед", 65.0, 5, 2_000L)
            )
        )

        val history = ObserveExerciseHistoryUseCase(repo)(1L).first()!!

        assertEquals("Присед", history.exercise.name)
        assertEquals(listOf(10L, 11L), history.sessions.map { it.sessionId })
        assertEquals(listOf(8, 6), history.sessions[0].sets.map { it.reps })
    }

    @Test
    fun `нет записи справочника — нет истории`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeExercise(9L) } returns flowOf(null)
        every { repo.observeSetsForExercise(9L) } returns flowOf(emptyList())

        assertNull(ObserveExerciseHistoryUseCase(repo)(9L).first())
    }
}
