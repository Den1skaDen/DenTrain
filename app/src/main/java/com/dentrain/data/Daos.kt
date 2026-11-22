package com.dentrain.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class WorkingSetForProgress(val completedAt: Long, val weight: Double, val reps: Int)

data class WorkoutListEntry(
    @Embedded val workout: WorkoutEntity,
    val exercisesCount: Int,
    val setsCount: Int
)

private const val WORKOUT_LIST_COLUMNS = """w.*,
    (SELECT COUNT(*) FROM exercises e WHERE e.workoutId = w.id) AS exercisesCount,
    (SELECT COUNT(*) FROM exercises e JOIN set_results s ON s.exerciseId = e.id
        WHERE e.workoutId = w.id) AS setsCount"""

@Dao
interface WorkoutDao {
    @Insert suspend fun insertWorkout(w: WorkoutEntity): Long
    @Update suspend fun updateWorkout(w: WorkoutEntity)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: Long)
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun getWorkout(id: Long): WorkoutEntity?
    @Query("SELECT * FROM workouts WHERE cloudId = :cloudId ORDER BY id LIMIT 1")
    suspend fun findCloudWorkout(cloudId: String): WorkoutEntity?
    @Query("SELECT id FROM workouts WHERE assignmentId = :assignmentId ORDER BY id LIMIT 1")
    suspend fun findAssignment(assignmentId: String): Long?

    @Query(
        "SELECT * FROM workouts WHERE status = :status AND cloudId IS NULL " +
            "AND completedAt = :completedAt AND title = :title AND dateEpochDay = :dateEpochDay LIMIT 2"
    )
    suspend fun findLegacyWorkouts(status: Int, completedAt: Long, title: String, dateEpochDay: Long): List<WorkoutEntity>

    @Query("UPDATE workouts SET status = :status, currentExerciseIndex = 0, startedAt = :startedAt WHERE id = :id")
    suspend fun startWorkout(id: Long, status: Int, startedAt: Long)

    @Query("UPDATE workouts SET currentExerciseIndex = :position WHERE id = :id")
    suspend fun updateCurrentExercise(id: Long, position: Int)

    @Query("UPDATE workouts SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String)

    @Insert suspend fun insertExercise(e: ExerciseEntity): Long
    @Query("SELECT name FROM exercises WHERE id = :id")
    suspend fun getExerciseName(id: Long): String
    @Update suspend fun updateExercise(e: ExerciseEntity)
    @Query("UPDATE exercises SET plannedSets = plannedSets + 1 WHERE id = :exerciseId")
    suspend fun increasePlannedSets(exerciseId: Long)

    @Query("""
        UPDATE exercises SET plannedSets = plannedSets - 1
        WHERE id = :exerciseId AND plannedSets > 1 AND plannedSets > (
            SELECT COUNT(*) FROM set_results WHERE exerciseId = :exerciseId AND isWarmup = 0
        )
    """)
    suspend fun removePendingPlannedSet(exerciseId: Long)

    @Query("UPDATE exercises SET plannedSets = plannedSets - 1 WHERE id = :exerciseId AND plannedSets >= :workingCount AND plannedSets > 0")
    suspend fun decreasePlanAfterWorkingSetDeletion(exerciseId: Long, workingCount: Int)

    @Query("DELETE FROM exercises WHERE id = :id") suspend fun deleteExercise(id: Long)
    @Query("SELECT id FROM exercises WHERE workoutId = :workoutId") suspend fun getExerciseIds(workoutId: Long): List<Long>

    @Insert suspend fun insertSet(s: SetResultEntity): Long
    @Update suspend fun updateSet(s: SetResultEntity)
    @Query("DELETE FROM set_results WHERE id = :id") suspend fun deleteSet(id: Long)
    @Query("SELECT * FROM set_results WHERE exerciseId = :exerciseId ORDER BY setNumber, id")
    suspend fun getSets(exerciseId: Long): List<SetResultEntity>
    @Query("SELECT COALESCE(MAX(setNumber), 0) + 1 FROM set_results WHERE exerciseId = :exerciseId")
    suspend fun nextSetNumber(exerciseId: Long): Int

    @Transaction @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutWithExercises?>

    @Transaction @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getFull(id: Long): WorkoutWithExercises?

    @Transaction @Query("SELECT * FROM workouts WHERE status = :status ORDER BY dateEpochDay ASC, id DESC")
    fun observePlanned(status: Int): Flow<List<WorkoutWithExercises>>

    @Query("""
        SELECT $WORKOUT_LIST_COLUMNS
        FROM workouts w WHERE w.status = :status ORDER BY w.dateEpochDay DESC, w.id DESC
    """)
    fun observeHistory(status: Int): Flow<List<WorkoutListEntry>>

    @Transaction @Query("""
        SELECT * FROM workouts WHERE status = :status AND dayOfWeek = :dayOfWeek AND dateEpochDay < :beforeDay
        ORDER BY dateEpochDay DESC, id DESC LIMIT 1
    """)
    suspend fun previousWorkout(status: Int, dayOfWeek: Int, beforeDay: Long): WorkoutWithExercises?

    @Transaction @Query("SELECT * FROM workouts WHERE status = :status ORDER BY dateEpochDay DESC, id DESC LIMIT 1")
    fun observeLatestByStatus(status: Int): Flow<WorkoutWithExercises?>

    @Query("SELECT id, dateEpochDay, status FROM workouts ORDER BY id")
    fun observeCalendarWorkouts(): Flow<List<CalendarWorkout>>
    @Transaction @Query("SELECT * FROM workouts")
    suspend fun allOnce(): List<WorkoutWithExercises>

    @Query("SELECT * FROM pinned_workouts")
    suspend fun pinsOnce(): List<PinnedWorkoutEntity>

    @Query("SELECT DISTINCT name FROM exercises WHERE name != '' ORDER BY name ASC")
    fun observeRecentExerciseNames(): Flow<List<String>>

    @Query("""
        SELECT s.completedAt, s.weight, s.reps
        FROM set_results s
        JOIN exercises e ON e.id = s.exerciseId
        JOIN workouts w ON w.id = e.workoutId
        WHERE e.name IN (:exerciseNames) AND w.status IN (:activeStatus, :completedStatus)
          AND s.isWarmup = 0 AND s.isTimeBased = 0 AND s.weight > 0 AND s.reps > 0
    """)
    fun observeWorkingSetsForProgress(
        exerciseNames: List<String>,
        activeStatus: Int,
        completedStatus: Int
    ): Flow<List<WorkingSetForProgress>>


    @Query(
        "SELECT $WORKOUT_LIST_COLUMNS FROM workouts w INNER JOIN pinned_workouts p ON p.workoutId = w.id " +
            "ORDER BY p.pinnedAt DESC"
    )
    fun observePinned(): Flow<List<WorkoutListEntry>>

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
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): RecordEntity?
    @Query("UPDATE records SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)
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
