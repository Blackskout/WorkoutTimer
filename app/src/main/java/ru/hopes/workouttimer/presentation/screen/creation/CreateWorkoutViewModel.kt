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
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase
import javax.inject.Inject

@HiltViewModel
class CreateWorkoutViewModel @Inject constructor(
    private val addWorkoutUseCase: AddWorkoutUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val updateWorkoutUseCase: UpdateWorkoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWorkoutState())
    val state = _state.asStateFlow()

    private var editingWorkoutId: Int? = null
    private var editingLastUseAt: Long? = null

    /** Слепок состояния после загрузки — с ним сравнивается текущее при выходе. */
    private var savedSnapshot: CreateWorkoutState = CreateWorkoutState()

    fun processCommand(command: CreateWorkoutCommand) {
        when (command) {
            is CreateWorkoutCommand.ChangeWorkoutName -> {
                _state.update { it.copy(workoutName = command.name) }
            }

            is CreateWorkoutCommand.AddExercise -> {
                _state.update { state ->
                    val exercises = state.exercises
                    state.copy(
                        exercises = exercises + ExerciseItem(
                            id = (exercises.maxOfOrNull { it.id } ?: 0) + 1,
                            name = "",
                            weight = 0.0,
                            sets = 4,
                            reps = 12,
                            restTimeSeconds = 120,
                            note = ""
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

            is CreateWorkoutCommand.MoveExercise -> {
                _state.update { state ->
                    val items = state.exercises
                    if (command.from !in items.indices || command.to !in items.indices) {
                        return@update state
                    }
                    val reordered = items.toMutableList().apply {
                        add(command.to, removeAt(command.from))
                    }
                    // order здесь не трогаем: он присваивается при сохранении
                    // через mapIndexed, поэтому порядок списка и есть порядок упражнений.
                    state.copy(exercises = reordered)
                }
            }

            is CreateWorkoutCommand.UpdateExerciseNote -> {
                _state.update {
                    it.copy(
                        exercises = it.exercises.map { ex ->
                            if (ex.id == command.id) ex.copy(note = command.note) else ex
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
                                order = index + 1,
                                note = ex.note
                            )
                        }

                    if (_state.value.workoutName.isNotBlank() && validExercises.isNotEmpty()) {
                        val workout = Workout(
                            id = editingWorkoutId ?: 0,
                            name = _state.value.workoutName,
                            exercises = validExercises,
                            // 0 означает «ещё не делали»: при сортировке очереди по
                            // возрастанию новая тренировка встаёт первой, а не последней.
                            lastUseAt = editingLastUseAt ?: 0L
                        )

                        if (editingWorkoutId != null) {
                            updateWorkoutUseCase(workout)
                        } else {
                            addWorkoutUseCase(workout)
                        }
                        _state.update { it.copy(isFinished = true) }
                    }
                }
            }

            CreateWorkoutCommand.Back -> {
                _state.update { it.copy(isFinished = true) }
            }
        }

        if (command !is CreateWorkoutCommand.Save && command !is CreateWorkoutCommand.Back) {
            _state.update { current ->
                val changed = current.workoutName != savedSnapshot.workoutName ||
                        current.exercises != savedSnapshot.exercises
                if (current.hasUnsavedChanges == changed) current else current.copy(hasUnsavedChanges = changed)
            }
        }
    }

    fun loadWorkout(workoutId: Int) {
        viewModelScope.launch {
            val workout = getWorkoutByIdUseCase(workoutId)
            workout?.let { w ->
                editingWorkoutId = w.id
                editingLastUseAt = w.lastUseAt
                _state.update {
                    CreateWorkoutState(
                        workoutName = w.name,
                        exercises = w.exercises.map { ex ->
                            ExerciseItem(
                                id = ex.id,
                                name = ex.name,
                                weight = ex.weight,
                                sets = ex.sets,
                                reps = ex.reps,
                                restTimeSeconds = (ex.timeMillis / 1000).toInt(),
                                note = ex.note
                            )
                        },
                        isFinished = false
                    )
                }
                savedSnapshot = _state.value
            }
        }
    }
}

sealed interface CreateWorkoutCommand {
    data class ChangeWorkoutName(val name: String) : CreateWorkoutCommand
    data class UpdateExercise(val id: Int, val exercise: ExerciseItem) : CreateWorkoutCommand
    data class AddExercise(val dummy: Unit = Unit) : CreateWorkoutCommand
    data class RemoveExercise(val id: Int) : CreateWorkoutCommand
    data class MoveExercise(val from: Int, val to: Int) : CreateWorkoutCommand
    data class UpdateExerciseNote(val id: Int, val note: String) : CreateWorkoutCommand
    data object Save : CreateWorkoutCommand
    data object Back : CreateWorkoutCommand
}

data class ExerciseItem(
    val id: Int,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeSeconds: Int,
    val note: String = ""
)

data class CreateWorkoutState(
    val workoutName: String = "",
    val exercises: List<ExerciseItem> = emptyList(),
    val isFinished: Boolean = false,
    val hasUnsavedChanges: Boolean = false
) {
    val isSaveEnabled: Boolean
        get() = workoutName.isNotBlank() && exercises.any { it.name.isNotBlank() }
}
