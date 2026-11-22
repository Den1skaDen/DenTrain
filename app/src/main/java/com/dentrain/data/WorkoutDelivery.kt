package com.dentrain.data

import com.dentrain.util.checkForUser
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Date

internal fun activityEvent(
    recipientUid: String,
    senderUid: String,
    event: String,
    subjectId: String,
    subject: String
): Map<String, Any> {
    val now = System.currentTimeMillis()
    return mapOf(
        "recipientUid" to recipientUid,
        "senderUid" to senderUid,
        "event" to event,
        "subjectId" to subjectId,
        "subject" to subject,
        "timestamp" to now,
        "occurredAt" to FieldValue.serverTimestamp(),
        "expiresAt" to Timestamp(Date(now + 90 * 86_400_000L)),
        "isRead" to false
    )
}

class WorkoutDelivery(
    context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val scheduleSync: () -> Unit = { scheduleWorkoutDelivery(context) }
) {
    suspend fun enqueue(
        senderUid: String,
        senderName: String,
        athleteUid: String,
        title: String,
        dateEpochDay: Long,
        description: String,
        exercises: List<AssignedExerciseModel>,
        admin: Boolean = false
    ): Task<Void> {
        val name = title.trim()
        checkForUser(name.isNotEmpty() && name.length <= 160) { "Название должно содержать от 1 до 160 символов." }
        checkForUser(description.length <= 4000) { "Описание должно содержать до 4000 символов." }
        checkForUser(exercises.size in 1..100 && exercises.all {
            it.name.isNotBlank() && it.name.length <= 160 && it.plannedReps.length <= 40 &&
                it.plannedSets in 1..100 && (it.plannedWeight == null || it.plannedWeight in 0.0..2000.0)
        }) { "Проверьте упражнения, подходы и вес. В тренировке должно быть от 1 до 100 упражнений." }
        val workout = db.collection("assigned_workouts").document()
        val notification = db.collection("notifications").document("assigned_${workout.id}")
        val batch = db.batch()
        batch.set(workout, mapOf(
            "trainerUid" to senderUid,
            "trainerName" to senderName.take(80),
            "athleteUid" to athleteUid,
            "title" to name,
            "dateEpochDay" to dateEpochDay,
            "description" to description.trim(),
            "exercises" to exercises.map { exercise ->
                mapOf(
                    "name" to exercise.name,
                    "plannedSets" to exercise.plannedSets,
                    "plannedReps" to exercise.plannedReps,
                    "plannedWeight" to exercise.plannedWeight
                )
            },
            "deliveryVersion" to 1,
            "createdAt" to System.currentTimeMillis(),
            "status" to "pending"
        ))
        batch.set(notification, activityEvent(athleteUid, senderUid, "assigned", workout.id, name))
        if (admin) {
            batch.set(db.collection("admin_audit").document("workout_${workout.id}"), auditEvent(
                senderUid, "workout_assigned", athleteUid, name
            ))
        }
        val persisted = TaskCompletionSource<Void>()
        val listener = workout.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
            if (snapshot?.exists() == true) persisted.trySetResult(null)
        }
        val result = batch.commit()
        result.addOnSuccessListener { persisted.trySetResult(null) }
        result.addOnFailureListener { persisted.trySetException(it) }
        try {
            persisted.task.await()
        } finally {
            listener.remove()
        }
        if (!result.isComplete) scheduleSync()
        return result
    }

    companion object {
        suspend fun loadAssignments(
            db: FirebaseFirestore,
            athleteUid: String,
            trainerUid: String? = null
        ): List<AssignedWorkoutModel> {
            val byAthlete = db.collection("assigned_workouts").whereEqualTo("athleteUid", athleteUid)
            val query = if (trainerUid == null) byAthlete else byAthlete.whereEqualTo("trainerUid", trainerUid)
            val source = if (trainerUid == null) Source.DEFAULT else Source.SERVER
            return query.get(source).await().documents.mapNotNull { doc ->
                val status = doc.getString("status")
                if (status == "cancelled") return@mapNotNull null
                val exercises = (doc.get("exercises") as? List<*>).orEmpty().mapNotNull exercise@{ item ->
                    val exercise = item as? Map<*, *> ?: return@exercise null
                    AssignedExerciseModel(
                        name = exercise["name"] as? String ?: "",
                        plannedSets = (exercise["plannedSets"] as? Number)?.toInt() ?: 3,
                        plannedReps = exercise["plannedReps"] as? String ?: "10",
                        plannedWeight = (exercise["plannedWeight"] as? Number)?.toDouble()
                    )
                }
                AssignedWorkoutModel(
                    id = doc.id,
                    trainerUid = doc.getString("trainerUid").orEmpty(),
                    trainerName = doc.getString("trainerName").orEmpty(),
                    athleteUid = athleteUid,
                    title = doc.getString("title").orEmpty(),
                    dateEpochDay = doc.getLong("dateEpochDay") ?: 0,
                    description = doc.getString("description").orEmpty(),
                    exercises = exercises,
                    createdAt = doc.getLong("createdAt") ?: 0,
                    isCompleted = status == "completed",
                    deliveryState = doc.deliveryState()
                )
            }
        }

        suspend fun importAssignment(
            db: FirebaseFirestore,
            athleteUid: String,
            repository: WorkoutRepository,
            doc: DocumentSnapshot
        ) {
            val status = doc.getString("status")
            if (doc.metadata.hasPendingWrites() || status == "cancelled") return
            if (status != "completed") {
                val title = doc.getString("title") ?: error("Assignment title missing")
                val trainer = doc.getString("trainerName") ?: "Тренер"
                val day = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay()
                val description = doc.getString("description").orEmpty()
                val exercises = (doc.get("exercises") as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                repository.saveWorkout(WorkoutDraft(
                    id = 0L,
                    title = title,
                    epochDay = day,
                    dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek.value,
                    description = "Назначено тренером: $trainer. $description".trim(),
                    exercises = exercises.map { exercise ->
                        ExerciseDraft(
                            id = 0L,
                            name = exercise["name"] as? String ?: "Упражнение",
                            sets = (exercise["plannedSets"] as? Number)?.toInt() ?: 3,
                            reps = exercise["plannedReps"] as? String ?: "10",
                            weight = (exercise["plannedWeight"] as? Number)?.toDouble()
                        )
                    },
                    assignmentId = doc.id
                ))
            }
            if (!doc.metadata.isFromCache && doc.getLong("deliveryVersion") == 1L &&
                doc.getTimestamp("receivedAt") == null
            ) {
                acknowledge(db, athleteUid, doc.id)
            }
        }

        private suspend fun acknowledge(db: FirebaseFirestore, athleteUid: String, workoutId: String) {
            val workout = db.collection("assigned_workouts").document(workoutId)
            val receipt = db.collection("notifications").document("received_$workoutId")
            db.runTransaction { tx ->
                val stored = tx.get(workout)
                val sender = stored.getString("trainerUid")
                if (sender != null && stored.getTimestamp("receivedAt") == null &&
                    stored.getString("athleteUid") == athleteUid && stored.getString("status") != "cancelled"
                ) {
                    tx.update(workout, "receivedAt", FieldValue.serverTimestamp())
                    tx.set(receipt, activityEvent(
                        sender, athleteUid, "received", workoutId, stored.getString("title").orEmpty()
                    ))
                }
            }.await()
        }
    }
}

private fun scheduleWorkoutDelivery(context: Context) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    val job = JobInfo.Builder(WorkoutDeliveryJob.ID, ComponentName(context, WorkoutDeliveryJob::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build()
    if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
        android.util.Log.w("WorkoutDelivery", "Pending writes will resume when Firestore restarts")
    }
}

class WorkoutDeliveryJob : JobService() {
    private var running: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (FirebaseAuth.getInstance().currentUser == null) return false
        running = params
        FirebaseFirestore.getInstance().waitForPendingWrites().addOnCompleteListener { task ->
            if (running === params) {
                running = null
                jobFinished(params, !task.isSuccessful)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running = null
        return true
    }

    companion object {
        const val ID = 3021
    }
}

fun DocumentSnapshot.deliveryState(): WorkoutDeliveryState? = when {
    getLong("deliveryVersion") != 1L -> null
    metadata.hasPendingWrites() -> WorkoutDeliveryState.QUEUED
    getTimestamp("receivedAt") != null -> WorkoutDeliveryState.RECEIVED
    else -> WorkoutDeliveryState.SENT
}

internal fun auditEvent(actorUid: String, action: String, targetId: String, label: String) = mapOf(
    "actorUid" to actorUid,
    "action" to action,
    "targetId" to targetId,
    "label" to label.take(160),
    "occurredAt" to FieldValue.serverTimestamp()
)
