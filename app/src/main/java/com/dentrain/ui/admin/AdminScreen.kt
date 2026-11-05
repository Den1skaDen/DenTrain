package com.dentrain.ui.admin

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.data.AdminUserModel
import com.dentrain.data.ClubModel
import com.dentrain.data.TrainerModel
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    var users by mutableStateOf<List<AdminUserModel>>(emptyList())
    var clubs by mutableStateOf<List<ClubModel>>(emptyList())
    var trainers by mutableStateOf<List<TrainerModel>>(emptyList())

    var searchQuery by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init {
        loadData()
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
                        role = doc.getString("role") ?: "athlete",
                        gender = doc.getString("gender") ?: "male",
                        heightCm = doc.getLong("heightCm")?.toInt(),
                        weightKg = doc.getDouble("weightKg"),
                        dobEpochDay = doc.getLong("dobEpochDay")
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

                // Загрузка тренеров
                val trainersSnapshot = db.collection("trainers").get().await()
                trainers = trainersSnapshot.documents.map { doc ->
                    TrainerModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        specialty = doc.getString("specialty") ?: "",
                        phone = doc.getString("phone") ?: "",
                        bio = doc.getString("bio") ?: "",
                        clubId = doc.getString("clubId") ?: "",
                        clubName = doc.getString("clubName") ?: ""
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
    fun saveUser(user: AdminUserModel, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "nickname" to user.nickname.trim(),
                    "username" to user.username.trim(),
                    "role" to user.role,
                    "gender" to user.gender
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

    // --- Управление Тренерами ---
    fun saveTrainer(trainer: TrainerModel, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val docRef = if (trainer.id.isBlank()) db.collection("trainers").document() else db.collection("trainers").document(trainer.id)
                val data = hashMapOf(
                    "name" to trainer.name.trim(),
                    "specialty" to trainer.specialty.trim(),
                    "phone" to trainer.phone.trim(),
                    "bio" to trainer.bio.trim(),
                    "clubName" to trainer.clubName.trim()
                )
                docRef.set(data).await()
                loadData()
                onDone()
            } catch (e: Exception) {
                error = "Ошибка сохранения тренера: ${e.localizedMessage}"
            }
        }
    }

    fun deleteTrainer(id: String) {
        viewModelScope.launch {
            try {
                db.collection("trainers").document(id).delete().await()
                loadData()
            } catch (e: Exception) {
                error = "Ошибка при удалении тренера: ${e.localizedMessage}"
            }
        }
    }
}

@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel() }
    var selectedTab by remember { mutableStateOf(0) }

    // Диалоги редактирования
    var editingUser by remember { mutableStateOf<AdminUserModel?>(null) }
    var editingClub by remember { mutableStateOf<ClubModel?>(null) }
    var editingTrainer by remember { mutableStateOf<TrainerModel?>(null) }

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "Админ-панель",
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Пользователи (${vm.users.size})") },
                    icon = { Icon(Icons.Filled.People, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Клубы (${vm.clubs.size})") },
                    icon = { Icon(Icons.Filled.Business, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Тренеры (${vm.trainers.size})") },
                    icon = { Icon(Icons.Filled.Sports, null) }
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
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        OutlinedTextField(
                            value = vm.searchQuery,
                            onValueChange = { vm.searchQuery = it },
                            label = { Text("Поиск пользователя...") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        val filteredUsers = remember(vm.users, vm.searchQuery) {
                            if (vm.searchQuery.isBlank()) vm.users
                            else vm.users.filter { u ->
                                u.nickname.contains(vm.searchQuery, true) ||
                                u.username.contains(vm.searchQuery, true) ||
                                u.email.contains(vm.searchQuery, true)
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredUsers, key = { it.uid }) { user ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Person, null)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(user.nickname.ifBlank { "Без имени" }, fontWeight = FontWeight.Bold)
                                            if (user.username.isNotBlank()) {
                                                Text("@${user.username}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                "Роль: ${user.role.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (user.role == "admin") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { editingUser = user }) {
                                            Icon(Icons.Filled.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { vm.deleteUser(user.uid) }) {
                                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // ВКЛАДКА: КЛУБЫ
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        GradientButton(
                            text = "+ Добавить клуб",
                            onClick = { editingClub = ClubModel() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.clubs, key = { it.id }) { club ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Business, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(club.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (club.address.isNotBlank()) Text(club.address, style = MaterialTheme.typography.bodySmall)
                                            if (club.phone.isNotBlank()) Text("Тел: ${club.phone}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { editingClub = club }) {
                                            Icon(Icons.Filled.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { vm.deleteClub(club.id) }) {
                                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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
                        GradientButton(
                            text = "+ Добавить тренера",
                            onClick = { editingTrainer = TrainerModel() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.trainers, key = { it.id }) { trainer ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Sports, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(trainer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (trainer.specialty.isNotBlank()) Text(trainer.specialty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            if (trainer.clubName.isNotBlank()) Text("Клуб: ${trainer.clubName}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { editingTrainer = trainer }) {
                                            Icon(Icons.Filled.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { vm.deleteTrainer(trainer.id) }) {
                                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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

    // --- Диалог Редактирования Пользователя ---
    editingUser?.let { user ->
        var nickname by remember { mutableStateOf(user.nickname) }
        var username by remember { mutableStateOf(user.username) }
        var role by remember { mutableStateOf(user.role) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Редактирование аккаунта") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Никнейм") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Роль пользователя:", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = role == "athlete",
                            onClick = { role = "athlete" },
                            label = { Text("Спортсмен") }
                        )
                        FilterChip(
                            selected = role == "trainer",
                            onClick = { role = "trainer" },
                            label = { Text("Тренер") }
                        )
                        FilterChip(
                            selected = role == "admin",
                            onClick = { role = "admin" },
                            label = { Text("Админ") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveUser(user.copy(nickname = nickname, username = username, role = role)) {
                        editingUser = null
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Отмена") }
            }
        )
    }

    // --- Диалог Создания / Редактирования Клуба ---
    editingClub?.let { club ->
        var name by remember { mutableStateOf(club.name) }
        var address by remember { mutableStateOf(club.address) }
        var phone by remember { mutableStateOf(club.phone) }
        var description by remember { mutableStateOf(club.description) }

        AlertDialog(
            onDismissRequest = { editingClub = null },
            title = { Text(if (club.id.isBlank()) "Новый клуб" else "Редактировать клуб") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название клуба") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Адрес") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Телефон") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveClub(club.copy(name = name, address = address, phone = phone, description = description)) {
                        editingClub = null
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingClub = null }) { Text("Отмена") }
            }
        )
    }

    // --- Диалог Создания / Редактирования Тренера ---
    editingTrainer?.let { trainer ->
        var name by remember { mutableStateOf(trainer.name) }
        var specialty by remember { mutableStateOf(trainer.specialty) }
        var phone by remember { mutableStateOf(trainer.phone) }
        var clubName by remember { mutableStateOf(trainer.clubName) }
        var bio by remember { mutableStateOf(trainer.bio) }

        AlertDialog(
            onDismissRequest = { editingTrainer = null },
            title = { Text(if (trainer.id.isBlank()) "Новый тренер" else "Редактировать тренера") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("ФИО / Имя тренера") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = specialty,
                        onValueChange = { specialty = it },
                        label = { Text("Специализация (Бодибилдинг, Кроссфит и др.)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clubName,
                        onValueChange = { clubName = it },
                        label = { Text("Название клуба") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Телефон") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("О тренере / Регалии") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveTrainer(trainer.copy(name = name, specialty = specialty, phone = phone, clubName = clubName, bio = bio)) {
                        editingTrainer = null
                    }
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTrainer = null }) { Text("Отмена") }
            }
        )
    }
}
