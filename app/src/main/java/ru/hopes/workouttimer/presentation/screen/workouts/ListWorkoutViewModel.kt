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
//        viewModelScope.launch {
//            var order = 1
//            addWorkoutUseCase(
//                Workout(
//                    0,
//                    "Тестовый 2",
//                    listOf(
//                        Exercise(0, "Жим ног", 180, 3, 12, 1500_000, order),
//                        Exercise(0, "Жим лежа", 35, 3, 12, 900_000, order++),
//                        Exercise(0, "Спина", 61, 3, 12, 900_000, order),
//                        Exercise(0, "Сгибание лежа", 50, 3, 12, 120_000, order++),
//                        Exercise(0, "Плечи", 8, 3, 12, 90_000, order++),
//                        Exercise(0, "Бицепс", 16, 3, 12, 90_000, order++),
//                        Exercise(0, "Трицепс велосипедои", 7, 3, 12, 90_000, order++),
//                        Exercise(0, "Спина в хаммере", 48, 3, 12, 120_000, order++),
//                    ),
//                    System.currentTimeMillis()
//                )
//            )
//        }


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

}

data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutEntity> = listOf()
)

