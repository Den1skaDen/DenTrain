package com.dentrain.data

import com.dentrain.util.checkForUser
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class TrainerRequests(private val db: FirebaseFirestore) {
    suspend fun send(
        athleteUid: String,
        trainerUid: String,
        club: ClubModel? = null,
        profileChanges: Map<String, Any?> = emptyMap()
    ) {
        val athlete = db.collection("profiles").document(athleteUid)
        val trainer = db.collection("profiles").document(trainerUid)
        val request = db.collection("trainer_requests").document("$athleteUid~$trainerUid")
        val notification = db.collection("notifications").document()
        val eventId = notification.id
        db.runTransaction { tx ->
            val athleteData = tx.get(athlete)
            val trainerData = tx.get(trainer)
            val previous = tx.get(request)
            checkForUser(athleteData.getString("role") == "athlete") { "Заявку может отправить спортсмен." }
            checkForUser(trainerData.getString("role") in setOf("trainer", "admin")) { "Тренер недоступен." }
            checkForUser(athleteData.getString("trainerUid") == null) { "Сначала открепитесь от текущего тренера." }
            val newClubId = club?.id ?: profileChanges["clubId"]
            checkForUser(newClubId == null || trainerData.getString("clubId") == newClubId) {
                "Тренер больше не состоит в выбранном клубе."
            }
            val now = System.currentTimeMillis()
            val last = previous.getTimestamp("lastSent")
            val second = previous.getTimestamp("secondSent")
            val third = previous.getTimestamp("thirdSent")
            checkForUser(last == null || now - last.toDate().time >= HOUR) {
                "Следующую заявку этому тренеру можно отправить через час после предыдущей."
            }
            checkForUser(third == null || now - third.toDate().time >= 24 * HOUR) {
                "Этому тренеру можно отправить до трёх заявок за 24 часа."
            }
            tx.set(request, mapOf(
                "athleteUid" to athleteUid,
                "athleteName" to athleteData.getString("nickname").orEmpty(),
                "trainerUid" to trainerUid,
                "trainerName" to trainerData.getString("nickname").orEmpty(),
                "status" to "pending",
                "lastSent" to FieldValue.serverTimestamp(),
                "secondSent" to last,
                "thirdSent" to second,
                "expiresAt" to Timestamp(Date(now + 7 * 24 * HOUR)),
                "eventId" to eventId
            ))
            tx.set(notification, activityEvent(
                recipientUid = athleteUid,
                senderUid = athleteUid,
                event = "request_sent",
                subjectId = request.id,
                subject = trainerData.getString("nickname").orEmpty()
            ))
            tx.set(db.collection("notifications").document("request_$eventId"), activityEvent(
                recipientUid = trainerUid,
                senderUid = athleteUid,
                event = "request_received",
                subjectId = request.id,
                subject = athleteData.getString("nickname").orEmpty()
            ))
            val changes = profileChanges + if (club == null) emptyMap() else mapOf(
                "clubId" to club.id, "clubName" to club.name
            )
            if (changes.isNotEmpty()) tx.update(athlete, changes)
        }.await()
    }

    suspend fun decide(req: TrainerRequestModel, accepted: Boolean) {
        val request = db.collection("trainer_requests").document(req.id)
        val athlete = db.collection("profiles").document(req.athleteUid)
        db.runTransaction { tx ->
            val current = tx.get(request)
            val student = tx.get(athlete)
            checkForUser(current.activeTrainerRequest()?.eventId == req.eventId && req.eventId.isNotEmpty()) {
                "Заявка уже обновлена или закрыта."
            }
            val status = if (accepted) "accepted" else "rejected"
            if (accepted) {
                checkForUser(student.getString("trainerUid") == null) { "У спортсмена уже есть тренер." }
                tx.update(athlete, mapOf("trainerUid" to req.trainerUid, "trainerName" to req.trainerName))
            }
            tx.update(request, mapOf("status" to status, "expiresAt" to closedExpiry(current)))
            tx.set(db.collection("notifications").document("decision_${req.eventId}"), activityEvent(
                recipientUid = req.athleteUid,
                senderUid = req.trainerUid,
                event = status,
                subjectId = req.id,
                subject = req.trainerName
            ))
        }.await()
    }

    suspend fun withdraw(req: TrainerRequestModel) {
        val request = db.collection("trainer_requests").document(req.id)
        db.runTransaction { tx ->
            val current = tx.get(request)
            checkForUser(current.activeTrainerRequest()?.eventId == req.eventId && req.eventId.isNotEmpty()) {
                "Заявка уже обновлена или закрыта."
            }
            tx.update(request, mapOf("status" to "withdrawn", "expiresAt" to closedExpiry(current)))
        }.await()
    }

    private fun closedExpiry(doc: DocumentSnapshot): Timestamp {
        val latest = requireNotNull(doc.getTimestamp("lastSent")).toDate().time
        return Timestamp(Date(latest + 24 * HOUR + 60_000L))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}

fun DocumentSnapshot.activeTrainerRequest(now: Long = System.currentTimeMillis()): TrainerRequestModel? {
    if (getString("status") != "pending") return null
    val expiry = getTimestamp("expiresAt")?.toDate()?.time ?: return null
    if (expiry <= now) return null
    return TrainerRequestModel(
        id = id,
        trainerUid = getString("trainerUid").orEmpty(),
        trainerName = getString("trainerName").orEmpty(),
        athleteUid = getString("athleteUid").orEmpty(),
        athleteName = getString("athleteName").orEmpty(),
        timestamp = getTimestamp("lastSent")?.toDate()?.time ?: 0L,
        eventId = getString("eventId").orEmpty()
    )
}
