package ru.hopes.workouttimer.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun showGraph(start: String = Screen.Workouts.route) {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = start) {
                composable(Screen.Workouts.route) { Text("Список") }
                composable(
                    route = Screen.History.route,
                    arguments = listOf(navArgument("workout_id") { type = NavType.IntType })
                ) { Text("Другой экран") }
                composable(
                    route = Screen.Execution.route,
                    arguments = listOf(navArgument("workout_id") { type = NavType.IntType; defaultValue = 1 })
                ) { entry -> Text("Выполнение ${Screen.Execution.getWorkoutId(entry.arguments)}") }
            }
        }
    }

    /** Маршруты стека снизу вверх, без корневого графа. */
    private fun stack(): List<String> = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun возврат_через_плашку_кладёт_одну_запись_выполнения() {
        showGraph()
        composeRule.runOnIdle {
            nav.navigate(Screen.Execution.createRoute(1))
            nav.minimizeExecution()
            nav.navigate(Screen.History.createRoute(2))
            nav.returnToSession(1)
            nav.returnToSession(1) // двойной тап
        }

        composeRule.onNodeWithText("Выполнение 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                listOf(Screen.Workouts.route, Screen.History.route, Screen.Execution.route),
                stack()
            )
        }
    }

    @Test
    fun назад_после_возврата_ведёт_на_экран_под_выполнением() {
        showGraph()
        composeRule.runOnIdle {
            nav.navigate(Screen.Execution.createRoute(1))
            nav.minimizeExecution()
            nav.navigate(Screen.History.createRoute(2))
            nav.returnToSession(1)
            nav.minimizeExecution()
        }

        composeRule.onNodeWithText("Другой экран").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(Screen.Workouts.route, Screen.History.route), stack())
        }
    }

    @Test
    fun сворачивание_единственного_выполнения_открывает_список() {
        showGraph(start = Screen.Execution.route)
        composeRule.runOnIdle { nav.minimizeExecution() }

        composeRule.onNodeWithText("Список").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(Screen.Workouts.route), stack()) }
    }
}
