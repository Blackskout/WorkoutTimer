package ru.hopes.workouttimer.presentation.screen.workoutExecution

import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

/** Плитка нагрузки подписана единицей упражнения, а у упражнения без веса её нет. */
@RunWith(AndroidJUnit4::class)
class ActiveContentTilesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(unit: ExerciseUnit, weight: Double, extra: Double = 0.0) {
        val exercise = Exercise(
            id = 1, name = "Тяга блока", weight = weight, sets = 3, reps = 12, order = 1,
            unit = unit, extraWeight = extra
        )
        composeRule.setContent {
            WorkoutTimerTheme {
                ActiveContent(
                    state = WorkoutExecutionState.Active(exercise = exercise, currentSet = 1),
                    onEditNote = {},
                    onEditWeightAndReps = {}
                )
            }
        }
    }

    @Test
    fun плитка_плиты_показывает_номер_и_добавку() {
        show(ExerciseUnit.PLATE, weight = 5.0, extra = 2.0)

        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("ПЛИТА +2 КГ").assertIsDisplayed()
    }

    @Test
    fun плита_без_добавки_подписана_просто_плитой() {
        show(ExerciseUnit.PLATE, weight = 5.0)

        composeRule.onNodeWithText("ПЛИТА").assertIsDisplayed()
    }

    @Test
    fun без_веса_остаётся_только_плитка_повторов() {
        show(ExerciseUnit.BODYWEIGHT, weight = 0.0)

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("ПЛИТА").assertDoesNotExist()
        composeRule.onNodeWithText("ПОВТ").assertIsDisplayed()
    }
}
