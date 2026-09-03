package ru.hopes.workouttimer.presentation.navigation

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ru.hopes.workouttimer.presentation.screen.creation.CreateWorkoutScreen
import ru.hopes.workouttimer.presentation.screen.exportImport.ExportImportScreen
import ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionScreen
import ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryScreen
import ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutScreen

@Composable
fun NavGraph(
    newIntent: Intent? = null,
    onIntentHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Виджет шлёт интент с FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK, а это всегда
    // поднимает НОВЫЙ экземпляр MainActivity: CLEAR_TASK сносит задачу целиком, и после этого
    // singleTop нечего переиспользовать. Поэтому тап по виджету на практике всегда обрабатывает
    // разбор стартового интента в setGraph() (см. NavHost ниже), а не этот LaunchedEffect.
    // Путь через onNewIntent()/newIntent остаётся защитой на случай, если флаги когда-нибудь
    // поменяют и активити начнёт переиспользоваться — не убирайте его в расчёте, что он мёртвый.
    // Если это когда-нибудь всё же сработает: стартовый интент туда не попадает — NavController
    // уже обработал его сам, а публичный handleDeepLink() флагом deepLinkHandled не защищён и
    // отработал бы второй раз.
    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            navController.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }

    // NavHost — это "Карта сайта"
    NavHost(
        navController = navController,
        startDestination = Screen.Workouts.route // Стартовая страница
    ) {
        // Экран списка тренировок
        composable(Screen.Workouts.route) {
            ListWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(),
                onAddWorkoutClick = {
                    navController.navigate(Screen.CreateWorkout.route)
                },
                onLongClick = {
                    // TODO
                },
                // КОГДА КЛИКНУЛИ:
                // Мы собираем ссылку вручную: "execution_screen/5"
                onWorkoutClick = { workout ->
                    navController.navigate(Screen.Execution.createRoute(workout.id))
                },
                onEditClick = { workout ->
                    navController.navigate(Screen.EditWorkout.createRoute(workout.id))
                },
                onExportImportClick = {
                    navController.navigate(Screen.ExportImport.route)
                },
                onHistoryClick = { workout ->
                    navController.navigate(Screen.History.createRoute(workout.id))
                }
            )
        }

        // Экран создания тренировки
        composable(Screen.CreateWorkout.route) {
            CreateWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(),
                onFinished = {
                    navController.popBackStack()
                }
            )
        }

        // Экран редактирования тренировки
        composable(
            route = Screen.EditWorkout.route,
            arguments = listOf(
                navArgument("workout_id") { type = NavType.IntType }
            )
        ) { entry ->
            val workoutId = Screen.EditWorkout.getWorkoutId(entry.arguments)
            CreateWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(),
                onFinished = {
                    navController.popBackStack()
                },
                workoutId = workoutId
            )
        }

        // Экран выполнения тренировки
        composable(
            route = Screen.Execution.route, // Это строка "execution/{workout_id}"
            arguments = listOf(
                navArgument("workout_id") { type = NavType.IntType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = Screen.Execution.DEEP_LINK_PATTERN }
            )
        ) { entry ->
            val workoutId = Screen.Execution.getWorkoutId(entry.arguments)
            WorkoutExecutionScreen(
                viewModel = hiltViewModel(),
                onExerciseCompleted = {
                    navController.popBackStack()
                },
                workoutId = workoutId
            )
        }

        // Экран экспорта/импорта
        composable(Screen.ExportImport.route) {
            ExportImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Экран истории сессий тренировки
        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument("workout_id") { type = NavType.IntType }
            )
        ) { entry ->
            val workoutId = Screen.History.getWorkoutId(entry.arguments)
            WorkoutHistoryScreen(
                viewModel = hiltViewModel(),
                workoutId = workoutId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

internal sealed class Screen(val route: String) {
    data object Workouts : Screen("workouts")
    data object CreateWorkout : Screen("create_workout")
    data object ExportImport : Screen("export_import")
    data object EditWorkout : Screen("edit_workout/{workout_id}") {
        fun createRoute(workoutId: Int): String {
            return "edit_workout/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }

    // ВАЖНО: Маршрут должен содержать placeholder {workout_id}
    data object Execution : Screen("execution/{workout_id}") {

        const val DEEP_LINK_PATTERN = "workouttimer://execution/{workout_id}"

        // ВАЖНО: Формируем ссылку, которая совпадает с названием экрана (было "edit_note", стало "execution")
        fun createRoute(workoutId: Int): String {
            return "execution/$workoutId"
        }

        // Ссылка для виджета: строится из DEEP_LINK_PATTERN, а не дублирует его строкой —
        // иначе схема окажется захардкожена в двух местах, и расхождение не поймает ни
        // компилятор, ни тест, только тап по виджету на устройстве.
        fun createDeepLink(workoutId: Int): String =
            DEEP_LINK_PATTERN.replace("{workout_id}", workoutId.toString())

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }

    data object History : Screen("history/{workout_id}") {
        fun createRoute(workoutId: Int): String {
            return "history/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }
}