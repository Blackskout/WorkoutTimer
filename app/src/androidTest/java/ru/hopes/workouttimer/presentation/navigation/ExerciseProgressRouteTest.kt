package ru.hopes.workouttimer.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseProgressRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun маршрут_прогресса_передаёт_id_записи_как_long() {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Text("Список") }
                composable(
                    route = Screen.ExerciseProgress.route,
                    arguments = Screen.ExerciseProgress.arguments
                ) { entry ->
                    Text("id ${Screen.ExerciseProgress.getCatalogId(entry.arguments)}")
                }
            }
        }

        composeRule.runOnIdle { nav.navigate(Screen.ExerciseProgress.createRoute(4_000_000_000L)) }

        composeRule.onNodeWithText("id 4000000000").assertIsDisplayed()
    }
}
