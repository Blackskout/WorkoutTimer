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
//            var order = 1
//            addWorkoutUseCase(
//                Workout(
//                    0,
//                    "Тестовый 3",
//                    listOf(
//                        Exercise(0, "Жим ног", 180, 3, 12, 1500_000, order),
//                        Exercise(0, "Жим лежа", 35, 3, 12, 900_000, order++),
//                        Exercise(0, "Спина", 61, 3, 12, 900_000, order++),
//                        Exercise(0, "Сгибание лежа", 50, 3, 12, 120_000, order++),
//                        Exercise(0, "Плечи", 8, 3, 12, 90_000, order++),
//                        Exercise(0, "Бицепс", 16, 3, 12, 90_000, order++),
//                        Exercise(0, "Трицепс велосипедои", 7, 3, 12, 90_000, order++),
//                        Exercise(0, "Спина в хаммере", 48, 3, 12, 120_000, order++),
//                    ),
//                    System.currentTimeMillis()
//                )
//            )


//            addWorkoutUseCase(
//
//                    // ПОНЕДЕЛЬНИК
//                    Workout(
//                        id = 1,
//                        name = "Понедельник",
//                        lastUseAt = System.currentTimeMillis(),
//                        exercises = listOf(
//                            Exercise(id = 0, name = "Ягодичный мост", weight = 35.0, sets = 3, reps = 12, timeMillis = 150_000, order = 1),
//                            Exercise(id = 0, name = "Жим лёжа (скамья на 5)", weight = 25.0, sets = 3, reps = 12, timeMillis = 150_000 , order = 2),
//                            Exercise(id = 0, name = "Тяга верхнего блока шир. хватом", weight = 61.0, sets = 3, reps = 12, order = 3),
//                            Exercise(id = 0, name = "Экстензия", weight = 12.5, sets = 3, reps = 12, order = 4),
//                            Exercise(id = 0, name = "Трицепс с велосипедом", weight = 7.0, sets = 3, reps = 12, order = 5),
//                            Exercise(id = 0, name = "Плечи", weight = 8.0, sets = 3, reps = 12, order = 6),
//                            Exercise(id = 0, name = "Бицепс", weight = 13.0, sets = 3, reps = 12, order = 7)
//                        )
//                    )
//            )
//
//            addWorkoutUseCase(
//
//                // СРЕДА
//                Workout(
//                    id = 2,
//                    name = "Среда",
//                    lastUseAt = System.currentTimeMillis(),
//                    exercises = listOf(
//                        Exercise(id = 0, name = "Тяга верхнего блока шир. хватом", weight = 61.0, sets = 3, reps = 12, order = 1),
//                        Exercise(id = 0, name = "Жим лёжа (скамья на 5)", weight = 25.0, sets = 3, reps = 12, timeMillis = 150_000, order = 2),
//                        Exercise(id = 0, name = "Ноги разгибание", weight = 88.0, sets = 3, reps = 12, timeMillis = 150_000, order = 3),
//                        Exercise(id = 0, name = "Ноги сгибание", weight = 70.0, sets = 3, reps = 12, timeMillis = 150_000, order = 4),
//                        Exercise(id = 0, name = "Плечи", weight = 8.0, sets = 3, reps = 12, order = 5),
//                        Exercise(id = 0, name = "Трицепс с велосипедом", weight = 7.0, sets = 3, reps = 12, order = 6),
//                        Exercise(id = 0, name = "Грудь горизонтальная тяга", weight = 35.0, sets = 3, reps = 12, order = 7),
//                        Exercise(id = 0, name = "Бицепс", weight = 13.0, sets = 3, reps = 12, order = 8)
//                    )
//                )
//            )
//
//            addWorkoutUseCase(
//
//                // ПЯТНИЦА
//                Workout(
//                    id = 3,
//                    name = "Пятница",
//                    lastUseAt = System.currentTimeMillis(),
//                    exercises = listOf(
//                        Exercise(id = 0, name = "Жим ног", weight = 180.0, sets = 3, reps = 12, timeMillis = 150_000, order = 1),
//                        Exercise(id = 0, name = "Жим лёжа (скамья на 5)", weight = 25.0, sets = 3, reps = 12, timeMillis = 150_000, order = 2),
//                        Exercise(id = 0, name = "Тяга верхнего блока шир. хватом", weight = 61.0, sets = 3, reps = 12, order = 3),
//                        Exercise(id = 0, name = "Сгибание лежа", weight = 50.0, sets = 3, reps = 12, timeMillis = 150_000, order = 4),
//                        Exercise(id = 0, name = "Плечи", weight = 8.0, sets = 3, reps = 12, order = 5),
//                        Exercise(id = 0, name = "Бицепс", weight = 13.0, sets = 3, reps = 12, order = 6),
//                        Exercise(id = 0, name = "Трицепс с велосипедом", weight = 7.0, sets = 3, reps = 12, order = 7),
//                        Exercise(id = 0, name = "Хаммер на спину", weight = 47.5, sets = 3, reps = 12, timeMillis = 150_000, order = 8)
//                    )
//                )
//            )


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

}

data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutEntity> = listOf()
)

