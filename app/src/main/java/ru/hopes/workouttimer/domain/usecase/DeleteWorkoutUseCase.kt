package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class DeleteWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(workout: WorkoutEntity) {
        repo.deleteWorkout(workout)
    }
}