package ru.hopes.workouttimer.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSubtitleFormatterTest {

    @Test
    fun `shows exercise count and the last session duration`() {
        assertEquals(
            WidgetSubtitle.Done(exerciseCount = 7, durationMillis = 3_120_000L),
            widgetSubtitleOf(exerciseCount = 7, lastUseAt = 1_000L, lastDurationMillis = 3_120_000L)
        )
    }

    @Test
    fun `says the workout was never done when there is no session`() {
        assertEquals(
            WidgetSubtitle.NeverDone(exerciseCount = 5),
            widgetSubtitleOf(exerciseCount = 5, lastUseAt = 0L, lastDurationMillis = null)
        )
    }

    @Test
    fun `handles a workout without exercises`() {
        assertEquals(
            WidgetSubtitle.NeverDone(exerciseCount = 0),
            widgetSubtitleOf(exerciseCount = 0, lastUseAt = 0L, lastDurationMillis = null)
        )
    }

    @Test
    fun `never done wins even if a stray duration is present`() {
        // lastUseAt == 0L is the single source of truth for "never done" — a duration
        // should not be able to override it.
        assertEquals(
            WidgetSubtitle.NeverDone(exerciseCount = 5),
            widgetSubtitleOf(exerciseCount = 5, lastUseAt = 0L, lastDurationMillis = 3_120_000L)
        )
    }

    @Test
    fun `does not claim never done for a workout performed weeks ago with no recorded duration`() {
        // Pins the bug: a workout done long before session recording existed has no
        // duration, but lastUseAt is non-zero, so it must NOT say "ещё не делали".
        val twentySevenDaysAgo = 1_000L
        assertEquals(
            WidgetSubtitle.Done(exerciseCount = 8, durationMillis = null),
            widgetSubtitleOf(exerciseCount = 8, lastUseAt = twentySevenDaysAgo, lastDurationMillis = null)
        )
    }
}
