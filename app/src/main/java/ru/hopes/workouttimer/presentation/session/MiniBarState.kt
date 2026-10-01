package ru.hopes.workouttimer.presentation.session

/** Что показывает плашка свёрнутой тренировки. */
data class MiniBarState(
    val workoutName: String,
    val status: MiniBarStatus
)

sealed interface MiniBarStatus {
    /** Идёт отдых: «Отдых 01:18», кольцо — доля оставшегося времени. */
    data class Resting(val timeLeftMillis: Long, val totalMillis: Long) : MiniBarStatus

    /** Идёт подход: «Подход 2 из 4 · Жим лёжа». */
    data class Working(val currentSet: Int, val totalSets: Int, val exerciseName: String) : MiniBarStatus

    /** Отдых закончился сам: «Пора: подход 2». */
    data class RestOver(val nextSet: Int) : MiniBarStatus
}

/** Плашка есть только в Rest и Active: в Loading, Error и Finished тренировка не идёт. */
fun miniBarStateOf(session: WorkoutSession): MiniBarState? {
    val present = session as? WorkoutSession.Present ?: return null
    val status = when (val phase = present.phase) {
        is WorkoutExecutionState.Rest ->
            MiniBarStatus.Resting(phase.restTimeMillis, phase.totalRestTimeMillis)
        is WorkoutExecutionState.Active ->
            if (present.restOver) {
                MiniBarStatus.RestOver(phase.currentSet)
            } else {
                MiniBarStatus.Working(phase.currentSet, phase.totalSets, phase.exercise.name)
            }
        else -> return null
    }
    return MiniBarState(present.workoutName, status)
}

/** Заполненная доля кольца: остаток отдыха, пусто в подходе, полное — «пора». */
fun ringFraction(status: MiniBarStatus): Float = when (status) {
    is MiniBarStatus.Resting ->
        if (status.totalMillis > 0L) {
            (status.timeLeftMillis.toFloat() / status.totalMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    is MiniBarStatus.Working -> 0f
    is MiniBarStatus.RestOver -> 1f
}
