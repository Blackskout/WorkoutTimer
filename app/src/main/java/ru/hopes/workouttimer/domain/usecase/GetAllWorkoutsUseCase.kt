package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetAllWorkoutsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(): Flow<List<WorkoutEntity>> {
        return repo.getAllWorkouts()
    }
}

