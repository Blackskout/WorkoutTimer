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

/** Сессия идёт: Loading, Rest или Active. Error и Finished — уже нет. */
internal val WorkoutExecutionState.isRunning: Boolean
    get() = this is WorkoutExecutionState.Loading || isInProgress

/** Тренировка в процессе — её можно свернуть, и её нельзя закрыть мимоходом. */
internal val WorkoutExecutionState.isInProgress: Boolean
    get() = this is WorkoutExecutionState.Rest || this is WorkoutExecutionState.Active

/**
 * Сводка для списка, просмотра и плашки — без тиков таймера: меняется только при старте
 * и конце сессии и при добавлении упражнения. Подписчики, которым тики не нужны, читают её,
 * а не session (та во время отдыха меняется каждые 200 мс).
 */
data class RunningWorkout(
    val workoutId: Int,
    val workoutName: String,
    val isLoading: Boolean,
    /** exercise.id исходных строк, добавленных «+ в сегодняшнюю». */
    val addedExerciseIds: Set<Int>
)

internal fun runningWorkoutOf(session: WorkoutSession): RunningWorkout? {
    val present = session as? WorkoutSession.Present ?: return null
    if (!present.phase.isRunning) return null
    return RunningWorkout(
        workoutId = present.workoutId,
        workoutName = present.workoutName,
        isLoading = present.phase is WorkoutExecutionState.Loading,
        addedExerciseIds = present.exercises.filter { it.addedToday }.map { it.exercise.id }.toSet()
    )
}
