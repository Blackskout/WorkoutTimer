package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout

interface WorkoutRepository {
        fun getAllWorkouts(): Flow<List<WorkoutEntity>>
        fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>>
        suspend fun getWorkoutById(id: Int): Workout?
        suspend fun addWorkout(workout: Workout)
        suspend fun updateWorkout(workout: Workout)
        suspend fun deleteWorkout(workout: WorkoutEntity)
        fun searchWorkoutUseCase(query: String): Flow<List<WorkoutEntity>>
        suspend fun updateExerciseNote(exerciseId: Int, note: String)
}