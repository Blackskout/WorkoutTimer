package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DateFormatterTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `relativeTimeOf returns JustNow when under an hour has passed`() {
        val timestamp = now - TimeUnit.MINUTES.toMillis(30)

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.JustNow, result)
    }

    @Test
    fun `relativeTimeOf returns HoursAgo when under a day has passed`() {
        val timestamp = now - TimeUnit.HOURS.toMillis(5)

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.HoursAgo(5), result)
    }

    @Test
    fun `relativeTimeOf returns HoursAgo just under the one day boundary`() {
        val timestamp = now - (TimeUnit.HOURS.toMillis(23) + TimeUnit.MINUTES.toMillis(59))

        val result = relativeTimeOf(timestamp, now)

        assertTrue(result is RelativeTime.HoursAgo)
    }

    @Test
    fun `relativeTimeOf returns DaysAgo just over the one day boundary`() {
        val timestamp = now - (TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(1))

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.DaysAgo(1), result)
    }

    @Test
    fun `relativeTimeOf returns DaysAgo when 13 days have passed`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(13)

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.DaysAgo(13), result)
    }

    @Test
    fun `relativeTimeOf returns Absolute at exactly 14 days`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(14)

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.Absolute(timestamp), result)
    }

    @Test
    fun `relativeTimeOf returns Absolute well beyond 14 days`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(20)

        val result = relativeTimeOf(timestamp, now)

        assertEquals(RelativeTime.Absolute(timestamp), result)
    }

    @Test
    fun `isStaleWorkout returns false when under 13 days have passed`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(12)

        assertEquals(false, isStaleWorkout(timestamp, now))
    }

    @Test
    fun `isStaleWorkout returns true at exactly 13 days`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(13)

        assertEquals(true, isStaleWorkout(timestamp, now))
    }

    @Test
    fun `isStaleWorkout returns true well beyond 13 days`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(30)

        assertEquals(true, isStaleWorkout(timestamp, now))
    }

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
