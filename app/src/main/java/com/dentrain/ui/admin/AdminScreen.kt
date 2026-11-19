package com.dentrain.ui.admin

import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AdminViewModel : ViewModel() {
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

    fun assignWorkoutToUser(
        userUid: String,
        title: String,
        dateEpochDay: Long,
        description: String,
        exercises: List<AssignedExerciseModel>,
        onDone: () -> Unit
    ) {
        if (title.isBlank()) {
            error = "Введите название тренировки"
            return
        }
        viewModelScope.launch {
            try {
                val currentAdminUid = FirebaseAuth.getInstance().uid ?: "admin"
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
                    "trainerUid" to currentAdminUid,
                    "trainerName" to "Администратор",
                    "athleteUid" to userUid,
                    "title" to title.trim(),
                    "dateEpochDay" to dateEpochDay,
                    "description" to description.trim(),
                    "exercises" to exercisesMap,
                    "createdAt" to System.currentTimeMillis()
                )
                docRef.set(data).await()
                loadUserWorkouts(userUid)
                onDone()
            } catch (e: Exception) {
                error = "Ошибка назначения тренировки: ${e.localizedMessage}"
            }
        }
    }

    fun loadData() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                // Загрузка пользователей
                val usersSnapshot = db.collection("users").get().await()
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
                error = "Ошибка загрузки данных админки: ${e.localizedMessage}"
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
        viewModelScope.launch {
            try {
                val data = mutableMapOf<String, Any?>(
                    "nickname" to nickname.trim(),
                    "username" to username.trim(),
                    "role" to role,
                    "clubId" to club?.id,
                    "clubName" to club?.name,
                    "trainerUid" to trainer?.uid,
                    "trainerName" to trainer?.nickname
                )
                db.collection("users").document(userUid).update(data as Map<String, Any>).await()

                if (trainer != null) {
                    val reqData = hashMapOf(
                        "trainerUid" to trainer.uid,
                        "trainerName" to trainer.nickname,
                        "athleteUid" to userUid,
                        "athleteName" to nickname,
                        "status" to "pending",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("trainer_requests").add(reqData).await()
                }

                loadData()
                onDone()
            } catch (e: Exception) {
                error = "Ошибка при сохранении пользователя: ${e.localizedMessage}"
            }
        }
    }

    fun saveUser(user: AdminUserModel, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "nickname" to user.nickname.trim(),
                    "username" to user.username.trim(),
                    "role" to user.role
                )
                db.collection("users").document(user.uid).update(data as Map<String, Any>).await()
                loadData()
                onDone()
            } catch (e: Exception) {
                error = "Ошибка при сохранении пользователя: ${e.localizedMessage}"
            }
        }
    }

    fun deleteUser(uid: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).delete().await()
                loadData()
            } catch (e: Exception) {
                error = "Ошибка при удалении пользователя: ${e.localizedMessage}"
            }
        }
    }

    // --- Управление Клубами ---
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
                loadData()
                onDone()
            } catch (e: Exception) {
                error = "Ошибка сохранения клуба: ${e.localizedMessage}"
            }
        }
    }

    fun deleteClub(id: String) {
        viewModelScope.launch {
            try {
                db.collection("clubs").document(id).delete().await()
                loadData()
            } catch (e: Exception) {
                error = "Ошибка при удалении клуба: ${e.localizedMessage}"
            }
        }
    }
}

@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel() }
    var selectedTab by remember { mutableStateOf(0) }

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
            if (editingUser != null) {
                val user = editingUser!!
                val availableTrainers = remember(vm.users) { vm.users.filter { it.role == "trainer" || it.role == "admin" } }
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
                        onAssignWorkout = {
                            workoutToReassign = null
                            showAssignWorkoutPanel = true
                        },
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
                }

                if (vm.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }

                ErrorText(vm.error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

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
                                    targetValue = if (isFocused) Color.White else Color(0xFF333644),
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
                                            .background(Color(0xFF1B1D25), RoundedCornerShape(24.dp))
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
                                                    tint = Color(0xFFA0A7B8),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Box(Modifier.weight(1f)) {
                                                    if (vm.searchQuery.isEmpty()) {
                                                        Text("Поиск пользователя...", color = Color(0xFFA0A7B8), fontSize = 15.sp)
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
                                            .background(Color(0xFF1B1D25), CircleShape)
                                            .border(1.dp, Color(0xFF333644), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Filled.FilterList,
                                            contentDescription = "Фильтр",
                                            tint = if (roleFilter != null || clubFilter != null) Color(0xFFA6FF00) else Color.White
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false },
                                        modifier = Modifier.background(Color(0xFF222938))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Все пользователи", color = if (roleFilter == null) Color(0xFFA6FF00) else Color.White) },
                                            onClick = { roleFilter = null; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Админы", color = if (roleFilter == "admin") Color(0xFFA6FF00) else Color.White) },
                                            onClick = { roleFilter = "admin"; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Тренеры", color = if (roleFilter == "trainer") Color(0xFFA6FF00) else Color.White) },
                                            onClick = { roleFilter = "trainer"; showFilterMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Атлеты", color = if (roleFilter == "athlete") Color(0xFFA6FF00) else Color.White) },
                                            onClick = { roleFilter = "athlete"; showFilterMenu = false }
                                        )
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        DropdownMenuItem(
                                            text = { Text("Все клубы", color = if (clubFilter == null) Color(0xFFA6FF00) else Color.White) },
                                            onClick = { clubFilter = null; showFilterMenu = false }
                                        )
                                        vm.clubs.forEach { club ->
                                            DropdownMenuItem(
                                                text = { Text(club.name.ifBlank { "Без названия" }, color = if (clubFilter == club.id) Color(0xFFA6FF00) else Color.White) },
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
                                            Icon(Icons.Filled.Business, null, Modifier.size(32.dp), tint = Color(0xFFA6FF00))
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
                                vm.users.filter { it.role == "trainer" }.sortedBy { it.clubName ?: "Без клуба" }
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
                }
            }
        }
    }

    showDeleteClubConfirm?.let { clubToDel ->
        AlertDialog(
            onDismissRequest = { showDeleteClubConfirm = null },
            title = { Text("Удалить клуб?") },
            text = { Text("Вы уверены, что хотите удалить клуб ${clubToDel.name}? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteClub(clubToDel.id)
                    showDeleteClubConfirm = null
                    if (viewingClub?.id == clubToDel.id) {
                        viewingClub = null
                    }
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteClubConfirm = null }) {
                    Text("Отмена")
                }
            }
        )
    }
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

    val clubTrainers = remember(vm.users, club.id) { vm.users.filter { it.clubId == club.id && it.role == "trainer" } }
    val clubAthletes = remember(vm.users, club.id) { vm.users.filter { it.clubId == club.id && it.role == "athlete" } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        ScreenHeader(
            title = if (club.id.isBlank()) "Новый клуб" else "Подробнее о клубе",
            onBack = onBack,
            actions = {
                if (isEditing) {
                    IconButton(onClick = {
                        vm.saveClub(club.copy(name = name, address = address, phone = phone, description = description)) {
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
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {

                if (isEditing) {
                    androidx.compose.material3.TextField(
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
    val decodedBitmap = remember(user.avatarBase64) {
        if (user.avatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(user.avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { null }
        } else null
    }

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
                    .background(Color(0xFF242732))
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
                            color = Color(0xFFA0A7B8)
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
                .background(Color(0xFF242732))
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
                        color = Color(0xFFA0A7B8)
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
