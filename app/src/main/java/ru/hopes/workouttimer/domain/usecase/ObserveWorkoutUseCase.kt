package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Тренировка по id как поток: после правки в редакторе и возврата просмотр показывает
 * новый состав. Нового SQL нет — так же устроен getWorkoutById. null — тренировку удалили.
 */
class ObserveWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(id: Int): Flow<Workout?> =
        repo.getAllWorkoutsWithExercise()
            .map { list -> list.find { it.workout.id == id }?.toDomain() }
            .distinctUntilChanged()
}
