package ru.hopes.workouttimer.presentation.screen.workoutExecution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseTapGuardTest {

    @Test
    fun `второе касание двойного тапа сразу после смены фазы не проходит`() {
        assertFalse(acceptsPhaseTap(phaseShownAt = 1_000L, now = 1_150L))
        assertFalse(acceptsPhaseTap(phaseShownAt = 1_000L, now = 1_000L + PHASE_TAP_GUARD_MILLIS - 1))
    }

    @Test
    fun `обычное нажатие после паузы проходит`() {
        assertTrue(acceptsPhaseTap(phaseShownAt = 1_000L, now = 1_000L + PHASE_TAP_GUARD_MILLIS))
        assertTrue(acceptsPhaseTap(phaseShownAt = 1_000L, now = 60_000L))
    }
}
