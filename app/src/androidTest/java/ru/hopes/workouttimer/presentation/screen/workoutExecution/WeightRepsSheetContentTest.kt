package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

/**
 * Шит правки веса открывается поверх текущего упражнения, поэтому обязан
 * стартовать с его чисел: нажатие «Готово» без единого движения барабана
 * должно вернуть ровно то, что было, а не начало списка значений.
 */
@RunWith(AndroidJUnit4::class)
class WeightRepsSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun готово_без_прокрутки_возвращает_текущие_значения() {
        var applied: Pair<Double, Int>? = null

        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Жим лёжа",
                    weight = 80.0,
                    reps = 8,
                    onApply = { newWeight, newReps -> applied = newWeight to newReps }
                )
            }
        }

        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(80.0 to 8, applied)
    }

    /**
     * Вес мог попасть в тренировку импортом или из старой версии приложения и не
     * лечь на шаг барабана. Барабан обязан встать на ближайшее значение, а не
     * уехать в начало списка, иначе «Готово» молча обнулит вес.
     */
    @Test
    fun вес_вне_шага_барабана_прилипает_к_ближайшему_значению() {
        var applied: Pair<Double, Int>? = null

        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Жим лёжа",
                    weight = 78.7,
                    reps = 8,
                    onApply = { newWeight, newReps -> applied = newWeight to newReps }
                )
            }
        }

        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(78.75 to 8, applied)
    }
}
