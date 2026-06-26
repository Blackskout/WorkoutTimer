package ru.hopes.workouttimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao
) : WorkoutRepository {


    override fun getAllWorkouts(): Flow<List<WorkoutEntity>> {
        return dao.getAllWorkouts()
    }

    override fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>> {
        return dao.getAllWorkoutsWithExercises()
    }

    override suspend fun getWorkoutById(id: Int): Workout? {
        val withExercises = dao.getAllWorkoutsWithExercises()
            .map { list -> list.find { it.workout.id == id } }
            .firstOrNull()
        return withExercises?.toDomain()
    }

    override suspend fun updateWorkout(workout: Workout) {
        TODO("Not yet implemented")
    }

    override fun deleteWorkout(workout: WorkoutEntity) {
        dao.deleteWorkout(workout)
    }

    override fun searchWorkoutUseCase(query: String): Flow<List<WorkoutEntity>> {
        return dao.searchWorkouts(query)
    }

    override suspend fun addWorkout(workout: Workout) {
        val workoutEntity = WorkoutEntity(name = workout.name, lastUseAt = workout.lastUseAt)
        val id = dao.insertWorkout(workoutEntity)
        val exerciseEntities = workout.exercises.mapIndexed { idx, ex ->
            ExerciseEntity(
                workoutId = id,
                name = ex.name,
                weight = ex.weight,
                sets = ex.sets,
                reps = ex.reps,
                restTimeMillis = ex.timeMillis,
                orderInWorkout = ex.order
            )
        }
        dao.insertExercises(exerciseEntities)
    }

    override suspend fun updateLastUseAt(workoutId: Int) {
        dao.updateLastUseAt(workoutId, System.currentTimeMillis())
    }
}