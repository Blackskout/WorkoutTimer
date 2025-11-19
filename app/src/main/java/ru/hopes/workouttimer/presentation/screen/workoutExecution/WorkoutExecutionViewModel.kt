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
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import javax.inject.Inject

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    private val soundPlayer: SoundPlayer
) : ViewModel() {


    private var exercises: List<Exercise> = listOf(
        Exercise(0, "Жим лежа", 80, 3, 12, 60_000L, 1),
        Exercise(1, "Разводка гантелей", 12, 3, 15, 45_000L, 2)
    )
    private val workout = Workout(
        name = "день груди",
        exercises = exercises,
        lastUseAt = System.currentTimeMillis()
    )

    private var exerciseIndex = 0


    private val _uiState = MutableStateFlow<WorkoutExecutionState>(
        WorkoutExecutionState.Rest(
            exercise = Exercise(0, "Жим лежа", 80, 3, 12, 12_000L, 1),
            currentSet = 1,
        )
    )
    val uiState: StateFlow<WorkoutExecutionState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        if (_uiState.value is WorkoutExecutionState.Rest) {
            startRestTimer()
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
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value is WorkoutExecutionState.Rest) {
                val currentState = _uiState.value as WorkoutExecutionState.Rest

                if (currentState.restTimeMillis <= 0L) {
                    onRestFinished()
                    break
                }
                delay(1000L)
                _uiState.update { state ->
                    when (state) {
                        is WorkoutExecutionState.Rest -> {
                            state.copy(
                                restTimeMillis = state.restTimeMillis - 1000L
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
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release() // <-- Освобождаем ресурсы здесь, в нужном месте
    }

    fun onExerciseFinished() {
        // Здесь можно перейти к следующему подходу или завершить упражнение
        // Для простоты — просто вернемся в режим отдыха (если есть еще подходы)

        var shouldStartTimer = false

        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Active -> {
                    if (state.currentSet < state.totalSets) {

                        shouldStartTimer = true

                        WorkoutExecutionState.Rest(
                            exercise = state.exercise,
                            currentSet = state.currentSet + 1,
                            totalSets = state.totalSets
                        )
                    } else {
                        //Логика завершения всего упражнения— можно отправить событие навигации
                        state
                    }
                }

                else -> state
            }
        }
        if (shouldStartTimer) {
            startRestTimer()
        }

    }
}

sealed class WorkoutExecutionState {

    data class Rest(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val restTimeMillis: Long = exercise.restTimeMillis
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