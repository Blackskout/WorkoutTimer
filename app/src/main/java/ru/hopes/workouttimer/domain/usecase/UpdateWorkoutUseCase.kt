package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class UpdateWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workout: Workout) {
        repo.updateWorkout(workout)
    }
}