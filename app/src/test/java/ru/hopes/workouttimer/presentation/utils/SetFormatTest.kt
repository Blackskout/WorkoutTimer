package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ExerciseUnit.BODYWEIGHT
import ru.hopes.workouttimer.domain.model.ExerciseUnit.KG
import ru.hopes.workouttimer.domain.model.ExerciseUnit.PLATE
import ru.hopes.workouttimer.domain.model.SessionSet
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SetFormatTest {

    // Рабочий каталог JVM-тестов — модуль app/, поэтому путь относительный.
    private val strings: Map<String, String> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private val format by lazy {
        SetFormat(
            loadKg = strings.getValue("unit_load_kg"),
            loadPlate = strings.getValue("unit_load_plate"),
            loadPlateExtra = strings.getValue("unit_load_plate_extra"),
            set = strings.getValue("unit_set")
        )
    }

    private fun set(unit: ExerciseUnit, weight: Double, reps: Int, extra: Double = 0.0) =
        SessionSet(id = 0, sessionId = 1, catalogId = 1, weight = weight, extraWeight = extra, reps = reps, unit = unit)

    @Test
    fun `кг — вес с единицей и повторы`() {
        assertEquals("60 кг × 8", formatSet(set(KG, 60.0, 8), format))
        assertEquals("62.5 кг × 6", formatSet(set(KG, 62.5, 6), format))
    }

    @Test
    fun `кг в перечислении — без единицы`() {
        assertEquals("60 × 8", formatSet(set(KG, 60.0, 8), format, bareKg = true))
    }

    @Test
    fun `плита — номер, добавка только если есть`() {
        assertEquals("плита 5 × 12", formatSet(set(PLATE, 5.0, 12), format))
        assertEquals("плита 5 +2 кг × 12", formatSet(set(PLATE, 5.0, 12, extra = 2.0), format))
        assertEquals("плита 5 +2.5 кг × 12", formatSet(set(PLATE, 5.0, 12, extra = 2.5), format))
    }

    @Test
    fun `плита пишется полностью и в перечислении`() {
        assertEquals("плита 5 × 12", formatSet(set(PLATE, 5.0, 12), format, bareKg = true))
    }

    @Test
    fun `без веса — только повторы`() {
        assertEquals("12", formatSet(set(BODYWEIGHT, 0.0, 12), format))
    }

    @Test
    fun `перечисление подходов — первый полностью, следующие кг без единицы`() {
        assertEquals(
            "60 кг × 8 · 60 × 8 · 62.5 × 6",
            formatSetList(listOf(set(KG, 60.0, 8), set(KG, 60.0, 8), set(KG, 62.5, 6)), format)
        )
    }

    @Test
    fun `перечисление плиты и без веса`() {
        assertEquals(
            "плита 5 × 12 · плита 5 +2 кг × 10",
            formatSetList(listOf(set(PLATE, 5.0, 12), set(PLATE, 5.0, 10, extra = 2.0)), format)
        )
        assertEquals("12 · 10", formatSetList(listOf(set(BODYWEIGHT, 0.0, 12), set(BODYWEIGHT, 0.0, 10)), format))
    }

    @Test
    fun `нагрузка без повторов`() {
        assertEquals("60 кг", formatLoad(KG, 60.0, 0.0, format))
        assertEquals("плита 5 +2 кг", formatLoad(PLATE, 5.0, 2.0, format))
        assertNull(formatLoad(BODYWEIGHT, 0.0, 0.0, format))
    }
}
