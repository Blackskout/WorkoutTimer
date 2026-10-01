package ru.hopes.workouttimer.presentation.screen.workoutPreview

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class WorkoutPreviewContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val press = Exercise(
        id = 5, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L,
        order = 1, catalogId = 50L
    )
    private val flyes = Exercise(
        id = 6, name = "Разводка", weight = 0.0, sets = 3, reps = 12, timeMillis = 60_000L,
        order = 2, catalogId = 51L, unit = ExerciseUnit.BODYWEIGHT
    )
    private val back = Workout(id = 2, name = "Спина", exercises = listOf(press, flyes), lastUseAt = 0L)

    private var started = 0
    private var returned = 0
    private val progress = mutableListOf<Long>()
    private val added = mutableListOf<Exercise>()

    private fun kg(weight: Double, reps: Int) =
        SessionSet(id = 0, sessionId = 7, catalogId = 50, weight = weight, extraWeight = 0.0, reps = reps, unit = ExerciseUnit.KG)

    private fun show(state: WorkoutPreviewState) = composeRule.setContent {
        WorkoutTimerTheme {
            WorkoutPreviewContent(
                state = state,
                onStart = { started++ },
                onReturn = { returned++ },
                onOpenProgress = { progress += it },
                onAddToday = { added += it }
            )
        }
    }

    private fun loaded(bottom: PreviewBottom, addedIds: Set<Int> = emptySet()) = WorkoutPreviewState(
        isLoaded = true,
        workout = back,
        lastTime = mapOf(
            50L to LastTime(
                finishedAt = System.currentTimeMillis() - 3 * 86_400_000L - 3_600_000L,
                sets = listOf(kg(60.0, 8), kg(60.0, 8), kg(62.5, 6))
            )
        ),
        lastDurationMillis = 3_120_000L,
        bottom = bottom,
        addedExerciseIds = addedIds
    )

    @Test
    fun строка_показывает_план_и_прошлый_раз() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("4 подхода · 60 кг × 8 · отдых 02:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Прошлый раз · 3 дн. назад: 60 кг × 8 · 60 × 8 · 62.5 × 6", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("3 подхода · 12 · отдых 01:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ещё не делали", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 упр · 52:00", useUnmergedTree = true).assertIsDisplayed()
    }

    // PrimaryButton пишет текст прописными: «Начать» на экране — «НАЧАТЬ».
    @Test
    fun без_сессии_кнопка_начать() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("НАЧАТЬ").performClick()
        composeRule.onNodeWithText("В сегодняшнюю").assertDoesNotExist()

        composeRule.runOnIdle { assertEquals(1, started) }
    }

    @Test
    fun идёт_эта_тренировка_вернуться() {
        show(loaded(PreviewBottom.Return))

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ К ТРЕНИРОВКЕ").performClick()

        composeRule.runOnIdle { assertEquals(1, returned) }
    }

    @Test
    fun идёт_другая_подсказка_и_добавление_в_сегодняшнюю() {
        show(loaded(PreviewBottom.RunningOther("Ноги")))

        composeRule.onNodeWithText("Идёт «Ноги» — упражнение можно добавить в неё").assertIsDisplayed()
        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Добавить «Жим лёжа» в сегодняшнюю тренировку").performClick()

        composeRule.runOnIdle { assertEquals(listOf(press), added) }
    }

    @Test
    fun добавленное_упражнение_неактивно() {
        show(loaded(PreviewBottom.RunningOther("Ноги"), addedIds = setOf(5)))

        composeRule.onNodeWithText("Добавлено").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Добавить «Разводка» в сегодняшнюю тренировку").assertIsDisplayed()
    }

    @Test
    fun тап_по_строке_открывает_прогресс_с_меткой() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("Жим лёжа")
            .assert(
                SemanticsMatcher("метка «Жим лёжа, открыть прогресс»") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Жим лёжа, открыть прогресс"
                }
            )
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(50L), progress) }
    }

    @Test
    fun пустая_тренировка_без_кнопки_начать() {
        show(WorkoutPreviewState(isLoaded = true, workout = back.copy(exercises = emptyList()), bottom = PreviewBottom.None))

        composeRule.onNodeWithText("В тренировке нет упражнений").assertIsDisplayed()
        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
    }

    @Test
    fun удалённая_тренировка_не_найдена() {
        show(WorkoutPreviewState(isLoaded = true, workout = null))

        composeRule.onNodeWithText("Тренировка не найдена").assertIsDisplayed()
    }
}
