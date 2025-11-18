package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetAllWorkoutsWithExerciseUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(): Flow<List<WorkoutWithExercises>> {
        return repo.getAllWorkoutsWithExercise()
    }
}

