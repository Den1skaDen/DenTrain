package com.dentrain.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert suspend fun insertWorkout(w: WorkoutEntity): Long
    @Update suspend fun updateWorkout(w: WorkoutEntity)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: Long)
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun getWorkout(id: Long): WorkoutEntity?

    @Insert suspend fun insertExercise(e: ExerciseEntity): Long
    @Update suspend fun updateExercise(e: ExerciseEntity)
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun deleteExercise(id: Long)
    @Query("SELECT id FROM exercises WHERE workoutId = :workoutId") suspend fun getExerciseIds(workoutId: Long): List<Long>

    @Insert suspend fun insertSet(s: SetResultEntity): Long
    @Update suspend fun updateSet(s: SetResultEntity)
    @Query("DELETE FROM set_results WHERE id = :id") suspend fun deleteSet(id: Long)
    @Query("SELECT * FROM set_results WHERE exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun getSets(exerciseId: Long): List<SetResultEntity>

    @Transaction @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutWithExercises?>

    @Transaction @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getFull(id: Long): WorkoutWithExercises?

    @Transaction @Query("SELECT * FROM workouts WHERE status = :status ORDER BY dateEpochDay DESC, id DESC")
    fun observeByStatus(status: Int): Flow<List<WorkoutWithExercises>>

    @Transaction @Query("SELECT * FROM workouts WHERE status = :status ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getOnceByStatus(status: Int): List<WorkoutWithExercises>

    @Transaction @Query("SELECT * FROM workouts")
    fun observeAll(): Flow<List<WorkoutWithExercises>>

    @Query("SELECT DISTINCT name FROM exercises WHERE name != '' ORDER BY name ASC")
    fun observeRecentExerciseNames(): Flow<List<String>>


    @Transaction
    @Query(
        "SELECT w.* FROM workouts w INNER JOIN pinned_workouts p ON p.workoutId = w.id " +
            "ORDER BY p.pinnedAt DESC"
    )
    fun observePinned(): Flow<List<WorkoutWithExercises>>

    @Query("SELECT workoutId FROM pinned_workouts") fun observePinnedIds(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun pin(p: PinnedWorkoutEntity)
    @Query("DELETE FROM pinned_workouts WHERE workoutId = :id") suspend fun unpin(id: Long)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records")
    suspend fun getOnceAll(): List<RecordEntity>
    @Insert suspend fun insert(r: RecordEntity): Long
    @Update suspend fun update(r: RecordEntity)
    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: Long)
}


@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1") fun observe(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun getOnce(): SettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(s: SettingsEntity)
}
