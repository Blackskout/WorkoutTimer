package ru.hopes.workouttimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts")
    fun getAllWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>

    @Query("SELECT * FROM workouts")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Delete
    fun deleteWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesByWorkoutId(workoutId: Long)

    @Query("UPDATE workouts SET name = :name, lastUseAt = :lastUseAt WHERE id = :id")
    suspend fun updateWorkout(id: Int, name: String, lastUseAt: Long)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Int): WorkoutEntity?

    @Transaction
    @Query(
        """
        SELECT DISTINCT workouts.* FROM workouts JOIN exercises
        ON workouts.id == exercises.workoutId
        WHERE workouts.name LIKE '%' || :query || '%'
        OR exercises.name LIKE '%' || :query || '%'
        ORDER BY orderInWorkout DESC
        """
    )
    fun searchWorkouts(query: String): Flow<List<WorkoutEntity>>

    @Query("UPDATE exercises SET note = :note WHERE id = :exerciseId")
    suspend fun updateExerciseNote(exerciseId: Int, note: String)

    @Transaction
    suspend fun insertWorkoutWithExercises(workout: WorkoutEntity, exercises: List<ExerciseEntity>) {
        val id = insertWorkout(workout)
        insertExercises(exercises.map { it.copy(workoutId = id) })
    }
}