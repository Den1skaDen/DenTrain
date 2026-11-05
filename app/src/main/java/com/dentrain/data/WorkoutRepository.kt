package com.dentrain.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Упражнение в форме редактора (уже проверенное и разобранное). */
data class ExerciseDraft(
    val id: Long,            // 0 = новое упражнение
    val name: String,
    val sets: Int,
    val reps: String?,
    val weight: Double?
)

data class WorkoutDraft(
    val id: Long,            // 0 = новая тренировка
    val title: String,
    val epochDay: Long,
    val dayOfWeek: Int,
    val description: String,
    val exercises: List<ExerciseDraft>
)

/**
 * Единая точка доступа к данным. ViewModel'и ничего не знают о Room —
 * только об этом классе.
 */
class WorkoutRepository(private val db: AppDatabase) {
    private val workouts = db.workoutDao()
    private val records = db.recordDao()
    private val settingsDao = db.settingsDao()

    val planned: Flow<List<WorkoutWithExercises>> = workouts.observeByStatus(WorkoutStatus.PLANNED)
    val completed: Flow<List<WorkoutWithExercises>> = workouts.observeByStatus(WorkoutStatus.COMPLETED)
    val inProgress: Flow<WorkoutWithExercises?> =
        workouts.observeByStatus(WorkoutStatus.IN_PROGRESS).map { it.firstOrNull() }
    val pinned: Flow<List<WorkoutWithExercises>> = workouts.observePinned()
    val pinnedIds: Flow<List<Long>> = workouts.observePinnedIds()
    val allWorkouts: Flow<List<WorkoutWithExercises>> = workouts.observeAll()
    val allRecords: Flow<List<RecordEntity>> = records.observeAll()
    val settings: Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity() }


    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?> = workouts.observe(id)
    suspend fun getWorkout(id: Long): WorkoutWithExercises? = workouts.getFull(id)

    /**
     * Сохранение тренировки из редактора одной транзакцией.
     * Упражнения, которые остались, ОБНОВЛЯЮТСЯ (их подходы сохраняются),
     * удалённые — удаляются вместе с подходами, новые — добавляются.
     */
    suspend fun saveWorkout(d: WorkoutDraft): Long = db.withTransaction {
        val workoutId: Long
        if (d.id == 0L) {
            workoutId = workouts.insertWorkout(
                WorkoutEntity(
                    title = d.title, dateEpochDay = d.epochDay,
                    dayOfWeek = d.dayOfWeek, description = d.description
                )
            )
        } else {
            val old = workouts.getWorkout(d.id) ?: error("Тренировка не найдена")
            workouts.updateWorkout(
                old.copy(
                    title = d.title, dateEpochDay = d.epochDay,
                    dayOfWeek = d.dayOfWeek, description = d.description
                )
            )
            workoutId = d.id
        }
        val keep = d.exercises.filter { it.id != 0L }.map { it.id }.toSet()
        workouts.getExerciseIds(workoutId).filter { it !in keep }.forEach { workouts.deleteExercise(it) }
        d.exercises.forEachIndexed { index, e ->
            val entity = ExerciseEntity(
                id = e.id, workoutId = workoutId, position = index, name = e.name,
                plannedSets = e.sets, plannedReps = e.reps, plannedWeight = e.weight
            )
            if (e.id == 0L) workouts.insertExercise(entity) else workouts.updateExercise(entity)
        }
        workoutId
    }

    suspend fun startWorkout(id: Long) {
        val w = workouts.getWorkout(id) ?: return
        workouts.updateWorkout(
            w.copy(status = WorkoutStatus.IN_PROGRESS, currentExerciseIndex = 0, startedAt = System.currentTimeMillis())
        )
    }

    suspend fun finishWorkout(id: Long) {
        val w = workouts.getWorkout(id) ?: return
        workouts.updateWorkout(w.copy(status = WorkoutStatus.COMPLETED, completedAt = System.currentTimeMillis()))
    }

    suspend fun setCurrentExercise(id: Long, index: Int) {
        val w = workouts.getWorkout(id) ?: return
        workouts.updateWorkout(w.copy(currentExerciseIndex = index))
    }

    suspend fun deleteWorkout(id: Long) = workouts.deleteWorkout(id)

    // ----- подходы -----

    suspend fun addSet(exerciseId: Long, setNumber: Int, reps: Int, weight: Double?, isTimeBased: Boolean) {
        workouts.insertSet(SetResultEntity(exerciseId = exerciseId, setNumber = setNumber, reps = reps, weight = weight, isTimeBased = isTimeBased))
    }

    suspend fun updateSet(set: SetResultEntity, reps: Int, weight: Double?) {
        workouts.updateSet(set.copy(reps = reps, weight = weight))
    }

    /** После удаления подхода номера оставшихся пересчитываются, чтобы не было дыр (1, 3, 4). */
    suspend fun deleteSet(set: SetResultEntity) = db.withTransaction {
        workouts.deleteSet(set.id)
        workouts.getSets(set.exerciseId).forEachIndexed { i, s ->
            if (s.setNumber != i + 1) workouts.updateSet(s.copy(setNumber = i + 1))
        }
    }

    /** Добавить ещё один плановый подход к упражнению. */
    suspend fun addPlannedSet(e: ExerciseEntity) {
        workouts.updateExercise(e.copy(plannedSets = e.plannedSets + 1))
    }

    // ----- закрепление -----

    suspend fun setPinned(workoutId: Long, pinned: Boolean) {
        if (pinned) workouts.pin(PinnedWorkoutEntity(workoutId)) else workouts.unpin(workoutId)
    }

    /** Создаёт НОВУЮ запланированную тренировку по образцу выполненной. Старая не меняется. */
    suspend fun repeatWorkout(id: Long): Long = db.withTransaction {
        val src = workouts.getFull(id) ?: error("Тренировка не найдена")
        val today = LocalDate.now()
        val newId = workouts.insertWorkout(
            WorkoutEntity(
                title = src.workout.title, dateEpochDay = today.toEpochDay(),
                dayOfWeek = today.dayOfWeek.value, description = src.workout.description
            )
        )
        src.exercises.sortedBy { it.exercise.position }.forEachIndexed { i, ex ->
            workouts.insertExercise(
                ExerciseEntity(
                    workoutId = newId, position = i, name = ex.exercise.name,
                    plannedSets = ex.exercise.plannedSets, plannedReps = ex.exercise.plannedReps,
                    // планируемый вес — тот, что реально использовали в последнем подходе
                    plannedWeight = ex.sets.maxByOrNull { it.setNumber }?.weight ?: ex.exercise.plannedWeight
                )
            )
        }
        newId
    }

    // ----- рекорды -----

    /**
     * Сохранение или обновление рекорда:
     * Если у этого же веса увеличиваются повторения — прошлый рекорд с этим же весом ЗАМЕНЯЕТСЯ на новый!
     */
    suspend fun saveOrUpdateRecord(r: RecordEntity): Pair<Boolean, RecordEntity> = db.withTransaction {
        val existingAll = records.getOnceAll()
        val sameExerciseRecords = existingAll.filter { it.exerciseName.equals(r.exerciseName, ignoreCase = true) }
        val sameWeightRecord = sameExerciseRecords.find { Math.abs(it.weight - r.weight) < 0.01 }

        if (sameWeightRecord != null) {
            if (r.reps > sameWeightRecord.reps) {
                val updated = sameWeightRecord.copy(
                    reps = r.reps,
                    dateEpochDay = r.dateEpochDay,
                    comment = r.comment.ifBlank { sameWeightRecord.comment },
                    isFromWorkout = r.isFromWorkout || sameWeightRecord.isFromWorkout
                )
                records.update(updated)
                Pair(true, updated)
            } else if (r.id != 0L && r.id == sameWeightRecord.id) {
                records.update(r)
                Pair(false, r)
            } else {
                Pair(false, sameWeightRecord)
            }
        } else {
            if (r.id == 0L) {
                val newId = records.insert(r)
                Pair(true, r.copy(id = newId))
            } else {
                records.update(r)
                Pair(false, r)
            }
        }
    }

    suspend fun saveRecord(r: RecordEntity) {
        saveOrUpdateRecord(r)
    }

    suspend fun deleteRecord(id: Long) = records.delete(id)


    // ----- настройки -----

    suspend fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        settingsDao.save(change(settingsDao.getOnce() ?: SettingsEntity()))
    }
}
