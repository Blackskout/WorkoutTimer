package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.ExerciseUnit
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
                    unit = ExerciseUnit.KG,
                    weight = 80.0,
                    extraWeight = 0.0,
                    reps = 8,
                    onApply = { newWeight, _, newReps -> applied = newWeight to newReps }
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
                    unit = ExerciseUnit.KG,
                    weight = 78.7,
                    extraWeight = 0.0,
                    reps = 8,
                    onApply = { newWeight, _, newReps -> applied = newWeight to newReps }
                )
            }
        }

        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(78.75 to 8, applied)
    }

    @Test
    fun плита_с_добавкой_без_прокрутки_возвращает_текущие_значения() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Тяга блока",
                    unit = ExerciseUnit.PLATE,
                    weight = 5.0,
                    extraWeight = 2.0,
                    reps = 12,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("ПЛИТА").assertExists()
        composeRule.onNodeWithText("+КГ").assertExists()
        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(5.0, 2.0, 12), applied)
    }

    @Test
    fun плита_вне_сетки_прилипает_к_ближайшим_значениям() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Тяга блока",
                    unit = ExerciseUnit.PLATE,
                    weight = 4.6,
                    extraWeight = 2.3,
                    reps = 12,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(5.0, 2.5, 12), applied)
    }

    @Test
    fun без_веса_только_повторы_и_нулевая_нагрузка() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Подтягивания",
                    unit = ExerciseUnit.BODYWEIGHT,
                    weight = 7.0,
                    extraWeight = 0.0,
                    reps = 8,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(0.0, 0.0, 8), applied)
    }
}
