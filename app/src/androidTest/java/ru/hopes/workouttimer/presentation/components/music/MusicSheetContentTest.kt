package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

private fun track(
    canSeek: Boolean = true,
    canRate: Boolean = true,
    isLiked: Boolean? = null,
    durationMs: Long = 195_000L
) = TrackInfo(
    title = "Ne Naprasno",
    artist = "Soft Blade",
    artwork = null,
    durationMs = durationMs,
    positionMs = 42_000L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = canSeek,
    canRate = canRate,
    canSkipNext = true,
    canSkipPrevious = true,
    isLiked = isLiked
)

@RunWith(AndroidJUnit4::class)
class MusicSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        trackInfo: TrackInfo,
        playing: Boolean = true,
        onLike: (Boolean) -> Unit = {},
        onSeek: (Long) -> Unit = {},
        onOpenPlayer: () -> Unit = {}
    ) {
        composeRule.setContent {
            WorkoutTimerTheme {
                Column {
                    MusicSheetContent(
                        track = trackInfo,
                        playing = playing,
                        onPlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = onSeek,
                        onLike = onLike,
                        onOpenPlayer = onOpenPlayer
                    )
                }
            }
        }
    }

    @Test
    fun шторка_показывает_трек_и_исполнителя() {
        setContent(track())

        composeRule.onNodeWithText("Ne Naprasno").assertIsDisplayed()
        composeRule.onNodeWithText("Soft Blade").assertIsDisplayed()
    }

    @Test
    fun слайдер_есть_когда_перемотка_доступна() {
        setContent(track(canSeek = true))

        composeRule.onNodeWithContentDescription("Перемотка").assertIsDisplayed()
    }

    /** Радиопоток без длительности: слайдера быть не должно. */
    @Test
    fun слайдера_нет_когда_перемотка_недоступна() {
        setContent(track(canSeek = false, durationMs = 0L))

        composeRule.onNodeWithContentDescription("Перемотка").assertDoesNotExist()
    }

    @Test
    fun сердечка_нет_когда_рейтинг_недоступен() {
        setContent(track(canRate = false))

        composeRule.onNodeWithContentDescription("Лайк").assertDoesNotExist()
    }

    @Test
    fun лайкнутый_трек_показывает_закрашенное_сердце() {
        setContent(track(isLiked = true))

        composeRule.onNodeWithContentDescription("Убрать лайк").assertIsDisplayed()
    }

    @Test
    fun неизвестное_состояние_лайка_показывает_контур_и_ставит_лайк() {
        var liked: Boolean? = null
        setContent(track(isLiked = null), onLike = { liked = it })

        composeRule.onNodeWithContentDescription("Лайк").performClick()

        assertEquals(true, liked)
    }

    @Test
    fun конец_отдыха_закрывает_шторку_а_начало_нет() {
        var resting by mutableStateOf(false)
        var visibleNow = false

        composeRule.setContent {
            val visible = rememberMusicSheetVisibility(isResting = resting, state = MusicState.Playing(track()))
            LaunchedEffect(Unit) { visible.value = true }
            visibleNow = visible.value
        }

        composeRule.runOnIdle { resting = true }
        composeRule.runOnIdle { assertEquals(true, visibleNow) }

        composeRule.runOnIdle { resting = false }
        composeRule.runOnIdle { assertEquals(false, visibleNow) }
    }

    @Test
    fun пропавшая_сессия_закрывает_шторку() {
        var state by mutableStateOf<MusicState>(MusicState.Playing(track()))
        var visibleNow = false

        composeRule.setContent {
            val visible = rememberMusicSheetVisibility(isResting = true, state = state)
            LaunchedEffect(Unit) { visible.value = true }
            visibleNow = visible.value
        }

        composeRule.runOnIdle { state = MusicState.NoSession }
        composeRule.runOnIdle { assertEquals(false, visibleNow) }
    }

    @Test
    fun долгий_тап_по_обложке_открывает_плеер() {
        var opened = 0
        setContent(track(), onOpenPlayer = { opened++ })

        composeRule.onNodeWithContentDescription("Обложка трека").performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(1, opened)
    }

    /** Короткий тап не должен уводить из приложения посреди отдыха. */
    @Test
    fun обычный_тап_по_обложке_не_открывает_плеер() {
        var opened = 0
        setContent(track(), onOpenPlayer = { opened++ })

        composeRule.onNodeWithContentDescription("Обложка трека").performClick()
        composeRule.waitForIdle()

        assertEquals(0, opened)
    }

    /**
     * Отпустив ползунок, пользователь должен видеть то место, куда он его
     * привёл, а не то, откуда тянул. Возврат назад читается как «перемотка не
     * сработала» — ровно тот порок обратной связи, ради которого затевался
     * весь плеер.
     */
    @Test
    fun после_отпускания_ползунок_остаётся_на_новом_месте() {
        var seeked: Long? = null
        setContent(track(), playing = false, onSeek = { seeked = it })

        composeRule.onNodeWithContentDescription("Перемотка").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(true, (seeked ?: 0L) > 42_000L)
        composeRule.onNodeWithText("00:42").assertDoesNotExist()
    }
}
