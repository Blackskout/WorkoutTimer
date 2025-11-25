package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import javax.inject.Inject

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    private val soundPlayer: SoundPlayer,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase
) : ViewModel() {

    private var workout: Workout? = null
    private var exercises: List<Exercise> = emptyList()
    private var exerciseIndex = 0

    val workoutName: String
        get() = workout?.name ?: ""

    val totalExercises: Int
        get() = exercises.size

    val currentExerciseNumber: Int
        get() = exerciseIndex + 1

    private val _uiState = MutableStateFlow<WorkoutExecutionState>(
        WorkoutExecutionState.Loading
    )
    val uiState: StateFlow<WorkoutExecutionState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun loadWorkout(workoutId: Int) {
        viewModelScope.launch {
            _uiState.value = WorkoutExecutionState.Loading
            val loadedWorkout = getWorkoutByIdUseCase(workoutId)
            
            if (loadedWorkout != null && loadedWorkout.exercises.isNotEmpty()) {
                workout = loadedWorkout
                exercises = loadedWorkout.exercises.sortedBy { it.order }
                exerciseIndex = 0
                
                // Начинаем с первого упражнения в состоянии Rest
                val firstExercise = exercises[0]
                _uiState.value = WorkoutExecutionState.Rest(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                    restTimeMillis = firstExercise.timeMillis,
                    totalRestTimeMillis = firstExercise.timeMillis
                )
                startRestTimer()
            } else {
                _uiState.value = WorkoutExecutionState.Error(
                    message = "Тренировка не найдена или не содержит упражнений"
                )
            }
        }
    }

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
        val currentState = _uiState.value as? WorkoutExecutionState.Rest ?: return
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            countdownFlow(currentState.restTimeMillis)
                .onCompletion { cause ->
                    if (cause == null) {
                        onRestFinished()
                    }
                }
                .collect { timeLeft ->
                    _uiState.update { state ->
                        when (state) {
                            is WorkoutExecutionState.Rest -> state.copy(restTimeMillis = timeLeft)
                            else -> state
                        }
                    }
                }
        }
    }

    private fun onRestFinished() {
        timerJob?.cancel()
        soundPlayer.playSound(R.raw.soundgong) // <-- Воспроизводим звук
    }

    private fun countdownFlow(
        totalMillis: Long,
        stepMillis: Long = 1_000L
    ): Flow<Long> = flow {
        var remaining = totalMillis.coerceAtLeast(0L)
        emit(remaining)
        while (remaining > 0L) {
            delay(stepMillis)
            remaining = (remaining - stepMillis).coerceAtLeast(0L)
            emit(remaining)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release() // <-- Освобождаем ресурсы здесь, в нужном месте
    }

    fun onExerciseFinished() {
        val currentState = _uiState.value
        if (currentState is WorkoutExecutionState.Active) {
            if (currentState.currentSet < currentState.totalSets) {
                // Переход к следующему подходу того же упражнения
                _uiState.value = WorkoutExecutionState.Rest(
                    exercise = currentState.exercise,
                    currentSet = currentState.currentSet + 1,
                    totalSets = currentState.totalSets,
                    restTimeMillis = currentState.exercise.timeMillis,
                    totalRestTimeMillis = currentState.exercise.timeMillis
                )
                startRestTimer()
            } else {
                // Упражнение завершено, переходим к следующему
                moveToNextExercise()
            }
        }
    }

    private fun moveToNextExercise() {
        if (exerciseIndex < exercises.size - 1) {
            exerciseIndex++
            val nextExercise = exercises[exerciseIndex]
            _uiState.value = WorkoutExecutionState.Rest(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets,
                restTimeMillis = nextExercise.timeMillis,
                totalRestTimeMillis = nextExercise.timeMillis
            )
            startRestTimer()
        } else {
            // Все упражнения завершены
            _uiState.value = WorkoutExecutionState.Finished
        }
    }
}

sealed class WorkoutExecutionState {
    data object Loading : WorkoutExecutionState()
    
    data class Error(val message: String) : WorkoutExecutionState()
    
    data class Rest(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val restTimeMillis: Long = exercise.timeMillis,
        val totalRestTimeMillis: Long = exercise.timeMillis
    ) : WorkoutExecutionState()

    data class Active(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val weight: Int = exercise.weight,
        val reps: Int = exercise.reps
    ) : WorkoutExecutionState()
    
    data object Finished : WorkoutExecutionState()
}