package ru.hopes.workouttimer.presentation.screen.workoutExecution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionBackTest {

    @Test
    fun `обычное сворачивание отдаётся навигации — с анимацией жеста`() {
        assertFalse(interceptsBack(isLive = true, isFinishing = false, hasScreenBelow = true))
    }

    @Test
    fun `во время записи завершения назад поглощается`() {
        assertTrue(interceptsBack(isLive = true, isFinishing = true, hasScreenBelow = true))
    }

    @Test
    fun `под экраном пусто — перехватываем, чтобы открыть список`() {
        assertTrue(interceptsBack(isLive = true, isFinishing = false, hasScreenBelow = false))
    }

    @Test
    fun `тренировка не идёт — назад обычный`() {
        assertFalse(interceptsBack(isLive = false, isFinishing = false, hasScreenBelow = false))
    }
}
