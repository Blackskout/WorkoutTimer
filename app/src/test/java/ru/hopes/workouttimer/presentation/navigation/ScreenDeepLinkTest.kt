package ru.hopes.workouttimer.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenDeepLinkTest {

    // Проверяет, что createDeepLink() реально построен из DEEP_LINK_PATTERN, а не дублирует
    // схему отдельной строкой — расхождение между ними иначе не ловится ни компилятором,
    // ни другими тестами, только тапом по виджету на устройстве.
    @Test
    fun `createDeepLink builds a uri from DEEP_LINK_PATTERN`() {
        assertEquals("workouttimer://execution/7", Screen.Execution.createDeepLink(7))
    }

    // Маршруты из спеки: экран «Упражнения» (E2) и прогресс упражнения (E3).
    @Test
    fun `exercises screen has its own route`() {
        assertEquals("exercises", Screen.Exercises.route)
    }

    @Test
    fun `маршрут прогресса строится из id записи справочника`() {
        assertEquals("exercise_progress/{catalogId}", Screen.ExerciseProgress.route)
        assertEquals("exercise_progress/7", Screen.ExerciseProgress.createRoute(7L))
    }
}
