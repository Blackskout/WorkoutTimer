package ru.hopes.workouttimer.presentation.navigation

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.screen.creation.CreateWorkoutScreen
import ru.hopes.workouttimer.presentation.screen.exercises.ExerciseCatalogScreen
import ru.hopes.workouttimer.presentation.screen.exportImport.ExportImportScreen
import ru.hopes.workouttimer.presentation.screen.progress.ExerciseProgressScreen
import ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionScreen
import ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryScreen
import ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutScreen
import ru.hopes.workouttimer.presentation.session.MiniWorkoutBar
import ru.hopes.workouttimer.presentation.session.MiniWorkoutBarViewModel

private const val BAR_ANIMATION_MILLIS = 180

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavGraph(
    newIntent: Intent? = null,
    onIntentHandled: () -> Unit = {},
    returnToSessionRequested: Boolean = false,
    onReturnHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Область — активность: плашка одна на все экраны.
    val miniBar: MiniWorkoutBarViewModel = hiltViewModel()
    // Корень читает только видимость: тики отдыха его и NavHost не перерисовывают.
    val sessionShown by miniBar.visible.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onExecution = backStackEntry?.destination?.route == Screen.Execution.route
    // isImeVisible работает только в edge-to-edge окне (API 35+). На Android 7–14 Compose
    // клавиатуру не видит, и плашка остаётся внизу окна, как FAB списка, — решение спеки.
    val barVisible = sessionShown && !onExecution && !WindowInsets.isImeVisible
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // Возврат в тренировку — один путь для плашки, просмотра, списка, виджета и уведомления.
    val returnToSession: () -> Unit = {
        miniBar.prepareReturn()?.let { navController.returnToSession(it) }
    }

    // Виджет шлёт интент только с FLAG_ACTIVITY_NEW_TASK. Холодный старт без идущей сессии
    // разбирает стартовый интент в setGraph() (см. NavHost ниже). Если задача жива и сессии
    // нет, интент приходит сюда через MainActivity.onNewIntent: handleDeepLink() с NEW_TASK
    // без CLEAR_TASK сам перезапускает задачу со стеком «список → выполнение». При идущей
    // сессии MainActivity диплинк не отдаёт, а просит вернуться в тренировку (ниже).
    // Стартовый интент сюда не попадает — NavController уже обработал его сам, а публичный
    // handleDeepLink() флагом deepLinkHandled не защищён.
    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            navController.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }

    // Виджет или уведомление при идущей сессии — тот же возврат, что тап по плашке.
    LaunchedEffect(returnToSessionRequested) {
        if (returnToSessionRequested) {
            returnToSession()
            onReturnHandled()
        }
    }

    // Плашка — под NavHost по раскладке: она не перекрывает экраны, FAB и снекбары
    // оказываются выше неё сами. Отступ системной навигации берёт на себя плашка, а экраны
    // его не добавляют повторно (их Scaffold вычитает поглощённые отступы).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Workouts.route,
            modifier = Modifier
                .weight(1f)
                .then(if (barVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            // Экран списка тренировок
            composable(Screen.Workouts.route) {
                ListWorkoutScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel(),
                    onAddWorkoutClick = {
                        navController.navigate(Screen.CreateWorkout.route)
                    },
                    // До экрана просмотра: без сессии тап по строке запускает тренировку, как
                    // раньше; при идущей — возвращает в неё, а не начинает вторую.
                    onOpen = { workout ->
                        val runningId = miniBar.prepareReturn()
                        if (runningId != null) {
                            navController.returnToSession(runningId)
                        } else {
                            navController.navigate(Screen.Execution.createRoute(workout.id))
                        }
                    },
                    onStart = { workout ->
                        navController.navigate(Screen.Execution.createRoute(workout.id))
                    },
                    onReturn = returnToSession,
                    onEditClick = { workout ->
                        navController.navigate(Screen.EditWorkout.createRoute(workout.id))
                    },
                    onExportImportClick = {
                        navController.navigate(Screen.ExportImport.route)
                    },
                    onExercisesClick = { navController.navigate(Screen.Exercises.route) },
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
                route = Screen.Execution.route,
                arguments = listOf(
                    navArgument("workout_id") { type = NavType.IntType }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = Screen.Execution.DEEP_LINK_PATTERN }
                )
            ) { entry ->
                WorkoutExecutionScreen(
                    workoutId = Screen.Execution.getWorkoutId(entry.arguments),
                    onMinimize = { navController.minimizeExecution() },
                    onLeave = { navController.popBackStack() },
                    viewModel = hiltViewModel()
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
                    },
                    onExerciseClick = { catalogId ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(catalogId))
                    }
                )
            }

            // Экран «Упражнения» — справочник
            composable(Screen.Exercises.route) {
                ExerciseCatalogScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenProgress = { exercise ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(exercise.id))
                    }
                )
            }

            // Экран прогресса упражнения — из «Упражнений» и из развёрнутой сессии истории
            composable(
                route = Screen.ExerciseProgress.route,
                arguments = Screen.ExerciseProgress.arguments
            ) { entry ->
                ExerciseProgressScreen(
                    catalogId = Screen.ExerciseProgress.getCatalogId(entry.arguments),
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically(tween(BAR_ANIMATION_MILLIS)) { it } +
                expandVertically(tween(BAR_ANIMATION_MILLIS)),
            exit = slideOutVertically(tween(BAR_ANIMATION_MILLIS)) { it } +
                shrinkVertically(tween(BAR_ANIMATION_MILLIS))
        ) {
            MiniWorkoutBar(
                viewModel = miniBar,
                onOpen = returnToSession,
                onExit = { showExitDialog = true },
                // Фон до отступа: плашка заливает и полосу под системной навигацией.
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            )
        }
    }

    // Тот же диалог, что в меню ⋮ экрана выполнения. «Выйти» — сброс без записи; текущий
    // экран остаётся, плашка уезжает.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.execution_exit_title)) },
            text = { Text(stringResource(R.string.execution_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    miniBar.abandon()
                }) { Text(stringResource(R.string.common_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

internal sealed class Screen(val route: String) {
    data object Workouts : Screen("workouts")
    data object CreateWorkout : Screen("create_workout")
    data object ExportImport : Screen("export_import")
    data object Exercises : Screen("exercises")
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

    data object ExerciseProgress : Screen("exercise_progress/{catalogId}") {
        private const val CATALOG_ID = "catalogId"

        // Long, а не Int: id справочника — Long, и getLong по аргументу IntType вернул бы 0.
        // Геттер, а не поле: JVM-тест маршрута не должен собирать аргументы навигации.
        val arguments: List<NamedNavArgument>
            get() = listOf(navArgument(CATALOG_ID) { type = NavType.LongType })

        fun createRoute(catalogId: Long): String = "exercise_progress/$catalogId"

        fun getCatalogId(arguments: Bundle?): Long = arguments?.getLong(CATALOG_ID) ?: 0L
    }
}
