package ru.hopes.workouttimer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.hopes.workouttimer.data.dao.SessionSetDraft
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
    private val widgetUpdater: WidgetUpdater
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
        val exerciseEntities = workout.exercises.map { ex ->
            ExerciseEntity(
                workoutId = workout.id.toLong(),
                name = ex.name,
                weight = ex.weight,
                sets = ex.sets,
                reps = ex.reps,
                restTimeMillis = ex.timeMillis,
                orderInWorkout = ex.order,
                note = ex.note,
                catalogId = 0L // проставит транзакция DAO
            )
        }
        withContext(Dispatchers.IO) {
            dao.updateWorkoutResolvingCatalog(workout.id, workout.name, exerciseEntities)
        }
        widgetUpdater.requestUpdate()
    }

    override suspend fun deleteWorkout(workout: WorkoutEntity) {
        withContext(Dispatchers.IO) {
            dao.deleteWorkoutWithExercises(workout)
        }
        widgetUpdater.requestUpdate()
    }

    override fun searchWorkoutUseCase(query: String): Flow<List<WorkoutWithExercises>> {
        return dao.searchWorkouts(query)
    }

    override suspend fun addWorkout(workout: Workout) {
        val workoutEntity = WorkoutEntity(name = workout.name, lastUseAt = workout.lastUseAt)
        val exerciseEntities = workout.exercises.map { ex ->
            ExerciseEntity(
                workoutId = 0L, // проставит транзакция DAO
                name = ex.name,
                weight = ex.weight,
                sets = ex.sets,
                reps = ex.reps,
                restTimeMillis = ex.timeMillis,
                orderInWorkout = ex.order,
                note = ex.note,
                catalogId = 0L // проставит транзакция DAO
            )
        }
        dao.insertWorkoutResolvingCatalog(workoutEntity, exerciseEntities)
        widgetUpdater.requestUpdate()
    }

    override suspend fun updateLastUseAt(workoutId: Int) {
        dao.updateLastUseAt(workoutId, System.currentTimeMillis())
        widgetUpdater.requestUpdate()
    }

    // Виджет обязан обновиться и здесь: иначе «Отменить пропуск» починит очередь
    // в приложении и оставит на домашнем экране неправильную.
    override suspend fun setLastUseAt(workoutId: Int, timestamp: Long) {
        dao.updateLastUseAt(workoutId, timestamp)
        widgetUpdater.requestUpdate()
    }

    override suspend fun getLastUseAt(workoutId: Int): Long? {
        return dao.getWorkoutById(workoutId)?.lastUseAt
    }

    override suspend fun updateExerciseNote(exerciseId: Int, note: String) {
        withContext(Dispatchers.IO) {
            dao.updateExerciseNote(exerciseId, note)
        }
    }

    override suspend fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, reps: Int) {
        withContext(Dispatchers.IO) {
            dao.updateExerciseWeightAndReps(exerciseId, weight, reps)
        }
    }

    override suspend fun finishWorkoutSession(
        workoutId: Int,
        startedAt: Long,
        finishedAt: Long,
        durationMillis: Long,
        sets: List<RecordedSet>
    ) {
        dao.finishSession(
            WorkoutSessionEntity(
                workoutId = workoutId.toLong(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis
            ),
            sets.map {
                SessionSetDraft(
                    catalogId = it.catalogId,
                    exerciseName = it.exerciseName,
                    weight = it.weight,
                    extraWeight = it.extraWeight,
                    reps = it.reps,
                    unit = it.unit.name
                )
            }
        )
    }

    override fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>> {
        return dao.getSessionsForWorkout(workoutId.toLong()).map { sessions -> sessions.map { it.toDomain() } }
    }

    override fun getLastSessionDurations(): Flow<Map<Int, Long>> {
        return dao.getLastSessionDurations().map { list ->
            list.associate { it.workoutId.toInt() to it.durationMillis }
        }
    }
}