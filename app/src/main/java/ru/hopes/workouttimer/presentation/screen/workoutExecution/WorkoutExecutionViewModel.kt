package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
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
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.service.TimerNotificationService
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper
import javax.inject.Inject

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    private val soundPlayer: SoundPlayer,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val vibrationManager: VibrationManager,
    private val wakeLockHelper: WakeLockHelper,
    private val workoutRepository: WorkoutRepository,
    application: Application
) : AndroidViewModel(application) {

    private var workout: Workout? = null
    var exercises: List<Exercise> = emptyList()
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

    private fun startNotification(exerciseName: String, currentSet: Int, totalSets: Int, timeLeftMillis: Long) {
        val intent = Intent(getApplication(), TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_START
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, totalSets)
            putExtra(TimerNotificationService.EXTRA_TIME_LEFT, timeLeftMillis)
        }
        getApplication<android.app.Application>().startService(intent)
    }

    private fun updateNotification(exerciseName: String, currentSet: Int, totalSets: Int, timeLeftMillis: Long) {
        val intent = Intent(getApplication(), TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_UPDATE
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, totalSets)
            putExtra(TimerNotificationService.EXTRA_TIME_LEFT, timeLeftMillis)
        }
        getApplication<android.app.Application>().startService(intent)
    }

    private fun stopNotification() {
        val intent = Intent(getApplication(), TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_STOP
        }
        getApplication<android.app.Application>().startService(intent)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

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
                _uiState.value = WorkoutExecutionState.Active(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                )
            } else {
                _uiState.value = WorkoutExecutionState.Error(
                    message = "Тренировка не найдена или не содержит упражнений"
                )
            }
        }
    }

    fun skipRest() {
        timerJob?.cancel()
        wakeLockHelper.release()
        stopNotification()
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

        wakeLockHelper.acquire()

        val finishTime = System.currentTimeMillis() + currentState.restTimeMillis

        // Запускаем уведомление с таймером
        startNotification(
            exerciseName = currentState.exercise.name,
            currentSet = currentState.currentSet,
            totalSets = currentState.totalSets,
            timeLeftMillis = currentState.restTimeMillis
        )

        timerJob = viewModelScope.launch {
            countdownFlow(finishTime)
                .onCompletion { cause ->
                    if (cause == null) {
                        onRestFinished()
                    } else {
                        wakeLockHelper.release()
                    }
                }
                .collect { timeLeft ->
                    _uiState.update { state ->
                        when (state) {
                            is WorkoutExecutionState.Rest -> state.copy(restTimeMillis = timeLeft)
                            else -> state
                        }
                    }
                    
                    // Обновляем уведомление каждые 200мс (но ограничим частоту)
                    updateNotification(
                        exerciseName = currentState.exercise.name,
                        currentSet = currentState.currentSet,
                        totalSets = currentState.totalSets,
                        timeLeftMillis = timeLeft
                    )
                }
        }
    }

    private fun countdownFlow(finishTime: Long): Flow<Long> = flow {
        while (true) {
            val currentTime = System.currentTimeMillis()

            val remaining = finishTime - currentTime

            if (remaining <= 0) {
                emit(0L)
                break
            }

            emit(remaining)
            delay(200)
        }
    }

    private fun onRestFinished() {
        timerJob?.cancel()
        stopNotification()
        soundPlayer.playSound(R.raw.timer)
        vibrationManager.vibrate()
        wakeLockHelper.release()
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets
                    )
                }
                else -> state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
        wakeLockHelper.release()
        stopNotification()
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

    fun moveToSelectedExercise(exercise: Exercise) {
        // 1. Находим реальный индекс упражнения в текущем списке
        val index = exercises.indexOfFirst { it.id == exercise.id }

        if (index != -1) {
            timerJob?.cancel() // Обязательно останавливаем текущий таймер перед сменой
            exerciseIndex = index
            val nextExercise = exercises[exerciseIndex]

            _uiState.value = WorkoutExecutionState.Active(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets
            )
            startRestTimer()
        }
    }

    fun updateExerciseNote(exerciseId: Int, note: String) {
        viewModelScope.launch {
            workoutRepository.updateExerciseNote(exerciseId, note)
            
            // Обновляем локальный список упражнений
            val exerciseIndex = exercises.indexOfFirst { it.id == exerciseId }
            if (exerciseIndex != -1) {
                val updatedExercise = exercises[exerciseIndex].copy(note = note)
                exercises = exercises.toMutableList().apply {
                    set(exerciseIndex, updatedExercise)
                }
                
                // Обновляем текущее состояние, если это текущее упражнение
                val currentState = _uiState.value
                when (currentState) {
                    is WorkoutExecutionState.Active -> {
                        if (currentState.exercise.id == exerciseId) {
                            _uiState.value = currentState.copy(
                                exercise = updatedExercise
                            )
                        }
                    }
                    is WorkoutExecutionState.Rest -> {
                        if (currentState.exercise.id == exerciseId) {
                            _uiState.value = currentState.copy(
                                exercise = updatedExercise
                            )
                        }
                    }
                    else -> {}
                }
            }
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
        val weight: Double = exercise.weight,
        val reps: Int = exercise.reps
    ) : WorkoutExecutionState()
    
    data object Finished : WorkoutExecutionState()
}