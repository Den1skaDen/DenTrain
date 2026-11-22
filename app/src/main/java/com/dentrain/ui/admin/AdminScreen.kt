package com.dentrain.ui.admin

import com.dentrain.ui.components.DiscardChangesDialog
import androidx.activity.compose.BackHandler
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.util.checkForUser
import com.dentrain.util.userMessage
import androidx.compose.animation.animateContentSize
import com.dentrain.ui.components.EditorTextField
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.data.AdminUserModel
import com.dentrain.data.AssignedExerciseModel
import com.dentrain.data.AssignedWorkoutModel
import com.dentrain.data.ClubModel
import com.dentrain.data.WorkoutDelivery
import com.dentrain.data.auditEvent
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Transaction
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.dentrain.data.TrainerModel
import com.dentrain.ui.components.AdminEditUserPanel
import com.dentrain.ui.components.AssignWorkoutPanel
import com.dentrain.ui.components.AssignedWorkoutDetailPanel
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.UserProfilePanel
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.dentrain.ui.components.rememberAvatarBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val ADMIN_AUDIT_LIMIT = 200L

class AdminViewModel(private val delivery: WorkoutDelivery) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    var users by mutableStateOf<List<AdminUserModel>>(emptyList())
    var clubs by mutableStateOf<List<ClubModel>>(emptyList())

    var searchQuery by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    var userWorkouts by mutableStateOf<List<AssignedWorkoutModel>>(emptyList())
    var isLoadingWorkouts by mutableStateOf(false)

    init {
        loadData()
    }

    fun loadUserWorkouts(userUid: String) {
        isLoadingWorkouts = true
        viewModelScope.launch {
            try {
                userWorkouts = WorkoutDelivery.loadAssignments(db, userUid)
            } catch (e: Exception) {
                error = e.userMessage("Не удалось загрузить тренировки пользователя.")
                userWorkouts = emptyList()
            } finally {
                isLoadingWorkouts = false
            }
        }
    }

    var assignmentMessage by mutableStateOf<String?>(null)
    private var isQueuingWorkout = false
    var audit by mutableStateOf<List<Map<String, Any>>>(emptyList())
        private set
    var isLoadingAudit by mutableStateOf(false)
        private set

    fun loadAudit() {
        if (isLoadingAudit) return
        error = null
        isLoadingAudit = true
        viewModelScope.launch {
            try {
                audit = db.collection("admin_audit").orderBy("occurredAt", Query.Direction.DESCENDING)
                    .limit(ADMIN_AUDIT_LIMIT).get().await().documents.mapNotNull { it.data }
            } catch (e: Exception) {
                error = e.userMessage("Не удалось загрузить журнал.")
            } finally {
                isLoadingAudit = false
            }
        }
    }

    fun assignWorkoutToUser(
        userUid: String,
        title: String,
        dateEpochDay: Long,
        description: String,
        exercises: List<AssignedExerciseModel>,
        onDone: () -> Unit
    ) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        if (isQueuingWorkout) return
        isQueuingWorkout = true
        error = null
        viewModelScope.launch {
            try {
                val sending = delivery.enqueue(
                    uid, "Администратор", userUid, title, dateEpochDay, description, exercises, admin = true
                )
                assignmentMessage = "Тренировка в очереди. Подтверждение появится в уведомлениях после получения."
                onDone()
                sending.addOnSuccessListener {
                    assignmentMessage = "Тренировка отправлена. Ожидаем получения учеником."
                    loadUserWorkouts(userUid)
                }.addOnFailureListener { failure ->
                    assignmentMessage = null
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

    fun loadData() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                // Загрузка пользователей
                val usersSnapshot = db.collection("profiles").get().await()
                users = usersSnapshot.documents.map { doc ->
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

                // Загрузка клубов
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
            } catch (e: Exception) {
                error = e.userMessage("Ошибка загрузки данных админки.")
            } finally {
                isLoading = false
            }
        }
    }

    // --- Управление Пользователями ---
    fun saveUserFull(
        userUid: String,
        nickname: String,
        username: String,
        role: String,
        club: ClubModel?,
        trainer: AdminUserModel?,
        onDone: () -> Unit
    ) {
        val actor = FirebaseAuth.getInstance().uid ?: return
        val cleanNickname = nickname.trim()
        val cleanUsername = username.trim()
        if (cleanNickname.isEmpty() || cleanNickname.length > 80 || cleanUsername.length > 40) {
            error = "Введите имя от 1 до 80 символов и никнейм до 40 символов."
            return
        }
        if (isSavingProfile) return
        isSavingProfile = true
        error = null
        viewModelScope.launch {
            try {
                val profile = db.collection("profiles").document(userUid)
                if (role == "athlete") checkNoStudents(userUid)
                db.runTransaction { tx ->
                    val before = tx.get(profile)
                    val oldRole = before.getString("role")
                    val oldTrainer = before.getString("trainerUid")
                    val trainerUid = trainer?.uid.takeIf { role == "athlete" }
                    checkForUser(userUid != actor || oldRole == role) { "Изменить собственную роль нельзя." }
                    val clubName = when {
                        club == null -> null
                        club.id == before.getString("clubId") -> before.getString("clubName")
                        else -> checkNotNull(tx.get(db.collection("clubs").document(club.id)).getString("name")) {
                            "Выбранный клуб больше недоступен."
                        }
                    }
                    val trainerName = when {
                        trainerUid == null -> null
                        trainerUid == oldTrainer -> before.getString("trainerName")
                        else -> {
                            val currentTrainer = tx.get(db.collection("profiles").document(trainerUid))
                            checkForUser(currentTrainer.getString("role") in listOf("trainer", "admin")) {
                                "Выбранный пользователь больше не может быть тренером."
                            }
                            currentTrainer.getString("nickname")
                        }
                    }
                    tx.update(profile, mapOf(
                        "nickname" to cleanNickname,
                        "username" to cleanUsername,
                        "role" to role,
                        "clubId" to club?.id,
                        "clubName" to clubName,
                        "trainerUid" to trainerUid,
                        "trainerName" to trainerName
                    ))
                    val trainerChanged = oldTrainer != trainerUid
                    val roleChanged = oldRole != role
                    if (trainerChanged || roleChanged) {
                        val trainerLabel = listOf(
                            before.getString("trainerName") ?: "Без тренера",
                            trainerName ?: "Без тренера"
                        ).joinToString(" → ")
                        val roleLabel = "${oldRole.orEmpty()} → $role"
                        when {
                            roleChanged && trainerChanged -> tx.recordAudit(
                                actor, "profile", userUid, "access_changed", "$roleLabel · $trainerLabel"
                            )
                            roleChanged -> tx.recordAudit(actor, "profile", userUid, "role_changed", roleLabel)
                            else -> tx.recordAudit(actor, "profile", userUid, "trainer_changed", trainerLabel)
                        }
                    }
                }.await()
                loadData()
                onDone()
            } catch (e: Exception) {
                error = e.userMessage("Не удалось сохранить профиль.")
            } finally {
                isSavingProfile = false
            }
        }
    }

    private var isSavingProfile = false

    private suspend fun checkNoStudents(trainerUid: String) {
        val students = db.collection("profiles").whereEqualTo("trainerUid", trainerUid)
            .limit(1).get(Source.SERVER).await()
        checkForUser(students.isEmpty) { "Сначала переведите учеников к другому тренеру или открепите их." }
    }

    private fun Transaction.recordAudit(actor: String, kind: String, id: String, action: String, label: String) {
        val entry = db.collection("admin_audit").document()
        val head = db.collection("admin_audit_heads").document(kind).collection("targets").document(id)
        set(entry, auditEvent(actor, action, id, label))
        set(head, mapOf("auditId" to entry.id, "action" to action, "occurredAt" to FieldValue.serverTimestamp()))
    }

    fun deleteUser(uid: String) {
        val actor = FirebaseAuth.getInstance().uid ?: return
        if (actor == uid) {
            error = "Собственный профиль удаляется в настройках аккаунта."
            return
        }
        viewModelScope.launch {
            try {
                checkNoStudents(uid)
                val profile = db.collection("profiles").document(uid)
                db.runTransaction { tx ->
                    val before = tx.get(profile)
                    checkForUser(before.exists()) { "Профиль уже удалён." }
                    tx.delete(profile)
                    tx.recordAudit(actor, "profile", uid, "profile_deleted", before.getString("nickname").orEmpty())
                }.await()
                loadData()
            } catch (e: Exception) {
                error = e.userMessage("Не удалось удалить профиль.")
            }
        }
    }

    fun saveClub(club: ClubModel, onDone: () -> Unit) {
        val actor = FirebaseAuth.getInstance().uid ?: return
        viewModelScope.launch {
            try {
                val document = if (club.id.isBlank()) db.collection("clubs").document() else db.collection("clubs").document(club.id)
                val data = mutableMapOf(
                    "name" to club.name.trim(),
                    "address" to club.address.trim(),
                    "phone" to club.phone.trim(),
                    "description" to club.description.trim()
                )
                if (club.id.isBlank()) data["ownerUid"] = actor
                document.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                loadData()
                onDone()
            } catch (e: Exception) {
                error = e.userMessage("Не удалось сохранить клуб.")
            }
        }
    }

    fun deleteClub(id: String) {
        val actor = FirebaseAuth.getInstance().uid ?: return
        viewModelScope.launch {
            try {
                val members = db.collection("profiles").whereEqualTo("clubId", id).limit(1).get(Source.SERVER).await()
                checkForUser(members.isEmpty) { "Сначала переведите участников в другой клуб или уберите клуб из их профилей." }
                val club = db.collection("clubs").document(id)
                db.runTransaction { tx ->
                    val before = tx.get(club)
                    checkForUser(before.exists()) { "Клуб уже удалён." }
                    tx.delete(club)
                    tx.recordAudit(actor, "club", id, "club_deleted", before.getString("name").orEmpty())
                }.await()
                loadData()
            } catch (e: Exception) {
                error = e.userMessage("Не удалось удалить клуб.")
            }
        }
    }
}

@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel(it.workoutDelivery) }
    var selectedTab by remember { mutableStateOf(0) }
    LaunchedEffect(selectedTab) { if (selectedTab == 3) vm.loadAudit() }

    // Диалоги редактирования и просмотр
    var editingUser by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingUserAccount by remember { mutableStateOf<AdminUserModel?>(null) }
    var viewingClub by remember { mutableStateOf<ClubModel?>(null) }
    var showDeleteClubConfirm by remember { mutableStateOf<ClubModel?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ErrorText(vm.error, Modifier.padding(horizontal = 16.dp))
            vm.assignmentMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = DenTrainPalette.Lime) }
            if (editingUser != null) {
                val user = editingUser!!
                val availableTrainers = remember(vm.users, user.uid) {
                    vm.users.filter { it.uid != user.uid && (it.role == "trainer" || it.role == "admin") }
                }
                AdminEditUserPanel(
                    user = user,
                    availableClubs = vm.clubs,
                    availableTrainers = availableTrainers,
                    onBack = { editingUser = null },
                    onDelete = {
                        vm.deleteUser(user.uid)
                        editingUser = null
                    },
                    onSave = { nickname, username, role, club, trainer ->
                        vm.saveUserFull(user.uid, nickname, username, role, club, trainer) {
                            editingUser = null
                        }
                    }
                )
            } else if (viewingUserAccount != null) {
                val user = viewingUserAccount!!
                val currentUserId = FirebaseAuth.getInstance().uid
                LaunchedEffect(user.uid) {
                    vm.loadUserWorkouts(user.uid)
                }

                var viewingWorkoutDetail by remember { mutableStateOf<AssignedWorkoutModel?>(null) }
                var showAssignWorkoutPanel by remember { mutableStateOf(false) }
                var workoutToReassign by remember { mutableStateOf<AssignedWorkoutModel?>(null) }

                if (viewingWorkoutDetail != null) {
                    AssignedWorkoutDetailPanel(
                        workout = viewingWorkoutDetail!!,
                        user = user,
                        onBack = { viewingWorkoutDetail = null },
                        onReassign = {
                            workoutToReassign = viewingWorkoutDetail
                            viewingWorkoutDetail = null
                            showAssignWorkoutPanel = true
                        }
                    )
                } else if (showAssignWorkoutPanel) {
                    AssignWorkoutPanel(
                        athleteName = user.nickname,
                        onBack = {
                            showAssignWorkoutPanel = false
                            workoutToReassign = null
                        },
                        onAssign = { title, dateEpochDay, description, exercises ->
                            vm.assignWorkoutToUser(user.uid, title, dateEpochDay, description, exercises) {
                                showAssignWorkoutPanel = false
                                workoutToReassign = null
                            }
                        },
                        initialTitle = workoutToReassign?.title ?: "",
                        initialDescription = workoutToReassign?.description ?: "",
                        initialExercises = workoutToReassign?.exercises ?: emptyList()
                    )
                } else {
                    UserProfilePanel(
                        user = user,
                        onBack = { viewingUserAccount = null },
                        onEdit = { editingUser = user },
                        onDelete = {
                            vm.deleteUser(user.uid)
                            viewingUserAccount = null
                        },
                        onAssignWorkout = if (user.role == "athlete") {
                            {
                                workoutToReassign = null
                                showAssignWorkoutPanel = true
                            }
                        } else null,
                        onWorkoutClick = { workout ->
                            viewingWorkoutDetail = workout
                        },
                        currentUserId = currentUserId,
                        viewerRole = "admin",
                        userWorkouts = vm.userWorkouts,
                        isLoadingWorkouts = vm.isLoadingWorkouts
                    )
                }
            } else if (viewingClub != null) {
                ClubDetailsPanel(
                    club = viewingClub!!,
                    vm = vm,
                    onBack = { viewingClub = null },
                    onDeleteRequest = { showDeleteClubConfirm = viewingClub },
                    onUserClick = { viewingUserAccount = it }
                )
            } else {
                ScreenHeader(
                    title = "Админ-панель",
                    onBack = onBack
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Пользователи (${vm.users.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.People, null) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = Color.Gray
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Клубы (${vm.clubs.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.Business, null) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = Color.Gray
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Тренеры", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.Sports, null) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = Color.Gray
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Журнал") },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = Color.Gray
                    )
                }

                if (vm.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }


                when (selectedTab) {
                    0 -> {
                        // ВКЛАДКА: ПОЛЬЗОВАТЕЛИ
                        var roleFilter by remember { mutableStateOf<String?>(null) }
                        var clubFilter by remember { mutableStateOf<String?>(null) }
                        
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isFocused by remember { mutableStateOf(false) }
                                val searchBorderColor by animateColorAsState(
                                    targetValue = if (isFocused) Color.White else DenTrainPalette.Border,
                                    animationSpec = tween(300),
                                    label = "searchBorder"
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = vm.searchQuery,
                                        onValueChange = { vm.searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .background(DenTrainPalette.Panel, RoundedCornerShape(24.dp))
                                            .border(1.dp, searchBorderColor, RoundedCornerShape(24.dp))
                                            .onFocusChanged { isFocused = it.isFocused },
                                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFFFFB300)),
                                        decorationBox = { innerTextField ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = "Поиск",
                                                    tint = DenTrainPalette.MutedText,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Box(Modifier.weight(1f)) {
                                                    if (vm.searchQuery.isEmpty()) {
                                                        Text("Поиск пользователя...", color = DenTrainPalette.MutedText, fontSize = 15.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        }
                                    )
                                }
                                
                                Spacer(Modifier.width(8.dp))
                                
                                var showFilterMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showFilterMenu = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(DenTrainPalette.Panel, CircleShape)
                                            .border(1.dp, DenTrainPalette.Border, CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Filled.FilterList,
                                            contentDescription = "Фильтр",
                                            tint = if (roleFilter != null || clubFilter != null) DenTrainPalette.Lime else Color.White
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false },
                                        modifier = Modifier.background(Color(0xFF222938))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Все пользователи", color = if (roleFilter == null) DenTrainPalette.Lime else Color.White) },
                                            onClick = { roleFilter = null; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Админы", color = if (roleFilter == "admin") DenTrainPalette.Lime else Color.White) },
                                            onClick = { roleFilter = "admin"; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Тренеры", color = if (roleFilter == "trainer") DenTrainPalette.Lime else Color.White) },
                                            onClick = { roleFilter = "trainer"; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Атлеты", color = if (roleFilter == "athlete") DenTrainPalette.Lime else Color.White) },
                                            onClick = { roleFilter = "athlete"; showFilterMenu = false }
                                        )
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        DropdownMenuItem(
                                            text = { Text("Все клубы", color = if (clubFilter == null) DenTrainPalette.Lime else Color.White) },
                                            onClick = { clubFilter = null; showFilterMenu = false }
                                        )
                                        vm.clubs.forEach { club ->
                                            DropdownMenuItem(
                                                text = { Text(club.name.ifBlank { "Без названия" }, color = if (clubFilter == club.id) DenTrainPalette.Lime else Color.White) },
                                                onClick = { clubFilter = club.id; showFilterMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            val filteredUsers = remember(vm.users, vm.searchQuery, roleFilter, clubFilter) {
                                vm.users.filter { u ->
                                    val matchesSearch = if (vm.searchQuery.isBlank()) true else {
                                        u.nickname.contains(vm.searchQuery, true) ||
                                        u.username.contains(vm.searchQuery, true) ||
                                        u.email.contains(vm.searchQuery, true)
                                    }
                                    val matchesRole = if (roleFilter == null) true else u.role == roleFilter
                                    val matchesClub = if (clubFilter == null) true else u.clubId == clubFilter
                                    matchesSearch && matchesRole && matchesClub
                                }
                            }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val currentUserId = FirebaseAuth.getInstance().uid
                                items(filteredUsers, key = { it.uid }) { user ->
                                    UserListItem(
                                        user = user,
                                        currentUserId = currentUserId,
                                        onItemClick = { viewingUserAccount = user },
                                        onEdit = { editingUser = user }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // ВКЛАДКА: КЛУБЫ
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1C1E26))
                                    .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                                    .clickable { viewingClub = ClubModel() }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ Добавить клуб", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(12.dp))

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(vm.clubs, key = { it.id }) { club ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1C1E26))
                                    ) {
                                        Row(
                                            Modifier.padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Business, null, Modifier.size(32.dp), tint = DenTrainPalette.Lime)
                                            Spacer(Modifier.width(16.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(club.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
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

                    2 -> {
                        // ВКЛАДКА: ТРЕНЕРЫ
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            val trainers = remember(vm.users) {
                                vm.users.filter { it.role in setOf("trainer", "admin") }.sortedBy { it.clubName ?: "Без клуба" }
                            }
                            
                            if (trainers.isEmpty()) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Нет тренеров", color = Color.Gray) }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val currentUserId = FirebaseAuth.getInstance().uid
                                    items(trainers, key = { it.uid }) { trainer ->
                                        UserListItem(
                                            user = trainer,
                                            currentUserId = currentUserId,
                                            onItemClick = { viewingUserAccount = trainer },
                                            onEdit = { editingUser = trainer }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> AdminAuditList(vm, onOpenProfile = { viewingUserAccount = it })
                }
            }
        }
    }

    showDeleteClubConfirm?.let { club ->
        ConfirmDialog(
            title = "Удалить клуб?",
            text = "Клуб «${club.name}» будет удалён. Восстановить его не получится.",
            confirmText = "Удалить",
            onConfirm = {
                showDeleteClubConfirm = null
                vm.deleteClub(club.id)
                if (viewingClub?.id == club.id) viewingClub = null
            },
            onDismiss = { showDeleteClubConfirm = null }
        )
    }


}

@Composable
private fun AdminAuditList(vm: AdminViewModel, onOpenProfile: (AdminUserModel) -> Unit) {
    val timestampFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm").withZone(ZoneId.systemDefault()) }
    val usersById = remember(vm.users) { vm.users.associateBy { it.uid } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Последние $ADMIN_AUDIT_LIMIT событий", Modifier.weight(1f), color = DenTrainPalette.MutedText)
            TextButton(onClick = vm::loadAudit, enabled = !vm.isLoadingAudit) { Text("Обновить") }
        }
        if (vm.isLoadingAudit) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else if (vm.audit.isEmpty()) {
            Text("Записей пока нет", color = DenTrainPalette.MutedText)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.audit) { entry ->
                val action = when (entry["action"]) {
                    "role_changed" -> "Изменена роль"
                    "trainer_changed" -> "Изменён тренер"
                    "access_changed" -> "Изменены роль и тренер"
                    "profile_deleted" -> "Удалён профиль"
                    "club_deleted" -> "Удалён клуб"
                    "workout_assigned" -> "Назначена тренировка"
                    else -> "Действие администратора"
                }
                val timestamp = entry["occurredAt"] as? com.google.firebase.Timestamp
                val actorId = (entry["actorUid"] as? String).orEmpty()
                val targetId = (entry["targetId"] as? String).orEmpty()
                val actor = usersById[actorId]?.nickname ?: actorId
                val targetProfile = when (entry["action"]) {
                    "role_changed", "trainer_changed", "access_changed", "workout_assigned" -> usersById[targetId]
                    else -> null
                }
                val target = targetProfile?.nickname ?: targetId
                val label = entry["label"] as? String ?: ""
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(DenTrainPalette.RaisedPanel).padding(12.dp)) {
                    Text(action, color = Color.White, fontWeight = FontWeight.Bold)
                    if (entry["action"] in listOf("role_changed", "access_changed")) {
                        Text(auditRoleTransition(label), color = Color.White)
                    } else {
                        Text(label, color = Color.White)
                    }
                    Text("Кто · $actor", color = DenTrainPalette.MutedText, fontSize = 12.sp)
                    if (targetProfile != null) {
                        TextButton(onClick = { onOpenProfile(targetProfile) }) {
                            Text("Объект · $target")
                        }
                    } else {
                        Text("Объект · $target", color = DenTrainPalette.MutedText, fontSize = 12.sp)
                    }
                    timestamp?.let {
                        Text(timestampFormat.format(it.toDate().toInstant()), color = DenTrainPalette.MutedText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun auditRoleTransition(label: String) = buildAnnotatedString {
    val roleAndDetails = label.split(" · ", limit = 2)
    val roles = roleAndDetails.first().split(" → ", limit = 2)
    if (roles.size != 2) {
        append(label)
        return@buildAnnotatedString
    }
    roles.forEachIndexed { index, role ->
        if (index == 1) append(" → ")
        val (name, color) = when (role) {
            "athlete" -> "Пользователь" to Color.White
            "trainer" -> "Тренер" to Color(0xFF64B5F6)
            "admin" -> "Администратор" to Color(0xFFFFD54F)
            else -> role to Color.White
        }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(name) }
    }
    roleAndDetails.getOrNull(1)?.let { append("\n$it") }
}

@Composable
fun ClubDetailsPanel(
    club: ClubModel,
    vm: AdminViewModel,
    onBack: () -> Unit,
    onDeleteRequest: () -> Unit,
    onUserClick: ((AdminUserModel) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(club.id.isBlank()) }
    
    var name by remember { mutableStateOf(club.name) }
    var address by remember { mutableStateOf(club.address) }
    var phone by remember { mutableStateOf(club.phone) }
    var description by remember { mutableStateOf(club.description) }

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

    val clubTrainers = remember(vm.users, club.id) { vm.users.filter { it.clubId == club.id && it.role in setOf("trainer", "admin") } }
    val clubAthletes = remember(vm.users, club.id) { vm.users.filter { it.clubId == club.id && it.role == "athlete" } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        ScreenHeader(
            title = if (club.id.isBlank()) "Новый клуб" else "Подробнее о клубе",
            onBack = handleBack,
            actions = {
                if (isEditing) {
                    IconButton(onClick = {
                        vm.saveClub(editedClub) {
                            savedClub = editedClub
                            isEditing = false
                            if (club.id.isBlank()) onBack()
                        }
                    }) {
                        Icon(Icons.Filled.Check, "Сохранить", tint = Color.White)
                    }
                } else if (club.id.isNotBlank()) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, "Редактировать", tint = Color.White)
                    }
                    IconButton(onClick = onDeleteRequest) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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
                    Text(club.name.ifBlank { "Без названия" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    if (club.address.isNotBlank()) Text(club.address, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (club.phone.isNotBlank()) Text("Тел: ${club.phone}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (club.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Описание:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Text(club.description, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (club.id.isNotBlank()) {
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
                            UserListItem(user = trainer, renderAsCard = false, onItemClick = { onUserClick?.invoke(trainer) })
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
                            UserListItem(user = athlete, renderAsCard = false, onItemClick = { onUserClick?.invoke(athlete) })
                            if (index < clubAthletes.size - 1) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun UserListItem(
    user: AdminUserModel, 
    currentUserId: String? = null, 
    onEdit: (() -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
    renderAsCard: Boolean = true
) {
    val decodedBitmap = rememberAvatarBitmap(user.avatarBase64)

    val ageText = remember(user.dobEpochDay) {
        if (user.dobEpochDay != null) {
            val age = ChronoUnit.YEARS.between(
                LocalDate.ofEpochDay(user.dobEpochDay),
                LocalDate.now()
            ).toInt()
            ", $age лет"
        } else {
            ""
        }
    }

    val avatarBorderColor = when (user.role) {
        "admin" -> Color(0xFFFFC107)
        "trainer" -> Color(0xFF00BFFF)
        else -> Color.White
    }
    
    val clickModifier = if (onItemClick != null) Modifier.clickable { onItemClick() } else Modifier

    if (renderAsCard) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1E26))
                .border(1.dp, avatarBorderColor, RoundedCornerShape(16.dp))
                .then(clickModifier)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarModifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DenTrainPalette.RaisedPanel)
                    .border(1.5.dp, avatarBorderColor, CircleShape)
                
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap.asImageBitmap(),
                        contentDescription = "Аватар",
                        modifier = avatarModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = Color.White)
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = user.nickname.ifBlank { "Без имени" } + ageText, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White, 
                        fontSize = 16.sp
                    )
                    if (user.username.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "@${user.username}", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = DenTrainPalette.MutedText
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user.clubName ?: "Без клуба", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = Color.Gray
                    )
                    if (user.role == "admin" || user.role == "trainer") {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = user.role.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = avatarBorderColor
                        )
                    }
                }

                if (onEdit != null) {
                    Row(modifier = Modifier.align(Alignment.Top)) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, "Просмотр / Редактирование", tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarModifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DenTrainPalette.RaisedPanel)
                .border(1.5.dp, avatarBorderColor, CircleShape)
            
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "Аватар",
                    modifier = avatarModifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, tint = Color.White)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.nickname.ifBlank { "Без имени" } + ageText, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White, 
                    fontSize = 16.sp
                )
                if (user.username.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "@${user.username}", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = DenTrainPalette.MutedText
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.clubName ?: "Без клуба", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
                if (user.role == "admin" || user.role == "trainer") {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = user.role.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = avatarBorderColor
                    )
                }
            }

            if (onEdit != null) {
                Row(modifier = Modifier.align(Alignment.Top)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, "Просмотр / Редактирование", tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
