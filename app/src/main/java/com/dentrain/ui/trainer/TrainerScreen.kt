package com.dentrain.ui.trainer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dentrain.data.TrainerRequestModel
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class TrainerViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var trainerNickname by mutableStateOf("")
    var selectedClubId by mutableStateOf<String?>(null)
    var selectedClubName by mutableStateOf<String?>(null)

    var clubs by mutableStateOf<List<ClubModel>>(emptyList())
    var requests by mutableStateOf<List<TrainerRequestModel>>(emptyList())
    var students by mutableStateOf<List<AdminUserModel>>(emptyList())

    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)

    init {
        loadTrainerData()
    }

    fun loadTrainerData() {
        val uid = auth.uid ?: return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                // Загружаем профиль тренера
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    trainerNickname = userDoc.getString("nickname") ?: userDoc.getString("email") ?: "Тренер"
                    selectedClubId = userDoc.getString("clubId")
                    selectedClubName = userDoc.getString("clubName")
                }

                // Загружаем список клубов
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

                // Загружаем заявки учеников к данному тренеру
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

                // Загружаем список принятых учеников
                val studentsSnapshot = db.collection("users")
                    .whereEqualTo("trainerUid", uid)
                    .get().await()
                students = studentsSnapshot.documents.map { doc ->
                    AdminUserModel(
                        uid = doc.id,
                        email = doc.getString("email") ?: "",
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        role = doc.getString("role") ?: "athlete",
                        gender = doc.getString("gender") ?: "male",
                        heightCm = doc.getLong("heightCm")?.toInt(),
                        weightKg = doc.getDouble("weightKg"),
                        dobEpochDay = doc.getLong("dobEpochDay")
                    )
                }
            } catch (e: Exception) {
                error = "Ошибка загрузки данных тренера: ${e.localizedMessage}"
            } finally {
                isLoading = false
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
            } catch (e: Exception) {
                error = "Ошибка выбора клуба: ${e.localizedMessage}"
            }
        }
    }

    fun acceptRequest(req: TrainerRequestModel) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                // 1. Обновляем статус заявки
                db.collection("trainer_requests").document(req.id).update("status", "accepted").await()

                // 2. Назначаем тренера в профиле ученика
                db.collection("users").document(req.athleteUid).update(
                    mapOf(
                        "trainerUid" to uid,
                        "trainerName" to trainerNickname
                    )
                ).await()

                successMsg = "Ученик ${req.athleteName} принят под ваше руководство!"
                loadTrainerData()
            } catch (e: Exception) {
                error = "Ошибка принятия заявки: ${e.localizedMessage}"
            }
        }
    }

    fun rejectRequest(req: TrainerRequestModel) {
        viewModelScope.launch {
            try {
                db.collection("trainer_requests").document(req.id).update("status", "rejected").await()
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
fun TrainerScreen(onBack: () -> Unit) {
    val vm = appViewModel { TrainerViewModel() }
    var selectedTab by remember { mutableStateOf(0) }

    var selectedStudentForWorkout by remember { mutableStateOf<AdminUserModel?>(null) }

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "Панель тренера",
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )

            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
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
                return@Column
            }

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
                        ZCard {
                            SectionTitle("Текущий клуб работы")
                            if (vm.selectedClubName != null) {
                                Text(
                                    vm.selectedClubName!!,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("Вы пока не выбрали клуб", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Доступные фитнес-клубы")
                        Spacer(Modifier.height(8.dp))

                        if (vm.clubs.isEmpty()) {
                            Text("Список клубов пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            vm.clubs.forEach { club ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (club.id == vm.selectedClubId)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Business, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(club.name, fontWeight = FontWeight.Bold)
                                            if (club.address.isNotBlank()) Text(club.address, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (club.id == vm.selectedClubId) {
                                            Text("Выбрано", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            OutlinedButton(onClick = { vm.selectClub(club) }) {
                                                Text("Выбрать")
                                            }
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
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Person, null)
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(req.athleteName.ifBlank { "Спортсмен" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                    Text(req.athleteEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { vm.acceptRequest(req) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Check, null)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Принять")
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
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(vm.students, key = { it.uid }) { student ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Person, null)
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(student.nickname.ifBlank { "Ученик" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                    if (student.username.isNotBlank()) {
                                                        Text("@${student.username}", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    val details = listOfNotNull(
                                                        student.gender.let { if (it == "male") "Мужчина" else "Женщина" },
                                                        student.heightCm?.let { "$it см" },
                                                        student.weightKg?.let { Fmt.weight(it) + " кг" }
                                                    ).joinToString(" • ")
                                                    if (details.isNotBlank()) {
                                                        Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            GradientButton(
                                                text = "+ Назначить тренировку",
                                                onClick = { selectedStudentForWorkout = student },
                                                modifier = Modifier.fillMaxWidth()
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

    // --- ДИАЛОГ НАЗНАЧЕНИЯ ТРЕНИРОВКИ УЧЕНИКУ ---
    selectedStudentForWorkout?.let { student ->
        var workoutTitle by remember { mutableStateOf("") }
        var workoutDateEpochDay by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
        var workoutDescription by remember { mutableStateOf("") }
        val exercisesList = remember { mutableStateListOf(AssignedExerciseModel("Жим лежа", 4, "10", 60.0)) }

        AlertDialog(
            onDismissRequest = { selectedStudentForWorkout = null },
            title = { Text("Тренировка для ${student.nickname}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = workoutTitle,
                        onValueChange = { workoutTitle = it },
                        label = { Text("Название тренировки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    DateField(
                        epochDay = workoutDateEpochDay,
                        onChange = { workoutDateEpochDay = it },
                        label = "Дата выполнения",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = workoutDescription,
                        onValueChange = { workoutDescription = it },
                        label = { Text("Инструкции / Описание") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Упражнения")

                    exercisesList.forEachIndexed { index, ex ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = ex.name,
                                        onValueChange = { exercisesList[index] = ex.copy(name = it) },
                                        label = { Text("Упражнение") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { exercisesList.removeAt(index) }) {
                                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = ex.plannedSets.toString(),
                                        onValueChange = { exercisesList[index] = ex.copy(plannedSets = it.toIntOrNull() ?: 1) },
                                        label = { Text("Подходы") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = ex.plannedReps,
                                        onValueChange = { exercisesList[index] = ex.copy(plannedReps = it) },
                                        label = { Text("Повторы") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = ex.plannedWeight?.toString() ?: "",
                                        onValueChange = { exercisesList[index] = ex.copy(plannedWeight = it.toDoubleOrNull()) },
                                        label = { Text("Вес, кг") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { exercisesList.add(AssignedExerciseModel("", 3, "10", 0.0)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить упражнение")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.assignWorkoutToStudent(
                        studentUid = student.uid,
                        title = workoutTitle,
                        dateEpochDay = workoutDateEpochDay,
                        description = workoutDescription,
                        exercises = exercisesList.toList(),
                        onDone = { selectedStudentForWorkout = null }
                    )
                }) {
                    Text("Отправить ученику")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedStudentForWorkout = null }) { Text("Отмена") }
            }
        )
    }
}
