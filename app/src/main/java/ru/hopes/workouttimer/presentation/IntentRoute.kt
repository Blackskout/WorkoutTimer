package ru.hopes.workouttimer.presentation

import ru.hopes.workouttimer.presentation.navigation.Screen

/** Что сделать с интентом виджета или уведомления. */
sealed interface IntentRoute {
    /** Виджет без идущей сессии: разобрать диплинк и начать тренировку, как раньше. */
    data object OpenDeepLink : IntentRoute

    /** Сессия идёт: открыть её экран выполнения, диплинк не разбирать. */
    data object ReturnToSession : IntentRoute

    /** Уведомление без сессии, запуск с иконки, чужие данные — навигацию не трогать. */
    data object Ignore : IntentRoute
}

private val EXECUTION_DEEP_LINK_PREFIX = Screen.Execution.DEEP_LINK_PATTERN.substringBefore("{")

/**
 * На вход — строки, а не Intent: в JVM-тестах Intent и Uri — заглушки, возвращающие null.
 * При идущей сессии виджет любой тренировки возвращает в идущую (спека, решение 6).
 */
fun resolveIntent(action: String?, dataString: String?, isRunning: Boolean): IntentRoute {
    val fromNotification = action == MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
    val fromWidget = dataString?.startsWith(EXECUTION_DEEP_LINK_PREFIX) == true
    return when {
        !fromNotification && !fromWidget -> IntentRoute.Ignore
        isRunning -> IntentRoute.ReturnToSession
        fromWidget -> IntentRoute.OpenDeepLink
        else -> IntentRoute.Ignore
    }
}
