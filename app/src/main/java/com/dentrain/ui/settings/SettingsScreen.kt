package com.dentrain.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.ClubModel
import com.dentrain.data.AdminUserModel
import com.dentrain.timer.WorkoutReminderScheduler
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.AccentThemes
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class EditProfileState(
    val nickname: String = "",
    val username: String = "",
    val dobEpochDay: Long = LocalDate.now().toEpochDay(),
    val avatarUrl: String = "",
    val clubId: String? = null,
    val clubName: String? = null,
    val trainerUid: String? = null,
    val trainerName: String? = null
)

data class ClubMember(
    val uid: String,
    val nickname: String,
    val avatarUrl: String,
    val dobEpochDay: Long,
    val role: String
)

class ProfileSettingsViewModel(private val repo: WorkoutRepository? = null) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val settings: StateFlow<SettingsEntity>? =
        repo?.settings?.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    var originalState by mutableStateOf(EditProfileState())
    var currentState by mutableStateOf(EditProfileState())
    var hasChanges by mutableStateOf(false)
    
    var isLoading by mutableStateOf(true)
    var isSaving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var availableClubs by mutableStateOf<List<ClubModel>>(emptyList())
    var availableTrainers by mutableStateOf<List<AdminUserModel>>(emptyList())

    var clubMembers by mutableStateOf<List<ClubMember>>(emptyList())
    var isLoadingMembers by mutableStateOf(false)
    var currentUserRole by mutableStateOf("athlete")

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    currentUserRole = doc.getString("role") ?: "athlete"
                    val state = EditProfileState(
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        dobEpochDay = doc.getLong("dobEpochDay") ?: LocalDate.now().toEpochDay(),
                        avatarUrl = doc.getString("avatarBase64") ?: "",
                        clubId = doc.getString("clubId"),
                        clubName = doc.getString("clubName"),
                        trainerUid = doc.getString("trainerUid"),
                        trainerName = doc.getString("trainerName")
                    )
                    originalState = state
                    currentState = state
                }

                val clubsSnap = db.collection("clubs").get().await()
                availableClubs = clubsSnap.documents.map { d ->
                    ClubModel(id = d.id, name = d.getString("name") ?: "", address = d.getString("address") ?: "")
                }
            } catch (e: Exception) {
                error = "Не удалось загрузить профиль"
            } finally {
                isLoading = false
            }
        }
    }

    fun updateState(update: (EditProfileState) -> EditProfileState) {
        currentState = update(currentState)
        hasChanges = currentState != originalState
    }

    fun loadTrainersForClub(clubId: String) {
        viewModelScope.launch {
            try {
                val usersSnap = db.collection("users").whereEqualTo("clubId", clubId).get().await()
                availableTrainers = usersSnap.documents.mapNotNull { d ->
                    val r = d.getString("role") ?: "athlete"
                    if (r == "trainer" || r == "admin") {
                        AdminUserModel(
                            uid = d.id,
                            nickname = d.getString("nickname") ?: "Тренер",
                            username = d.getString("username") ?: "",
                            role = r
                        )
                    } else null
                }
            } catch (e: Exception) {
                error = "Ошибка загрузки тренеров"
            }
        }
    }

    fun loadClubMembers() {
        val clubId = currentState.clubId ?: return
        viewModelScope.launch {
            isLoadingMembers = true
            try {
                val usersSnap = db.collection("users").whereEqualTo("clubId", clubId).get().await()
                clubMembers = usersSnap.documents.mapNotNull { d ->
                    ClubMember(
                        uid = d.id,
                        nickname = d.getString("nickname") ?: "Без имени",
                        avatarUrl = d.getString("avatarBase64") ?: "",
                        dobEpochDay = d.getLong("dobEpochDay") ?: LocalDate.now().toEpochDay(),
                        role = d.getString("role") ?: "athlete"
                    )
                }
            } catch (e: Exception) {
                error = "Ошибка загрузки участников"
            } finally {
                isLoadingMembers = false
            }
        }
    }

    fun changeRoleTest(newRole: String) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update("role", newRole).await()
                currentUserRole = newRole
            } catch (e: Exception) {
                error = "Ошибка изменения роли"
            }
        }
    }

    fun setAvatarBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val base64String = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                updateState { it.copy(avatarUrl = base64String) }
            } catch (e: Exception) {
                error = "Ошибка обработки фото"
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val uid = auth.uid ?: return
        val athleteName = currentState.nickname.ifBlank { auth.currentUser?.email ?: "Спортсмен" }
        viewModelScope.launch {
            isSaving = true
            try {
                // Если изменился тренер, нужно создать заявку (если выбран новый тренер)
                // А в профиле пока ставим тренера, как есть.
                val updates = hashMapOf<String, Any?>(
                    "nickname" to currentState.nickname.trim(),
                    "username" to currentState.username.trim(),
                    "dobEpochDay" to currentState.dobEpochDay,
                    "avatarBase64" to currentState.avatarUrl,
                    "clubId" to currentState.clubId,
                    "clubName" to currentState.clubName,
                    // Если отправляем заявку тренеру, не прописываем его сразу в профиль, ждем подтверждения.
                    // Но если тренер был и не менялся, оставляем. Если очистили (null), то сохраняем null.
                )
                
                if (currentState.trainerUid != null && currentState.trainerUid != originalState.trainerUid) {
                    // Новый тренер - создаем заявку. В профиле пока не меняем trainerUid (или ставим null/оставляем старого)
                    val reqData = hashMapOf(
                        "trainerUid" to currentState.trainerUid,
                        "trainerName" to currentState.trainerName,
                        "athleteUid" to uid,
                        "athleteName" to athleteName,
                        "athleteEmail" to (auth.currentUser?.email ?: ""),
                        "status" to "pending",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("trainer_requests").add(reqData).await()
                    
                    // Уведомление для тренера
                    val notifTrainer = hashMapOf(
                        "recipientUid" to currentState.trainerUid,
                        "title" to "Новая заявка",
                        "message" to "Спортсмен $athleteName хочет присоединиться к вам.",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false,
                        "type" to "info"
                    )
                    db.collection("notifications").add(notifTrainer)
                    
                    // Обнуляем тренера в стейте для users, так как он еще не подтвержден
                    updates["trainerUid"] = originalState.trainerUid
                    updates["trainerName"] = originalState.trainerName
                } else {
                    updates["trainerUid"] = currentState.trainerUid
                    updates["trainerName"] = currentState.trainerName
                }

                // Уведомление о вступлении в клуб
                if (currentState.clubId != null && currentState.clubId != originalState.clubId) {
                    val notifClub = hashMapOf(
                        "recipientUid" to uid,
                        "title" to "Добро пожаловать в клуб!",
                        "message" to "Вы успешно вступили в клуб ${currentState.clubName}.",
                        "timestamp" to System.currentTimeMillis(),
                        "isRead" to false,
                        "type" to "success"
                    )
                    db.collection("notifications").add(notifClub)
                }

                db.collection("users").document(uid).update(updates).await()
                originalState = currentState
                hasChanges = false
                onSuccess()
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
                error = "Для полного удаления аккаунта перезайдите в систему: ${e.localizedMessage}"
            }
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, Color(0xFF282B36)),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1D25))
            .border(border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val vm = appViewModel { ProfileSettingsViewModel() }
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (vm.hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val maxDimension = 180
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
            }
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
            vm.setAvatarBitmap(scaledBitmap)
        }
    }

    val decodedBitmap = remember(vm.currentState.avatarUrl) {
        if (vm.currentState.avatarUrl.isNotBlank()) {
            try {
                val imageBytes = Base64.decode(vm.currentState.avatarUrl, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) { null }
        } else null
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки профиля",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    BorderedBackButton(onClick = handleBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            if (vm.hasChanges) {
                FloatingActionButton(
                    onClick = { vm.saveProfile(onBack) },
                    containerColor = Color(0xFFA6FF00)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Сохранить", tint = Color.Black)
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (vm.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFA6FF00))
                }
                return@Scaffold
            }

            // Фото профиля
            SectionTitle("Фото профиля")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarModifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242732))
                        
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Аватар",
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(avatarModifier, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Аватар", Modifier.size(32.dp), tint = Color(0xFFA0A7B8))
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text("Изменить фото")
                    }
                }
            }

            SectionTitle("Основная информация")
            SettingsCard {
                OutlinedTextField(
                    value = vm.currentState.nickname,
                    onValueChange = { if (it.length <= 30) vm.updateState { s -> s.copy(nickname = it) } },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = vm.currentState.username,
                    onValueChange = { 
                        if (it.length <= 25) {
                            vm.updateState { s -> s.copy(username = it.filter { c -> c.isLetterOrDigit() || c == '_' }) }
                        }
                    },
                    label = { Text("Никнейм (@username)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                DateField(
                    epochDay = vm.currentState.dobEpochDay,
                    onChange = { vm.updateState { s -> s.copy(dobEpochDay = it) } },
                    label = "Дата рождения",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            SectionTitle("Мой клуб и Тренер")
            SettingsCard {
                if (vm.currentState.clubName != null) {
                    Text("Вы состоите в клубе:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA0A7B8))
                    Text(vm.currentState.clubName!!, fontWeight = FontWeight.Bold, color = Color.White)
                    
                    if (vm.currentState.trainerName != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ваш тренер:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA0A7B8))
                        Text(vm.currentState.trainerName!!, color = Color.White)
                    }
                    
                    var showClubMembersDialog by remember { mutableStateOf(false) }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { 
                            showClubMembersDialog = true
                            vm.loadClubMembers()
                        },
                        border = BorderStroke(1.dp, Color(0xFF00BFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00BFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Подробнее", color = Color(0xFF00BFFF))
                    }

                    if (showClubMembersDialog) {
                        AlertDialog(
                            onDismissRequest = { showClubMembersDialog = false },
                            title = { Text("Участники клуба") },
                            text = {
                                if (vm.isLoadingMembers) {
                                    CircularProgressIndicator(color = Color(0xFF00BFFF))
                                } else {
                                    Column(Modifier.verticalScroll(rememberScrollState())) {
                                        vm.clubMembers.forEach { member ->
                                            val memberAge = ChronoUnit.YEARS.between(
                                                LocalDate.ofEpochDay(member.dobEpochDay),
                                                LocalDate.now()
                                            ).toInt()
                                            
                                            val memberBitmap = remember(member.avatarUrl) {
                                                if (member.avatarUrl.isNotBlank()) {
                                                    try {
                                                        val imageBytes = Base64.decode(member.avatarUrl, Base64.DEFAULT)
                                                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                                    } catch (e: Exception) { null }
                                                } else null
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (memberBitmap != null) {
                                                    Image(
                                                        bitmap = memberBitmap.asImageBitmap(),
                                                        contentDescription = "Аватар",
                                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF242732)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Filled.CameraAlt, "Аватар", Modifier.size(20.dp), tint = Color.White)
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(member.nickname, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Text("Возраст: $memberAge лет", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showClubMembersDialog = false }) {
                                    Text("Закрыть", color = Color(0xFF00BFFF))
                                }
                            }
                        )
                    }
                } else {
                    var showJoinDialog by remember { mutableStateOf(false) }
                    var selectedClubForJoin by remember { mutableStateOf<ClubModel?>(null) }
                    
                    Button(onClick = { showJoinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Выбрать клуб")
                    }
                    
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
                                                        selectedClubForJoin = club 
                                                        vm.loadTrainersForClub(club.id)
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
                                                    vm.updateState { it.copy(clubId = selectedClubForJoin!!.id, clubName = selectedClubForJoin!!.name, trainerUid = null, trainerName = null) }
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
                                                        vm.updateState { it.copy(
                                                            clubId = selectedClubForJoin!!.id, clubName = selectedClubForJoin!!.name,
                                                            trainerUid = trainer.uid, trainerName = trainer.nickname
                                                        )}
                                                        showJoinDialog = false
                                                        selectedClubForJoin = null
                                                    },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(trainer.nickname, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    vm.updateState { it.copy(clubId = selectedClubForJoin!!.id, clubName = selectedClubForJoin!!.name, trainerUid = null, trainerName = null) }
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
            
            Spacer(Modifier.height(8.dp))
            SectionTitle("Смена роли (тест)")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Текущая роль: ${vm.currentUserRole}", color = Color.White)
                    Button(
                        onClick = {
                            val newRole = when (vm.currentUserRole) {
                                "athlete" -> "trainer"
                                "trainer" -> "admin"
                                else -> "athlete"
                            }
                            vm.changeRoleTest(newRole)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6FF00), contentColor = Color.Black)
                    ) {
                        Text("Сменить")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Удалить аккаунт полностью", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Сохранить изменения?") },
            text = { Text("У вас есть несохраненные изменения. Хотите применить их?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    vm.saveProfile(onBack)
                }) {
                    Text("Да, сохранить", color = Color(0xFFA6FF00))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Нет, выйти", color = MaterialTheme.colorScheme.error)
                }
            }
        )
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

@Composable
private fun AccentOption(title: String, brush: Brush, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1D25))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFA6FF00) else Color(0xFF282B36)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(brush))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun BackgroundOption(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1D25))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFA6FF00) else Color(0xFF282B36)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, null, tint = if (selected) Color(0xFFA6FF00) else Color(0xFFA0A7B8), modifier = Modifier.size(28.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        }
    }
}
