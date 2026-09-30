package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class FinishWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(
        workoutId: Int,
        startedAt: Long,
        finishedAt: Long,
        durationMillis: Long,
        sets: List<RecordedSet>
    ) = repo.finishWorkoutSession(workoutId, startedAt, finishedAt, durationMillis, sets)
}
