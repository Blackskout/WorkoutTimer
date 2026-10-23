package ru.hopes.workouttimer.presentation.session

/** Сколько тренировки прошло и примерно осталось — по долгому нажатию на экране выполнения. */
data class WorkoutEta(
    val elapsedMillis: Long,
    val remainingMillis: Long,
    val remainingSets: Int
)

/** Сколько длится сам подход, пока темпа сегодня ещё нет. */
internal const val SET_WORK_MILLIS = 45_000L

/**
 * Подходов осталось по упражнениям (тот же порядок, что и список): у текущего — от подхода
 * на экране (так считается и повтор сделанного), у остальных — несделанные.
 */
internal fun setsLeftOf(exercises: List<SessionExercise>, currentIndex: Int, phase: WorkoutExecutionState): List<Int> {
    val currentSet = when (phase) {
        is WorkoutExecutionState.Active -> phase.currentSet
        is WorkoutExecutionState.Rest -> phase.currentSet
        else -> return exercises.map { 0 }
    }
    return exercises.mapIndexed { index, row ->
        val left = if (index == currentIndex) row.exercise.sets - currentSet + 1 else row.exercise.sets - row.doneSets
        left.coerceAtLeast(0)
    }
}

/**
 * Оценка остатка. Есть закрытые подходы — по своему темпу сегодня (подход вместе с отдыхом
 * и переходами); нет — по шаблону: отдых упражнения плюс [SET_WORK_MILLIS] на сам подход.
 */
internal fun workoutEtaOf(
    exercises: List<SessionExercise>,
    currentIndex: Int,
    phase: WorkoutExecutionState,
    elapsedMillis: Long,
    closedSets: Int
): WorkoutEta {
    val left = setsLeftOf(exercises, currentIndex, phase)
    val remainingSets = left.sum()
    val remainingMillis = if (closedSets > 0) {
        // В отдыхе последний закрытый подход ещё без своего отдыха — темп считается так,
        // будто отдых уже прошёл, иначе он выходит заниженным.
        val restLeft = (phase as? WorkoutExecutionState.Rest)?.restTimeMillis ?: 0L
        (elapsedMillis + restLeft) / closedSets * remainingSets
    } else {
        exercises.indices.sumOf { left[it] * (exercises[it].exercise.timeMillis + SET_WORK_MILLIS) }
    }
    return WorkoutEta(elapsedMillis, remainingMillis, remainingSets)
}
