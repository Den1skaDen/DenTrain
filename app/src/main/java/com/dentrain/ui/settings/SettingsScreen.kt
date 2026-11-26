package com.dentrain.ui.settings

import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.util.userMessage
import androidx.compose.animation.animateContentSize
import com.dentrain.ui.components.EditorTextField
import com.dentrain.ui.theme.DenTrainPalette
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.dentrain.ui.icons.filled.CameraAlt
import com.dentrain.ui.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.DenTrainApp
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.UserFacingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

data class EditProfileState(
    val nickname: String = "",
    val username: String = "",
    val dobEpochDay: Long = LocalDate.now().toEpochDay()
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

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("profiles").document(uid).get().await()
                if (doc.exists()) {
                    val state = EditProfileState(
                        nickname = doc.getString("nickname") ?: "",
                        username = doc.getString("username") ?: "",
                        dobEpochDay = doc.getLong("dobEpochDay") ?: LocalDate.now().toEpochDay()
                    )
                    originalState = state
                    currentState = state
                }
            } catch (e: Exception) {
                error = e.userMessage("Не удалось загрузить профиль.")
            } finally {
                isLoading = false
            }
        }
    }

    fun updateState(update: (EditProfileState) -> EditProfileState) {
        currentState = update(currentState)
        hasChanges = currentState != originalState
    }

    suspend fun saveAvatar(avatar: String) {
        val uid = auth.uid ?: throw UserFacingException("Войдите в аккаунт заново.")
        db.collection("profiles").document(uid).update("avatarBase64", avatar).await()
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            isSaving = true
            try {
                val updates = mapOf(
                    "nickname" to currentState.nickname.trim(),
                    "username" to currentState.username.trim(),
                    "dobEpochDay" to currentState.dobEpochDay
                )
                db.collection("profiles").document(uid).update(updates).await()
                originalState = currentState
                hasChanges = false
                onSuccess()
            } catch (e: Exception) {
                error = e.userMessage("Ошибка сохранения.")
            } finally {
                isSaving = false
            }
        }
    }
    
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
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
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProfileSettingsViewModel() }
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    var avatarUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val handleBack = {
        if (vm.hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarUri = uri
    }

    avatarUri?.let { AvatarCropDialog(it, onDismiss = { avatarUri = null }, onSave = vm::saveAvatar) }

    val account = (context.applicationContext as DenTrainApp).container
    val decodedBitmap = account.profileAvatar?.bitmap?.collectAsStateWithLifecycle()?.value

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
                    containerColor = DenTrainPalette.Lime
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
                    CircularProgressIndicator(color = DenTrainPalette.Lime)
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
                        .background(DenTrainPalette.RaisedPanel)
                        
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Аватар",
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(avatarModifier, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Аватар", Modifier.size(32.dp), tint = DenTrainPalette.MutedText)
                        }
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }, enabled = !vm.isSaving) {
                        Text("Изменить фото", color = Color.White)
                    }
                }
            }

            SectionTitle("Основная информация")
            SettingsCard(Modifier.animateContentSize()) {
                EditorTextField(
                    value = vm.currentState.nickname,
                    onValueChange = { if (it.length <= 30) vm.updateState { s -> s.copy(nickname = it) } },
                    label = "Имя"
                )
                EditorTextField(
                    value = vm.currentState.username,
                    onValueChange = {
                        if (it.length <= 25) {
                            vm.updateState { s -> s.copy(username = it.filter { c -> c.isLetterOrDigit() || c == '_' }) }
                        }
                    },
                    label = "Никнейм (@username)"
                )

                DateField(
                    epochDay = vm.currentState.dobEpochDay,
                    onChange = { vm.updateState { s -> s.copy(dobEpochDay = it) } },
                    label = "Дата рождения",
                    modifier = Modifier.fillMaxWidth(),
                    maxEpochDay = LocalDate.now().toEpochDay()
                )
            }
            
        }
    }
    
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Сохранить изменения?",
            text = "Вы изменили профиль. Сохраните изменения или выйдите без сохранения.",
            confirmText = "Сохранить",
            destructive = false,
            onConfirm = {
                showDiscardDialog = false
                vm.saveProfile(onBack)
            },
            dismissText = "Выйти без сохранения",
            secondaryDestructive = true,
            onSecondary = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }
}
