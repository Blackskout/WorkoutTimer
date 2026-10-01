package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ExerciseProgressContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun kg(id: Long, weight: Double, reps: Int) = SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun plate(id: Long, plate: Double, extra: Double, reps: Int) =
        SessionSet(id, id, 1L, plate, extra, reps, ExerciseUnit.PLATE)

    private fun bodyweight(id: Long, reps: Int) = SessionSet(id, id, 1L, 0.0, 0.0, reps, ExerciseUnit.BODYWEIGHT)

    private fun session(daysAgo: Int, set: SessionSet) = ProgressSession(set.sessionId, now - daysAgo * day, listOf(set))

    private fun localNoon(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    private fun stateOf(
        unit: ExerciseUnit,
        sessions: List<ProgressSession>,
        period: ProgressPeriod = ProgressPeriod.QUARTER,
        selected: Long? = null
    ) = ExerciseProgressState(
        isLoaded = true,
        exercise = CatalogExercise(1L, "Присед", unit),
        sessions = sessions,
        period = period,
        summary = summarizeProgress(sessions, unit, period, now, TimeZone.getDefault()),
        selectedSessionId = selected
    )

    private fun show(state: ExerciseProgressState) = composeRule.setContent {
        WorkoutTimerTheme { ProgressContent(state = state, onPeriodChange = {}, onSelectPoint = {}) }
    }

    @Test
    fun главная_цифра_и_изменение_за_период_в_кг() {
        show(stateOf(ExerciseUnit.KG, listOf(session(60, kg(1, 60.0, 8)), session(10, kg(2, 62.5, 6)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("62.5 кг × 6")
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Нажмите на точку, чтобы увидеть подход").assertIsDisplayed()
    }

    @Test
    fun изменение_плиты_склоняется_по_числу() {
        show(stateOf(ExerciseUnit.PLATE, listOf(session(60, plate(1, 5.0, 2.0, 12)), session(10, plate(2, 6.0, 0.0, 10)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("плита 6 × 10")
        composeRule.onNodeWithText("+1 плита за 3 мес").assertIsDisplayed()
    }

    @Test
    fun без_веса_главная_цифра_в_повторениях() {
        show(stateOf(ExerciseUnit.BODYWEIGHT, listOf(session(60, bodyweight(1, 8)), session(10, bodyweight(2, 10)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("10 повт.")
        composeRule.onNodeWithText("+2 повт за 3 мес").assertIsDisplayed()
    }

    @Test
    fun без_сессий_пустое_состояние() {
        show(stateOf(ExerciseUnit.KG, emptyList()))

        composeRule.onNodeWithText("Сделайте упражнение — здесь появится прогресс").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertDoesNotExist()
    }

    @Test
    fun одна_сессия_главная_цифра_и_список_без_графика() {
        show(stateOf(ExerciseUnit.KG, listOf(session(10, kg(1, 60.0, 8)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("60 кг × 8")
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("График появится, когда за период будет две тренировки").assertIsDisplayed()
        composeRule.onNodeWithText("за 3 мес", substring = true).assertDoesNotExist()
    }

    @Test
    fun переключение_периода_пересчитывает_график_и_изменение() {
        val sessions = listOf(session(60, kg(1, 60.0, 8)), session(10, kg(2, 62.5, 6)))
        var period by mutableStateOf(ProgressPeriod.QUARTER)
        composeRule.setContent {
            WorkoutTimerTheme {
                ProgressContent(state = stateOf(ExerciseUnit.KG, sessions, period), onPeriodChange = { period = it }, onSelectPoint = {})
            }
        }
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertIsDisplayed()

        composeRule.onNodeWithText("1 мес").performClick()

        composeRule.onNodeWithText("1 мес").assertIsSelected()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("График появится, когда за период будет две тренировки").assertIsDisplayed()
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertDoesNotExist()
    }

    @Test
    fun подходы_в_других_единицах_отмечены_и_видны_в_списке_со_своей_подписью() {
        show(
            stateOf(
                ExerciseUnit.PLATE,
                listOf(session(80, kg(1, 40.0, 12)), session(60, plate(2, 4.0, 0.0, 12)), session(10, plate(3, 5.0, 2.0, 12)))
            )
        )

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("плита 5 +2 кг × 12")
        composeRule.onNodeWithText("Подходы в других единицах — в списке ниже").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_LIST_TAG).performScrollToNode(hasText("40 кг × 12"))
        composeRule.onNodeWithText("40 кг × 12").assertIsDisplayed()
    }

    @Test
    fun только_другие_единицы_нет_цифры_и_графика_но_есть_список() {
        show(stateOf(ExerciseUnit.PLATE, listOf(session(5, kg(1, 40.0, 12)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Сделайте упражнение — здесь появится прогресс").assertDoesNotExist()
        composeRule.onNodeWithText("Подходы в других единицах — в списке ниже").assertIsDisplayed()
        composeRule.onNodeWithText("40 кг × 12").assertIsDisplayed()
    }

    @Test
    fun тап_по_точке_показывает_подпись_и_подсвечивает_строку_сессии() {
        val sessions = listOf(
            ProgressSession(1L, localNoon(2026, 9, 20), listOf(kg(1, 55.0, 8))),
            ProgressSession(2L, localNoon(2026, 9, 26), listOf(kg(2, 60.0, 8)))
        )
        var selected by mutableStateOf<Long?>(null)
        composeRule.setContent {
            WorkoutTimerTheme {
                ProgressContent(
                    state = stateOf(ExerciseUnit.KG, sessions, ProgressPeriod.ALL, selected),
                    onPeriodChange = {},
                    onSelectPoint = { selected = if (it == selected) null else it }
                )
            }
        }
        val sessionRow = { text: String -> hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) }

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.98f, 0.5f)) }

        composeRule.onNodeWithText("26 сен · 60 кг × 8").assertIsDisplayed()
        composeRule.onNode(sessionRow("60 кг × 8")).assertIsSelected()
        composeRule.onNode(sessionRow("55 кг × 8")).assertIsNotSelected()
    }

    @Test
    fun неизвестное_упражнение_не_найдено() {
        show(ExerciseProgressState(isLoaded = true))

        composeRule.onNodeWithText("Упражнение не найдено").assertIsDisplayed()
    }

    @Test
    fun сбой_чтения_показан_вместо_экрана() {
        show(ExerciseProgressState(isLoaded = true, loadFailed = true))

        composeRule.onNodeWithText("Не удалось загрузить прогресс").assertIsDisplayed()
    }
}
