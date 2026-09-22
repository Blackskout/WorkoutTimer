package ru.hopes.workouttimer.presentation.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelPickerTest {

    private val weights = generateSequence(0.0) { it + 0.25 }
        .takeWhile { it <= 300.0 }
        .toList()

    @Test
    fun `точное значение находит само себя`() {
        assertEquals(80.0, weights[wheelIndexOfNearest(weights, 80.0)], 0.0001)
    }

    @Test
    fun `значение вне сетки округляется к ближайшему`() {
        assertEquals(47.25, weights[wheelIndexOfNearest(weights, 47.3)], 0.0001)
    }

    @Test
    fun `значение ниже диапазона даёт первый элемент`() {
        assertEquals(0, wheelIndexOfNearest(weights, -10.0))
    }

    @Test
    fun `значение выше диапазона даёт последний элемент`() {
        assertEquals(weights.lastIndex, wheelIndexOfNearest(weights, 999.0))
    }

    @Test
    fun `пустой список даёт нулевой индекс`() {
        assertEquals(0, wheelIndexOfNearest(emptyList(), 5.0))
    }
}
