package ru.hopes.workouttimer.presentation.session

import ru.hopes.workouttimer.domain.model.Exercise

sealed class WorkoutExecutionState {
    data object Loading : WorkoutExecutionState()

    data object Error : WorkoutExecutionState()

    data class Rest(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val restTimeMillis: Long = exercise.timeMillis,
        val totalRestTimeMillis: Long = exercise.timeMillis
    ) : WorkoutExecutionState()

    data class Active(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val weight: Double = exercise.weight,
        val reps: Int = exercise.reps,
        val extraWeight: Double = exercise.extraWeight
    ) : WorkoutExecutionState()

    data class Finished(val durationMillis: Long) : WorkoutExecutionState()
}
