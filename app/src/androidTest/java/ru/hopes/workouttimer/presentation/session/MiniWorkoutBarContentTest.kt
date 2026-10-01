package ru.hopes.workouttimer.presentation.session

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class MiniWorkoutBarContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var opened = 0
    private var exited = 0

    private fun show(status: MiniBarStatus) = composeRule.setContent {
        WorkoutTimerTheme {
            MiniWorkoutBarContent(
                state = MiniBarState("Ноги", status),
                onOpen = { opened++ },
                onExit = { exited++ }
            )
        }
    }

    // В compose-ui-test есть только assertTouchHeightIsEqualTo; «не меньше» — по границам
    // зоны нажатия узла (они уже учитывают минимальный размер касания).
    private fun SemanticsNodeInteraction.assertTouchHeightAtLeast48() {
        val height = with(composeRule.density) { fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
        assertTrue("высота зоны нажатия $height < 48.dp", height >= 48.dp)
    }

    @Test
    fun отдых_показывает_отсчёт() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Отдых 01:18").assertIsDisplayed()
    }

    @Test
    fun переход_в_суперсете_показывает_отсчёт_и_упражнение() {
        show(MiniBarStatus.Resting(15_000L, 20_000L, "Икры"))

        composeRule.onNodeWithText("Переход 00:15 · Икры").assertIsDisplayed()
    }

    @Test
    fun подход_показывает_номер_и_упражнение() {
        show(MiniBarStatus.Working(2, 4, "Жим лёжа"))

        composeRule.onNodeWithText("Подход 2 из 4 · Жим лёжа").assertIsDisplayed()
    }

    @Test
    fun после_отдыха_пора_делать_подход() {
        show(MiniBarStatus.RestOver(2))

        composeRule.onNodeWithText("Пора: подход 2").assertIsDisplayed()
    }

    @Test
    fun тап_по_плашке_возвращает_а_крестик_выходит() {
        show(MiniBarStatus.Working(2, 4, "Жим лёжа"))

        composeRule.onNodeWithText("Ноги").performClick()
        composeRule.onNodeWithContentDescription("Выйти из тренировки без сохранения").performClick()

        assertEquals(1, opened)
        assertEquals(1, exited)
    }

    @Test
    fun зоны_нажатия_не_меньше_48_dp() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Ноги").assertTouchHeightAtLeast48()
        composeRule.onNodeWithContentDescription("Выйти из тренировки без сохранения")
            .assertTouchHeightAtLeast48()
    }

    @Test
    fun плашка_читается_одной_фразой_с_меткой_нажатия() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Ноги")
            .assertTextContains("Отдых 01:18")
            .assert(
                SemanticsMatcher("метка нажатия «Вернуться к тренировке»") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Вернуться к тренировке"
                }
            )
    }
}
