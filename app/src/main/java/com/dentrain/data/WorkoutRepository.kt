package com.dentrain.data

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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
    val recentExerciseNames: Flow<List<String>> = workouts.observeRecentExerciseNames()

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
        val w = workouts.getFull(id) ?: return
        val updatedWorkout = w.workout.copy(status = WorkoutStatus.COMPLETED, completedAt = System.currentTimeMillis())
        workouts.updateWorkout(updatedWorkout)
        
        // Синхронизация выполненной тренировки с облаком Firebase
        syncCompletedWorkoutToCloud(w.copy(workout = updatedWorkout))
    }

    private suspend fun syncCompletedWorkoutToCloud(w: WorkoutWithExercises) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("completed_workouts").document()
            val exercisesList = w.exercises.map { exWithSets ->
                val setsList = exWithSets.sets.map { s ->
                    mapOf(
                        "setNumber" to s.setNumber,
                        "reps" to s.reps,
                        "weight" to s.weight,
                        "isTimeBased" to s.isTimeBased,
                        "isWarmup" to s.isWarmup
                    )
                }
                mapOf(
                    "name" to exWithSets.exercise.name,
                    "plannedSets" to exWithSets.exercise.plannedSets,
                    "plannedReps" to exWithSets.exercise.plannedReps,
                    "plannedWeight" to exWithSets.exercise.plannedWeight,
                    "sets" to setsList
                )
            }
            val data = hashMapOf(
                "athleteUid" to uid,
                "title" to w.workout.title,
                "dateEpochDay" to w.workout.dateEpochDay,
                "dayOfWeek" to w.workout.dayOfWeek,
                "startedAt" to w.workout.startedAt,
                "completedAt" to w.workout.completedAt,
                "description" to w.workout.description,
                "exercises" to exercisesList,
                "createdAt" to System.currentTimeMillis()
            )
            docRef.set(data).await()

            // Если эта тренировка была назначена тренером — меняем статус в assigned_workouts на "completed"
            val assignedSnap = firestore.collection("assigned_workouts")
                .whereEqualTo("athleteUid", uid)
                .whereEqualTo("title", w.workout.title)
                .whereEqualTo("dateEpochDay", w.workout.dateEpochDay)
                .get().await()
            for (assignedDoc in assignedSnap.documents) {
                assignedDoc.reference.update("status", "completed").await()
            }
        } catch (_: Exception) {}
    }

    suspend fun setCurrentExercise(id: Long, index: Int) {
        val w = workouts.getWorkout(id) ?: return
        workouts.updateWorkout(w.copy(currentExerciseIndex = index))
    }

    suspend fun deleteWorkout(id: Long) = workouts.deleteWorkout(id)

    suspend fun updateWorkoutDescription(id: Long, description: String) {
        val w = workouts.getWorkout(id) ?: return
        workouts.updateWorkout(w.copy(description = description))
    }

    // ----- подходы -----

    suspend fun addSet(exerciseId: Long, setNumber: Int, reps: Int, weight: Double?, isTimeBased: Boolean, isWarmup: Boolean = false) {
        workouts.insertSet(SetResultEntity(exerciseId = exerciseId, setNumber = setNumber, reps = reps, weight = weight, isTimeBased = isTimeBased, isWarmup = isWarmup))
    }

    suspend fun updateSet(set: SetResultEntity, reps: Int, weight: Double?, isWarmup: Boolean = set.isWarmup) {
        workouts.updateSet(set.copy(reps = reps, weight = weight, isWarmup = isWarmup))
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

    /** Уменьшить число плановых подходов к упражнению (не меньше 1). */
    suspend fun removePlannedSet(e: ExerciseEntity) {
        if (e.plannedSets > 1) {
            workouts.updateExercise(e.copy(plannedSets = e.plannedSets - 1))
        }
    }

    /** Быстрое добавление нового упражнения в текущую тренировку. */
    suspend fun addExerciseToWorkout(workoutId: Long, name: String, plannedSets: Int = 3): Long = db.withTransaction {
        val existingIds = workouts.getExerciseIds(workoutId)
        val position = existingIds.size
        workouts.insertExercise(
            ExerciseEntity(
                workoutId = workoutId,
                position = position,
                name = name,
                plannedSets = plannedSets
            )
        )
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

        val result: Pair<Boolean, RecordEntity>
        if (sameWeightRecord != null) {
            if (r.reps > sameWeightRecord.reps) {
                val updated = sameWeightRecord.copy(
                    reps = r.reps,
                    dateEpochDay = r.dateEpochDay,
                    comment = r.comment.ifBlank { sameWeightRecord.comment }
                )
                records.update(updated)
                result = Pair(true, updated)
            } else if (r.id != 0L && r.id == sameWeightRecord.id) {
                records.update(r)
                result = Pair(false, r)
            } else {
                result = Pair(false, sameWeightRecord)
            }
        } else {
            if (r.id == 0L) {
                val newId = records.insert(r)
                result = Pair(true, r.copy(id = newId))
            } else {
                records.update(r)
                result = Pair(false, r)
            }
        }

        // Синхронизация рекорда с облаком
        if (result.first || r.id != 0L) {
            syncRecordToCloud(result.second)
        }
        result
    }

    private suspend fun syncRecordToCloud(r: RecordEntity) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val safeExName = r.exerciseName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_а-яА-Я]"), "_")
            val weightKey = (r.weight * 100).toLong()
            val docId = "${uid}_${safeExName}_${weightKey}"

            val data = hashMapOf(
                "athleteUid" to uid,
                "exerciseName" to r.exerciseName,
                "weight" to r.weight,
                "reps" to r.reps,
                "dateEpochDay" to r.dateEpochDay,
                "comment" to r.comment,
                "isFavorite" to r.isFavorite,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("records").document(docId).set(data).await()
        } catch (_: Exception) {}
    }

    suspend fun syncUserDataFromCloud() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()

            // 1. Загрузка выполненных тренировок из Firebase в локальную базу данных
            val completedSnap = firestore.collection("completed_workouts")
                .whereEqualTo("athleteUid", uid)
                .get().await()

            val existingLocalCompleted = workouts.getOnceByStatus(WorkoutStatus.COMPLETED)

            for (doc in completedSnap.documents) {
                val title = doc.getString("title") ?: continue
                val dateEpochDay = doc.getLong("dateEpochDay") ?: continue
                val dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt()
                val description = doc.getString("description") ?: ""
                val startedAt = doc.getLong("startedAt")
                val completedAt = doc.getLong("completedAt") ?: System.currentTimeMillis()

                val alreadyLocal = existingLocalCompleted.any {
                    it.workout.title == title && it.workout.dateEpochDay == dateEpochDay
                }
                if (!alreadyLocal) {
                    val newWorkoutId = workouts.insertWorkout(
                        WorkoutEntity(
                            title = title,
                            dateEpochDay = dateEpochDay,
                            dayOfWeek = dayOfWeek,
                            description = description,
                            status = WorkoutStatus.COMPLETED,
                            startedAt = startedAt,
                            completedAt = completedAt
                        )
                    )
                    @Suppress("UNCHECKED_CAST")
                    val exListRaw = doc.get("exercises") as? List<Map<String, Any>> ?: emptyList()
                    exListRaw.forEachIndexed { i, exMap ->
                        val exName = exMap["name"] as? String ?: "Упражнение"
                        val plannedSets = (exMap["plannedSets"] as? Long)?.toInt() ?: 3
                        val plannedReps = exMap["plannedReps"] as? String
                        val plannedWeight = (exMap["plannedWeight"] as? Number)?.toDouble()
                        val exId = workouts.insertExercise(
                            ExerciseEntity(
                                workoutId = newWorkoutId,
                                position = i,
                                name = exName,
                                plannedSets = plannedSets,
                                plannedReps = plannedReps,
                                plannedWeight = plannedWeight
                            )
                        )
                        @Suppress("UNCHECKED_CAST")
                        val setsRaw = exMap["sets"] as? List<Map<String, Any>> ?: emptyList()
                        setsRaw.forEach { setMap ->
                            val setNum = (setMap["setNumber"] as? Long)?.toInt() ?: 1
                            val reps = (setMap["reps"] as? Long)?.toInt() ?: 0
                            val weight = (setMap["weight"] as? Number)?.toDouble()
                            val isTimeBased = setMap["isTimeBased"] as? Boolean ?: false
                            workouts.insertSet(
                                SetResultEntity(
                                    exerciseId = exId,
                                    setNumber = setNum,
                                    reps = reps,
                                    weight = weight,
                                    isTimeBased = isTimeBased
                                )
                            )
                        }
                    }
                }
            }

            // 2. Загрузка рекордов из Firebase в локальную базу данных
            val recordsSnap = firestore.collection("records")
                .whereEqualTo("athleteUid", uid)
                .get().await()

            val existingLocalRecords = records.getOnceAll()

            for (doc in recordsSnap.documents) {
                val exName = doc.getString("exerciseName") ?: continue
                val weight = doc.getDouble("weight") ?: 0.0
                val reps = (doc.getLong("reps") ?: 0L).toInt()
                val dateEpochDay = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay()
                val comment = doc.getString("comment") ?: ""
                val isFavorite = doc.getBoolean("isFavorite") ?: false

                val sameExRecord = existingLocalRecords.find {
                    it.exerciseName.equals(exName, ignoreCase = true) && Math.abs(it.weight - weight) < 0.01
                }

                if (sameExRecord == null) {
                    records.insert(
                        RecordEntity(
                            exerciseName = exName,
                            weight = weight,
                            reps = reps,
                            dateEpochDay = dateEpochDay,
                            comment = comment,
                            isFavorite = isFavorite
                        )
                    )
                } else if (reps > sameExRecord.reps) {
                    records.update(
                        sameExRecord.copy(
                            reps = reps,
                            dateEpochDay = dateEpochDay,
                            comment = comment.ifBlank { sameExRecord.comment },
                            isFavorite = isFavorite
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun saveRecord(r: RecordEntity) {
        saveOrUpdateRecord(r)
    }

    suspend fun deleteRecord(id: Long) = records.delete(id)

    suspend fun toggleRecordFavorite(record: RecordEntity) {
        records.update(record.copy(isFavorite = !record.isFavorite))
    }


    // ----- настройки -----

    suspend fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        settingsDao.save(change(settingsDao.getOnce() ?: SettingsEntity()))
    }
}
