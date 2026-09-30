package ru.hopes.workouttimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.SessionSetEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.exerciseNameKey
import ru.hopes.workouttimer.domain.model.normalizedExerciseName

@Dao
interface WorkoutDao {
    // id ASC — тай-брейк: импорт может проставить нескольким тренировкам одинаковый
    // lastUseAt, и без вторичного ключа их порядок в очереди не был бы детерминирован.
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY lastUseAt ASC, id ASC")
    fun getAllWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>

    // id ASC — тай-брейк: импорт может проставить нескольким тренировкам одинаковый
    // lastUseAt, и без вторичного ключа их порядок в очереди не был бы детерминирован.
    @Query("SELECT * FROM workouts ORDER BY lastUseAt ASC, id ASC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesByWorkoutId(workoutId: Long)

    // lastUseAt меняет только updateLastUseAt(): правка тренировки — не её выполнение
    @Query("UPDATE workouts SET name = :name WHERE id = :id")
    suspend fun updateWorkout(id: Int, name: String)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Int): WorkoutEntity?

    // workouts.id ASC — тай-брейк: импорт может проставить нескольким тренировкам
    // одинаковый lastUseAt, и без вторичного ключа их порядок в очереди не был бы
    // детерминирован.
    @Transaction
    @Query(
        """
        SELECT DISTINCT workouts.* FROM workouts JOIN exercises
        ON workouts.id == exercises.workoutId
        WHERE workouts.name LIKE '%' || :query || '%'
        OR exercises.name LIKE '%' || :query || '%'
        ORDER BY workouts.lastUseAt ASC, workouts.id ASC
        """
    )
    fun searchWorkouts(query: String): Flow<List<WorkoutWithExercises>>

    @Query("UPDATE workouts SET lastUseAt = :lastUseAt WHERE id = :id")
    suspend fun updateLastUseAt(id: Int, lastUseAt: Long)

    @Query("UPDATE exercises SET note = :note WHERE id = :id")
    suspend fun updateExerciseNote(id: Int, note: String)

    @Query("UPDATE exercises SET weight = :weight, reps = :reps WHERE id = :id")
    suspend fun updateExerciseWeightAndReps(id: Int, weight: Double, reps: Int)

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM exercise_catalog WHERE nameKey = :nameKey")
    suspend fun findCatalogByKey(nameKey: String): ExerciseCatalogEntity?

    @Query("SELECT * FROM exercise_catalog WHERE id = :id")
    suspend fun findCatalogById(id: Long): ExerciseCatalogEntity?

    @Insert
    suspend fun insertCatalog(entry: ExerciseCatalogEntity): Long

    @Query("SELECT * FROM exercise_catalog ORDER BY name")
    suspend fun getCatalog(): List<ExerciseCatalogEntity>

    @Insert
    suspend fun insertSessionSet(set: SessionSetEntity): Long

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getSessionSets(sessionId: Long): List<SessionSetEntity>

    // Запись справочника живёт, пока её используют тренировки или у неё есть подходы.
    @Query(
        """
        DELETE FROM exercise_catalog
        WHERE id NOT IN (SELECT catalogId FROM exercises)
        AND id NOT IN (SELECT catalogId FROM session_sets)
        """
    )
    suspend fun cleanupCatalog()

    /** Ищет запись по ключу названия, создаёт с единицей «кг», если её нет. */
    suspend fun findOrCreateCatalog(rawName: String): ExerciseCatalogEntity {
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        findCatalogByKey(key)?.let { return it }
        val entry = ExerciseCatalogEntity(name = name, nameKey = key, unit = ExerciseUnit.KG.name)
        return entry.copy(id = insertCatalog(entry))
    }

    // В упражнение пишется название из справочника: «присед » сохраняется как «Присед».
    private suspend fun resolveCatalog(workoutId: Long, exercises: List<ExerciseEntity>): List<ExerciseEntity> =
        exercises.map { ex ->
            val entry = findOrCreateCatalog(ex.name)
            ex.copy(workoutId = workoutId, catalogId = entry.id, name = entry.name)
        }

    @Transaction
    suspend fun insertWorkoutResolvingCatalog(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val workoutId = insertWorkout(workout)
        insertExercises(resolveCatalog(workoutId, exercises))
        cleanupCatalog()
        return workoutId
    }

    @Query("SELECT name FROM workouts")
    suspend fun getAllWorkoutNames(): List<String>

    // Весь файл — одна транзакция: сбой посреди файла не оставляет половину тренировок.
    @Transaction
    suspend fun importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseEntity>>>): Int {
        for ((workout, exercises) in workouts) {
            val workoutId = insertWorkout(workout)
            insertExercises(resolveCatalog(workoutId, exercises))
        }
        cleanupCatalog()
        return workouts.size
    }

    @Transaction
    suspend fun updateWorkoutResolvingCatalog(workoutId: Int, name: String, exercises: List<ExerciseEntity>) {
        updateWorkout(workoutId, name)
        deleteExercisesByWorkoutId(workoutId.toLong())
        insertExercises(resolveCatalog(workoutId.toLong(), exercises))
        cleanupCatalog()
    }

    // Сессии и подходы не удаляются: на них держится прогресс упражнения.
    @Transaction
    suspend fun deleteWorkoutWithExercises(workout: WorkoutEntity) {
        deleteExercisesByWorkoutId(workout.id.toLong())
        deleteWorkout(workout)
        cleanupCatalog()
    }

    // Сессия не должна потеряться из-за подхода: неизвестный catalogId
    // привязывается по названию, а FK на справочник уже не сработает.
    @Transaction
    suspend fun finishSession(session: WorkoutSessionEntity, sets: List<SessionSetDraft>): Long {
        val sessionId = insertSession(session)
        for (draft in sets) {
            val catalogId = draft.catalogId.takeIf { it > 0 && findCatalogById(it) != null }
                ?: findOrCreateCatalog(draft.exerciseName).id
            insertSessionSet(
                SessionSetEntity(
                    sessionId = sessionId,
                    catalogId = catalogId,
                    weight = draft.weight,
                    extraWeight = draft.extraWeight,
                    reps = draft.reps,
                    unit = draft.unit
                )
            )
        }
        return sessionId
    }

    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId ORDER BY finishedAt DESC")
    fun getSessionsForWorkout(workoutId: Long): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT ws.workoutId AS workoutId, ws.durationMillis AS durationMillis
        FROM workout_sessions ws
        INNER JOIN (
            SELECT workoutId, MAX(finishedAt) AS maxFinishedAt
            FROM workout_sessions
            GROUP BY workoutId
        ) latest ON ws.workoutId = latest.workoutId AND ws.finishedAt = latest.maxFinishedAt
        """
    )
    fun getLastSessionDurations(): Flow<List<LastSessionDuration>>
}