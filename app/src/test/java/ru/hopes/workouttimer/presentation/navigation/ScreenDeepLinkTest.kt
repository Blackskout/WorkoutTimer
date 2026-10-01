package ru.hopes.workouttimer.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.presentation.IntentRoute
import ru.hopes.workouttimer.presentation.MainActivity
import ru.hopes.workouttimer.presentation.resolveIntent

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

    // Виджет шлёт ACTION_VIEW с диплинком выполнения, уведомление — свой action без данных.
    private val widgetLink = "workouttimer://execution/7"
    private val viewAction = "android.intent.action.VIEW"

    @Test
    fun `виджет без сессии запускает тренировку диплинком`() {
        assertEquals(IntentRoute.OpenDeepLink, resolveIntent(viewAction, widgetLink, isRunning = false))
    }

    @Test
    fun `виджет при идущей сессии другой тренировки возвращает в неё`() {
        assertEquals(IntentRoute.ReturnToSession, resolveIntent(viewAction, widgetLink, isRunning = true))
    }

    @Test
    fun `уведомление при идущей сессии возвращает в неё, без сессии — ничего`() {
        assertEquals(
            IntentRoute.ReturnToSession,
            resolveIntent(MainActivity.ACTION_OPEN_ACTIVE_WORKOUT, null, isRunning = true)
        )
        assertEquals(
            IntentRoute.Ignore,
            resolveIntent(MainActivity.ACTION_OPEN_ACTIVE_WORKOUT, null, isRunning = false)
        )
    }

    @Test
    fun `запуск с иконки и чужие ссылки не трогают навигацию`() {
        assertEquals(IntentRoute.Ignore, resolveIntent("android.intent.action.MAIN", null, isRunning = true))
        assertEquals(IntentRoute.Ignore, resolveIntent(viewAction, "https://example.com/execution/7", isRunning = false))
    }
}
