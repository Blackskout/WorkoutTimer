package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListWorkoutViewModel @Inject constructor(
    private val getAllWorkoutsUseCase: GetAllWorkoutsUseCase,
    private val searchWorkoutsUseCase: SearchWorkoutsUseCase,
    private val addWorkoutUseCase: AddWorkoutUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(ListWorkoutState())
    val state = _state.asStateFlow()

    init {

        viewModelScope.launch {
            addWorkoutUseCase(
                Workout(
                    0,
                    "Arm day",
                    listOf(Exercise(0, "Жим лежа", 3, 80, 12, 120, 1)),
                    System.currentTimeMillis()
                )
            )
        }


        query
            .onEach { input ->
                _state.update { it.copy(query = input) }
            }
            .flatMapLatest { input ->
                if (input.isBlank()) {
                    getAllWorkoutsUseCase()
                } else {
                    searchWorkoutsUseCase(input)
                }
            }.onEach { workouts ->
                _state.update { it.copy(workouts = workouts) }
            }
            .launchIn(viewModelScope)
    }


    fun updateSearchQuery(newQuery: String) {
        query.update { newQuery.trim() }
    }


    fun processCommand(command: WorkoutCommand) {

        viewModelScope.launch {
            when (command) {
                is WorkoutCommand.InputSearchQuery -> {
                    query.update { command.query.trim() }
                }
            }
        }
    }
}

sealed interface WorkoutCommand {

    data class InputSearchQuery(val query: String) : WorkoutCommand

}

data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutEntity> = listOf()
)


//    private var workout: Workout? = null
//    private var currentExerciseIndex = 0
//    private var currentSet = 1
//
//
//    fun onSetCompleted() {
//        val ex = workout?.exercises?.getOrNull(currentExerciseIndex) ?: return
//        if (currentSet < ex.sets) {
//            currentSet++
//            startRest(ex.restSeconds)
//            _state.update { it.copy(currentSet = currentSet, isResting = true, restSecondsLeft = ex.restSeconds) }
//        } else {
//            // переход к следующему упражнения
//            currentExerciseIndex++
//            currentSet = 1
//            val next = workout?.exercises?.getOrNull(currentExerciseIndex)
//            val nextNext = workout?.exercises?.getOrNull(currentExerciseIndex + 1)
//            _state.update { it.copy(
//                currentExercise = next,
//                nextExercise = nextNext,
//                currentSet = currentSet,
//                isResting = false,
//                restSecondsLeft = 0
//            )}
//        }
//    }
//
//    private var restJob: Job? = null
//    private fun startRest(seconds: Int) {
//        restJob?.cancel()
//        restJob = viewModelScope.launch {
//            var left = seconds
//            while (left > 0) {
//                _state.update { it.copy(restSecondsLeft = left) }
//                delay(1000)
//                left--
//            }
//            _state.update { it.copy(isResting = false, restSecondsLeft = 0) }
//        }
//    }
//
//    fun stopRest() {
//        restJob?.cancel()
//        _state.update { it.copy(isResting = false, restSecondsLeft = 0) }
//    }
//}
//
//data class ListWorkoutState(
//    val query: String = "",
//    val workouts: List<Workout> = listOf()
////    val workoutName: String = "",
////    val currentExercise: Exercise? = null,
////    val nextExercise: Exercise? = null,
////    val currentSet: Int = 1,
////    val isResting: Boolean = false,
////    val restSecondsLeft: Int = 0
//)

