package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

private fun track(
    title: String = "Ne Naprasno",
    artist: String = "Soft Blade",
    canSkipNext: Boolean = true,
    canSkipPrevious: Boolean = true
) = TrackInfo(
    title = title,
    artist = artist,
    artwork = null,
    durationMs = 195_000L,
    positionMs = 0L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = true,
    canRate = true,
    canSkipNext = canSkipNext,
    canSkipPrevious = canSkipPrevious,
    isLiked = null
)

@RunWith(AndroidJUnit4::class)
class MusicBarContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: MusicState,
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onOpenPlayer: () -> Unit = {},
        onGrantPermission: () -> Unit = {},
        onExpand: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicBarContent(
                    state = state,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onOpenPlayer = onOpenPlayer,
                    onGrantPermission = onGrantPermission,
                    onExpand = onExpand,
                    modifier = modifier
                )
            }
        }
    }

    @Test
    fun без_доступа_полоска_зовёт_в_настройки() {
        var granted = 0
        setContent(MusicState.PermissionRequired, onGrantPermission = { granted++ })

        composeRule.onNodeWithText("Разрешить управление музыкой").performClick()

        assertEquals(1, granted)
    }

    @Test
    fun без_сессии_полоска_запускает_плеер() {
        var opened = 0
        setContent(MusicState.NoSession, onOpenPlayer = { opened++ })

        composeRule.onNodeWithText("Включить музыку").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun во_время_игры_показана_пауза() {
        setContent(MusicState.Playing(track()))

        composeRule.onNodeWithContentDescription("Пауза").assertIsDisplayed()
    }

    @Test
    fun на_паузе_показана_кнопка_играть() {
        setContent(MusicState.Paused(track()))

        composeRule.onNodeWithContentDescription("Играть").assertIsDisplayed()
    }

    @Test
    fun тап_по_треку_раскрывает_шторку_а_тап_по_кнопке_нет() {
        var expanded = 0
        var nexts = 0
        setContent(MusicState.Playing(track()), onNext = { nexts++ }, onExpand = { expanded++ })

        composeRule.onNodeWithContentDescription("Следующий трек").performClick()
        assertEquals(0, expanded)
        assertEquals(1, nexts)

        composeRule.onNodeWithContentDescription("Открыть плеер").performClick()
        assertEquals(1, expanded)
    }

    /**
     * Яндекс убирает действие из маски, когда оно недоступно. Кнопки при этом
     * не исчезают — иначе раскладка прыгала бы на каждом треке, — а гаснут.
     */
    @Test
    fun недоступная_перемотка_треков_гасит_кнопки() {
        setContent(MusicState.Playing(track(canSkipNext = false, canSkipPrevious = false)))

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Предыдущий трек").assertIsNotEnabled()
    }

    @Test
    fun доступная_перемотка_треков_оставляет_кнопки_живыми() {
        setContent(MusicState.Playing(track()))

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsEnabled()
    }

    /**
     * Длинное название не должно выталкивать кнопки за край: текст ужимается
     * бегущей строкой, а управление остаётся на экране.
     */
    @Test
    fun длинное_название_не_выталкивает_кнопки_за_экран() {
        setContent(
            MusicState.Playing(track(title = "Очень длинное название трека, которое никуда не помещается целиком")),
            modifier = Modifier.width(320.dp)
        )

        composeRule.onNodeWithContentDescription("Следующий трек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Пауза").assertIsDisplayed()
    }

    /** Метаданные пустуют при переключении трека — кнопки обязаны работать. */
    @Test
    fun прочерки_вместо_метаданных_не_ломают_кнопки() {
        var nexts = 0
        setContent(MusicState.Playing(track(title = "—", artist = "—")), onNext = { nexts++ })

        composeRule.onNodeWithContentDescription("Следующий трек").performClick()

        assertEquals(1, nexts)
    }

    /** Пользователь может жать next очередью — падать нельзя. */
    @Test
    fun повторные_тапы_по_кнопке_не_ломают_полоску() {
        var nexts = 0
        setContent(MusicState.Playing(track()), onNext = { nexts++ })

        repeat(5) { composeRule.onNodeWithContentDescription("Следующий трек").performClick() }

        assertEquals(5, nexts)
    }
}
