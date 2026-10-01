package ru.hopes.workouttimer.presentation.screen.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)

    private fun showRows(vararg rows: CatalogSummary, onMenu: (CatalogExercise) -> Unit = {}) =
        composeRule.setContent {
            WorkoutTimerTheme {
                CatalogContent(state = ExerciseCatalogState(rows = rows.toList(), isLoaded = true), onMenu = onMenu)
            }
        }

    @Test
    fun строка_показывает_последний_лучший_подход_с_давностью() {
        val doneAt = System.currentTimeMillis() - 3 * 86_400_000L - 3_600_000L
        showRows(CatalogSummary(squat, SessionSet(1L, 1L, 1L, 60.0, 0.0, 8, ExerciseUnit.KG), doneAt))

        composeRule.onNodeWithText("60 кг × 8 · 3 дн. назад").assertIsDisplayed()
    }

    @Test
    fun лучший_подход_без_веса_подписан_повторениями() {
        val pullUps = CatalogExercise(2L, "Подтягивания", ExerciseUnit.BODYWEIGHT)
        showRows(
            CatalogSummary(pullUps, SessionSet(1L, 1L, 2L, 0.0, 0.0, 8, ExerciseUnit.BODYWEIGHT), System.currentTimeMillis())
        )

        composeRule.onNodeWithText("8 повт. · сегодня").assertIsDisplayed()
    }

    @Test
    fun упражнение_без_подходов_подписано_ещё_не_делали() {
        showRows(CatalogSummary(squat, null, null))

        composeRule.onNodeWithText("ещё не делали").assertIsDisplayed()
    }

    @Test
    fun меню_строки_открывается_кнопкой() {
        var menuFor: CatalogExercise? = null
        showRows(CatalogSummary(squat, null, null), onMenu = { menuFor = it })

        composeRule.onNodeWithContentDescription("Действия с упражнением").performClick()

        assertEquals(squat, menuFor)
    }

    @Test
    fun занятое_название_показывает_ошибку_в_диалоге() {
        composeRule.setContent {
            WorkoutTimerTheme {
                RenameDialog(initialName = "Присед", nameTaken = true, onNameEdited = {}, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Такое упражнение уже есть").assertIsDisplayed()
    }

    @Test
    fun пустое_название_не_сохраняется() {
        composeRule.setContent {
            WorkoutTimerTheme {
                RenameDialog(initialName = "", nameTaken = false, onNameEdited = {}, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Сохранить").assertIsNotEnabled()
    }

    @Test
    fun выбор_единицы_передаёт_её_наружу() {
        var picked: ExerciseUnit? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                UnitDialog(current = ExerciseUnit.KG, onSelect = { picked = it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("плита").performClick()

        assertEquals(ExerciseUnit.PLATE, picked)
    }
}
