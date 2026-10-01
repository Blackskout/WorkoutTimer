package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLoadTest {

    @Test
    fun `плита округляется до ближайшего номера и зажимается в 1-30`() {
        assertEquals(5.0, fitLoadToUnit(4.6, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(3.0, fitLoadToUnit(2.5, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(1.0, fitLoadToUnit(0.0, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(30.0, fitLoadToUnit(60.0, 0.0, ExerciseUnit.PLATE).weight, 0.0)
    }

    @Test
    fun `добавка к плите ложится на шаг 0_5 и не выходит из 0-10`() {
        assertEquals(2.5, fitLoadToUnit(5.0, 2.3, ExerciseUnit.PLATE).extraWeight, 0.0)
        assertEquals(10.0, fitLoadToUnit(5.0, 12.0, ExerciseUnit.PLATE).extraWeight, 0.0)
        assertEquals(0.0, fitLoadToUnit(5.0, -1.0, ExerciseUnit.PLATE).extraWeight, 0.0)
    }

    @Test
    fun `у кг добавки нет, вес сохраняется`() {
        assertEquals(Load(62.5, 0.0), fitLoadToUnit(62.5, 2.5, ExerciseUnit.KG))
    }

    @Test
    fun `без веса нагрузка всегда нулевая`() {
        assertEquals(Load(0.0, 0.0), fitLoadToUnit(40.0, 2.0, ExerciseUnit.BODYWEIGHT))
    }

    @Test
    fun `смена на плиту округляет вес и обнуляет добавку`() {
        assertEquals(Load(30.0, 0.0), convertLoadToUnit(60.0, 0.0, ExerciseUnit.PLATE))
        assertEquals(Load(5.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.PLATE))
    }

    @Test
    fun `смена на кг сохраняет число веса`() {
        assertEquals(Load(5.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.KG))
    }

    @Test
    fun `смена на без веса обнуляет всё`() {
        assertEquals(Load(0.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.BODYWEIGHT))
    }

    @Test
    fun `единица читается по имени, неизвестная — как кг`() {
        assertEquals(ExerciseUnit.PLATE, exerciseUnitOf("PLATE"))
        assertEquals(ExerciseUnit.BODYWEIGHT, exerciseUnitOf("BODYWEIGHT"))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf(null))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf("LBS"))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf("plate"))
    }
}
