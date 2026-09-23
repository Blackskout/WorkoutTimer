package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsWithExerciseUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.SkipWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.UndoSkipWorkoutUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListWorkoutViewModel @Inject constructor(
    private val getAllWorkoutsWithExerciseUseCase: GetAllWorkoutsWithExerciseUseCase,
    private val searchWorkoutsUseCase: SearchWorkoutsUseCase,
    private val deleteWorkoutUseCase: DeleteWorkoutUseCase,
    private val getLastSessionDurationsUseCase: GetLastSessionDurationsUseCase,
    private val skipWorkoutUseCase: SkipWorkoutUseCase,
    private val undoSkipWorkoutUseCase: UndoSkipWorkoutUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(ListWorkoutState())
    val state = _state.asStateFlow()

    init {
        query
            .onEach { input ->
                _state.update { it.copy(query = input) }
            }
            .flatMapLatest { input ->
                // Триммим только здесь, для запроса в БД — поле state.query хранит
                // сырой ввод, иначе конечный пробел, набранный пользователем, стирался
                // бы на каждый символ и многословный запрос набрать было бы нельзя.
                val trimmedInput = input.trim()
                val workoutsFlow = if (trimmedInput.isBlank()) {
                    getAllWorkoutsWithExerciseUseCase()
                } else {
                    searchWorkoutsUseCase(trimmedInput)
                }
                workoutsFlow.combine(getLastSessionDurationsUseCase()) { workouts, durations ->
                    workouts to durations
                }
            }
            .onEach { (workouts, durations) ->
                _state.update { it.copy(workouts = workouts, lastSessionDurations = durations) }
            }
            .launchIn(viewModelScope)
    }

    fun updateSearchQuery(newQuery: String) {
        // Сырой текст, без trim(): поле — источник для TextField, и обрезка тут
        // съедала бы пробел сразу после ввода, не давая набрать «Грудь и трицепс».
        // Обрезка происходит там, где запрос реально уходит в БД (см. flatMapLatest).
        query.update { newQuery }
    }

    fun deleteWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            deleteWorkoutUseCase(workout)
        }
    }

    /**
     * Отправляет тренировку в конец очереди без записи в историю.
     * Прежнее время кладётся в состояние, чтобы снекбар мог предложить отмену.
     */
    fun skipWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            val previous = skipWorkoutUseCase(workout.id) ?: return@launch
            _state.update {
                it.copy(
                    skippedWorkout = SkippedWorkout(
                        id = workout.id,
                        name = workout.name,
                        previousLastUseAt = previous
                    )
                )
            }
        }
    }

    fun undoSkip() {
        val skipped = _state.value.skippedWorkout ?: return
        viewModelScope.launch {
            undoSkipWorkoutUseCase(skipped.id, skipped.previousLastUseAt)
            _state.update { it.copy(skippedWorkout = null) }
        }
    }

    fun dismissSkipUndo() {
        _state.update { it.copy(skippedWorkout = null) }
    }
}

/** Пропущенная тренировка, пока на экране висит снекбар с отменой. */
data class SkippedWorkout(
    val id: Int,
    val name: String,
    val previousLastUseAt: Long
)

data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutWithExercises> = listOf(),
    val lastSessionDurations: Map<Int, Long> = emptyMap(),
    val skippedWorkout: SkippedWorkout? = null
) {
    /** Первая в очереди — та, которую не делали дольше всех. */
    val nextWorkout: WorkoutWithExercises? get() = workouts.firstOrNull()

    /** Остальные, в порядке очереди. */
    val restOfQueue: List<WorkoutWithExercises> get() = workouts.drop(1)

    val isSearching: Boolean get() = query.isNotBlank()
}
