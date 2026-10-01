package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

@RunWith(AndroidJUnit4::class)
class HistoryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = WorkoutSession(
        id = 1, workoutId = 1, startedAt = 1_759_300_000_000L - 3_000_000L,
        finishedAt = 1_759_300_000_000L, durationMillis = 3_000_000L
    )

    private fun set(id: Long, weight: Double, reps: Int) =
        SessionSet(id, 1L, 7L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun show(setsBySession: Map<Long, List<SessionExerciseSets>>?) = composeRule.setContent {
        WorkoutTimerTheme { HistoryContent(sessions = listOf(session), setsBySession = setsBySession) }
    }

    @Test
    fun тап_по_сессии_показывает_подходы_по_упражнениям() {
        show(
            mapOf(
                1L to listOf(
                    SessionExerciseSets(7L, "Присед", listOf(set(1, 60.0, 8), set(2, 60.0, 8), set(3, 62.5, 6))),
                    SessionExerciseSets(9L, "Выпады", listOf(set(4, 20.0, 10)))
                )
            )
        )
        val squat = "Присед — 60 кг × 8 · 60 × 8 · 62.5 × 6"
        composeRule.onNodeWithText(squat).assertDoesNotExist()

        composeRule.onNodeWithText(DateFormatter.formatSessionDateTime(session.finishedAt)).performClick()

        composeRule.onNodeWithText(squat).assertIsDisplayed()
        composeRule.onNodeWithText("Выпады — 20 кг × 10").assertIsDisplayed()
    }

    @Test
    fun сессия_без_подходов_подписана_и_не_разворачивается() {
        show(emptyMap())

        composeRule.onNodeWithText("подходы не записывались").assertIsDisplayed()
        // Без подходов clickable не вешается вовсе: у карточки нет ложного действия для TalkBack.
        composeRule.onNodeWithText("подходы не записывались").assertHasNoClickAction()
    }

    @Test
    fun пока_подходы_не_загрузились_нет_подписи_и_нажатия() {
        show(null)

        composeRule.onNodeWithText("подходы не записывались").assertDoesNotExist()
        composeRule.onNodeWithText(DateFormatter.formatSessionDateTime(session.finishedAt)).assertHasNoClickAction()
    }

    @Test
    fun карточка_с_подходами_сообщает_состояние_и_нажимается() {
        show(mapOf(1L to listOf(SessionExerciseSets(7L, "Присед", listOf(set(1, 60.0, 8))))))
        val card = composeRule.onNodeWithText(DateFormatter.formatSessionDateTime(session.finishedAt))

        card.assertHasClickAction()
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Свёрнуто"))
        card.performClick()
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто"))
    }
}
