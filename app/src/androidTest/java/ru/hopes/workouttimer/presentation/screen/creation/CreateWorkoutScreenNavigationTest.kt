package ru.hopes.workouttimer.presentation.screen.creation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase

/**
 * Экран сообщал о завершении вызовом onFinished() прямо в теле composable.
 * Тело композиции выполняется столько раз, сколько Compose решит перекомпоновать
 * экран, поэтому такой вызов может повториться — а onFinished() здесь снимает
 * экран со стека навигации. Второй вызов снимает уже следующий экран.
 */
@RunWith(AndroidJUnit4::class)
class CreateWorkoutScreenNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(repo: FakeWorkoutRepository) = CreateWorkoutViewModel(
        AddWorkoutUseCase(repo),
        GetWorkoutByIdUseCase(repo),
        UpdateWorkoutUseCase(repo)
    )

    private fun CreateWorkoutViewModel.fillAndSave() {
        processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        processCommand(CreateWorkoutCommand.AddExercise())
        val exercise = state.value.exercises.first()
        processCommand(
            CreateWorkoutCommand.UpdateExercise(exercise.id, exercise.copy(name = "Присед"))
        )
        processCommand(CreateWorkoutCommand.Save)
    }

    @Test
    fun сохранение_снимает_со_стека_только_экран_создания() {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)
        lateinit var nav: NavHostController
        var finishedCalls = 0

        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Text("Список тренировок") }
                composable("create") {
                    CreateWorkoutScreen(
                        viewModel = vm,
                        onFinished = {
                            finishedCalls++
                            nav.popBackStack()
                        }
                    )
                }
            }
        }

        composeRule.runOnIdle { nav.navigate("create") }
        composeRule.waitForIdle()

        composeRule.runOnIdle { vm.fillAndSave() }
        composeRule.waitForIdle()

        assertEquals("тренировка должна сохраниться один раз", 1, repo.added.size)
        assertEquals("onFinished вызван повторно", 1, finishedCalls)
        // Второй popBackStack() снял бы и "home", оставив стек пустым.
        assertEquals("home", nav.currentBackStackEntry?.destination?.route)
    }
}
