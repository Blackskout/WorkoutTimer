package ru.hopes.workouttimer.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import javax.inject.Inject

@HiltViewModel
class PerformWorkoutViewModel @Inject constructor(
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerformUiState())
    val uiState: StateFlow<PerformUiState> = _uiState.asStateFlow()

    private var workout: Workout? = null
    private var currentExerciseIndex = 0
    private var currentSet = 1

    init {
        val id = savedStateHandle.get<Int>("workoutId")
        if (id != null) {
            viewModelScope.launch {
                workout = getWorkoutByIdUseCase(id)
                workout?.let {
                    _uiState.update {
                        it.copy(
                            workoutName = workout!!.name,
                            currentExercise = workout!!.exercises.firstOrNull(),
                            nextExercise = workout!!.exercises.getOrNull(1)
                        )
                    }
                }
            }
        }
    }

    fun onSetCompleted() {
        val ex = workout?.exercises?.getOrNull(currentExerciseIndex) ?: return
        if (currentSet < ex.sets) {
            currentSet++
            startRest(ex.restTimeMillis)
            _uiState.update { it.copy(currentSet = currentSet, isResting = true, restMillisLeft = ex.restTimeMillis) }
        } else {
            // переход к следующему упражнения
            currentExerciseIndex++
            currentSet = 1
            val next = workout?.exercises?.getOrNull(currentExerciseIndex)
            val nextNext = workout?.exercises?.getOrNull(currentExerciseIndex + 1)
            _uiState.update { it.copy(
                currentExercise = next,
                nextExercise = nextNext,
                currentSet = currentSet,
                isResting = false,
                restMillisLeft = 0
            )}
        }
    }

    private var restJob: Job? = null
    private fun startRest(millis: Long) {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            var left = millis
            while (left > 0) {
                _uiState.update { it.copy(restMillisLeft = left) }
                delay(1000)
                left--
            }
            _uiState.update { it.copy(isResting = false, restMillisLeft = 0) }
        }
    }

    fun stopRest() {
        restJob?.cancel()
        _uiState.update { it.copy(isResting = false, restMillisLeft = 0) }
    }
}

data class PerformUiState(
    val workoutName: String = "",
    val currentExercise: Exercise? = null,
    val nextExercise: Exercise? = null,
    val currentSet: Int = 1,
    val isResting: Boolean = false,
    val restMillisLeft: Long = 0
)