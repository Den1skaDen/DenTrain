package com.dentrain.data

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.room.Room
import com.dentrain.ui.records.ProgressFilter
import com.dentrain.ui.records.ProgressPoint
import com.dentrain.ui.records.dailyProgress
import com.dentrain.ui.records.progressDateFraction
import com.dentrain.ui.records.progressWeightTicks
import com.dentrain.ui.settings.AvatarCropTest
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONObject

class DiaryTestRunner : Instrumentation() {
    private var firestorePort = 8080
    private var avatarOnly = false
    private var progressOnly = false

    override fun newApplication(loader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(loader, Application::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        firestorePort = arguments.getString("firestorePort")?.toIntOrNull() ?: 8080
        avatarOnly = arguments.getString("avatarOnly") == "true"
        progressOnly = arguments.getString("progressOnly") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            if (avatarOnly) {
                AvatarCropTest.run()
                result.putString("stream", "PASS: selected photo area and avatar size limit\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            recordsProgressUsesWorkingHistory()
            screenCacheTracksLocalChanges()
            recordsRetainOnlyBestResults()
            migrationPreservesDiary()
            if (progressOnly) {
                result.putString("stream", "PASS: personal records and progress\nPASS: screen cache and workout history\nPASS: diary migration and persisted workout records\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            offlineWorkoutRestoresIntoFreshDatabase()
            offlineAssignmentReachesStudentAndConfirmsOnce()
            result.putString("stream", "PASS: personal records and progress\nPASS: Room migration and diary synchronization\nPASS: offline coaching delivery\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Exception) {
            result.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun screenCacheTracksLocalChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dao = db.workoutDao()
            val repo = WorkoutRepository(db, null)
            val day = LocalDate.of(2026, 4, 1)
            val completed = WorkoutEntity(title = "Среда", dateEpochDay = day.toEpochDay(), dayOfWeek = 3,
                status = WorkoutStatus.COMPLETED)
            dao.insertWorkout(completed)
            val latest = dao.insertWorkout(completed)
            dao.pin(PinnedWorkoutEntity(latest))
            dao.insertWorkout(completed.copy(dateEpochDay = day.plusDays(1).toEpochDay(), dayOfWeek = 4))
            val exercise = dao.insertExercise(ExerciseEntity(workoutId = latest, position = 0, name = "Жим", plannedSets = 1))
            val plannedDay = day.plusDays(7)
            val plannedId = dao.insertWorkout(completed.copy(dateEpochDay = plannedDay.toEpochDay(), status = WorkoutStatus.PLANNED))
            val cache = WorkoutScreenCache(repo, scope)

            withTimeout(5_000) {
                val entries = cache.history.first { it.size == 3 }
                check(entries[1].workout.id == latest && entries[1].exercisesCount == 1 && entries[1].setsCount == 0)
                check(cache.pinned.first { it.isNotEmpty() }.single() == entries[1])
                check(cache.planned.first { it.isNotEmpty() }.single().workout.id == plannedId)
                check(repo.previousWorkout(3, plannedDay.toEpochDay())?.workout?.id == latest)

                dao.insertSet(SetResultEntity(exerciseId = exercise, setNumber = 1, reps = 5, isWarmup = true))
                cache.history.first { entries -> entries.any { it.workout.id == latest && it.setsCount == 1 } }
                cache.pinned.first { entries -> entries.singleOrNull()?.setsCount == 1 }
                repo.startWorkout(plannedId)
                cache.active.first { it?.workout?.id == plannedId }
                repo.finishWorkout(plannedId)
                cache.calendar.first { plannedDay in it.completedDates && plannedDay !in it.plannedDates }
                cache.history.first { entries -> entries.any { it.workout.id == plannedId } }
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }

    private fun recordsProgressUsesWorkingHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        try {
            val repo = WorkoutRepository(db, null)
            val dao = db.workoutDao()
            val day = LocalDate.of(2026, 4, 1)
            val first = day.toEpochDay()
            val workoutId = dao.insertWorkout(WorkoutEntity(title = "Сила", dateEpochDay = first, dayOfWeek = 3,
                status = WorkoutStatus.COMPLETED))
            val exercise = dao.insertExercise(ExerciseEntity(workoutId = workoutId, position = 0, name = "ПРИСЕД", plannedSets = 5))
            val other = dao.insertExercise(ExerciseEntity(workoutId = workoutId, position = 1, name = "Жим", plannedSets = 1))
            val planned = dao.insertWorkout(WorkoutEntity(title = "План", dateEpochDay = first, dayOfWeek = 3))
            val plannedExercise = dao.insertExercise(ExerciseEntity(workoutId = planned, position = 0, name = "Присед", plannedSets = 1))

            suspend fun set(id: Long, offset: Long, weight: Double, reps: Int, warmup: Boolean = false, timed: Boolean = false) {
                val time = day.plusDays(offset).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                dao.insertSet(SetResultEntity(exerciseId = id, setNumber = dao.nextSetNumber(id),
                    reps = reps, weight = weight, isTimeBased = timed, isWarmup = warmup, completedAt = time))
            }
            set(exercise, 0, 60.0, 5)
            set(exercise, 1, 65.0, 5)
            set(exercise, 1, 65.0, 6)
            set(exercise, 1, 90.0, 2)
            set(exercise, 1, 100.0, 5, warmup = true)
            set(exercise, 1, 110.0, 5, timed = true)
            set(exercise, 10, 70.0, 5)
            set(other, 1, 120.0, 5)
            set(plannedExercise, 1, 130.0, 5)
            val record = RecordEntity(exerciseName = "присед", weight = 70.0, reps = 5,
                dateEpochDay = first + 10, isFavorite = true)
            val manual = record.copy(weight = 75.5, dateEpochDay = first + 20, isFavorite = false)
            val sets = withTimeout(5_000) { repo.observeWorkingSetsForProgress("присед").first() }
            val filter = ProgressFilter("присед", "5-8", false)
            val points = dailyProgress("присед", listOf(record, manual), sets, filter, ZoneOffset.UTC)
            check(points == listOf(ProgressPoint(first, 60.0, 5), ProgressPoint(first + 1, 65.0, 6),
                ProgressPoint(first + 10, 70.0, 5), ProgressPoint(first + 20, 75.5, 5))) { points }
            check(dailyProgress("присед", listOf(record, manual), sets, filter.copy(favoritesOnly = true), ZoneOffset.UTC) ==
                listOf(points[2]))
            check(progressDateFraction(first + 1, first, first + 20) == 0.05f)
            check(progressDateFraction(first, first, first) == 0.5f)
            val ticks = progressWeightTicks(points)
            check(ticks.first() < 60 && ticks.last() > 75.5)
            val flatTicks = progressWeightTicks(listOf(ProgressPoint(first, 0.0, 5)))
            check(flatTicks.first() == 0.0 && flatTicks.last() > 0.0)
        } finally {
            db.close()
        }
    }

    private fun recordsRetainOnlyBestResults() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        try {
            val repo = WorkoutRepository(db, null)
            val workoutId = repo.saveWorkout(WorkoutDraft(
                0, "Тренировка", 20800, 3, "", listOf(ExerciseDraft(0, "Присед", 5, null, null))
            ))
            val exerciseId = repo.getWorkout(workoutId)!!.exercises.single().exercise.id
            val first = checkNotNull(repo.addSet(exerciseId, 3, 12.0, isTimeBased = false))
            repo.toggleRecordFavorite(first)
            val heavier = checkNotNull(repo.addSetWithRecord(exerciseId, 3, 15.0, isTimeBased = false, isWarmup = false))
            check(heavier.record.id == first.id && heavier.record.weight == 15.0 && heavier.record.isFavorite)
            check(heavier.previous?.weight == 12.0 && heavier.previous.reps == 3)
            check(repo.addSet(exerciseId, 3, 15.0, isTimeBased = false) == null)
            check(repo.addSet(exerciseId, 2, 13.0, isTimeBased = false) == null)
            val moreReps = checkNotNull(repo.addSetWithRecord(exerciseId, 8, 12.0, isTimeBased = false, isWarmup = false))
            check(moreReps.previous == null)
            check(db.recordDao().getOnceAll().size == 2)
            check(repo.addSet(exerciseId, 10, 25.0, isTimeBased = false, isWarmup = true) == null)
            check(repo.addSet(exerciseId, 30, 25.0, isTimeBased = true) == null)
            check(repo.addSet(exerciseId, 4, 15.0, isTimeBased = false)?.id == first.id)
            val improved = checkNotNull(repo.addSetWithRecord(exerciseId, 8, 16.0, isTimeBased = false, isWarmup = false))
            check(improved.previous == moreReps.record)
            val best = db.recordDao().getOnceAll().single()
            check(best.weight == 16.0 && best.reps == 8 && best.isFavorite)
            check(repo.getWorkout(workoutId)!!.exercises.single().sets.size == 9)
        } finally {
            db.close()
        }
    }

    private fun migrationPreservesDiary() = runBlocking {
        val context = targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val date = 20800L
        try {
            val original = AppDatabase.create(context, name)
            val workoutId: Long
            try {
                val repo = WorkoutRepository(original, null)
                workoutId = repo.saveWorkout(WorkoutDraft(0, "Migration workout", date, 3, "",
                    listOf(ExerciseDraft(0, "Squat", 1, "10", 12.75))))
                val exerciseId = repo.getWorkout(workoutId)!!.exercises.single().exercise.id
                repo.addSet(exerciseId, 10, 12.75, isTimeBased = false, isWarmup = true)
                repo.setPinned(workoutId, true)
                repo.saveOrUpdateRecord(RecordEntity(exerciseName = "Squat", weight = 12.75, reps = 10, dateEpochDay = date))
            } finally {
                original.close()
            }
            // Recreate the v14 layout before testing the chained migrations.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                listOf(
                    "workouts_status_dateEpochDay", "workouts_cloudId", "workouts_assignmentId",
                    "records_dateEpochDay", "records_weight", "set_results_exerciseId_setNumber"
                ).forEach { old.execSQL("DROP INDEX index_$it") }
                old.execSQL("CREATE INDEX index_set_results_exerciseId ON set_results (exerciseId)")
                listOf("isPersonalRecord", "previousRecordWeight", "previousRecordReps").forEach {
                    old.execSQL("ALTER TABLE set_results DROP COLUMN $it")
                }
                old.version = 14
            }
            val migrated = AppDatabase.create(context, name)
            try {
                val workout = migrated.workoutDao().getFull(workoutId)!!
                check(workout.workout.title == "Migration workout")
                val set = workout.exercises.single().sets.single()
                check(set.reps == 10 && set.weight == 12.75 && set.isWarmup)
                check(migrated.workoutDao().pinsOnce().single().workoutId == workoutId)
                check(migrated.recordDao().getOnceAll().single().weight == 12.75)
                val repo = WorkoutRepository(migrated, null)
                repo.addSet(set.exerciseId, 10, 15.0, isTimeBased = false)
                repo.finishWorkout(workoutId)
            } finally {
                migrated.close()
            }
            val reopened = AppDatabase.create(context, name)
            try {
                val update = WorkoutRepository(reopened, null).observeWorkoutRecords(workoutId).first().single()
                check(update.record.weight == 15.0 && update.previous?.weight == 12.75)
                check(update.previous?.reps == 10)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun syncUntil(sync: DiarySync, ready: suspend () -> Boolean) = coroutineScope {
        val job = launch(Dispatchers.IO) { sync.run() }
        try {
            withTimeout(45000) {
                while (!ready()) delay(100)
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun offlineWorkoutRestoresIntoFreshDatabase() = runBlocking {
        val context = targetContext
        val project = "demo-dentrain-rules-test"
        val options = FirebaseOptions.Builder()
            .setProjectId(project).setApplicationId("1:123456789:android:abcdef").setApiKey("fake-api-key")
            .build()
        val app = FirebaseApp.initializeApp(context, options, "diary-test")
        val auth = FirebaseAuth.getInstance(app)
        val store = FirebaseFirestore.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        store.useEmulator("10.0.2.2", firestorePort)
        val first = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val second = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val uid = auth.createUserWithEmailAndPassword("${UUID.randomUUID()}@example.test", "test-password").await().user!!.uid
            store.disableNetwork().await()
            val repository = WorkoutRepository(first, null)
            val id = repository.saveWorkout(WorkoutDraft(0, "Приседания", 20800, 3, "",
                listOf(ExerciseDraft(0, "Присед", 1, "120", 12.75))))
            val exerciseId = repository.getWorkout(id)!!.exercises.single().exercise.id
            repository.addSet(exerciseId, 120, 12.75, isTimeBased = false, isWarmup = true)
            repository.finishWorkout(id)
            repository.setPinned(id, true)
            check(repository.getWorkout(id)!!.exercises.single().sets.single().reps == 120)
            val legacyRecord = store.collection("records").document("${uid}_legacy")
            store.enableNetwork().await()
            legacyRecord.set(mapOf(
                "athleteUid" to uid, "exerciseName" to "Присед", "weight" to 12.75,
                "reps" to 120, "dateEpochDay" to 20800, "comment" to "", "isFavorite" to false,
                "updatedAt" to System.currentTimeMillis()
            )).await()
            val secondRepo = WorkoutRepository(second, null)
            val firstSync = DiarySync(first, uid, repository, store, auth)
            val secondSync = DiarySync(second, uid, secondRepo, store, auth)
            syncUntil(firstSync) {
                val saved = first.diaryDao().links().filter { it.digest != null }
                saved.size == 3
            }
            syncUntil(secondSync) { second.diaryDao().links().size == 3 }
            val workout = second.workoutDao().allOnce().single()
            val set = workout.exercises.single().sets.single()
            check(set.reps == 120 && set.weight == 12.75 && set.isWarmup)
            check(workout.workout.id == second.workoutDao().pinsOnce().single().workoutId)

            val record = second.recordDao().getOnceAll().single()
            val recordLink = second.diaryDao().links().single { it.documentId.startsWith("record_") }
            check(secondRepo.editRecord(record.copy(exerciseName = "Присед со штангой", weight = 15.0)) == null)
            syncUntil(secondSync) {
                second.diaryDao().links().single { it.documentId == recordLink.documentId }.digest != recordLink.digest
            }
            syncUntil(firstSync) { first.recordDao().getOnceAll().singleOrNull()?.weight == 15.0 }
            check(first.recordDao().getOnceAll().single().exerciseName == "Присед со штангой")
            secondRepo.deleteRecord(record.id)
            syncUntil(secondSync) { second.diaryDao().links().none { it.documentId == recordLink.documentId } }
            syncUntil(firstSync) { first.recordDao().getOnceAll().isEmpty() }

            // Both local databases edit the same version while their sync jobs are stopped.
            val workoutLink = first.diaryDao().links().single { it.documentId.startsWith("workout_") }
            repository.updateWorkoutDescription(id, "First device")
            secondRepo.updateWorkoutDescription(workout.workout.id, "Second device")
            syncUntil(firstSync) {
                first.diaryDao().links().single { it.documentId == workoutLink.documentId }.digest != workoutLink.digest
            }
            syncUntil(secondSync) {
                second.diaryDao().links().count { it.documentId.startsWith("workout_") && it.digest != null } == 2
            }
            syncUntil(firstSync) { first.workoutDao().allOnce().size == 2 }
            check(first.workoutDao().allOnce().map { it.workout.description }.toSet() == setOf("First device", "Second device"))
            check(first.recordDao().getOnceAll().isEmpty())

            val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val freshSync = DiarySync(fresh, uid, WorkoutRepository(fresh, null), store, auth)
                syncUntil(freshSync) { fresh.diaryDao().links().size == 3 }
                check(fresh.recordDao().getOnceAll().isEmpty())
                check(legacyRecord.get().await().getDouble("weight") == 12.75)
            } finally {
                fresh.close()
            }
        } finally {
            first.close()
            second.close()
            app.delete()
        }
    }

    private fun offlineAssignmentReachesStudentAndConfirmsOnce() = runBlocking {
        withTimeout(45_000L) {
            val project = "demo-dentrain-rules-test"
            val options = FirebaseOptions.Builder().setProjectId(project)
                .setApplicationId("1:123456789:android:abcdef").setApiKey("fake-api-key").build()
            val coachApp = FirebaseApp.initializeApp(targetContext, options, "coach-delivery-test")
            val athleteApp = FirebaseApp.initializeApp(targetContext, options, "athlete-delivery-test")
            val database = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
            val coachStore = FirebaseFirestore.getInstance(coachApp).apply { useEmulator("10.0.2.2", firestorePort) }
            val athleteStore = FirebaseFirestore.getInstance(athleteApp).apply { useEmulator("10.0.2.2", firestorePort) }
            try {
                val coachUid = createTestUser(coachApp)
                val athleteUid = createTestUser(athleteApp)
                seedTestProfile(project, coachUid, "trainer", "Coach")
                seedTestProfile(project, athleteUid, "athlete", "Athlete")

                TrainerRequests(athleteStore).send(athleteUid, coachUid)
                val request = coachStore.collection("trainer_requests").document("$athleteUid~$coachUid")
                    .get(Source.SERVER).await().activeTrainerRequest()
                TrainerRequests(coachStore).decide(checkNotNull(request), accepted = true)

                coachStore.disableNetwork().await()
                var rescheduled = false
                val delivery = WorkoutDelivery(targetContext, coachStore) { rescheduled = true }
                val sending = delivery.enqueue(
                    coachUid, "Coach", athleteUid, "Offline plan", 20800, "",
                    listOf(AssignedExerciseModel("Squat", 3, "10", 20.0))
                )
                check(rescheduled && !sending.isComplete)
                val assigned = athleteStore.collection("assigned_workouts").whereEqualTo("athleteUid", athleteUid)
                check(assigned.get(Source.SERVER).await().isEmpty)
                coachStore.enableNetwork().await()
                sending.await()

                val remote = assigned.get(Source.SERVER).await().documents.single()
                val repository = WorkoutRepository(database, null)
                WorkoutDelivery.importAssignment(athleteStore, athleteUid, repository, remote)
                WorkoutDelivery.importAssignment(athleteStore, athleteUid, repository, remote)
                check(database.workoutDao().allOnce().single().exercises.single().exercise.name == "Squat")
                val receipts = coachStore.collection("notifications").whereEqualTo("recipientUid", coachUid)
                    .get(Source.SERVER).await().documents.filter { it.getString("event") == "received" }
                check(receipts.single().id == "received_${remote.id}")
            } finally {
                database.close()
                coachApp.delete()
                athleteApp.delete()
            }
        }
    }

    private suspend fun createTestUser(app: FirebaseApp): String {
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        return checkNotNull(auth.createUserWithEmailAndPassword(
            "${UUID.randomUUID()}@example.test", "test-password"
        ).await().user).uid
    }

    private fun seedTestProfile(project: String, uid: String, role: String, nickname: String) {
        val database = "projects/$project/databases/(default)"
        val connection = URL("http://10.0.2.2:$firestorePort/v1/$database/documents:commit")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            val fields = JSONObject().apply {
                mapOf("role" to role, "nickname" to nickname, "username" to "", "avatarBase64" to "")
                    .forEach { (key, value) -> put(key, JSONObject().put("stringValue", value)) }
                put("dobEpochDay", JSONObject().put("integerValue", "20000"))
            }
            val write = JSONObject().put("update", JSONObject().put("name", "$database/documents/profiles/$uid")
                .put("fields", fields))
            val body = JSONObject().put("writes", org.json.JSONArray().put(write))
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            check(connection.responseCode == 200) { "Could not seed test profile (${connection.responseCode})" }
        } finally {
            connection.disconnect()
        }
    }
}
