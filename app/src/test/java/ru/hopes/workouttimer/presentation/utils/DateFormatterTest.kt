package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DateFormatterTest {

    @Test
    fun `formatDurationToString shows only minutes under an hour`() {
        assertEquals("42 мин", DateFormatter.formatDurationToString(42 * 60_000L))
    }

    @Test
    fun `formatDurationToString shows hours and minutes over an hour`() {
        assertEquals("1 ч 15 мин", DateFormatter.formatDurationToString(75 * 60_000L))
    }

    @Test
    fun `formatDurationToString rounds down partial minutes`() {
        assertEquals("0 мин", DateFormatter.formatDurationToString(59_000L))
    }

    @Test
    fun `formatSessionDateTime formats date and time in ru locale`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JULY, 16, 18, 32, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val result = DateFormatter.formatSessionDateTime(calendar.timeInMillis)

        assertEquals("16 июля 2026, 18:32", result)
    }
}
