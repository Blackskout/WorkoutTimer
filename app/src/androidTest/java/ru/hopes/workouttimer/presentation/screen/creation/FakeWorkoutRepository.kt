package ru.hopes.workouttimer.presentation.screen.creation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

/**
 * Репозиторий в памяти. Тесты этого пакета проверяют навигацию, а не хранение,
 * поэтому реализовано только то, что действительно вызывается.
 */
class FakeWorkoutRepository : WorkoutRepository {

    val added = mutableListOf<Workout>()
    val updated = mutableListOf<Workout>()
    var storedWorkout: Workout? = null

    override fun getAllWorkouts(): Flow<List<WorkoutEntity>> = flowOf(emptyList())

    override fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>> = flowOf(emptyList())

    override suspend fun getWorkoutById(id: Int): Workout? = storedWorkout

    override suspend fun addWorkout(workout: Workout) {
        added += workout
    }

    override suspend fun updateWorkout(workout: Workout) {
        updated += workout
    }

    override suspend fun deleteWorkout(workout: WorkoutEntity) = Unit

    override fun searchWorkoutUseCase(query: String): Flow<List<WorkoutWithExercises>> =
        flowOf(emptyList())

    override suspend fun updateLastUseAt(workoutId: Int) = Unit

    override suspend fun setLastUseAt(workoutId: Int, timestamp: Long) = Unit

    override suspend fun getLastUseAt(workoutId: Int): Long? = null

    override suspend fun updateExerciseNote(exerciseId: Int, note: String) = Unit

    override suspend fun addWorkoutSession(
        workoutId: Int,
        startedAt: Long,
        finishedAt: Long,
        durationMillis: Long
    ) = Unit

    override fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>> =
        flowOf(emptyList())

    override fun getLastSessionDurations(): Flow<Map<Int, Long>> = flowOf(emptyMap())
}
