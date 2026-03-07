package ru.hopes.workouttimer.presentation.screen.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import javax.inject.Inject

@HiltViewModel
class CreateWorkoutViewModel @Inject constructor(
    private val addWorkoutUseCase: AddWorkoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWorkoutState())
    val state = _state.asStateFlow()

    fun processCommand(command: CreateWorkoutCommand) {
        when (command) {
            is CreateWorkoutCommand.ChangeWorkoutName -> {
                _state.update { it.copy(workoutName = command.name) }
            }

            is CreateWorkoutCommand.AddExercise -> {
                _state.update {
                    it.copy(
                        exercises = it.exercises + ExerciseItem(
                            id = System.currentTimeMillis().toInt(),
                            name = "",
                            weight = 0.0,
                            sets = 4,
                            reps = 12,
                            restTimeSeconds = 120
                        )
                    )
                }
            }

            is CreateWorkoutCommand.RemoveExercise -> {
                _state.update {
                    it.copy(exercises = it.exercises.filter { ex -> ex.id != command.id })
                }
            }

            is CreateWorkoutCommand.UpdateExercise -> {
                _state.update {
                    it.copy(
                        exercises = it.exercises.map { ex ->
                            if (ex.id == command.id) command.exercise else ex
                        }
                    )
                }
            }

            CreateWorkoutCommand.Save -> {
                viewModelScope.launch {
                    val validExercises = _state.value.exercises
                        .filter { it.name.isNotBlank() }
                        .mapIndexed { index, ex ->
                            Exercise(
                                id = 0,
                                name = ex.name,
                                weight = ex.weight,
                                sets = ex.sets,
                                reps = ex.reps,
                                timeMillis = ex.restTimeSeconds * 1000L,
                                order = index + 1
                            )
                        }

                    if (_state.value.workoutName.isNotBlank() && validExercises.isNotEmpty()) {
                        val workout = Workout(
                            id = 0,
                            name = _state.value.workoutName,
                            exercises = validExercises,
                            lastUseAt = System.currentTimeMillis()
                        )
                        addWorkoutUseCase(workout)
                        _state.update { it.copy(isFinished = true) }
                    }
                }
            }

            CreateWorkoutCommand.Back -> {
                _state.update { it.copy(isFinished = true) }
            }
        }
    }
}

sealed interface CreateWorkoutCommand {
    data class ChangeWorkoutName(val name: String) : CreateWorkoutCommand
    data class UpdateExercise(val id: Int, val exercise: ExerciseItem) : CreateWorkoutCommand
    data class AddExercise(val dummy: Unit = Unit) : CreateWorkoutCommand
    data class RemoveExercise(val id: Int) : CreateWorkoutCommand
    data object Save : CreateWorkoutCommand
    data object Back : CreateWorkoutCommand
}

data class ExerciseItem(
    val id: Int,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeSeconds: Int
)

data class CreateWorkoutState(
    val workoutName: String = "",
    val exercises: List<ExerciseItem> = emptyList(),
    val isFinished: Boolean = false
) {
    val isSaveEnabled: Boolean
        get() = workoutName.isNotBlank() && exercises.any { it.name.isNotBlank() }
}
