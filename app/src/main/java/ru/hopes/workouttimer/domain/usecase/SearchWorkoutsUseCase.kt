package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class SearchWorkoutsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(query: String): Flow<List<WorkoutEntity>> {
        return repo.searchWorkoutUseCase(query)
    }
}