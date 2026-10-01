package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExerciseProgressTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private fun at(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    private fun midnight(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, dayOfMonth, 0, 0)
        }.timeInMillis

    private fun kg(id: Long, sessionId: Long, weight: Double, reps: Int) =
        SessionSet(id, sessionId, 1L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun plate(id: Long, sessionId: Long, plate: Double, extra: Double, reps: Int) =
        SessionSet(id, sessionId, 1L, plate, extra, reps, ExerciseUnit.PLATE)

    private fun bodyweight(id: Long, sessionId: Long, reps: Int) =
        SessionSet(id, sessionId, 1L, 0.0, 0.0, reps, ExerciseUnit.BODYWEIGHT)

    private fun session(id: Long, finishedAt: Long, vararg sets: SessionSet) =
        ProgressSession(id, finishedAt, sets.toList())

    @Test
    fun `уровень плиты с добавкой не дотягивает до следующей плиты`() {
        assertEquals(5.0, progressLevel(plate(1, 1, 5.0, 0.0, 12)), 0.0)
        assertEquals(5.0 + 2.0 / 11, progressLevel(plate(1, 1, 5.0, 2.0, 12)), 1e-9)
        val maxExtra = progressLevel(plate(1, 1, 5.0, 10.0, 12))
        assertTrue(maxExtra < 6.0)
        assertTrue(maxExtra > progressLevel(plate(1, 1, 5.0, 9.5, 12)))
    }

    @Test
    fun `уровень кг — вес, без веса — повторы`() {
        assertEquals(62.5, progressLevel(kg(1, 1, 62.5, 6)), 0.0)
        assertEquals(12.0, progressLevel(bodyweight(1, 1, 12)), 0.0)
    }

    @Test
    fun `подходы собираются в сессии по времени, внутри — в порядке записи`() {
        val logged = listOf(
            LoggedSet(kg(5, 20, 70.0, 5), "Присед", 2_000L),
            LoggedSet(kg(1, 10, 60.0, 8), "Присед", 1_000L),
            LoggedSet(kg(2, 10, 62.5, 6), "Присед", 1_000L)
        )

        val sessions = groupProgressSessions(logged)

        assertEquals(listOf(10L, 20L), sessions.map { it.sessionId })
        assertEquals(listOf(1_000L, 2_000L), sessions.map { it.finishedAt })
        assertEquals(listOf(60.0, 62.5), sessions[0].sets.map { it.weight })
    }

    @Test
    fun `точка сессии — лучший подход в текущей единице, сессии в других единицах пропускаются`() {
        val sessions = listOf(
            session(1, 1_000, kg(1, 1, 40.0, 12)),
            session(2, 2_000, plate(2, 2, 5.0, 2.0, 12), plate(3, 2, 5.0, 2.5, 10), plate(4, 2, 4.0, 10.0, 15))
        )

        val points = progressPoints(sessions, ExerciseUnit.PLATE)

        assertEquals(listOf(2L), points.map { it.sessionId })
        assertEquals(plate(3, 2, 5.0, 2.5, 10), points.single().best)
        assertEquals(5.0 + 2.5 / 11, points.single().level, 1e-9)
    }

    @Test
    fun `начало периода считается календарными месяцами`() {
        // 31 марта минус месяц — 28 февраля, а не 3 марта.
        assertEquals(midnight(2026, 2, 28), periodStart(ProgressPeriod.MONTH, at(2026, 3, 31), utc))
        assertEquals(midnight(2026, 7, 1), periodStart(ProgressPeriod.QUARTER, at(2026, 10, 1), utc))
        assertNull(periodStart(ProgressPeriod.ALL, at(2026, 10, 1), utc))
    }

    @Test
    fun `начало периода — полночь, утренняя тренировка первого дня входит`() {
        val start = periodStart(ProgressPeriod.MONTH, at(2026, 10, 1) + 3 * 3_600_000L, utc)!!

        assertEquals(midnight(2026, 9, 1), start)
        assertTrue(at(2026, 9, 1) - 2 * 3_600_000L >= start)
    }

    @Test
    fun `изменение в кг — по весу, при том же весе — по повторам`() {
        assertEquals(ProgressDelta.Kg(2.5), progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 62.5, 6), ExerciseUnit.KG))
        assertEquals(ProgressDelta.Reps(2), progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 60.0, 10), ExerciseUnit.KG))
        assertEquals(ProgressDelta.NoChange, progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 60.0, 8), ExerciseUnit.KG))
    }

    @Test
    fun `изменение плиты — в плитах, при той же плите — в кг добавки, затем в повторах`() {
        assertEquals(
            ProgressDelta.Plates(1),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 6.0, 0.0, 10), ExerciseUnit.PLATE)
        )
        assertEquals(
            ProgressDelta.Kg(2.0),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 5.0, 4.0, 12), ExerciseUnit.PLATE)
        )
        assertEquals(
            ProgressDelta.Reps(2),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 5.0, 2.0, 14), ExerciseUnit.PLATE)
        )
    }

    @Test
    fun `изменение без веса — в повторах, может быть отрицательным`() {
        assertEquals(
            ProgressDelta.Reps(-2),
            progressDelta(bodyweight(1, 1, 10), bodyweight(2, 2, 8), ExerciseUnit.BODYWEIGHT)
        )
    }

    @Test
    fun `сводка — лучший за всё время, точки и изменение только за период`() {
        val now = at(2026, 10, 1)
        val sessions = listOf(
            session(1, now - 200 * day, kg(1, 1, 70.0, 3)),
            session(2, now - 60 * day, kg(2, 2, 60.0, 8)),
            session(3, now - 10 * day, kg(3, 3, 62.5, 6))
        )

        val quarter = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.QUARTER, now, utc)
        assertEquals(70.0, quarter.best!!.weight, 0.0)
        assertEquals(listOf(2L, 3L), quarter.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(2.5), quarter.delta)

        val month = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.MONTH, now, utc)
        assertEquals(listOf(3L), month.points.map { it.sessionId })
        assertNull(month.delta)
        assertTrue(month.hasPoints)

        val all = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.ALL, now, utc)
        assertEquals(listOf(1L, 2L, 3L), all.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(-7.5), all.delta)
    }

    @Test
    fun `после смены единицы старые подходы не попадают ни в главную цифру, ни в график`() {
        val now = at(2026, 10, 1)
        val sessions = listOf(session(1, now - 5 * day, kg(1, 1, 40.0, 12)))

        val summary = summarizeProgress(sessions, ExerciseUnit.PLATE, ProgressPeriod.QUARTER, now, utc)

        assertNull(summary.best)
        assertFalse(summary.hasPoints)
        assertTrue(summary.points.isEmpty())
        assertNull(summary.delta)
        assertTrue(summary.hasOtherUnits)
    }

    @Test
    fun `без подходов в других единицах пометки нет`() {
        val now = at(2026, 10, 1)

        val summary = summarizeProgress(
            listOf(session(1, now, kg(1, 1, 60.0, 8))), ExerciseUnit.KG, ProgressPeriod.ALL, now, utc
        )

        assertFalse(summary.hasOtherUnits)
    }
}
