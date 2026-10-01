package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.ExerciseUnit.BODYWEIGHT
import ru.hopes.workouttimer.domain.model.ExerciseUnit.KG
import ru.hopes.workouttimer.domain.model.ExerciseUnit.PLATE

class SessionSetsTest {

    private fun set(
        unit: ExerciseUnit,
        weight: Double,
        reps: Int,
        extra: Double = 0.0,
        id: Long = 0L,
        sessionId: Long = 1L,
        catalogId: Long = 1L
    ) = SessionSet(
        id = id, sessionId = sessionId, catalogId = catalogId,
        weight = weight, extraWeight = extra, reps = reps, unit = unit
    )

    private fun logged(set: SessionSet, name: String, finishedAt: Long = 1_000L) =
        LoggedSet(set = set, exerciseName = name, finishedAt = finishedAt)

    @Test
    fun `в кг больший вес важнее повторов`() {
        val heavy = set(KG, 62.5, 6)
        assertEquals(heavy, bestSet(listOf(set(KG, 60.0, 10), heavy), KG))
    }

    @Test
    fun `в кг при равном весе побеждают повторы`() {
        val more = set(KG, 60.0, 9)
        assertEquals(more, bestSet(listOf(set(KG, 60.0, 8), more), KG))
    }

    @Test
    fun `у плиты номер важнее добавки, добавка важнее повторов`() {
        val best = set(PLATE, 6.0, 6, extra = 2.0)
        val sets = listOf(
            set(PLATE, 5.0, 15, extra = 2.0),
            set(PLATE, 6.0, 12),
            best,
            set(PLATE, 6.0, 12, extra = 1.0)
        )
        assertEquals(best, bestSet(sets, PLATE))
    }

    @Test
    fun `у плиты при той же нагрузке побеждают повторы`() {
        val more = set(PLATE, 6.0, 12, extra = 2.0)
        assertEquals(more, bestSet(listOf(set(PLATE, 6.0, 10, extra = 2.0), more), PLATE))
    }

    @Test
    fun `без веса решают повторы`() {
        val more = set(BODYWEIGHT, 0.0, 15)
        assertEquals(more, bestSet(listOf(set(BODYWEIGHT, 0.0, 12), more), BODYWEIGHT))
    }

    @Test
    fun `подходы в другой единице не сравниваются`() {
        val plate = set(PLATE, 3.0, 10)
        assertEquals(plate, bestSet(listOf(set(KG, 100.0, 5), plate), PLATE))
        assertNull(bestSet(listOf(set(KG, 100.0, 5)), BODYWEIGHT))
    }

    @Test
    fun `при полном равенстве лучший — первый записанный`() {
        assertEquals(1L, bestSet(listOf(set(KG, 60.0, 8, id = 1L), set(KG, 60.0, 8, id = 2L)), KG)?.id)
    }

    @Test
    fun `группировка сохраняет порядок первого появления упражнения`() {
        val groups = groupSetsByExercise(
            listOf(
                logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Присед"),
                logged(set(KG, 20.0, 10, id = 2, catalogId = 9), "Выпады"),
                logged(set(KG, 62.5, 6, id = 3, catalogId = 7), "Присед")
            )
        )
        assertEquals(listOf("Присед", "Выпады"), groups.map { it.exerciseName })
        assertEquals(listOf(7L, 9L), groups.map { it.catalogId })
        assertEquals(listOf(1L, 3L), groups[0].sets.map { it.id })
    }

    @Test
    fun `справочник идёт по алфавиту без учёта регистра и ё`() {
        val catalog = listOf("Тяга", "жим лёжа", "Ёлочка", "Армейский жим")
            .mapIndexed { index, name -> CatalogExercise(index.toLong() + 1, name, KG) }
        assertEquals(
            listOf("Армейский жим", "Ёлочка", "жим лёжа", "Тяга"),
            summarizeCatalog(catalog, emptyList()).map { it.exercise.name }
        )
    }

    @Test
    fun `сводка берёт лучший подход последней сессии и её время`() {
        val squat = CatalogExercise(7L, "Присед", KG)
        val summary = summarizeCatalog(
            listOf(squat),
            listOf(
                logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Присед", finishedAt = 9_000L),
                logged(set(KG, 62.5, 6, id = 2, catalogId = 7), "Присед", finishedAt = 9_000L)
            )
        ).single()
        assertEquals(62.5, summary.lastBest!!.weight, 0.0)
        assertEquals(9_000L, summary.lastDoneAt)
    }

    @Test
    fun `запись без подходов — без лучшего подхода и давности`() {
        val summary = summarizeCatalog(listOf(CatalogExercise(1L, "Присед", KG)), emptyList()).single()
        assertNull(summary.lastBest)
        assertNull(summary.lastDoneAt)
    }

    @Test
    fun `лучший подход последней сессии считается в её единице, а не в текущей`() {
        // Единицу сменили на плиту, а последняя сессия была ещё в кг: строка
        // справочника честно показывает то, что было сделано, — «60 кг × 8».
        val summary = summarizeCatalog(
            listOf(CatalogExercise(7L, "Тяга", PLATE)),
            listOf(logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Тяга"))
        ).single()
        assertEquals(KG, summary.lastBest!!.unit)
    }
}
