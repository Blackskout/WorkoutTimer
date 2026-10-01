package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.percentOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ProgressChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = 86_400_000L

    private fun point(sessionId: Long, finishedAt: Long, weight: Double) = ProgressPoint(
        sessionId, finishedAt, SessionSet(sessionId, sessionId, 1L, weight, 0.0, 8, ExerciseUnit.KG), weight
    )

    // Дни 0, 1 и 100: середина оси X далеко от всех точек, правый край — у последней.
    private val points = listOf(point(1, 0, 55.0), point(2, day, 57.5), point(3, 100 * day, 60.0))

    private fun show(onSelect: (Long?) -> Unit) = composeRule.setContent {
        WorkoutTimerTheme { ProgressChart(points = points, selectedSessionId = null, onSelect = onSelect) }
    }

    @Test
    fun тап_у_правого_края_выбирает_последнюю_точку() {
        var picked: Long? = null
        show { picked = it }

        // Зона попадания шире точки: палец в нескольких dp от кружка всё равно его выбирает.
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.98f, 0.5f)) }

        composeRule.runOnIdle { assertEquals(3L, picked) }
    }

    @Test
    fun тап_вдали_от_точек_снимает_выбор() {
        var picked: Long? = -1L
        show { picked = it }

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.5f, 0.5f)) }

        composeRule.runOnIdle { assertNull(picked) }
    }

    @Test
    fun график_описан_для_TalkBack_числом_тренировок() {
        show {}

        composeRule.onNodeWithContentDescription("График лучшего подхода: 3 тренировки").assertExists()
    }
}
