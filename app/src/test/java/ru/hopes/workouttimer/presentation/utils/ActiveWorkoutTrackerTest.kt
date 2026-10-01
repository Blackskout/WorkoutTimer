package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutTrackerTest {

    @Test
    fun `inactive until started`() {
        assertFalse(ActiveWorkoutTracker().isActive)
    }

    @Test
    fun `start marks active and stop by the same owner clears it`() {
        val tracker = ActiveWorkoutTracker()
        val owner = Any()
        tracker.start(owner)
        assertTrue(tracker.isActive)
        tracker.stop(owner)
        assertFalse(tracker.isActive)
    }

    @Test
    fun `stop by a stale owner keeps the newer owner active`() {
        val tracker = ActiveWorkoutTracker()
        val old = Any()
        val new = Any()
        tracker.start(old)
        tracker.start(new)
        tracker.stop(old)
        assertTrue(tracker.isActive)
    }
}
