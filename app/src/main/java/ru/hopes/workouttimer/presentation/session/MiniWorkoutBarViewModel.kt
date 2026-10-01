package ru.hopes.workouttimer.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Плашка и возврат в тренировку для корня навигации (область — активность). */
@HiltViewModel
class MiniWorkoutBarViewModel @Inject constructor(
    private val manager: WorkoutSessionManager
) : ViewModel() {

    /** Во время отдыха меняется 5 раз в секунду — читает только сама плашка. */
    val barState: StateFlow<MiniBarState?> = manager.session
        .map { miniBarStateOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, miniBarStateOf(manager.session.value))

    /** Корню навигации — только этот признак: тики его не меняют и NavHost не перерисовывают. */
    val visible: StateFlow<Boolean> = barState
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, barState.value != null)

    fun abandon() = manager.abandon()

    /**
     * Возврат в тренировку — тап по плашке, «Вернуться» на просмотре и в списке, виджет,
     * уведомление. Возврат считается действием для учёта простоя. null — сессии нет.
     */
    fun prepareReturn(): Int? {
        val running = manager.runningWorkout.value ?: return null
        manager.registerInteraction()
        return running.workoutId
    }
}
