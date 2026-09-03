package ru.hopes.workouttimer.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSubtitleFormatterTest {

    @Test
    fun `shows exercise count and the last session duration`() {
        assertEquals("7 упр. · 52 мин", formatWidgetSubtitle(exerciseCount = 7, lastDurationMillis = 3_120_000L))
    }

    @Test
    fun `shows hours for long sessions`() {
        assertEquals("5 упр. · 1 ч 5 мин", formatWidgetSubtitle(exerciseCount = 5, lastDurationMillis = 3_900_000L))
    }

    @Test
    fun `says the workout was never done when there is no session`() {
        assertEquals("5 упр. · ещё не делали", formatWidgetSubtitle(exerciseCount = 5, lastDurationMillis = null))
    }

    @Test
    fun `handles a workout without exercises`() {
        assertEquals("0 упр. · ещё не делали", formatWidgetSubtitle(exerciseCount = 0, lastDurationMillis = null))
    }
}
