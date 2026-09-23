package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession

interface WorkoutRepository {
        fun getAllWorkouts(): Flow<List<WorkoutEntity>>
        fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>>
        suspend fun getWorkoutById(id: Int): Workout?
        suspend fun addWorkout(workout: Workout)
        suspend fun updateWorkout(workout: Workout)
        suspend fun deleteWorkout(workout: WorkoutEntity)
        fun searchWorkoutUseCase(query: String): Flow<List<WorkoutWithExercises>>
        suspend fun updateLastUseAt(workoutId: Int)
        suspend fun setLastUseAt(workoutId: Int, timestamp: Long)
        suspend fun getLastUseAt(workoutId: Int): Long?
        suspend fun updateExerciseNote(exerciseId: Int, note: String)
        suspend fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, reps: Int)
        suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long)
        fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>>
        fun getLastSessionDurations(): Flow<Map<Int, Long>>
}