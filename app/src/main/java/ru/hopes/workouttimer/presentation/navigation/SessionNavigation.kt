package ru.hopes.workouttimer.presentation.navigation

import androidx.navigation.NavController

/**
 * Возврат в идущую тренировку. Запись выполнения в стеке либо на вершине, либо её нет:
 * с экрана выполнения никуда не переходят, а сворачивание её снимает. launchSingleTop
 * страхует от двойного тапа — второй записи не будет. Стек — прежний плюс выполнение
 * сверху, и «Назад» снова сворачивает на экран, с которого вернулись.
 */
internal fun NavController.returnToSession(workoutId: Int) {
    navigate(Screen.Execution.createRoute(workoutId)) { launchSingleTop = true }
}

/**
 * Свернуть экран выполнения. Под ним штатно всегда что-то есть (диплинк строит стек
 * «список → выполнение»); если нет — открывается список: popBackStack() единственной
 * записи оставил бы NavHost пустым.
 */
internal fun NavController.minimizeExecution() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Screen.Workouts.route) {
            popUpTo(Screen.Execution.route) { inclusive = true }
        }
    }
}
