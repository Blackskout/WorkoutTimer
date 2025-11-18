package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import javax.inject.Inject

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundPlayer: SoundPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutExecutionState>(
        WorkoutExecutionState.Rest(
            exercise = Exercise(0, "Жим лежа", 80, 3, 12, 12_000L, 1),
            currentSet = 1,
        )
    )
    val uiState: StateFlow<WorkoutExecutionState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun skipRest() {
        timerJob?.cancel()
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets,
                        weight = state.exercise.weight,
                        reps = state.exercise.reps
                    )
                }

                else -> state
            }
        }
    }

    fun startRestTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value is WorkoutExecutionState.Rest) {
                val restState = _uiState.value as WorkoutExecutionState.Rest
                if (restState.exercise.restTimeMillis <= 0L) {
                    onRestFinished()
                    break
                }
                delay(1000L)
                _uiState.update { state ->
                    when (state) {
                        is WorkoutExecutionState.Rest -> {
                            val curExercise = state.exercise
                            state.copy(
                                exercise = Exercise(
                                    id = curExercise.id,
                                    name = curExercise.name,
                                    weight = curExercise.weight,
                                    sets = curExercise.sets,
                                    reps = curExercise.reps,
                                    restTimeMillis = curExercise.restTimeMillis - 1000L,
                                    order = curExercise.order
                                )
                            )
                        }

                        else -> state
                    }
                }
            }
        }
    }

    private fun onRestFinished() {
        timerJob?.cancel()
        soundPlayer.playSound(R.raw.soundgong) // <-- Воспроизводим звук
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets,
                        weight = state.exercise.weight,
                        reps = state.exercise.reps
                    )
                }
                else -> state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release() // <-- Освобождаем ресурсы здесь, в нужном месте
    }

    fun onExerciseFinished() {
        // Здесь можно перейти к следующему подходу или завершить упражнение
        // Для простоты — просто вернемся в режим отдыха (если есть еще подходы)
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Active -> {
                    if (state.currentSet < state.totalSets) {
                        WorkoutExecutionState.Rest(
                            exercise = state.exercise,
                            currentSet = state.currentSet + 1,
                            totalSets = state.totalSets
                        )
                    } else {
                        // Завершение упражнения — можно отправить событие навигации
                        // Например: navigateToNextScreen()
                        state // пока оставим как есть
                    }
                }

                else -> state
            }
        }
    }
}

sealed class WorkoutExecutionState {

    data class Rest(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        // нужно еще количество всех тренировок также тотал и текущее но я тут осознал что не использую Workout так что получается собсна я говнокодю или делаючто то не так
    ) : WorkoutExecutionState()

    data class Active(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val weight: Int = exercise.weight,
        val reps: Int = exercise.reps
    ) : WorkoutExecutionState()
}