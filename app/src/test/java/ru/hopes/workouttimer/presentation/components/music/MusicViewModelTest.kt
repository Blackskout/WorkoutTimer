package ru.hopes.workouttimer.presentation.components.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

private fun track() = TrackInfo(
    title = "Ne Naprasno",
    artist = "Soft Blade",
    artwork = null,
    durationMs = 195_000L,
    positionMs = 0L,
    positionUpdatedAt = 0L,
    playbackSpeed = 1f,
    canSeek = true,
    canRate = true,
    canSkipNext = true,
    canSkipPrevious = true,
    isLiked = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Одна кнопка на два действия: что именно она пошлёт, решает состояние
     * сессии. Перепутать местами — значит поставить музыку на паузу нажатием
     * на «играть».
     */
    @Test
    fun центральная_кнопка_ставит_на_паузу_когда_музыка_играет() = runTest(dispatcher) {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.playPause()

        assertEquals(1, repository.pauseCount)
        assertEquals(0, repository.playCount)
    }

    @Test
    fun центральная_кнопка_запускает_музыку_когда_стоит_пауза() = runTest(dispatcher) {
        val repository = FakeMusicControlRepository(MusicState.Paused(track()))
        val viewModel = MusicViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.playPause()

        assertEquals(1, repository.playCount)
        assertEquals(0, repository.pauseCount)
    }

    @Test
    fun остальные_команды_уходят_в_репозиторий() = runTest(dispatcher) {
        val repository = FakeMusicControlRepository(MusicState.Playing(track()))
        val viewModel = MusicViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.next()
        viewModel.previous()
        viewModel.seekTo(42_000L)
        viewModel.setLiked(true)
        viewModel.openPlayer()
        viewModel.grantPermission()
        viewModel.refresh()

        assertEquals(1, repository.nextCount)
        assertEquals(1, repository.previousCount)
        assertEquals(42_000L, repository.lastSeek)
        assertEquals(true, repository.lastLiked)
        assertEquals(1, repository.openPlayerCount)
        assertEquals(1, repository.openSettingsCount)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun состояние_репозитория_доходит_до_экрана() = runTest(dispatcher) {
        val repository = FakeMusicControlRepository(MusicState.NoSession)
        val viewModel = MusicViewModel(repository)
        backgroundScope.launch { viewModel.state.collect { } }
        advanceUntilIdle()

        repository.emit(MusicState.PermissionRequired)
        advanceUntilIdle()

        assertEquals(MusicState.PermissionRequired, viewModel.state.value)
    }
}
