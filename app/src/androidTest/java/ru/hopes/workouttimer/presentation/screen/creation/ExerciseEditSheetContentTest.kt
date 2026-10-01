package ru.hopes.workouttimer.presentation.screen.creation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ExerciseEditSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val item = ExerciseItem(id = 1, name = "при", weight = 60.0, sets = 4, reps = 8, restTimeSeconds = 120)

    private fun show(
        unit: ExerciseUnit,
        suggestions: List<CatalogExercise> = emptyList(),
        onChange: (ExerciseItem) -> Unit = {}
    ) = composeRule.setContent {
        WorkoutTimerTheme {
            ExerciseEditSheetContent(
                item = item,
                unit = unit,
                suggestions = suggestions,
                onChange = onChange,
                onDelete = {},
                onDone = {}
            )
        }
    }

    @Test
    fun подсказка_подставляет_название_записи() {
        var changed: ExerciseItem? = null
        show(
            unit = ExerciseUnit.KG,
            suggestions = listOf(
                CatalogExercise(1, "Присед", ExerciseUnit.KG),
                CatalogExercise(2, "Присед сумо", ExerciseUnit.PLATE)
            ),
            onChange = { changed = it }
        )

        composeRule.onNodeWithText("Присед сумо").assertIsDisplayed()
        composeRule.onNodeWithText("Присед").performClick()

        assertEquals("Присед", changed?.name)
    }

    @Test
    fun у_плиты_два_барабана_нагрузки() {
        show(unit = ExerciseUnit.PLATE)

        composeRule.onNodeWithText("ПЛИТА").assertIsDisplayed()
        composeRule.onNodeWithText("+КГ").assertIsDisplayed()
        composeRule.onNodeWithText("КГ").assertDoesNotExist()
    }

    @Test
    fun без_веса_нет_барабана_нагрузки() {
        show(unit = ExerciseUnit.BODYWEIGHT)

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("ПЛИТА").assertDoesNotExist()
        composeRule.onNodeWithText("ПОВТ").assertIsDisplayed()
    }
}
