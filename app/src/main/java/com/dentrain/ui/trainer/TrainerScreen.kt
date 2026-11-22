package com.dentrain.ui.trainer

import com.dentrain.ui.components.DiscardChangesDialog
import androidx.activity.compose.BackHandler
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.util.userMessage
import androidx.compose.animation.animateContentSize
import com.dentrain.ui.components.EditorTextField
import com.dentrain.ui.theme.DenTrainPalette
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
import com.dentrain.ui.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import com.dentrain.ui.icons.filled.People
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import com.dentrain.data.TrainerRequests
import com.dentrain.data.WorkoutDelivery
import com.dentrain.data.activeTrainerRequest
import com.google.firebase.firestore.ListenerRegistration
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

class TrainerViewModel(private val delivery: WorkoutDelivery) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var trainerNickname by mutableStateOf("")
    var selectedClubId by mutableStateOf<String?>(null)
    var selectedClubName by mutableStateOf<String?>(null)

    var clubs by mutableStateOf<List<ClubModel>>(emptyList())
    var requests by mutableStateOf<List<TrainerRequestModel>>(emptyList())
    var requestProfiles by mutableStateOf<Map<String, AdminUserModel>>(emptyMap())
        private set
    private val loadingRequestProfiles = mutableSetOf<String>()
    var students by mutableStateOf<List<AdminUserModel>>(emptyList())
    var allUsers by mutableStateOf<List<AdminUserModel>>(emptyList())

    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)

    var userWorkouts by mutableStateOf<List<AssignedWorkoutModel>>(emptyList())
    var isLoadingWorkouts by mutableStateOf(false)

    private var requestsListener: ListenerRegistration? = null
    private var studentsListener: ListenerRegistration? = null
    private var clubMembersListener: ListenerRegistration? = null
    private var adminAccess = false
    var decidingRequestIds by mutableStateOf(emptySet<String>())
        private set
    private var isQueuingWorkout = false

    init {
        listenToRoster()
        loadTrainerData()
    }

    fun loadUserWorkouts(userUid: String) {
        val trainerUid = auth.uid ?: return
        if (students.none { it.uid == userUid }) return
        isLoadingWorkouts = true
        userWorkouts = emptyList()
        viewModelScope.launch {
            try {
                userWorkouts = WorkoutDelivery.loadAssignments(db, userUid, trainerUid)
            } catch (e: Exception) {
                error = e.userMessage("Не удалось загрузить тренировки ученика.")
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
                val userDoc = db.collection("profiles").document(uid).get().await()
                if (userDoc.exists()) {
                    trainerNickname = userDoc.getString("nickname") ?: userDoc.getString("email") ?: "Тренер"
                    adminAccess = userDoc.getString("role") == "admin"
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
                        description = doc.getString("description").orEmpty(),
                        ownerUid = doc.getString("ownerUid").orEmpty()
                    )
                }

                if (clubs.none { it.id == selectedClubId }) {
                    selectedClubId = null
                    selectedClubName = null
                }
            } catch (e: Exception) {
                error = e.userMessage("Ошибка загрузки данных тренера.")
            } finally {
                isLoading = false
            }
        }
    }

    fun saveClub(club: ClubModel, onDone: () -> Unit) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val docRef = if (club.id.isBlank()) db.collection("clubs").document() else db.collection("clubs").document(club.id)
                val data = hashMapOf<String, Any>(
                    "name" to club.name.trim(),
                    "address" to club.address.trim(),
                    "phone" to club.phone.trim(),
                    "description" to club.description.trim()
                )
                if (club.id.isBlank()) {
                    data["ownerUid"] = uid
                    val batch = db.batch()
                    batch.set(docRef, data)
                    batch.update(db.collection("profiles").document(uid), mapOf(
                        "clubId" to docRef.id, "clubName" to data.getValue("name")
                    ))
                    batch.commit().await()
                } else {
                    docRef.update(data).await()
                }
                loadTrainerData()
                onDone()
            } catch (e: Exception) {
                error = e.userMessage("Ошибка при сохранении клуба.")
            }
        }
    }

    fun leaveClub() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("profiles").document(uid).update(
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
                error = e.userMessage("Ошибка выхода из клуба.")
            }
        }
    }

    fun selectClub(club: ClubModel) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("profiles").document(uid).update(
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
                error = e.userMessage("Ошибка выбора клуба.")
            }
        }
    }

    private fun listenToRoster() {
        val uid = auth.uid ?: return
        requestsListener = db.collection("trainer_requests")
            .whereEqualTo("trainerUid", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) error = "Не удалось обновить заявки."
                if (snapshot != null) requests = snapshot.documents.mapNotNull { it.activeTrainerRequest() }
                    .sortedByDescending { it.timestamp }
            }
        studentsListener = db.collection("profiles").whereEqualTo("trainerUid", uid)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) error = "Не удалось обновить список учеников."
                if (snapshot != null) students = snapshot.documents.map { it.rosterUser() }
                    .filter { it.role == "athlete" }.sortedBy { it.nickname.lowercase() }
            }
    }

    fun loadRequestProfile(athleteUid: String) {
        if (athleteUid in requestProfiles || !loadingRequestProfiles.add(athleteUid)) return
        viewModelScope.launch {
            try {
                val profile = db.collection("profiles").document(athleteUid).get().await()
                if (profile.exists()) requestProfiles = requestProfiles + (athleteUid to profile.rosterUser())
            } catch (failure: Exception) {
                error = failure.userMessage("Не удалось загрузить профиль спортсмена.")
            } finally {
                loadingRequestProfiles.remove(athleteUid)
            }
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.rosterUser() = AdminUserModel(
        uid = id,
        nickname = getString("nickname").orEmpty(),
        username = getString("username").orEmpty(),
        avatarBase64 = getString("avatarBase64").orEmpty(),
        role = getString("role") ?: "athlete",
        dobEpochDay = getLong("dobEpochDay"),
        clubId = getString("clubId"),
        clubName = getString("clubName"),
        trainerUid = getString("trainerUid"),
        trainerName = getString("trainerName")
    )

    fun observeClubMembers(clubId: String) {
        stopObservingClubMembers()
        clubMembersListener = db.collection("profiles").whereEqualTo("clubId", clubId)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) error = "Не удалось обновить состав клуба."
                if (snapshot != null) allUsers = snapshot.documents.map { it.rosterUser() }
            }
    }

    fun stopObservingClubMembers() {
        clubMembersListener?.remove()
        clubMembersListener = null
        allUsers = emptyList()
    }

    fun detachStudent(student: AdminUserModel) {
        viewModelScope.launch {
            try {
                db.collection("profiles").document(student.uid)
                    .update(mapOf("trainerUid" to null, "trainerName" to null)).await()
                successMsg = "Ученик ${student.nickname} откреплён"
            } catch (e: Exception) {
                error = e.userMessage("Не удалось открепить ученика.")
            }
        }
    }

    override fun onCleared() {
        requestsListener?.remove()
        studentsListener?.remove()
        clubMembersListener?.remove()
    }

    fun decideRequest(req: TrainerRequestModel, accepted: Boolean) {
        if (req.id in decidingRequestIds) return
        decidingRequestIds = decidingRequestIds + req.id
        error = null
        viewModelScope.launch {
            try {
                TrainerRequests(db).decide(req, accepted)
                successMsg = if (accepted) "Ученик ${req.athleteName} добавлен" else "Заявка отклонена"
            } catch (e: Exception) {
                error = e.userMessage("Не удалось обработать заявку.")
            } finally {
                decidingRequestIds = decidingRequestIds - req.id
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
        if (isQueuingWorkout) return
        isQueuingWorkout = true
        error = null
        viewModelScope.launch {
            try {
                val sending = delivery.enqueue(
                    uid, trainerNickname, studentUid, title, dateEpochDay, description, exercises, admin = adminAccess
                )
                successMsg = "Тренировка в очереди. Подтверждение появится в уведомлениях после получения."
                onDone()
                sending.addOnSuccessListener {
                    successMsg = "Тренировка отправлена. Ожидаем получения учеником."
                    loadUserWorkouts(studentUid)
                }.addOnFailureListener { failure ->
                    successMsg = null
                    error = failure.userMessage("Не удалось отправить тренировку «$title».")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage("Не удалось поставить тренировку в очередь.")
            } finally {
                isQueuingWorkout = false
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
    DisposableEffect(club.id) {
        vm.observeClubMembers(club.id)
        onDispose { vm.stopObservingClubMembers() }
    }
    var name by remember(club.id, club.name) { mutableStateOf(club.name) }
    var address by remember(club.id, club.address) { mutableStateOf(club.address) }
    var phone by remember(club.id, club.phone) { mutableStateOf(club.phone) }
    var description by remember(club.id, club.description) { mutableStateOf(club.description) }

    val editedClub = club.copy(name = name, address = address, phone = phone, description = description)
    var savedClub by remember(club) { mutableStateOf(club) }
    var showDiscard by remember { mutableStateOf(false) }
    val handleBack = {
        if (isEditing && editedClub != savedClub) {
            showDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = handleBack)
    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }

    val clubTrainers = remember(vm.allUsers, club.id) { vm.allUsers.filter { it.clubId == club.id && it.role in setOf("trainer", "admin") } }
    val clubAthletes = remember(vm.allUsers, club.id) { vm.allUsers.filter { it.clubId == club.id && it.role == "athlete" } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
    ) {
        ScreenHeader(
            title = "Подробнее о клубе",
            onBack = handleBack,
            actions = {
                if (isEditing) {
                    IconButton(onClick = {
                        vm.saveClub(editedClub) {
                            savedClub = editedClub
                            isEditing = false
                        }
                    }) {
                        Icon(Icons.Filled.Check, "Сохранить", tint = Color.White)
                    }
                } else if (club.ownerUid == FirebaseAuth.getInstance().uid) {
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
                    .background(if (isEditing) DenTrainPalette.Panel else Color(0xFF1C1E26))
                    .border(1.dp, if (isEditing) DenTrainPalette.SubtleBorder else Color(0xFFFFC107), RoundedCornerShape(16.dp))
                    .animateContentSize()
                    .padding(16.dp)
            ) {
                if (isEditing) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorTextField(name, { name = it }, "Название клуба")
                        EditorTextField(address, { address = it }, "Адрес")
                        EditorTextField(phone, { phone = it }, "Телефон")
                        EditorTextField(description, { description = it }, "Описание", singleLine = false)
                    }
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
fun TrainerScreen(onBack: () -> Unit, openRequests: Boolean = false) {
    val vm = appViewModel { TrainerViewModel(it.workoutDelivery) }
    var selectedTab by remember { mutableStateOf(if (openRequests) 1 else 0) }

    var selectedStudentForWorkout by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingUserAccount by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingWorkoutDetail by remember { mutableStateOf<AssignedWorkoutModel?>(null) }
    var workoutToReassign by remember { mutableStateOf<AssignedWorkoutModel?>(null) }

    var viewingClub by remember { mutableStateOf<ClubModel?>(null) }
    var showLeaveClubConfirm by remember { mutableStateOf(false) }
    var requestDecision by remember { mutableStateOf<Pair<TrainerRequestModel, Boolean>?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ErrorText(vm.error, Modifier.padding(horizontal = 16.dp))
            vm.successMsg?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = DenTrainPalette.Lime) }
            if (viewingWorkoutDetail != null && vm.students.any { it.uid == viewingUserAccount?.uid }) {
                AssignedWorkoutDetailPanel(
                    workout = viewingWorkoutDetail!!,
                    user = viewingUserAccount!!,
                    onBack = { viewingWorkoutDetail = null },
                    onReassign = if (vm.students.any { it.uid == viewingUserAccount?.uid }) {
                        {
                            workoutToReassign = viewingWorkoutDetail
                            viewingWorkoutDetail = null
                            selectedStudentForWorkout = viewingUserAccount
                        }
                    } else null
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
                val selectedUser = viewingUserAccount!!
                val user = vm.requestProfiles[selectedUser.uid] ?: selectedUser
                val currentUserId = FirebaseAuth.getInstance().uid
                val isStudent = vm.students.any { it.uid == user.uid }
                LaunchedEffect(user.uid, isStudent) {
                    viewingWorkoutDetail = null
                    if (isStudent) vm.loadUserWorkouts(user.uid)
                }
                val assign: (() -> Unit)? = if (isStudent) {
                    {
                        workoutToReassign = null
                        selectedStudentForWorkout = user
                    }
                } else null
                UserProfilePanel(
                    user = user.copy(trainerUid = currentUserId.takeIf { isStudent }),
                    onBack = { viewingUserAccount = null },
                    onAssignWorkout = assign,
                    onWorkoutClick = { workout ->
                        viewingWorkoutDetail = workout
                    },
                    currentUserId = currentUserId,
                    viewerRole = "trainer",
                    onDetachStudent = if (isStudent) ({ vm.detachStudent(user) }) else null,
                    userWorkouts = vm.userWorkouts.takeIf { isStudent }.orEmpty(),
                    isLoadingWorkouts = vm.isLoadingWorkouts
                )
            } else if (viewingClub != null) {
                TrainerClubDetailsPanel(
                    club = vm.clubs.find { it.id == viewingClub!!.id } ?: viewingClub!!,
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
                                                Icon(Icons.Filled.Business, null, tint = DenTrainPalette.Lime, modifier = Modifier.size(32.dp))
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
                                            LaunchedEffect(req.athleteUid) {
                                                vm.loadRequestProfile(req.athleteUid)
                                            }
                                            val athleteUser = vm.requestProfiles[req.athleteUid]
                                                ?: vm.allUsers.find { it.uid == req.athleteUid }
                                                ?: AdminUserModel(uid = req.athleteUid, nickname = req.athleteName)
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
                                                        onItemClick = {
                                                            vm.loadRequestProfile(req.athleteUid)
                                                            viewingUserAccount = athleteUser
                                                        }
                                                    )

                                                    Spacer(Modifier.height(16.dp))

                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = { requestDecision = req to true },
                                                            enabled = req.id !in vm.decidingRequestIds,
                                                            colors = ButtonDefaults.buttonColors(containerColor = DenTrainPalette.Lime, contentColor = Color.Black),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Filled.Check, null)
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Принять", fontWeight = FontWeight.Bold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { requestDecision = req to false },
                                                            enabled = req.id !in vm.decidingRequestIds,
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
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

    requestDecision?.let { (request, accepted) ->
        ConfirmDialog(
            title = if (accepted) "Принять заявку?" else "Отклонить заявку?",
            text = if (accepted) "Вы станете тренером спортсмена «${request.athleteName}» и получите доступ к его тренировкам."
                else "Спортсмен «${request.athleteName}» получит уведомление об отказе.",
            confirmText = if (accepted) "Принять" else "Отклонить",
            destructive = !accepted,
            onConfirm = {
                requestDecision = null
                vm.decideRequest(request, accepted)
            },
            onDismiss = { requestDecision = null }
        )
    }

    if (showLeaveClubConfirm) {
        ConfirmDialog(
            title = "Покинуть клуб?",
            text = "Вы перестанете числиться тренером этого клуба.",
            confirmText = "Покинуть",
            onConfirm = {
                showLeaveClubConfirm = false
                vm.leaveClub()
            },
            onDismiss = { showLeaveClubConfirm = false }
        )
    }


}
