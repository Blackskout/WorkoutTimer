package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetLastSessionDurationsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(): Flow<Map<Int, Long>> {
        return repo.getLastSessionDurations()
    }
}
