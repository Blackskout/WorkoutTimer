package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Помечает тренировку как сделанную сегодня, отправляя её в конец очереди,
 * но НЕ создаёт сессию: пропуск не должен попадать в историю и статистику.
 *
 * @return прежний `lastUseAt` для отмены, либо `null`, если тренировки нет.
 */
class SkipWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int): Long? {
        val previous = repo.getLastUseAt(workoutId) ?: return null
        repo.setLastUseAt(workoutId, System.currentTimeMillis())
        return previous
    }
}
