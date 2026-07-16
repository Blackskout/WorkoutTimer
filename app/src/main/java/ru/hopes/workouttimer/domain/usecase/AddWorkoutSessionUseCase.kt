package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class AddWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, startedAt: Long, finishedAt: Long): Long {
        return repo.addWorkoutSession(workoutId, startedAt, finishedAt)
    }
}
