package ru.hopes.workouttimer.presentation.session

import ru.hopes.workouttimer.domain.model.Exercise

/** Упражнение сессии. Позиция в списке — ключ: список только дописывается в конец. */
data class SessionExercise(
    val exercise: Exercise,
    /** Копия из другой тренировки («+ в сегодняшнюю»): правки не пишутся в БД. */
    val addedToday: Boolean = false,
    /** Сколько подходов сделано: наибольший закрытый номер подхода, повтор его не растит. */
    val doneSets: Int = 0
) {
    val isDone: Boolean get() = doneSets >= exercise.sets

    /** С какого подхода продолжать: начатое — со следующего, сделанное — повтор с первого. */
    val nextSet: Int get() = if (isDone) 1 else doneSets + 1
}

/** Суперсет на сегодня: после подхода [lead] — переход к [second], после [second] — отдых. */
data class Superset(val lead: Int, val second: Int) {
    fun contains(index: Int): Boolean = index == lead || index == second
}

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
        val isFinishing: Boolean = false,
        /** Active наступил сам по истечении отдыха: плашка пишет «Пора: подход N». */
        val restOver: Boolean = false,
        /** Пары суперсетов по индексам; список упражнений только дописывается, индексы не съезжают. */
        val supersets: List<Superset> = emptyList()
    ) : WorkoutSession {
        fun supersetOf(index: Int): Superset? = supersets.firstOrNull { it.contains(index) }
    }
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

/** Итог «+ в сегодняшнюю». */
enum class AddResult { ADDED, ALREADY_ADDED, NO_SESSION }
