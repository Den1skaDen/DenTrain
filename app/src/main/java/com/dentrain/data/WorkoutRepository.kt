package com.dentrain.data

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

internal const val BETTER_RECORD_EXISTS = "Уже есть результат с таким же или большим весом и числом повторений"

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
    val exercises: List<ExerciseDraft>,
    val assignmentId: String? = null
)

/**
 * Единая точка доступа к данным. ViewModel'и ничего не знают о Room —
 * только об этом классе.
 */
class WorkoutRepository(private val db: AppDatabase, private val owner: String?) {
    private val workouts = db.workoutDao()
    private val records = db.recordDao()
    private val settingsDao = db.settingsDao()
    private fun activeOwner() = owner?.takeIf { it == FirebaseAuth.getInstance().uid }

    val planned: Flow<List<WorkoutWithExercises>> = workouts.observePlanned(WorkoutStatus.PLANNED)
    val history: Flow<List<WorkoutListEntry>> = workouts.observeHistory(WorkoutStatus.COMPLETED)
    suspend fun previousWorkout(dayOfWeek: Int, beforeDay: Long): WorkoutWithExercises? =
        workouts.previousWorkout(WorkoutStatus.COMPLETED, dayOfWeek, beforeDay)
    val inProgress: Flow<WorkoutWithExercises?> = workouts.observeLatestByStatus(WorkoutStatus.IN_PROGRESS)
    val pinned: Flow<List<WorkoutListEntry>> = workouts.observePinned()
    val pinnedIds: Flow<List<Long>> = workouts.observePinnedIds()
    val calendarWorkouts: Flow<List<CalendarWorkout>> = workouts.observeCalendarWorkouts()
    val allRecords: Flow<List<RecordEntity>> = records.observeAll()
    val settings: Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity() }
    val recentExerciseNames: Flow<List<String>> = workouts.observeRecentExerciseNames()

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun observeWorkingSetsForProgress(exerciseName: String): Flow<List<WorkingSetForProgress>> =
        recentExerciseNames
            .map { names -> names.filter { it.trim().equals(exerciseName, ignoreCase = true) } }
            .distinctUntilChanged()
            .flatMapLatest { names ->
                if (names.isEmpty()) flowOf(emptyList())
                else workouts.observeWorkingSetsForProgress(names, WorkoutStatus.IN_PROGRESS, WorkoutStatus.COMPLETED)
            }

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
            d.assignmentId?.let { assignment ->
                workouts.findAssignment(assignment)?.let { return@withTransaction it }
            }
            workoutId = workouts.insertWorkout(
                WorkoutEntity(
                    title = d.title, dateEpochDay = d.epochDay,
                    dayOfWeek = d.dayOfWeek, description = d.description,
                    assignmentId = d.assignmentId
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
        workouts.startWorkout(id, WorkoutStatus.IN_PROGRESS, System.currentTimeMillis())
    }

    suspend fun finishWorkout(id: Long) {
        val completed = db.withTransaction {
            val w = workouts.getFull(id) ?: return@withTransaction null
            val updated = w.workout.copy(
                status = WorkoutStatus.COMPLETED,
                completedAt = w.workout.completedAt ?: System.currentTimeMillis(),
                cloudId = w.workout.cloudId ?: java.util.UUID.randomUUID().toString()
            )
            workouts.updateWorkout(updated)
            w.copy(workout = updated)
        } ?: return
        syncCompletedWorkoutToCloud(completed)
    }

    private fun syncCompletedWorkoutToCloud(w: WorkoutWithExercises) {
        val uid = activeOwner() ?: return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("completed_workouts").document(requireNotNull(w.workout.cloudId))
            val exercisesList = w.exercises.sortedBy { it.exercise.position }.map { exWithSets ->
                val setsList = exWithSets.sets.sortedBy { it.setNumber }.map { s ->
                    buildMap<String, Any> {
                        put("setNumber", s.setNumber)
                        put("reps", s.reps)
                        s.weight?.let { put("weight", it) }
                        if (s.isTimeBased) put("isTimeBased", true)
                        if (s.isWarmup) put("isWarmup", true)
                        put("completedAt", s.completedAt)
                    }
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
                "createdAt" to w.workout.completedAt
            )
            docRef.set(data).addOnFailureListener {
                android.util.Log.w("WorkoutRepository", "Could not publish completed workout", it)
            }

            // Если эта тренировка была назначена тренером — меняем статус в assigned_workouts на "completed"
            w.workout.assignmentId?.let {
                firestore.collection("assigned_workouts").document(it).update("status", "completed")
                    .addOnFailureListener { error -> android.util.Log.w("WorkoutRepository", "Could not complete assignment", error) }
            }
        } catch (_: Exception) {}
    }

    suspend fun setCurrentExercise(id: Long, index: Int) = workouts.updateCurrentExercise(id, index)

    suspend fun deleteWorkout(id: Long) = workouts.deleteWorkout(id)

    suspend fun updateWorkoutDescription(id: Long, description: String) = workouts.updateDescription(id, description)

    // ----- подходы -----

    suspend fun addSet(
        exerciseId: Long,
        reps: Int,
        weight: Double?,
        isTimeBased: Boolean,
        isWarmup: Boolean = false
    ): RecordEntity? = db.withTransaction {
        workouts.insertSet(
            SetResultEntity(
                exerciseId = exerciseId, setNumber = workouts.nextSetNumber(exerciseId),
                reps = reps, weight = weight, isTimeBased = isTimeBased, isWarmup = isWarmup
            )
        )
        if (isWarmup || isTimeBased || weight == null || !weight.isFinite() || weight <= 0 || reps <= 0) {
            return@withTransaction null
        }
        storeRecord(RecordEntity(
            exerciseName = workouts.getExerciseName(exerciseId),
            weight = weight,
            reps = reps,
            dateEpochDay = LocalDate.now().toEpochDay()
        ))
    }

    suspend fun updateSet(set: SetResultEntity, reps: Int, weight: Double?, isWarmup: Boolean = set.isWarmup) {
        workouts.updateSet(set.copy(reps = reps, weight = weight, isWarmup = isWarmup))
    }

    /** После удаления подхода номера оставшихся пересчитываются, чтобы не было дыр (1, 3, 4). */
    suspend fun deleteSet(set: SetResultEntity) = db.withTransaction {
        val existing = workouts.getSets(set.exerciseId)
        val stored = existing.firstOrNull { it.id == set.id } ?: return@withTransaction
        if (!stored.isWarmup) {
            workouts.decreasePlanAfterWorkingSetDeletion(set.exerciseId, existing.count { !it.isWarmup })
        }
        workouts.deleteSet(set.id)
        existing.filterNot { it.id == set.id }.forEachIndexed { i, s ->
            if (s.setNumber != i + 1) workouts.updateSet(s.copy(setNumber = i + 1))
        }
    }

    /** Добавить ещё один плановый подход к упражнению. */
    suspend fun addPlannedSet(exerciseId: Long) = workouts.increasePlannedSets(exerciseId)

    suspend fun removePlannedSet(exerciseId: Long) = workouts.removePendingPlannedSet(exerciseId)

    suspend fun deleteSessionExercise(workoutId: Long, exerciseId: Long) = db.withTransaction {
        val workout = workouts.getFull(workoutId) ?: return@withTransaction
        val ordered = workout.exercises.sortedBy { it.exercise.position }
        val removed = ordered.indexOfFirst { it.exercise.id == exerciseId }
        if (removed < 0) return@withTransaction
        val entry = ordered[removed]
        if (maxOf(entry.exercise.plannedSets, entry.sets.count { !it.isWarmup }) != 1) return@withTransaction

        workouts.deleteExercise(exerciseId)
        val remaining = ordered.filterNot { it.exercise.id == exerciseId }
        remaining.forEachIndexed { index, item ->
            if (item.exercise.position != index) workouts.updateExercise(item.exercise.copy(position = index))
        }
        val current = workout.workout.currentExerciseIndex
        val next = if (removed < current) current - 1 else current
        workouts.updateCurrentExercise(workoutId, next.coerceIn(0, remaining.lastIndex.coerceAtLeast(0)))
    }

    /** Быстрое добавление нового упражнения в текущую тренировку. */
    suspend fun addExerciseToWorkout(workoutId: Long, name: String, plannedSets: Int = 1): Long = db.withTransaction {
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

    suspend fun saveOrUpdateRecord(r: RecordEntity): RecordEntity? = db.withTransaction { storeRecord(r) }

    suspend fun editRecord(r: RecordEntity): String? = db.withTransaction {
        val existing = records.getById(r.id) ?: return@withTransaction "Рекорд уже удалён"
        val others = recordsForExercise(r.exerciseName).filter { it.id != r.id }
        if (others.any { it.covers(r) }) return@withTransaction BETTER_RECORD_EXISTS
        replaceRecords(r.copy(isFavorite = existing.isFavorite), others.filter { r.covers(it) })
        null
    }

    private suspend fun storeRecord(r: RecordEntity, keepImportedDetails: Boolean = false): RecordEntity? {
        val current = compactExerciseRecords(recordsForExercise(r.exerciseName))
        val better = current.firstOrNull { it.covers(r) }
        if (better != null) {
            if (keepImportedDetails) replaceRecords(better, listOf(r))
            return null
        }
        val surpassed = current.filter { r.covers(it) }
        val previous = surpassed.firstOrNull { it.weight == r.weight || it.reps == r.reps }
            ?: surpassed.firstOrNull()
        return replaceRecords(r.copy(id = previous?.id ?: 0), surpassed)
    }

    private suspend fun recordsForExercise(exerciseName: String): List<RecordEntity> =
        records.getOnceAll().filter { it.exerciseName.trim().equals(exerciseName.trim(), ignoreCase = true) }

    private suspend fun replaceRecords(record: RecordEntity, surpassed: List<RecordEntity>): RecordEntity {
        val extraComments = surpassed.map { it.comment }.filter { it.isNotBlank() && it != record.comment }
        val updated = record.copy(
            comment = if (extraComments.isEmpty()) record.comment else (listOf(record.comment) + extraComments)
                .flatMap { it.split("\n\n") }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n\n"),
            isFavorite = record.isFavorite || surpassed.any { it.isFavorite }
        )
        if (updated.id == 0L) return updated.copy(id = records.insert(updated))
        records.update(updated)
        surpassed.filter { it.id != 0L && it.id != updated.id }.forEach { records.delete(it.id) }
        return updated
    }

    internal suspend fun compactSyncedRecords(documentIds: Map<Long, String>) {
        val byExercise = sortedMapOf<String, MutableList<RecordEntity>>(String.CASE_INSENSITIVE_ORDER)
        for (record in records.getOnceAll().sortedBy { documentIds[it.id] }) {
            byExercise.getOrPut(record.exerciseName.trim()) { mutableListOf() }.add(record)
        }
        byExercise.values.forEach { compactExerciseRecords(it) }
    }

    private suspend fun compactExerciseRecords(rows: List<RecordEntity>): List<RecordEntity> {
        val best = mutableListOf<RecordEntity>()
        // При обходе от большего веса к меньшему повторы у оставшихся строк возрастают.
        for (row in rows.sortedWith(compareByDescending<RecordEntity> { it.weight }
            .thenByDescending { it.reps }.thenBy { it.dateEpochDay })) {
            val heavier = best.lastOrNull()
            if (heavier != null && heavier.covers(row)) {
                best[best.lastIndex] = replaceRecords(heavier, listOf(row))
            } else {
                best.add(row)
            }
        }
        return best
    }

    private fun RecordEntity.covers(other: RecordEntity): Boolean = weight >= other.weight && reps >= other.reps

    internal suspend fun importLegacyDiary(uid: String, firestore: FirebaseFirestore) {
        run {
            // 1. Загрузка выполненных тренировок из Firebase в локальную базу данных
            val completedSnap = firestore.collection("completed_workouts")
                .whereEqualTo("athleteUid", uid)
                .get().await()

            for (doc in completedSnap.documents) {
                val title = doc.getString("title") ?: continue
                val dateEpochDay = doc.getLong("dateEpochDay") ?: continue
                val dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt()
                val description = doc.getString("description") ?: ""
                val startedAt = doc.getLong("startedAt")
                val completedAt = doc.getLong("completedAt") ?: System.currentTimeMillis()

                db.withTransaction {
                    if (workouts.findCloudWorkout(doc.id) != null) return@withTransaction
                    val legacy = workouts.findLegacyWorkouts(
                        WorkoutStatus.COMPLETED, completedAt, title, dateEpochDay
                    ).singleOrNull()
                    if (legacy != null) {
                        workouts.updateWorkout(legacy.copy(cloudId = doc.id))
                        return@withTransaction
                    }
                    val newWorkoutId = workouts.insertWorkout(
                        WorkoutEntity(
                            title = title,
                            dateEpochDay = dateEpochDay,
                            dayOfWeek = dayOfWeek,
                            description = description,
                            status = WorkoutStatus.COMPLETED,
                            startedAt = startedAt,
                            completedAt = completedAt,
                            cloudId = doc.id
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
                                    isTimeBased = isTimeBased,
                                    isWarmup = setMap["isWarmup"] as? Boolean ?: false,
                                    completedAt = (setMap["completedAt"] as? Number)?.toLong() ?: completedAt
                                )
                            )
                        }
                    }
                }
            }

            // Старые версии публиковали рекорды здесь. Новые записи синхронизирует DiarySync.
            val recordsSnap = firestore.collection("records")
                .whereEqualTo("athleteUid", uid)
                .get().await()

            for (doc in recordsSnap.documents) {
                val exName = doc.getString("exerciseName") ?: continue
                val weight = doc.getDouble("weight") ?: 0.0
                val reps = (doc.getLong("reps") ?: 0L).toInt()
                val dateEpochDay = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay()
                val comment = doc.getString("comment") ?: ""
                val isFavorite = doc.getBoolean("isFavorite") ?: false

                db.withTransaction {
                    storeRecord(
                        RecordEntity(
                            exerciseName = exName,
                            weight = weight,
                            reps = reps,
                            dateEpochDay = dateEpochDay,
                            comment = comment,
                            isFavorite = isFavorite
                        ),
                        keepImportedDetails = true
                    )
                }
            }
        }
    }

    suspend fun deleteRecord(id: Long) = records.delete(id)

    suspend fun toggleRecordFavorite(record: RecordEntity) = records.toggleFavorite(record.id)


    // ----- настройки -----

    suspend fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        db.withTransaction {
            settingsDao.save(change(settingsDao.getOnce() ?: SettingsEntity()))
        }
    }
}
