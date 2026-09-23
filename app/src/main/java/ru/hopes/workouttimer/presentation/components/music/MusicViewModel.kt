package ru.hopes.workouttimer.presentation.components.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.repository.MusicControlRepository
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicControlRepository
) : ViewModel() {

    val state: StateFlow<MusicState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicState.NoSession)

    /** Зовётся на ON_RESUME: пользователь мог вернуться из системных настроек. */
    fun refresh() = repository.refresh()

    /** Что послать — решает состояние сессии, а не память о прошлом нажатии. */
    fun playPause() {
        if (state.value is MusicState.Playing) repository.pause() else repository.play()
    }

    fun next() = repository.next()
    fun previous() = repository.previous()
    fun seekTo(positionMs: Long) = repository.seekTo(positionMs)
    fun setLiked(liked: Boolean) = repository.setLiked(liked)
    fun openPlayer() = repository.openPlayer()
    fun grantPermission() = repository.openPermissionSettings()
}
