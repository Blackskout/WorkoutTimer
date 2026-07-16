package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetWorkoutSessionsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(workoutId: Int): Flow<List<WorkoutSession>> {
        return repo.getSessionsForWorkout(workoutId)
    }
}
