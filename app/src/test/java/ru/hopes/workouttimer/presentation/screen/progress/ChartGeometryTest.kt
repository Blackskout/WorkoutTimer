package ru.hopes.workouttimer.presentation.screen.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet

class ChartGeometryTest {

    private val day = 86_400_000L

    private fun point(sessionId: Long, finishedAt: Long, level: Double) = ProgressPoint(
        sessionId, finishedAt, SessionSet(sessionId, sessionId, 1L, level, 0.0, 8, ExerciseUnit.KG), level
    )

    @Test
    fun `ось кг — целые деления с шагом 1 вокруг значений`() {
        assertEquals(YAxis(60, 63, listOf(60, 61, 62, 63)), yAxisFor(listOf(60.0, 62.5)))
        assertEquals(YAxis(8, 12, listOf(8, 9, 10, 11, 12)), yAxisFor(listOf(8.0, 12.0)))
    }

    @Test
    fun `широкий разброс — не больше четырёх промежутков круглого шага`() {
        assertEquals(YAxis(40, 100, listOf(40, 60, 80, 100)), yAxisFor(listOf(40.0, 100.0)))
        assertEquals(YAxis(0, 30, listOf(0, 10, 20, 30)), yAxisFor(listOf(1.0, 30.0)))
    }

    @Test
    fun `ровная линия — посередине оси`() {
        assertEquals(YAxis(4, 6, listOf(4, 5, 6)), yAxisFor(listOf(5.0, 5.0)))
        assertEquals(YAxis(0, 1, listOf(0, 1)), yAxisFor(listOf(0.0)))
    }

    @Test
    fun `ровная нецелая линия — посередине оси, не у нижнего края`() {
        for (level in listOf(5.0 + 2.0 / 11, 61.25)) {
            val axis = yAxisFor(listOf(level, level))
            val fraction = levelFraction(level, axis)

            assertTrue("$axis $fraction", axis.min <= level && level <= axis.max)
            assertTrue("$axis $fraction", fraction > 0.3f && fraction < 0.7f)
        }
    }

    @Test
    fun `плита с добавкой лежит между делениями своей плиты и следующей`() {
        val level = 5.0 + 2.0 / 11
        val axis = yAxisFor(listOf(level, 5.0 + 4.0 / 11))

        assertEquals(listOf(5, 6), axis.ticks)
        val fraction = levelFraction(level, axis)
        assertTrue(fraction > 0f && fraction < 1f)
    }

    @Test
    fun `ось X — реальное время, перерыв виден`() {
        assertEquals(0f, timeFraction(0, 0, 100 * day), 0f)
        assertEquals(0.1f, timeFraction(10 * day, 0, 100 * day), 1e-6f)
        assertEquals(1f, timeFraction(100 * day, 0, 100 * day), 0f)
        assertEquals(0.5f, timeFraction(5, 5, 5), 0f)
    }

    @Test
    fun `точки ложатся в область графика, выше — больше`() {
        val axis = YAxis(60, 63, listOf(60, 61, 62, 63))
        val plot = PlotArea(left = 40f, top = 10f, right = 340f, bottom = 190f)

        val positions = pointPositions(
            listOf(point(1, 0, 60.0), point(2, 50, 63.0), point(3, 100, 61.5)), axis, plot
        )

        assertEquals(
            listOf(ChartPosition(40f, 190f), ChartPosition(190f, 10f), ChartPosition(340f, 100f)),
            positions
        )
        assertEquals(10f, tickY(63, axis, plot), 0f)
        assertEquals(190f, tickY(60, axis, plot), 0f)
    }

    @Test
    fun `тап выбирает ближайшую по X точку в пределах зоны попадания`() {
        val xs = listOf(40f, 190f, 340f)

        assertEquals(1, nearestPointIndex(xs, 200f, 30f))
        assertEquals(2, nearestPointIndex(xs, 330f, 30f))
        assertNull(nearestPointIndex(xs, 115f, 30f))
        assertNull(nearestPointIndex(emptyList(), 10f, 30f))
    }
}
