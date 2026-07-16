package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val getWorkoutSessionsUseCase: GetWorkoutSessionsUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutHistoryState())
    val state = _state.asStateFlow()

    fun loadHistory(workoutId: Int) {
        viewModelScope.launch {
            val workout = getWorkoutByIdUseCase(workoutId)
            _state.update { it.copy(workoutName = workout?.name ?: "") }
        }

        getWorkoutSessionsUseCase(workoutId)
            .onEach { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
            .launchIn(viewModelScope)
    }
}

data class WorkoutHistoryState(
    val workoutName: String = "",
    val sessions: List<WorkoutSession> = emptyList()
)
