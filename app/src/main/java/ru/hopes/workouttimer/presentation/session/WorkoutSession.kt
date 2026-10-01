package ru.hopes.workouttimer.presentation.session

import ru.hopes.workouttimer.domain.model.Exercise

/** Упражнение сессии. Позиция в списке — ключ: список только дописывается в конец. */
data class SessionExercise(
    val exercise: Exercise,
    /** Копия из другой тренировки («+ в сегодняшнюю»): правки не пишутся в БД. */
    val addedToday: Boolean = false
)

/**
 * Один снимок на всю сессию: список, индекс и фаза меняются вместе, поэтому экран
 * не увидит «индекс уже новый, список ещё старый».
 */
sealed interface WorkoutSession {
    data object None : WorkoutSession

    data class Present(
        /** Растёт на каждом start() новой сессии; по нему ViewModel закрывает только свою. */
        val sessionId: Long,
        val workoutId: Int,
        val workoutName: String,
        val exercises: List<SessionExercise>,
        val exerciseIndex: Int,
        val phase: WorkoutExecutionState,
        /** Идёт запись завершённой сессии: второй тап по последнему подходу ничего не делает. */
        val isFinishing: Boolean = false
    ) : WorkoutSession
}
