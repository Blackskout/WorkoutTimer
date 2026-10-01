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

    @Test
    fun `барабан плиты — номера с 1 по 30`() {
        assertEquals(30, PlateValues.size)
        assertEquals(1.0, PlateValues.first(), 0.0)
        assertEquals(30.0, PlateValues.last(), 0.0)
    }

    @Test
    fun `барабан добавки — от 0 до 10 с шагом 0_5`() {
        assertEquals(21, PlateExtraValues.size)
        assertEquals(0.0, PlateExtraValues.first(), 0.0)
        assertEquals(10.0, PlateExtraValues.last(), 0.0)
        assertEquals(2.5, PlateExtraValues[wheelIndexOfNearest(PlateExtraValues, 2.3)], 0.0)
    }

    // --- Вес в кг двумя барабанами: целые килограммы и доли ---

    @Test
    fun `вес раскладывается на целые и долю и собирается обратно`() {
        val wheels = weightWheelsOf(61.75)
        assertEquals(WeightWheels(wholeIndex = 61, fractionIndex = 3), wheels)
        assertEquals(61.75, weightOf(wheels), 0.0)
    }

    @Test
    fun `ноль и максимум`() {
        assertEquals(WeightWheels(0, 0), weightWheelsOf(0.0))
        assertEquals(WeightWheels(300, 0), weightWheelsOf(300.0))
        assertEquals(300.0, weightOf(WeightWheels(300, 0)), 0.0)
    }

    @Test
    fun `вес вне сетки прилипает к ближайшим 0_25`() {
        assertEquals(3.25, weightOf(weightWheelsOf(3.3)), 0.0)
        assertEquals(48.0, weightOf(weightWheelsOf(47.9)), 0.0) // перенос в целые
        assertEquals(300.0, weightOf(weightWheelsOf(999.0)), 0.0)
        assertEquals(0.0, weightOf(weightWheelsOf(-5.0)), 0.0)
    }

    @Test
    fun `доля сверх максимума не выводит вес за 300`() {
        assertEquals(300.0, weightOf(WeightWheels(300, 3)), 0.0)
    }

    @Test
    fun `барабаны веса — 0 до 300 целыми и четыре доли`() {
        assertEquals(301, WeightWholeValues.size)
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75), WeightFractionValues)
    }

    @Test
    fun `доли подписаны одинаковой ширины`() {
        assertEquals(listOf(",00", ",25", ",50", ",75"), WeightFractionValues.map(::formatWeightFraction))
    }
}
