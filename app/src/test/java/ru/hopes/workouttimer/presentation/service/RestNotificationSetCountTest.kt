package ru.hopes.workouttimer.presentation.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RestNotificationSetCountTest {
    @Test
    fun `next set counts as remaining`() {
        assertEquals(2, remainingRestSets(nextSet = 3, totalSets = 4))
        assertEquals(1, remainingRestSets(nextSet = 4, totalSets = 4))
    }

    @Test
    fun `rest before the first set of the next exercise shows its full set count`() {
        assertEquals(4, remainingRestSets(nextSet = 1, totalSets = 4))
    }

    @Test
    fun `remaining sets never becomes negative`() {
        assertEquals(0, remainingRestSets(nextSet = 5, totalSets = 4))
        assertEquals(0, remainingRestSets(nextSet = 1, totalSets = 0))
    }
}
