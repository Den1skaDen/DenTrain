package com.dentrain.data

import com.dentrain.util.checkForUser
import com.dentrain.util.userMessage
import androidx.room.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Entity(tableName = "diary_links")
data class DiaryLink(@PrimaryKey val documentId: String, val localId: Long, val digest: String?)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_links") suspend fun links(): List<DiaryLink>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(link: DiaryLink)
    @Query("DELETE FROM diary_links WHERE documentId = :id") suspend fun forget(id: String)
}

private data class WorkoutBackup(val session: WorkoutWithExercises, val pinnedAt: Long?)
private data class DiaryItem(val kind: String, val localId: Long, val value: Any, val source: String? = null)
private data class Change(val link: DiaryLink, val bytes: ByteArray?)

/** One independent, versioned document per workout, record or settings row. */
class DiarySync(
    private val db: AppDatabase,
    private val uid: String,
    private val repository: WorkoutRepository,
    private val store: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val status = MutableStateFlow("")
    private val gson = Gson()
    private val dao = db.diaryDao()
    private val workouts = db.workoutDao()
    private val records = db.recordDao()
    private val settings = db.settingsDao()
    private fun active() = auth.uid == uid

    private fun encode(value: Any): ByteArray = ByteArrayOutputStream().use { buffer ->
        GZIPOutputStream(buffer).bufferedWriter(Charsets.UTF_8).use { gson.toJson(value, it) }
        buffer.toByteArray().also { checkForUser(it.size <= 950000) { "Слишком большая запись дневника" } }
    }

    private fun digest(bytes: ByteArray?) = bytes?.let {
        android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(it), android.util.Base64.NO_WRAP)
    }

    private suspend fun items(): List<DiaryItem> {
        val pins = workouts.pinsOnce().associateBy { it.workoutId }
        return buildList {
            for (w in workouts.allOnce()) {
                val exercises = w.exercises.sortedBy { it.exercise.position }.map { ex ->
                    ExerciseWithSets(ex.exercise.copy(id = 0, workoutId = 0),
                        ex.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).map { it.copy(id = 0, exerciseId = 0) })
                }
                val session = WorkoutWithExercises(w.workout.copy(id = 0), exercises)
                val source = w.workout.assignmentId?.let { "assigned/$it" } ?: w.workout.cloudId?.let { "completed/$it" }
                add(DiaryItem("workout", w.workout.id, WorkoutBackup(session, pins[w.workout.id]?.pinnedAt), source))
            }
            records.getOnceAll().forEach { add(DiaryItem("record", it.id, it.copy(id = 0))) }
            settings.getOnce()?.let { add(DiaryItem("settings", 1, it)) }
        }
    }

    private suspend fun restore(kind: String, localId: Long?, bytes: ByteArray): Long {
        val json = GZIPInputStream(bytes.inputStream()).use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                require(buffer.size() + count <= 16000000)
                buffer.write(chunk, 0, count)
            }
            buffer.toString("UTF-8")
        }
        return when (kind) {
            "settings" -> { settings.save(gson.fromJson(json, SettingsEntity::class.java).copy(id = 1)); 1L }
            "record" -> {
                val row = gson.fromJson(json, RecordEntity::class.java).copy(id = localId ?: 0)
                if (localId == null) records.insert(row) else { records.update(row); localId }
            }
            "workout" -> {
                val backup = gson.fromJson(json, WorkoutBackup::class.java)
                val row = backup.session.workout.copy(id = localId ?: 0)
                val id = if (localId == null) workouts.insertWorkout(row) else {
                    workouts.updateWorkout(row)
                    workouts.getExerciseIds(localId).forEach { workouts.deleteExercise(it) }
                    localId
                }
                for (ex in backup.session.exercises) {
                    val exId = workouts.insertExercise(ex.exercise.copy(id = 0, workoutId = id))
                    for (set in ex.sets) workouts.insertSet(set.copy(id = 0, exerciseId = exId))
                }
                workouts.unpin(id)
                backup.pinnedAt?.let { workouts.pin(PinnedWorkoutEntity(id, it)) }
                id
            }
            else -> error("Неизвестный тип записи")
        }
    }

    private suspend fun reconcile(remote: Map<String, ByteArray>): List<Change> = db.withTransaction {
        val links = dao.links().associateBy { it.documentId }.toMutableMap()
        val byRow = links.values.associateBy { it.documentId.substringBefore('_') to it.localId }
        val local = mutableMapOf<String, ByteArray>()
        for (item in items()) {
            val link = byRow[item.kind to item.localId] ?: run {
                val uuid = item.source?.let { UUID.nameUUIDFromBytes(it.toByteArray()) } ?: UUID.randomUUID()
                val id = if (item.kind == "settings") "settings" else item.kind + "_" + uuid
                DiaryLink(id, item.localId, null).also { dao.save(it); links[id] = it }
            }
            local[link.documentId] = encode(item.value)
        }
        val changes = mutableListOf<Change>()
        for (id in links.keys + remote.keys) {
            val kind = id.substringBefore('_')
            val link = links[id]
            val cloud = remote[id]
            val current = local[id]
            val remoteHash = digest(cloud)
            val localHash = digest(current)
            when {
                localHash == remoteHash -> {
                    if (link != null && current == null) dao.forget(id)
                    else if (link != null && link.digest != localHash) dao.save(link.copy(digest = localHash))
                }
                link != null && remoteHash == link.digest -> changes.add(Change(link, current))
                link == null || localHash == link.digest || kind == "settings" && link.digest == null -> {
                    if (cloud != null) dao.save(DiaryLink(id, restore(kind, link?.localId, cloud), remoteHash))
                    else if (link != null) {
                        if (kind == "workout") workouts.deleteWorkout(link.localId)
                        if (kind == "record") records.delete(link.localId)
                        dao.forget(id)
                    }
                }
                else -> {
                    // Keep concurrent offline edits as a second entry. Both versions survive a retry.
                    if (kind == "settings") {
                        if (cloud != null) dao.save(link.copy(localId = restore(kind, link.localId, cloud), digest = remoteHash))
                    } else {
                        if (current != null) {
                            val fork = DiaryLink(kind + "_" + UUID.randomUUID(), link.localId, null)
                            dao.save(fork)
                            changes.add(Change(fork, current))
                        }
                        if (cloud == null) dao.forget(id)
                        else dao.save(DiaryLink(id, restore(kind, null, cloud), remoteHash))
                    }
                }
            }
        }
        val recordLinks = dao.links().filter { it.documentId.startsWith("record_") }
        repository.compactSyncedRecords(recordLinks.associate { it.localId to it.documentId })
        changes.mapNotNull { change ->
            if (!change.link.documentId.startsWith("record_")) return@mapNotNull change
            val row = records.getById(change.link.localId)
            val bytes = row?.let { encode(it.copy(id = 0)) }
            if (bytes == null && change.link.digest == null) null else change.copy(bytes = bytes)
        }
    }

    suspend fun run() {
        val collection = store.collection("accounts").document(uid).collection("diary")
        val signals = Channel<Unit>(Channel.CONFLATED)
        val observer = object : InvalidationTracker.Observer("workouts", "exercises", "set_results", "records", "pinned_workouts", "settings") {
            override fun onInvalidated(tables: Set<String>) { signals.trySend(Unit) }
        }
        db.invalidationTracker.addObserver(observer)
        try {
            while (active()) {
                // Keep a server-confirmed view. Offline edits accumulate in the account's Room file.
                val snapshots = MutableStateFlow<Map<String, ByteArray>?>(null)
                var listenerFailure: Exception? = null
                val listener = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                    if (error != null) listenerFailure = error
                    if (snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()) {
                        try {
                            snapshots.value = snap.documents.associate { doc ->
                                require(doc.getLong("version") == 1L)
                                doc.id to requireNotNull(doc.getBlob("payload")).toBytes()
                            }
                        } catch (e: Exception) { listenerFailure = e }
                    }
                    signals.trySend(Unit)
                }
                try {
                    while (active()) {
                        signals.receive()
                        listenerFailure?.let { throw it }
                        val remote = snapshots.value ?: continue
                        if (remote.isEmpty() && dao.links().isEmpty()) {
                            repository.importLegacyDiary(uid, store)
                            db.withTransaction {
                                if (settings.getOnce() == null) settings.save(SettingsEntity())
                            }
                        }
                        val changes = reconcile(remote)
                        for ((link, bytes) in changes) {
                            check(active())
                            val ref = collection.document(link.documentId)
                            store.runTransaction { tx ->
                                val previous = tx.get(ref).getBlob("payload")?.toBytes()
                                checkForUser(digest(previous) == link.digest || digest(previous) == digest(bytes)) {
                                    "Данные обновлены другим устройством"
                                }
                                if (bytes == null) tx.delete(ref)
                                else tx.set(ref, mapOf("payload" to Blob.fromBytes(bytes), "version" to 1,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                            }.await()
                            if (bytes == null) dao.forget(link.documentId)
                            else dao.save(link.copy(digest = digest(bytes)))
                        }
                        status.value = ""
                        // Resubscribe after writes so the next comparison uses an acknowledged server snapshot.
                        if (changes.isNotEmpty()) break
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    status.value = e.userMessage("Не удалось синхронизировать дневник.")
                    delay(15000)
                } finally { listener.remove() }
            }
        } finally { db.invalidationTracker.removeObserver(observer); signals.close() }
    }
}
