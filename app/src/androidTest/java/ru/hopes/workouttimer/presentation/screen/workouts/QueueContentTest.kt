package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class QueueContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Int>()
    private val started = mutableListOf<Int>()
    private var returned = 0

    private fun workout(id: Int, name: String) =
        WorkoutWithExercises(workout = WorkoutEntity(id = id, name = name, lastUseAt = 0L), exercises = emptyList())

    private fun show(runningWorkoutId: Int?) = composeRule.setContent {
        WorkoutTimerTheme {
            QueueContent(
                state = ListWorkoutState(
                    workouts = listOf(workout(1, "Ноги"), workout(2, "Спина")),
                    runningWorkoutId = runningWorkoutId
                ),
                onOpen = { opened += it.id },
                onStart = { started += it.id },
                onReturn = { returned++ },
                onMenuClick = {},
                onAddWorkoutClick = {}
            )
        }
    }

    @Test
    fun без_сессии_карточка_следующей_начинает_тренировку() {
        show(runningWorkoutId = null)

        composeRule.onNodeWithText("НАЧАТЬ").performClick()

        composeRule.runOnIdle { assertEquals(listOf(1), started) }
    }

    @Test
    fun идущая_тренировка_на_карточке_предлагает_вернуться() {
        show(runningWorkoutId = 1)

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, returned) }
    }

    @Test
    fun при_сессии_другой_тренировки_кнопки_на_карточке_нет() {
        show(runningWorkoutId = 2)

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ").assertDoesNotExist()
    }

    @Test
    fun тап_по_строке_очереди_открывает_тренировку() {
        show(runningWorkoutId = null)

        // Клик по самому тексту: под ним — строка, а не кнопка меню.
        composeRule.onNodeWithText("Спина", useUnmergedTree = true).performClick()

        composeRule.runOnIdle { assertEquals(listOf(2), opened) }
    }

    @Test
    fun тап_по_карточке_следующей_открывает_просмотр() {
        show(runningWorkoutId = null)

        // Клик по названию, а не по центру карточки: центр близко к кнопке «НАЧАТЬ».
        composeRule.onNodeWithText("Ноги", useUnmergedTree = true).performClick()

        composeRule.runOnIdle { assertEquals(listOf(1), opened) }
    }
}
