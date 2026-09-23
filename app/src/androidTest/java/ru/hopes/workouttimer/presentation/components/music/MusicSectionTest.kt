package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

private fun track() = TrackInfo(
    title = "Ne Naprasno",
    artist = "Soft Blade",
    artwork = null,
    durationMs = 195_000L,
    positionMs = 42_000L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = true,
    canRate = true,
    canSkipNext = true,
    canSkipPrevious = true,
    isLiked = null
)

/**
 * Полоска и шторка проверены по отдельности; здесь проверяется шов между ними
 * и настоящая MusicViewModel поверх фейкового репозитория. Hilt не нужен:
 * MusicSection принимает viewModel параметром со значением по умолчанию.
 */
@RunWith(AndroidJUnit4::class)
class MusicSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun тап_по_полоске_раскрывает_шторку() {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicSection(isResting = true, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithContentDescription("Открыть плеер").performClick()

        composeRule.onNodeWithText("Ne Naprasno").assertIsDisplayed()
    }

    @Test
    fun конец_отдыха_закрывает_раскрытую_шторку() {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        var resting by mutableStateOf(true)
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicSection(isResting = resting, viewModel = viewModel)
            }
        }
        composeRule.onNodeWithContentDescription("Открыть плеер").performClick()
        composeRule.onNodeWithText("Ne Naprasno").assertIsDisplayed()

        composeRule.runOnIdle { resting = false }

        composeRule.onNodeWithText("Ne Naprasno").assertDoesNotExist()
    }

    @Test
    fun пропавшая_сессия_возвращает_полоску_к_запуску_плеера() {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicSection(isResting = false, viewModel = viewModel)
            }
        }

        composeRule.runOnIdle { repository.emit(MusicState.NoSession) }

        composeRule.onNodeWithText("Включить музыку").assertIsDisplayed()
    }

    /** Одна кнопка на два действия — проверяем через настоящую ViewModel. */
    @Test
    fun центральная_кнопка_шлёт_паузу_когда_музыка_играет() {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        composeRule.setContent {
            WorkoutTimerTheme {
                MusicSection(isResting = false, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithContentDescription("Пауза").performClick()

        composeRule.runOnIdle {
            assertEquals(1, repository.pauseCount)
            assertEquals(0, repository.playCount)
        }
    }
}
