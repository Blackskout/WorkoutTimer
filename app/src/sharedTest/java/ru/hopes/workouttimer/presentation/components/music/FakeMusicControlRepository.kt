package ru.hopes.workouttimer.presentation.components.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.repository.MusicControlRepository

/**
 * Моки не нужны: MusicControlRepository — интерфейс, поверх фейка собирается
 * настоящая MusicViewModel. Заодно считает команды, чтобы тесты могли
 * проверить, что кнопка не отправила лишнего.
 */
class FakeMusicControlRepository(
    initial: MusicState = MusicState.NoSession
) : MusicControlRepository {

    private val flow = MutableStateFlow(initial)

    var playCount = 0; private set
    var pauseCount = 0; private set
    var nextCount = 0; private set
    var previousCount = 0; private set
    var refreshCount = 0; private set
    var lastSeek: Long? = null; private set
    var lastLiked: Boolean? = null; private set
    var openPlayerCount = 0; private set
    var openSettingsCount = 0; private set

    fun emit(state: MusicState) {
        flow.value = state
    }

    override val state: Flow<MusicState> = flow

    override fun refresh() { refreshCount++ }
    override fun play() { playCount++ }
    override fun pause() { pauseCount++ }
    override fun next() { nextCount++ }
    override fun previous() { previousCount++ }
    override fun seekTo(positionMs: Long) { lastSeek = positionMs }
    override fun setLiked(liked: Boolean) { lastLiked = liked }
    override fun openPlayer() { openPlayerCount++ }
    override fun openPermissionSettings() { openSettingsCount++ }
}
