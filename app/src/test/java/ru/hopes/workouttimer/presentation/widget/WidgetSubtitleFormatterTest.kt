package ru.hopes.workouttimer.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSubtitleFormatterTest {

    @Test
    fun `shows exercise count and the last session duration`() {
        assertEquals(
            "7 упр. · 52 мин",
            formatWidgetSubtitle(exerciseCount = 7, lastUseAt = 1_000L, lastDurationMillis = 3_120_000L)
        )
    }

    @Test
    fun `shows hours for long sessions`() {
        assertEquals(
            "5 упр. · 1 ч 5 мин",
            formatWidgetSubtitle(exerciseCount = 5, lastUseAt = 1_000L, lastDurationMillis = 3_900_000L)
        )
    }

    @Test
    fun `says the workout was never done when there is no session`() {
        assertEquals(
            "5 упр. · ещё не делали",
            formatWidgetSubtitle(exerciseCount = 5, lastUseAt = 0L, lastDurationMillis = null)
        )
    }

    @Test
    fun `handles a workout without exercises`() {
        assertEquals(
            "0 упр. · ещё не делали",
            formatWidgetSubtitle(exerciseCount = 0, lastUseAt = 0L, lastDurationMillis = null)
        )
    }

    @Test
    fun `never done wins even if a stray duration is present`() {
        // lastUseAt == 0L is the single source of truth for "never done" — a duration
        // should not be able to override it.
        assertEquals(
            "5 упр. · ещё не делали",
            formatWidgetSubtitle(exerciseCount = 5, lastUseAt = 0L, lastDurationMillis = 3_120_000L)
        )
    }

    @Test
    fun `does not claim never done for a workout performed weeks ago with no recorded duration`() {
        // Pins the bug: a workout done long before session recording existed has no
        // duration, but lastUseAt is non-zero, so it must NOT say "ещё не делали".
        val twentySevenDaysAgo = 1_000L
        assertEquals(
            "8 упр.",
            formatWidgetSubtitle(exerciseCount = 8, lastUseAt = twentySevenDaysAgo, lastDurationMillis = null)
        )
    }

    @Test
    fun `shows only the exercise count when done but no session duration is recorded`() {
        assertEquals(
            "1 упр.",
            formatWidgetSubtitle(exerciseCount = 1, lastUseAt = 42L, lastDurationMillis = null)
        )
    }
}
