package ru.hopes.workouttimer.presentation.screen.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.utils.SetFormat
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class ProgressFormatTest {

    // Рабочий каталог JVM-тестов — модуль app/, поэтому путь относительный.
    private val doc: Document by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
    }

    private val strings: Map<String, String> by lazy {
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private val months: List<String> by lazy {
        val arrays = doc.getElementsByTagName("string-array")
        val array = (0 until arrays.length).map { arrays.item(it) as Element }
            .single { it.getAttribute("name") == "progress_months" }
        val items = array.getElementsByTagName("item")
        (0 until items.length).map { items.item(it).textContent }
    }

    // plurals склоняет Android; здесь проверяется, что в склонение уходит модуль числа.
    private val templates by lazy {
        DeltaFormat(
            kg = strings.getValue("progress_delta_kg"),
            reps = strings.getValue("progress_delta_reps"),
            none = strings.getValue("progress_delta_none"),
            line = strings.getValue("progress_delta_line"),
            plates = { count, signed -> "$signed плит($count)" }
        )
    }

    private val setFormat by lazy {
        SetFormat(
            loadKg = strings.getValue("unit_load_kg"),
            loadPlate = strings.getValue("unit_load_plate"),
            loadPlateExtra = strings.getValue("unit_load_plate_extra"),
            set = strings.getValue("unit_set")
        )
    }

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcNoon(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    @Test
    fun `знак изменения — плюс, типографский минус или ноль`() {
        assertEquals("+2.5", signedNumber(2.5))
        assertEquals("\u22122", signedNumber(-2.0))
        assertEquals("+0.3", signedNumber(0.30000000000000004))
        assertEquals("0", signedNumber(0.0))
    }

    @Test
    fun `изменение в кг, повторах и без изменений — с периодом`() {
        assertEquals(
            "+2.5 кг за 3 мес",
            formatDelta(ProgressDelta.Kg(2.5), strings.getValue("progress_span_quarter"), templates)
        )
        assertEquals(
            "+2 повт за 1 мес",
            formatDelta(ProgressDelta.Reps(2), strings.getValue("progress_span_month"), templates)
        )
        assertEquals(
            "без изменений за всё время",
            formatDelta(ProgressDelta.NoChange, strings.getValue("progress_span_all"), templates)
        )
    }

    @Test
    fun `плиты склоняются по модулю числа, знак остаётся в тексте`() {
        assertEquals(
            "\u22122 плит(2) за 3 мес",
            formatDelta(ProgressDelta.Plates(-2), strings.getValue("progress_span_quarter"), templates)
        )
    }

    @Test
    fun `короткая дата — день и месяц из ресурсов`() {
        val template = strings.getValue("progress_short_date")

        assertEquals(12, months.size)
        assertEquals("26 сен", shortDate(utcNoon(2026, 9, 26), template, months, utc))
        assertEquals("3 мая", shortDate(utcNoon(2026, 5, 3), template, months, utc))
    }

    @Test
    fun `подход без веса подписан повторениями, с весом — общей подписью`() {
        val reps = strings.getValue("progress_reps")

        assertEquals(
            "12 повт.",
            progressSetText(SessionSet(1, 1, 1, 0.0, 0.0, 12, ExerciseUnit.BODYWEIGHT), setFormat, reps)
        )
        assertEquals(
            "плита 5 +2 кг × 12",
            progressSetText(SessionSet(1, 1, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), setFormat, reps)
        )
        assertEquals(
            "62.5 кг × 6",
            progressSetText(SessionSet(1, 1, 1, 62.5, 0.0, 6, ExerciseUnit.KG), setFormat, reps)
        )
    }
}
