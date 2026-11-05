package com.dentrain.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sports
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.data.AdminUserModel
import com.dentrain.data.ClubModel
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate

data class UserProfile(
    val nickname: String = "",
    val username: String = "",
    val dobEpochDay: Long = LocalDate.now().toEpochDay(),
    val avatarUrl: String = "",
    val role: String = "athlete", // "admin", "trainer", "athlete"
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val gender: String = "male", // "male" или "female"
    val clubId: String? = null,
    val clubName: String? = null,
    val trainerUid: String? = null,
    val trainerName: String? = null
)

class ProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var profile by mutableStateOf(UserProfile())
    var availableClubs by mutableStateOf<List<ClubModel>>(emptyList())
    var availableTrainers by mutableStateOf<List<AdminUserModel>>(emptyList())
    var pendingTrainerRequestName by mutableStateOf<String?>(null)

    var isLoading by mutableStateOf(true)
    var isSaving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    profile = UserProfile(
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        dobEpochDay = doc.getLong("dobEpochDay") ?: LocalDate.now().toEpochDay(),
                        avatarUrl = doc.getString("avatarBase64") ?: "",
                        role = doc.getString("role") ?: "athlete",
                        heightCm = doc.getLong("heightCm")?.toInt(),
                        weightKg = doc.getDouble("weightKg"),
                        gender = doc.getString("gender") ?: "male",
                        clubId = doc.getString("clubId"),
                        clubName = doc.getString("clubName"),
                        trainerUid = doc.getString("trainerUid"),
                        trainerName = doc.getString("trainerName")
                    )
                }

                // Загружаем список доступных клубов
                val clubsSnap = db.collection("clubs").get().await()
                availableClubs = clubsSnap.documents.map { d ->
                    ClubModel(id = d.id, name = d.getString("name") ?: "", address = d.getString("address") ?: "")
                }

                // Загружаем список всех зарегистрированных тренеров
                val usersSnap = db.collection("users").get().await()
                availableTrainers = usersSnap.documents.mapNotNull { d ->
                    val r = d.getString("role") ?: "athlete"
                    if (r == "trainer" || r == "admin") {
                        AdminUserModel(
                            uid = d.id,
                            nickname = d.getString("nickname") ?: d.getString("email") ?: "Тренер",
                            username = d.getString("username") ?: "",
                            role = r
                        )
                    } else null
                }

                // Проверяем, есть ли отправленные заявки
                val reqSnap = db.collection("trainer_requests")
                    .whereEqualTo("athleteUid", uid)
                    .whereEqualTo("status", "pending")
                    .get().await()
                if (!reqSnap.isEmpty) {
                    pendingTrainerRequestName = reqSnap.documents.first().getString("trainerName")
                }
            } catch (e: Exception) {
                error = "Не удалось загрузить данные: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadTrainersForClub(clubId: String) {
        viewModelScope.launch {
            try {
                val usersSnap = db.collection("users")
                    .whereEqualTo("clubId", clubId)
                    .get().await()
                
                availableTrainers = usersSnap.documents.mapNotNull { d ->
                    val r = d.getString("role") ?: "athlete"
                    if (r == "trainer" || r == "admin") {
                        AdminUserModel(
                            uid = d.id,
                            nickname = d.getString("nickname") ?: d.getString("email") ?: "Тренер",
                            username = d.getString("username") ?: "",
                            role = r
                        )
                    } else null
                }
            } catch (e: Exception) {
                error = "Ошибка загрузки тренеров клуба: ${e.localizedMessage}"
            }
        }
    }

    fun joinClubAndRequestTrainer(club: ClubModel, trainer: AdminUserModel?) {
        val uid = auth.uid ?: return
        val athleteName = profile.nickname.ifBlank { auth.currentUser?.email ?: "Спортсмен" }
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update(
                    mapOf("clubId" to club.id, "clubName" to club.name)
                ).await()
                profile = profile.copy(clubId = club.id, clubName = club.name)
                
                if (trainer != null) {
                    val data = hashMapOf(
                        "trainerUid" to trainer.uid,
                        "trainerName" to trainer.nickname,
                        "athleteUid" to uid,
                        "athleteName" to athleteName,
                        "athleteEmail" to (auth.currentUser?.email ?: ""),
                        "status" to "pending",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("trainer_requests").add(data).await()
                    pendingTrainerRequestName = trainer.nickname
                    successMsg = "Вы вступили в клуб ${club.name} и отправили заявку тренеру ${trainer.nickname}!"
                } else {
                    successMsg = "Вы вступили в клуб ${club.name}!"
                }
            } catch (e: Exception) {
                error = "Ошибка при вступлении в клуб: ${e.localizedMessage}"
            }
        }
    }

    fun leaveClubAndTrainer() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update(
                    mapOf("clubId" to null, "clubName" to null, "trainerUid" to null, "trainerName" to null)
                ).await()
                profile = profile.copy(clubId = null, clubName = null, trainerUid = null, trainerName = null)
                pendingTrainerRequestName = null
                successMsg = "Вы покинули клуб и открепились от тренера"
            } catch (e: Exception) {
                error = "Ошибка при выходе из клуба: ${e.localizedMessage}"
            }
        }
    }

    fun setAvatarBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                isSaving = true
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val byteArray = baos.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)

                profile = profile.copy(avatarUrl = base64String)
                saveProfile()
            } catch (e: Exception) {
                error = "Ошибка обработки фото: ${e.localizedMessage}"
            } finally {
                isSaving = false
            }
        }
    }

    fun saveProfile() {
        val uid = auth.uid ?: return
        isSaving = true
        error = null
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "nickname" to profile.nickname.trim(),
                    "username" to profile.username.trim(),
                    "dobEpochDay" to profile.dobEpochDay,
                    "avatarBase64" to profile.avatarUrl,
                    "role" to profile.role,
                    "heightCm" to profile.heightCm,
                    "weightKg" to profile.weightKg,
                    "gender" to profile.gender,
                    "clubId" to profile.clubId,
                    "clubName" to profile.clubName,
                    "trainerUid" to profile.trainerUid,
                    "trainerName" to profile.trainerName
                )
                db.collection("users").document(uid).set(data).await()
            } catch (e: Exception) {
                error = "Ошибка сохранения: ${e.localizedMessage}"
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        val user = auth.currentUser ?: run { onDeleted(); return }
        val uid = user.uid
        viewModelScope.launch {
            try {
                try {
                    db.collection("users").document(uid).delete().await()
                } catch (_: Exception) {}
                user.delete().await()
                auth.signOut()
                onDeleted()
            } catch (e: Exception) {
                error = "Для полного удаления аккаунта перезайдите в систему и нажмите кнопку снова: ${e.localizedMessage}"
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()
        onLoggedOut()
    }
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null,
    onOpenTrainer: (() -> Unit)? = null
) {
    val vm = appViewModel { ProfileViewModel() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val scaledBitmap = scaleBitmapDown(bitmap, 180)
            vm.setAvatarBitmap(scaledBitmap)
        }
    }

    val decodedBitmap = remember(vm.profile.avatarUrl) {
        if (vm.profile.avatarUrl.isNotBlank()) {
            try {
                val imageBytes = Base64.decode(vm.profile.avatarUrl, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) { null }
        } else null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                actions = {
                    IconButton(onClick = { vm.logout(onLogout) }) {
                        Icon(Icons.Filled.Logout, "Выйти", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )

            if (vm.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                ) {
                    val avatarModifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePickerLauncher.launch("image/*") }

                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Аватар",
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(avatarModifier, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Аватар", Modifier.size(40.dp))
                        }
                    }

                    if (vm.isSaving) {
                        CircularProgressIndicator(
                            Modifier
                                .size(120.dp)
                                .padding(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }

                Text(
                    FirebaseAuth.getInstance().currentUser?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                val roleTitle = when (vm.profile.role) {
                    "admin" -> "Администратор"
                    "trainer" -> "Тренер"
                    else -> "Спортсмен"
                }
                Text(
                    "Роль: $roleTitle",
                    fontWeight = FontWeight.Bold,
                    color = if (vm.profile.role == "admin") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )

                // Кнопка Административной панели
                if (vm.profile.role == "admin" && onOpenAdmin != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Панель администратора")
                    }
                }

                // Кнопка Тренерской панели (для тренеров и админов)
                if ((vm.profile.role == "trainer" || vm.profile.role == "admin") && onOpenTrainer != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenTrainer,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sports, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Тренерская панель")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // БЛОК: МОЙ КЛУБ И ТРЕНЕР
                ZCard {
                    SectionTitle("Мой Фитнес-Клуб")
                    if (vm.profile.clubName != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Business, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    vm.profile.clubName!!,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                if (vm.profile.trainerName != null) {
                                    Text("Тренер: ${vm.profile.trainerName}", style = MaterialTheme.typography.bodySmall)
                                } else if (vm.pendingTrainerRequestName != null) {
                                    Text("Заявка тренеру: ${vm.pendingTrainerRequestName} (Ожидание)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            OutlinedButton(onClick = { vm.leaveClubAndTrainer() }) {
                                Text("Покинуть")
                            }
                        }
                    } else {
                        var showJoinDialog by remember { mutableStateOf(false) }
                        var selectedClubForJoin by remember { mutableStateOf<ClubModel?>(null) }
                        
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Еще не в клубе?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showJoinDialog = true }) {
                                Text("Вступить!")
                            }
                        }

                        // Диалог выбора клуба и тренера
                        if (showJoinDialog) {
                            AlertDialog(
                                onDismissRequest = { 
                                    showJoinDialog = false
                                    selectedClubForJoin = null
                                },
                                title = { Text(if (selectedClubForJoin == null) "Выберите ваш клуб" else "Выберите тренера") },
                                text = {
                                    Column {
                                        if (selectedClubForJoin == null) {
                                            if (vm.availableClubs.isEmpty()) {
                                                Text("Нет доступных клубов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                vm.availableClubs.forEach { club ->
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                                            if (vm.profile.role == "trainer" || vm.profile.role == "admin") {
                                                                vm.joinClubAndRequestTrainer(club, null)
                                                                showJoinDialog = false
                                                            } else {
                                                                selectedClubForJoin = club 
                                                                vm.loadTrainersForClub(club.id)
                                                            }
                                                        },
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                    ) {
                                                        Column(Modifier.padding(12.dp)) {
                                                            Text(club.name, fontWeight = FontWeight.Bold)
                                                            if (club.address.isNotBlank()) Text(club.address, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Text("Выбран клуб: ${selectedClubForJoin!!.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(12.dp))
                                            
                                            if (vm.availableTrainers.isEmpty()) {
                                                Text("В этом клубе пока нет зарегистрированных тренеров", style = MaterialTheme.typography.bodySmall)
                                                Spacer(Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        vm.joinClubAndRequestTrainer(selectedClubForJoin!!, null)
                                                        showJoinDialog = false
                                                        selectedClubForJoin = null
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Вступить без тренера")
                                                }
                                            } else {
                                                Text("Выберите тренера:", style = MaterialTheme.typography.labelMedium)
                                                Spacer(Modifier.height(8.dp))
                                                vm.availableTrainers.forEach { trainer ->
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                                            vm.joinClubAndRequestTrainer(selectedClubForJoin!!, trainer)
                                                            showJoinDialog = false
                                                            selectedClubForJoin = null
                                                        },
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                    ) {
                                                        Column(Modifier.padding(12.dp)) {
                                                            Text(trainer.nickname, fontWeight = FontWeight.Bold)
                                                            if (trainer.username.isNotBlank()) Text("@${trainer.username}", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(12.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        vm.joinClubAndRequestTrainer(selectedClubForJoin!!, null)
                                                        showJoinDialog = false
                                                        selectedClubForJoin = null
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Тренер не нужен")
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = { },
                                dismissButton = {
                                    TextButton(onClick = { 
                                        if (selectedClubForJoin != null) selectedClubForJoin = null else showJoinDialog = false 
                                    }) {
                                        Text(if (selectedClubForJoin != null) "Назад" else "Отмена")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                ZCard {
                    SectionTitle("Основная информация")

                    OutlinedTextField(
                        value = vm.profile.nickname,
                        onValueChange = { vm.profile = vm.profile.copy(nickname = it) },
                        label = { Text("Никнейм (Имя)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.profile.username,
                        onValueChange = { vm.profile = vm.profile.copy(username = it.filter { c -> c.isLetterOrDigit() || c == '_' }) },
                        label = { Text("@username (Без пробелов)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    DateField(
                        epochDay = vm.profile.dobEpochDay,
                        onChange = { vm.profile = vm.profile.copy(dobEpochDay = it) },
                        label = "Дата рождения",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                ZCard {
                    SectionTitle("Антропометрия")

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = vm.profile.gender == "male",
                            onClick = { vm.profile = vm.profile.copy(gender = "male") },
                            label = { Text("Мужчина") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = vm.profile.gender == "female",
                            onClick = { vm.profile = vm.profile.copy(gender = "female") },
                            label = { Text("Женщина") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vm.profile.heightCm?.toString() ?: "",
                            onValueChange = { vm.profile = vm.profile.copy(heightCm = it.filter(Char::isDigit).take(3).toIntOrNull()) },
                            label = { Text("Рост, см") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vm.profile.weightKg?.let { Fmt.weight(it) } ?: "",
                            onValueChange = { vm.profile = vm.profile.copy(weightKg = Fmt.parseWeight(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5))) },
                            label = { Text("Вес, кг") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Переключение роли для быстрого тестирования админки / тренерской панели
                ZCard {
                    SectionTitle("Настройка роли")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = vm.profile.role == "athlete",
                            onClick = { vm.profile = vm.profile.copy(role = "athlete") },
                            label = { Text("Спортсмен") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = vm.profile.role == "trainer",
                            onClick = { vm.profile = vm.profile.copy(role = "trainer") },
                            label = { Text("Тренер") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = vm.profile.role == "admin",
                            onClick = { vm.profile = vm.profile.copy(role = "admin") },
                            label = { Text("Админ") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                ErrorText(vm.error)
                if (vm.successMsg != null) {
                    Text(vm.successMsg!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))

                GradientButton(
                    text = "Сохранить профиль",
                    onClick = { vm.saveProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.isSaving
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить аккаунт полностью", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление аккаунта") },
            text = { Text("Вы уверены, что хотите полностью удалить аккаунт из базы авторизации Firebase Auth и Firestore? Почта освободится для новой регистрации.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteAccount(onLogout)
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    var resizedWidth = maxDimension
    var resizedHeight = maxDimension

    if (originalHeight > originalWidth) {
        resizedHeight = maxDimension
        resizedWidth = (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
    } else if (originalWidth > originalHeight) {
        resizedWidth = maxDimension
        resizedHeight = (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
    } else if (originalHeight == originalWidth) {
        resizedWidth = maxDimension
        resizedHeight = maxDimension
    }
    return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
}
