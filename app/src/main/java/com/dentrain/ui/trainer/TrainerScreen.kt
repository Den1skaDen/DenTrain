package com.dentrain.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.data.AdminUserModel
import com.dentrain.data.AssignedExerciseModel
import com.dentrain.data.AssignedWorkoutModel
import com.dentrain.data.ClubModel
import com.dentrain.data.TrainerRequestModel
import com.dentrain.ui.admin.UserListItem
import com.dentrain.ui.components.AssignWorkoutPanel
import com.dentrain.ui.components.AssignedWorkoutDetailPanel
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.UserProfilePanel
import com.dentrain.ui.components.appViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TrainerViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var trainerNickname by mutableStateOf("")
    var selectedClubId by mutableStateOf<String?>(null)
    var selectedClubName by mutableStateOf<String?>(null)

    var clubs by mutableStateOf<List<ClubModel>>(emptyList())
    var requests by mutableStateOf<List<TrainerRequestModel>>(emptyList())
    var students by mutableStateOf<List<AdminUserModel>>(emptyList())
    var allUsers by mutableStateOf<List<AdminUserModel>>(emptyList())

    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)

    var userWorkouts by mutableStateOf<List<AssignedWorkoutModel>>(emptyList())
    var isLoadingWorkouts by mutableStateOf(false)

    init {
        loadTrainerData()
    }

    fun loadUserWorkouts(userUid: String) {
        isLoadingWorkouts = true
        viewModelScope.launch {
            try {
                val assignedSnap = db.collection("assigned_workouts")
                    .whereEqualTo("athleteUid", userUid)
                    .get().await()
                val assignedList = assignedSnap.documents.map { doc ->
                    val status = doc.getString("status") ?: "pending"
                    val exList = (doc.get("exercises") as? List<Map<String, Any>>)?.map { ex ->
                        AssignedExerciseModel(
                            name = ex["name"] as? String ?: "",
                            plannedSets = (ex["plannedSets"] as? Long)?.toInt() ?: 3,
                            plannedReps = ex["plannedReps"] as? String ?: "10",
                            plannedWeight = (ex["plannedWeight"] as? Number)?.toDouble()
                        )
                    } ?: emptyList()

                    AssignedWorkoutModel(
                        id = doc.id,
                        trainerUid = doc.getString("trainerUid") ?: "",
                        trainerName = doc.getString("trainerName") ?: "",
                        athleteUid = doc.getString("athleteUid") ?: "",
                        title = doc.getString("title") ?: "",
                        dateEpochDay = doc.getLong("dateEpochDay") ?: 0,
                        description = doc.getString("description") ?: "",
                        exercises = exList,
                        createdAt = doc.getLong("createdAt") ?: 0,
                        isCompleted = status == "completed"
                    )
                }

                val completedSnap = db.collection("completed_workouts")
                    .whereEqualTo("athleteUid", userUid)
                    .get().await()
                val completedList = completedSnap.documents.map { doc ->
                    val exList = (doc.get("exercises") as? List<Map<String, Any>>)?.map { ex ->
                        val setsList = (ex["sets"] as? List<Map<String, Any>>) ?: emptyList()
                        AssignedExerciseModel(
                            name = ex["name"] as? String ?: "",
                            plannedSets = (ex["plannedSets"] as? Long)?.toInt() ?: (if (setsList.isNotEmpty()) setsList.size else 3),
                            plannedReps = ex["plannedReps"] as? String ?: "10",
                            plannedWeight = (ex["plannedWeight"] as? Number)?.toDouble()
                        )
                    } ?: emptyList()

                    AssignedWorkoutModel(
                        id = doc.id,
                        trainerUid = "",
                        trainerName = "",
                        athleteUid = doc.getString("athleteUid") ?: userUid,
                        title = doc.getString("title") ?: "",
                        dateEpochDay = doc.getLong("dateEpochDay") ?: 0,
                        description = doc.getString("description") ?: "",
                        exercises = exList,
                        createdAt = doc.getLong("createdAt") ?: 0,
                        isCompleted = true
                    )
                }

                val allList = mutableListOf<AssignedWorkoutModel>()
                allList.addAll(completedList)
                assignedList.forEach { assigned ->
                    val existsInCompleted = completedList.any {
                        it.title == assigned.title && it.dateEpochDay == assigned.dateEpochDay
                    }
                    if (!existsInCompleted) {
                        allList.add(assigned)
                    }
                }

                userWorkouts = allList.sortedByDescending { it.dateEpochDay }
            } catch (e: Exception) {
                userWorkouts = emptyList()
            } finally {
                isLoadingWorkouts = false
            }
        }
    }

    fun loadTrainerData() {
        val uid = auth.uid ?: return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    trainerNickname = userDoc.getString("nickname") ?: userDoc.getString("email") ?: "Тренер"
                    selectedClubId = userDoc.getString("clubId")
                    selectedClubName = userDoc.getString("clubName")
                }

                val clubsSnapshot = db.collection("clubs").get().await()
                clubs = clubsSnapshot.documents.map { doc ->
                    ClubModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        address = doc.getString("address") ?: "",
                        phone = doc.getString("phone") ?: "",
                        description = doc.getString("description") ?: ""
                    )
                }

                val requestsSnapshot = db.collection("trainer_requests")
                    .whereEqualTo("trainerUid", uid)
                    .get().await()
                requests = requestsSnapshot.documents.mapNotNull { doc ->
                    val status = doc.getString("status") ?: "pending"
                    if (status == "pending") {
                        TrainerRequestModel(
                            id = doc.id,
                            trainerUid = doc.getString("trainerUid") ?: "",
                            trainerName = doc.getString("trainerName") ?: "",
                            athleteUid = doc.getString("athleteUid") ?: "",
                            athleteName = doc.getString("athleteName") ?: "",
                            athleteEmail = doc.getString("athleteEmail") ?: "",
                            status = status,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } else null
                }

                val allUsersSnapshot = db.collection("users").get().await()
                allUsers = allUsersSnapshot.documents.map { doc ->
                    AdminUserModel(
                        uid = doc.id,
                        email = doc.getString("email") ?: "",
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        avatarBase64 = doc.getString("avatarBase64") ?: "",
                        role = doc.getString("role") ?: "athlete",
                        dobEpochDay = doc.getLong("dobEpochDay"),
                        clubId = doc.getString("clubId"),
                        clubName = doc.getString("clubName"),
                        trainerUid = doc.getString("trainerUid"),
                        trainerName = doc.getString("trainerName")
                    )
                }

                val studentsSnapshot = db.collection("users")
                    .whereEqualTo("trainerUid", uid)
                    .get().await()
                students = studentsSnapshot.documents.map { doc ->
                    AdminUserModel(
                        uid = doc.id,
                        email = doc.getString("email") ?: "",
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        avatarBase64 = doc.getString("avatarBase64") ?: "",
                        role = doc.getString("role") ?: "athlete",
                        dobEpochDay = doc.getLong("dobEpochDay"),
                        clubId = doc.getString("clubId"),
                        clubName = doc.getString("clubName"),
                        trainerUid = doc.getString("trainerUid"),
                        trainerName = doc.getString("trainerName")
                    )
                }
            } catch (e: Exception) {
                error = "Ошибка загрузки данных тренера: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun saveClub(club: ClubModel, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val docRef = if (club.id.isBlank()) db.collection("clubs").document() else db.collection("clubs").document(club.id)
                val data = hashMapOf(
                    "name" to club.name.trim(),
                    "address" to club.address.trim(),
                    "phone" to club.phone.trim(),
                    "description" to club.description.trim()
                )
                docRef.set(data).await()
                loadTrainerData()
                onDone()
            } catch (e: Exception) {
                error = "Ошибка при сохранении клуба: ${e.localizedMessage}"
            }
        }
    }

    fun leaveClub() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "clubId" to null,
                        "clubName" to null
                    )
                ).await()
                selectedClubId = null
                selectedClubName = null
                successMsg = "Вы покинули клуб"
                loadTrainerData()
            } catch (e: Exception) {
                error = "Ошибка выхода из клуба: ${e.localizedMessage}"
            }
        }
    }

    fun selectClub(club: ClubModel) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update(
                    mapOf(
                        "clubId" to club.id,
                        "clubName" to club.name
                    )
                ).await()
                selectedClubId = club.id
                selectedClubName = club.name
                successMsg = "Вы вступили в клуб ${club.name}"
                loadTrainerData()
            } catch (e: Exception) {
                error = "Ошибка выбора клуба: ${e.localizedMessage}"
            }
        }
    }

    fun acceptRequest(req: TrainerRequestModel) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val allAthleteReqs = db.collection("trainer_requests")
                    .whereEqualTo("athleteUid", req.athleteUid)
                    .whereEqualTo("trainerUid", uid)
                    .get().await()
                
                for (doc in allAthleteReqs.documents) {
                    db.collection("trainer_requests").document(doc.id).delete().await()
                }

                db.collection("users").document(req.athleteUid).update(
                    mapOf(
                        "trainerUid" to uid,
                        "trainerName" to trainerNickname
                    )
                ).await()
                
                val notif = hashMapOf(
                    "recipientUid" to req.athleteUid,
                    "title" to "Заявка принята",
                    "message" to "Тренер $trainerNickname принял вашу заявку.",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "type" to "success"
                )
                db.collection("notifications").add(notif)

                successMsg = "Ученик ${req.athleteName} принят под ваше руководство!"
                loadTrainerData()
            } catch (e: Exception) {
                error = "Ошибка принятия заявки: ${e.localizedMessage}"
            }
        }
    }

    fun rejectRequest(req: TrainerRequestModel) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val allAthleteReqs = db.collection("trainer_requests")
                    .whereEqualTo("athleteUid", req.athleteUid)
                    .whereEqualTo("trainerUid", uid)
                    .get().await()
                
                for (doc in allAthleteReqs.documents) {
                    db.collection("trainer_requests").document(doc.id).delete().await()
                }
                
                val notif = hashMapOf(
                    "recipientUid" to req.athleteUid,
                    "title" to "Заявка отклонена",
                    "message" to "Тренер $trainerNickname отклонил вашу заявку.",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "type" to "warning"
                )
                db.collection("notifications").add(notif)
                
                successMsg = "Заявка отклонена"
                loadTrainerData()
            } catch (e: Exception) {
                error = "Ошибка отклонения заявки: ${e.localizedMessage}"
            }
        }
    }

    fun assignWorkoutToStudent(
        studentUid: String,
        title: String,
        dateEpochDay: Long,
        description: String,
        exercises: List<AssignedExerciseModel>,
        onDone: () -> Unit
    ) {
        val uid = auth.uid ?: return
        if (title.isBlank()) {
            error = "Введите название тренировки"
            return
        }
        viewModelScope.launch {
            try {
                val docRef = db.collection("assigned_workouts").document()
                val exercisesMap = exercises.map { ex ->
                    mapOf(
                        "name" to ex.name,
                        "plannedSets" to ex.plannedSets,
                        "plannedReps" to ex.plannedReps,
                        "plannedWeight" to ex.plannedWeight
                    )
                }
                val data = hashMapOf(
                    "trainerUid" to uid,
                    "trainerName" to trainerNickname,
                    "athleteUid" to studentUid,
                    "title" to title.trim(),
                    "dateEpochDay" to dateEpochDay,
                    "description" to description.trim(),
                    "exercises" to exercisesMap,
                    "createdAt" to System.currentTimeMillis()
                )
                docRef.set(data).await()
                successMsg = "Тренировка «$title» успешно назначена ученику!"
                onDone()
            } catch (e: Exception) {
                error = "Ошибка назначения тренировки: ${e.localizedMessage}"
            }
        }
    }
}

@Composable
fun TrainerClubDetailsPanel(
    club: ClubModel,
    vm: TrainerViewModel,
    onBack: () -> Unit,
    onUserClick: (AdminUserModel) -> Unit,
    onLeaveClubRequest: () -> Unit
) {
    var isEditing by remember(club.id) { mutableStateOf(false) }
    var name by remember(club.id, club.name) { mutableStateOf(club.name) }
    var address by remember(club.id, club.address) { mutableStateOf(club.address) }
    var phone by remember(club.id, club.phone) { mutableStateOf(club.phone) }
    var description by remember(club.id, club.description) { mutableStateOf(club.description) }

    val clubTrainers = remember(vm.allUsers, club.id) { vm.allUsers.filter { it.clubId == club.id && it.role == "trainer" } }
    val clubAthletes = remember(vm.allUsers, club.id) { vm.allUsers.filter { it.clubId == club.id && it.role == "athlete" } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        ScreenHeader(
            title = "Подробнее о клубе",
            onBack = onBack,
            actions = {
                if (isEditing) {
                    IconButton(onClick = {
                        vm.saveClub(club.copy(name = name, address = address, phone = phone, description = description)) {
                            isEditing = false
                        }
                    }) {
                        Icon(Icons.Filled.Check, "Сохранить", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, "Изменить", tint = Color.White)
                    }
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("Информация")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                if (isEditing) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название клуба") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222938),
                            unfocusedContainerColor = Color(0xFF222938),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Адрес") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222938),
                            unfocusedContainerColor = Color(0xFF222938),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Телефон") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222938),
                            unfocusedContainerColor = Color(0xFF222938),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF222938),
                            unfocusedContainerColor = Color(0xFF222938),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                } else {
                    Text(name.ifBlank { "Без названия" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    if (address.isNotBlank()) Text(address, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (phone.isNotBlank()) Text("Тел: $phone", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Описание:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Text(description, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, Color(0xFF00BFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                SectionTitle("Тренеры (${clubTrainers.size})")
                Spacer(Modifier.height(8.dp))
                if (clubTrainers.isEmpty()) {
                    Text("В этом клубе пока нет тренеров", color = Color.Gray)
                } else {
                    clubTrainers.forEachIndexed { index, trainer ->
                        UserListItem(user = trainer, renderAsCard = false, onItemClick = { onUserClick(trainer) })
                        if (index < clubTrainers.size - 1) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                SectionTitle("Участники (${clubAthletes.size})")
                Spacer(Modifier.height(8.dp))
                if (clubAthletes.isEmpty()) {
                    Text("В этом клубе пока нет участников", color = Color.Gray)
                } else {
                    clubAthletes.forEachIndexed { index, athlete ->
                        UserListItem(user = athlete, renderAsCard = false, onItemClick = { onUserClick(athlete) })
                        if (index < clubAthletes.size - 1) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (club.id == vm.selectedClubId) {
                OutlinedButton(
                    onClick = onLeaveClubRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Покинуть клуб", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(
                    onClick = { vm.selectClub(club) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Вступить в этот клуб", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TrainerScreen(onBack: () -> Unit) {
    val vm = appViewModel { TrainerViewModel() }
    var selectedTab by remember { mutableStateOf(0) }

    var selectedStudentForWorkout by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingUserAccount by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingWorkoutDetail by remember { mutableStateOf<AssignedWorkoutModel?>(null) }
    var workoutToReassign by remember { mutableStateOf<AssignedWorkoutModel?>(null) }

    var viewingClub by remember { mutableStateOf<ClubModel?>(null) }
    var showLeaveClubConfirm by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (viewingWorkoutDetail != null && viewingUserAccount != null) {
                AssignedWorkoutDetailPanel(
                    workout = viewingWorkoutDetail!!,
                    user = viewingUserAccount!!,
                    onBack = { viewingWorkoutDetail = null },
                    onReassign = {
                        workoutToReassign = viewingWorkoutDetail
                        viewingWorkoutDetail = null
                        selectedStudentForWorkout = viewingUserAccount
                    }
                )
            } else if (selectedStudentForWorkout != null) {
                val student = selectedStudentForWorkout!!
                AssignWorkoutPanel(
                    athleteName = student.nickname,
                    onBack = {
                        selectedStudentForWorkout = null
                        workoutToReassign = null
                    },
                    onAssign = { title, dateEpochDay, description, exercises ->
                        vm.assignWorkoutToStudent(student.uid, title, dateEpochDay, description, exercises) {
                            selectedStudentForWorkout = null
                            workoutToReassign = null
                        }
                    },
                    initialTitle = workoutToReassign?.title ?: "",
                    initialDescription = workoutToReassign?.description ?: "",
                    initialExercises = workoutToReassign?.exercises ?: emptyList()
                )
            } else if (viewingUserAccount != null) {
                val user = viewingUserAccount!!
                val currentUserId = FirebaseAuth.getInstance().uid
                LaunchedEffect(user.uid) {
                    vm.loadUserWorkouts(user.uid)
                }
                UserProfilePanel(
                    user = user,
                    onBack = { viewingUserAccount = null },
                    onAssignWorkout = {
                        workoutToReassign = null
                        selectedStudentForWorkout = user
                    },
                    onWorkoutClick = { workout ->
                        viewingWorkoutDetail = workout
                    },
                    currentUserId = currentUserId,
                    viewerRole = "trainer",
                    userWorkouts = vm.userWorkouts,
                    isLoadingWorkouts = vm.isLoadingWorkouts
                )
            } else if (viewingClub != null) {
                TrainerClubDetailsPanel(
                    club = viewingClub!!,
                    vm = vm,
                    onBack = { viewingClub = null },
                    onUserClick = { viewingUserAccount = it },
                    onLeaveClubRequest = { showLeaveClubConfirm = true }
                )
            } else {
                ScreenHeader(
                    title = "Панель тренера",
                    onBack = onBack
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Мой клуб") },
                        icon = { Icon(Icons.Filled.Business, null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Заявки (${vm.requests.size})") },
                        icon = { Icon(Icons.Filled.Notifications, null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Ученики (${vm.students.size})") },
                        icon = { Icon(Icons.Filled.People, null) }
                    )
                }

                if (vm.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    ErrorText(vm.error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (vm.successMsg != null) {
                        Text(
                            vm.successMsg!!,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    when (selectedTab) {
                        0 -> {
                            // ВКЛАДКА: МОЙ КЛУБ
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val myClub = vm.clubs.find { it.id == vm.selectedClubId }
                                if (myClub != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1C1E26))
                                            .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(16.dp))
                                            .clickable { viewingClub = myClub }
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            SectionTitle("Мой клуб")
                                            Spacer(Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Business, null, tint = Color(0xFFA6FF00), modifier = Modifier.size(32.dp))
                                                Spacer(Modifier.width(16.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(myClub.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                                    if (myClub.address.isNotBlank()) Text(myClub.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                                OutlinedButton(onClick = { viewingClub = myClub }) {
                                                    Text("Подробнее", color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1C1E26))
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            SectionTitle("Мой клуб")
                                            Spacer(Modifier.height(8.dp))
                                            Text("Вы пока не состоите ни в одном клубе. Выберите из списка ниже.", color = Color.Gray)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                SectionTitle("Другие клубы")
                                Spacer(Modifier.height(8.dp))

                                val otherClubs = vm.clubs.filter { it.id != vm.selectedClubId }
                                if (otherClubs.isEmpty()) {
                                    Text("Список других клубов пуст", color = Color.Gray)
                                } else {
                                    otherClubs.forEach { club ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF1C1E26))
                                                .clickable { viewingClub = club }
                                        ) {
                                            Row(
                                                Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Business, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(32.dp))
                                                Spacer(Modifier.width(16.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(club.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                                    if (club.address.isNotBlank()) Text(club.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                                OutlinedButton(onClick = { viewingClub = club }) {
                                                    Text("Подробнее", color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ВКЛАДКА: ЗАЯВКИ УЧЕНИКОВ (УВЕДОМЛЕНИЯ)
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                if (vm.requests.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Text("Новых заявок пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(vm.requests, key = { it.id }) { req ->
                                            val athleteUser = remember(vm.allUsers, req.athleteUid) {
                                                vm.allUsers.find { it.uid == req.athleteUid }
                                                    ?: AdminUserModel(
                                                        uid = req.athleteUid,
                                                        email = req.athleteEmail,
                                                        nickname = req.athleteName,
                                                        role = "athlete"
                                                    )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color(0xFF1C1E26))
                                                    .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                                            ) {
                                                Column(Modifier.padding(16.dp)) {
                                                    UserListItem(
                                                        user = athleteUser,
                                                        renderAsCard = false,
                                                        onItemClick = { viewingUserAccount = athleteUser }
                                                    )

                                                    Spacer(Modifier.height(16.dp))

                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = { vm.acceptRequest(req) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6FF00), contentColor = Color.Black),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Filled.Check, null)
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Принять", fontWeight = FontWeight.Bold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { vm.rejectRequest(req) },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Отклонить", color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // ВКЛАДКА: МОИ УЧЕНИКИ
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                if (vm.students.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Text("У вас пока нет принятых учеников", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        val currentUserId = FirebaseAuth.getInstance().uid
                                        items(vm.students, key = { it.uid }) { student ->
                                            UserListItem(
                                                user = student,
                                                currentUserId = currentUserId,
                                                onItemClick = { viewingUserAccount = student },
                                                renderAsCard = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLeaveClubConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveClubConfirm = false },
            title = { Text("Покинуть клуб?") },
            text = { Text("Вы уверены, что хотите покинуть свой текущий клуб? Вы больше не будете числиться его тренером.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.leaveClub()
                    showLeaveClubConfirm = false
                    viewingClub = null
                }) {
                    Text("Покинуть", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveClubConfirm = false }) { Text("Отмена") }
            }
        )
    }
}
