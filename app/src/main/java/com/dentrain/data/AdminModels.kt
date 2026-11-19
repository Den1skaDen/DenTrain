package com.dentrain.data

/** Модель спортивного клуба для Firestore */
data class ClubModel(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Модель тренера для Firestore */
data class TrainerModel(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val specialty: String = "",
    val phone: String = "",
    val bio: String = "",
    val clubId: String = "",
    val clubName: String = ""
)

/** Модель пользователя для админ-панели */
data class AdminUserModel(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val username: String = "",
    val avatarBase64: String = "",
    val role: String = "athlete", // "admin", "trainer", "athlete"
    val dobEpochDay: Long? = null,
    val clubId: String? = null,
    val clubName: String? = null,
    val trainerUid: String? = null,
    val trainerName: String? = null
)

/** Модель заявки спортсмена к тренеру */
data class TrainerRequestModel(
    val id: String = "",
    val trainerUid: String = "",
    val trainerName: String = "",
    val athleteUid: String = "",
    val athleteName: String = "",
    val athleteEmail: String = "",
    val status: String = "pending", // "pending", "accepted", "rejected"
    val timestamp: Long = System.currentTimeMillis()
)

/** Модель упражнения для назначаемой тренировки */
data class AssignedExerciseModel(
    val name: String = "",
    val plannedSets: Int = 3,
    val plannedReps: String = "10",
    val plannedWeight: Double? = null
)

/** Модель тренировки, назначаемой тренером ученику */
data class AssignedWorkoutModel(
    val id: String = "",
    val trainerUid: String = "",
    val trainerName: String = "",
    val athleteUid: String = "",
    val title: String = "",
    val dateEpochDay: Long = 0,
    val description: String = "",
    val exercises: List<AssignedExerciseModel> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)
