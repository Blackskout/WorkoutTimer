package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/** Возвращает тренировке `lastUseAt`, который был до пропуска. */
class UndoSkipWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, previousLastUseAt: Long) {
        repo.setLastUseAt(workoutId, previousLastUseAt)
    }
}
