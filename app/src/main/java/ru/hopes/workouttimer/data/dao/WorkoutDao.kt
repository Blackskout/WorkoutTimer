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
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.model.convertLoadToUnit
import ru.hopes.workouttimer.domain.model.exerciseNameKey
import ru.hopes.workouttimer.domain.model.exerciseUnitOf
import ru.hopes.workouttimer.domain.model.fitLoadToUnit
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

    @Query("UPDATE exercises SET weight = :weight, extraWeight = :extraWeight, reps = :reps WHERE id = :id")
    suspend fun updateExerciseWeightAndReps(id: Int, weight: Double, extraWeight: Double, reps: Int)

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

    /** Ищет запись по ключу названия; если её нет — создаёт с единицей [unitIfNew]. */
    suspend fun findOrCreateCatalog(rawName: String, unitIfNew: ExerciseUnit): ExerciseCatalogEntity {
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        findCatalogByKey(key)?.let { return it }
        val entry = ExerciseCatalogEntity(name = name, nameKey = key, unit = unitIfNew.name)
        return entry.copy(id = insertCatalog(entry))
    }

    // В упражнение пишется название из справочника: «присед » сохраняется как «Присед».
    // Нагрузка приводится к единице найденной записи — она главнее того, что пришло
    // из редактора или файла: у плиты целый номер 1..30, у кг и без веса нет добавки.
    private suspend fun resolveCatalog(workoutId: Long, drafts: List<ExerciseDraft>): List<ExerciseEntity> =
        drafts.map { draft ->
            val entry = findOrCreateCatalog(draft.exercise.name, draft.unitIfNew)
            val load = fitLoadToUnit(draft.exercise.weight, draft.exercise.extraWeight, exerciseUnitOf(entry.unit))
            draft.exercise.copy(
                workoutId = workoutId,
                catalogId = entry.id,
                name = entry.name,
                weight = load.weight,
                extraWeight = load.extraWeight
            )
        }

    // Редактор создаёт новые записи в кг: единицу меняют только на экране «Упражнения».
    private fun editorDrafts(exercises: List<ExerciseEntity>): List<ExerciseDraft> =
        exercises.map { ExerciseDraft(it, ExerciseUnit.KG) }

    @Transaction
    suspend fun insertWorkoutResolvingCatalog(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val workoutId = insertWorkout(workout)
        insertExercises(resolveCatalog(workoutId, editorDrafts(exercises)))
        cleanupCatalog()
        return workoutId
    }

    @Query("SELECT name FROM workouts")
    suspend fun getAllWorkoutNames(): List<String>

    // Весь файл — одна транзакция: сбой посреди файла не оставляет половину тренировок.
    @Transaction
    suspend fun importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseDraft>>>): Int {
        for ((workout, drafts) in workouts) {
            val workoutId = insertWorkout(workout)
            insertExercises(resolveCatalog(workoutId, drafts))
        }
        cleanupCatalog()
        return workouts.size
    }

    @Transaction
    suspend fun updateWorkoutResolvingCatalog(workoutId: Int, name: String, exercises: List<ExerciseEntity>) {
        updateWorkout(workoutId, name)
        deleteExercisesByWorkoutId(workoutId.toLong())
        insertExercises(resolveCatalog(workoutId.toLong(), editorDrafts(exercises)))
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
                ?: findOrCreateCatalog(draft.exerciseName, exerciseUnitOf(draft.unit)).id
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

    // Порядок задаёт Kotlin (по ключу названия): ORDER BY name в SQLite сравнивает байты
    // и поставил бы «Ёлочку» перед «А», а заглавные — перед строчными.
    @Query("SELECT * FROM exercise_catalog")
    fun observeCatalog(): Flow<List<ExerciseCatalogEntity>>

    @Query("UPDATE exercise_catalog SET name = :name, nameKey = :nameKey WHERE id = :id")
    suspend fun updateCatalogName(id: Long, name: String, nameKey: String)

    @Query("UPDATE exercises SET name = :name WHERE catalogId = :catalogId")
    suspend fun updateExerciseNamesOfCatalog(catalogId: Long, name: String)

    @Query("UPDATE exercise_catalog SET unit = :unit WHERE id = :id")
    suspend fun updateCatalogUnit(id: Long, unit: String)

    @Query("SELECT * FROM exercises WHERE catalogId = :catalogId")
    suspend fun getExercisesOfCatalog(catalogId: Long): List<ExerciseEntity>

    @Query("UPDATE exercises SET weight = :weight, extraWeight = :extraWeight WHERE id = :id")
    suspend fun updateExerciseLoad(id: Int, weight: Double, extraWeight: Double)

    // Название — текущее из справочника: после переименования история показывает новое.
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE ws.workoutId = :workoutId
        ORDER BY s.id
        """
    )
    fun observeSetsForWorkout(workoutId: Long): Flow<List<LoggedSetRow>>

    // Для каждой записи справочника — её подходы из последней по времени завершения
    // сессии (id — тай-брейк при одинаковом finishedAt).
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE s.sessionId = (
            SELECT s2.sessionId FROM session_sets s2
            JOIN workout_sessions w2 ON w2.id = s2.sessionId
            WHERE s2.catalogId = s.catalogId
            ORDER BY w2.finishedAt DESC, w2.id DESC
            LIMIT 1
        )
        ORDER BY s.id
        """
    )
    fun observeLastSessionSets(): Flow<List<LoggedSetRow>>

    // Поток, а не разовый запрос: после переименования или смены единицы на экране
    // «Упражнения» прогресс при возврате показывает новое название и единицу.
    @Query("SELECT * FROM exercise_catalog WHERE id = :id")
    fun observeCatalogEntry(id: Long): Flow<ExerciseCatalogEntity?>

    // Все подходы записи из всех тренировок, включая удалённые: сессии и подходы
    // живут дольше тренировки. JOIN только с workout_sessions — не с workouts.
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE s.catalogId = :catalogId
        ORDER BY ws.finishedAt, ws.id, s.id
        """
    )
    fun observeSetsForCatalog(catalogId: Long): Flow<List<LoggedSetRow>>

    /**
     * Переименование записи и всех её упражнений одной транзакцией: поиск тренировок
     * читает exercises.name. Ключ, занятый этой же записью («присед» → «Присед»),
     * занятым не считается.
     */
    @Transaction
    suspend fun renameCatalog(id: Long, rawName: String): RenameResult {
        if (rawName.isBlank()) return RenameResult.BLANK
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        val holder = findCatalogByKey(key)
        if (holder != null && holder.id != id) return RenameResult.NAME_TAKEN
        updateCatalogName(id, name, key)
        updateExerciseNamesOfCatalog(id, name)
        return RenameResult.RENAMED
    }

    /**
     * Смена единицы: шаблоны приводятся к новой, прошлые подходы сохраняют свою.
     * Повторный выбор той же единицы ничего не делает — иначе convertLoadToUnit
     * обнулил бы добавку к плите.
     */
    @Transaction
    suspend fun changeCatalogUnit(id: Long, unit: ExerciseUnit) {
        val entry = findCatalogById(id) ?: return
        if (entry.unit == unit.name) return
        updateCatalogUnit(id, unit.name)
        for (exercise in getExercisesOfCatalog(id)) {
            val load = convertLoadToUnit(exercise.weight, exercise.extraWeight, unit)
            updateExerciseLoad(exercise.id, load.weight, load.extraWeight)
        }
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