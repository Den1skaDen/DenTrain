package com.dentrain.ui.profile

import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.util.userMessage
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.dentrain.ui.icons.filled.AdminPanelSettings
import com.dentrain.ui.icons.filled.Business
import com.dentrain.ui.icons.filled.CameraAlt
import com.dentrain.ui.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import com.dentrain.ui.icons.filled.Sports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.TimePickerDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.timer.TimerNotifier
import com.dentrain.timer.WorkoutReminderNotifier
import com.dentrain.ui.components.LocalAlertSettings
import com.dentrain.ui.components.rememberNotificationAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import com.dentrain.util.Haptic
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import com.dentrain.ui.icons.filled.AccessTime
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.R
import com.dentrain.data.AdminUserModel
import com.dentrain.data.AssignedWorkoutModel
import com.dentrain.data.ClubModel
import com.dentrain.data.WorkoutDelivery
import com.dentrain.data.TrainerRequests
import com.dentrain.data.TrainerRequestModel
import com.dentrain.data.activeTrainerRequest
import com.google.firebase.firestore.ListenerRegistration
import com.dentrain.ui.admin.UserListItem
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.AssignedWorkoutDetailPanel
import com.dentrain.ui.components.UserProfilePanel
import com.dentrain.ui.components.appViewModel
import com.dentrain.DenTrainApp
import com.dentrain.util.Fmt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

data class UserProfile(
    val nickname: String = "",
    val username: String = "",
    val dobEpochDay: Long = LocalDate.now().toEpochDay(),
    val role: String = "athlete", // "admin", "trainer", "athlete"
    val gender: String = "male", // "male" или "female"
    val clubId: String? = null,
    val clubName: String? = null,
    val trainerUid: String? = null,
    val trainerName: String? = null
)

class ProfileViewModel(private val repo: WorkoutRepository? = null) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val settings: StateFlow<SettingsEntity>? =
        repo?.settings?.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    fun setAutoRest(enabled: Boolean) = updateSettings { it.copy(autoRestTimer = enabled) }
    fun setSwapWeightReps(enabled: Boolean) = updateSettings { it.copy(swapWeightReps = enabled) }

    fun setReminderEnabled(enabled: Boolean) = updateSettings { it.copy(reminderEnabled = enabled) }

    fun setWorkoutStartTime(time: String) = updateSettings { it.copy(workoutStartTime = time) }

    fun toggleScheduledDay(day: Int) = updateSettings {
        val currentSet = it.scheduledDays.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.toMutableSet()
        if (day in currentSet) {
            if (currentSet.size > 1) currentSet.remove(day)
        } else {
            currentSet.add(day)
        }
        val str = currentSet.sorted().joinToString(",")
        it.copy(scheduledDays = str)
    }

    fun toggleReminderLeadOption(leadMinutes: Int) = updateSettings {
        val currentSet = it.reminderLeadOptions.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.toMutableSet()
        if (leadMinutes in currentSet) {
            if (currentSet.size > 1) currentSet.remove(leadMinutes)
        } else {
            currentSet.add(leadMinutes)
        }
        val str = currentSet.sortedDescending().joinToString(",")
        it.copy(reminderLeadOptions = str)
    }

    private fun updateSettings(change: (SettingsEntity) -> SettingsEntity) {
        launchUserAction("Не удалось сохранить настройки напоминаний.", { error = it }) {
            repo?.updateSettings(change)
        }
    }

    var profile by mutableStateOf(UserProfile())
    var clubMembers by mutableStateOf<List<AdminUserModel>>(emptyList())
    var currentClubDetails by mutableStateOf<ClubModel?>(null)
    var availableClubs by mutableStateOf<List<ClubModel>>(emptyList())
        private set
    var loadingAvailableClubs by mutableStateOf(false)
        private set
    var joiningClub by mutableStateOf(false)
        private set
    var clubSelectionError by mutableStateOf<String?>(null)
        private set
    var pendingTrainerRequests by mutableStateOf<List<TrainerRequestModel>>(emptyList())
    private var profileListener: ListenerRegistration? = null
    private var requestListener: ListenerRegistration? = null

    var userWorkouts by mutableStateOf<List<AssignedWorkoutModel>>(emptyList())
    var isLoadingWorkouts by mutableStateOf(false)

    var isLoading by mutableStateOf(true)
    var isSaving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)
    var sentRequestMessage by mutableStateOf<String?>(null)
    var trainerRequestError by mutableStateOf<String?>(null)


    init {
        loadProfile()
    }

    fun loadUserWorkouts(userUid: String) {
        isLoadingWorkouts = true
        userWorkouts = emptyList()
        viewModelScope.launch {
            try {
                val trainerUid = auth.uid?.takeIf { profile.role == "trainer" }
                userWorkouts = WorkoutDelivery.loadAssignments(db, userUid, trainerUid)
            } catch (e: Exception) {
                error = e.userMessage("Не удалось загрузить тренировки.")
                userWorkouts = emptyList()
            } finally {
                isLoadingWorkouts = false
            }
        }
    }

    private fun loadProfile() {
        val uid = auth.uid ?: run {
            isLoading = false
            return
        }
        
        profileListener = db.collection("profiles").document(uid).addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) {
                error = e?.userMessage("Не удалось загрузить профиль.") ?: "Профиль больше недоступен."
                isLoading = false
                return@addSnapshotListener
            }
            profile = UserProfile(
                nickname = doc.getString("nickname") ?: "",
                username = doc.getString("username") ?: "",
                dobEpochDay = doc.getLong("dobEpochDay") ?: LocalDate.now().toEpochDay(),
                role = doc.getString("role") ?: "athlete",
                gender = doc.getString("gender") ?: "male",
                clubId = doc.getString("clubId"),
                clubName = doc.getString("clubName"),
                trainerUid = doc.getString("trainerUid"),
                trainerName = doc.getString("trainerName")
            )
            isLoading = false
        }

        requestListener = db.collection("trainer_requests")
            .whereEqualTo("athleteUid", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) error = failure.userMessage("Не удалось загрузить заявки тренерам.")
                if (snapshot != null) pendingTrainerRequests = snapshot.documents
                    .mapNotNull { it.activeTrainerRequest() }.sortedByDescending { it.timestamp }
            }

    }

    fun loadAvailableClubs() {
        if (loadingAvailableClubs || joiningClub) return
        loadingAvailableClubs = true
        clubSelectionError = null
        availableClubs = emptyList()
        viewModelScope.launch {
            try {
                availableClubs = db.collection("clubs").orderBy("name").get().await().documents.map { doc ->
                    ClubModel(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        address = doc.getString("address").orEmpty()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clubSelectionError = e.userMessage("Не удалось загрузить список клубов.")
            } finally {
                loadingAvailableClubs = false
            }
        }
    }

    fun joinClub(club: ClubModel) {
        val uid = auth.uid ?: return
        if (joiningClub || !profile.clubId.isNullOrBlank()) return
        joiningClub = true
        clubSelectionError = null
        viewModelScope.launch {
            try {
                val profileRef = db.collection("profiles").document(uid)
                val joined = db.runTransaction { transaction ->
                    val currentClubId = transaction.get(profileRef).getString("clubId")
                    if (!currentClubId.isNullOrBlank()) return@runTransaction false
                    transaction.update(profileRef, mapOf("clubId" to club.id, "clubName" to club.name))
                    true
                }.await()
                if (joined) successMsg = "Вы вступили в клуб «${club.name}»"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clubSelectionError = e.userMessage("Не удалось вступить в клуб.")
            } finally {
                joiningClub = false
            }
        }
    }

    fun loadClubDetails(clubId: String) {
        viewModelScope.launch {
            try {
                val clubDoc = db.collection("clubs").document(clubId).get().await()
                if (clubDoc.exists()) {
                    currentClubDetails = ClubModel(
                        id = clubDoc.id,
                        name = clubDoc.getString("name") ?: "",
                        address = clubDoc.getString("address") ?: "",
                        phone = clubDoc.getString("phone") ?: "",
                        description = clubDoc.getString("description") ?: ""
                    )
                }
                
                val usersSnap = db.collection("profiles")
                    .whereEqualTo("clubId", clubId)
                    .get().await()
                
                clubMembers = usersSnap.documents.map { d ->
                    AdminUserModel(
                        uid = d.id,
                        email = d.getString("email") ?: "",
                        nickname = d.getString("nickname") ?: "",
                        username = d.getString("username") ?: "",
                        avatarBase64 = d.getString("avatarBase64") ?: "",
                        role = d.getString("role") ?: "athlete",
                        dobEpochDay = d.getLong("dobEpochDay"),
                        clubId = d.getString("clubId"),
                        clubName = d.getString("clubName"),
                        trainerUid = d.getString("trainerUid"),
                        trainerName = d.getString("trainerName")
                    )
                }
            } catch (e: Exception) {
                error = e.userMessage("Ошибка загрузки информации о клубе.")
            }
        }
    }

    suspend fun loadTrainerAccount(uid: String): AdminUserModel? {
        return try {
            val doc = db.collection("profiles").document(uid).get().await()
            if (!doc.exists()) return null
            AdminUserModel(
                uid = doc.id,
                email = doc.getString("email").orEmpty(),
                nickname = doc.getString("nickname").orEmpty(),
                username = doc.getString("username").orEmpty(),
                avatarBase64 = doc.getString("avatarBase64").orEmpty(),
                role = doc.getString("role").orEmpty(),
                dobEpochDay = doc.getLong("dobEpochDay"),
                clubId = doc.getString("clubId"),
                clubName = doc.getString("clubName")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun requestTrainer(trainer: AdminUserModel) = sendTrainerRequest(trainer)

    fun withdrawTrainerRequest(req: TrainerRequestModel) {
        error = null
        viewModelScope.launch {
            try {
                TrainerRequests(db).withdraw(req)
                successMsg = "Заявка тренеру ${req.trainerName} отозвана"
            } catch (e: Exception) {
                error = e.userMessage("Не удалось отозвать заявку.")
            }
        }
    }

    private fun sendTrainerRequest(trainer: AdminUserModel, club: ClubModel? = null) {
        val uid = auth.uid ?: return
        if (isSaving || profile.role != "athlete") return
        isSaving = true
        trainerRequestError = null
        viewModelScope.launch {
            try {
                TrainerRequests(db).send(uid, trainer.uid, club)
                sentRequestMessage = "Заявка тренеру ${trainer.nickname} отправлена."
            } catch (e: Exception) {
                trainerRequestError = e.userMessage("Не удалось отправить заявку.")
            } finally {
                isSaving = false
            }
        }
    }

    fun leaveClub() {
        val uid = auth.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("profiles").document(uid).update(
                    mapOf("clubId" to null, "clubName" to null)
                ).await()
                successMsg = "Вы покинули клуб"
            } catch (e: Exception) {
                error = e.userMessage("Не удалось покинуть клуб.")
            }
        }
    }

    fun detachTrainer() {
        val uid = auth.uid ?: return
        error = null
        viewModelScope.launch {
            try {
                db.collection("profiles").document(uid)
                    .update(mapOf("trainerUid" to null, "trainerName" to null)).await()
                successMsg = "Вы открепились от тренера"
            } catch (e: Exception) {
                error = e.userMessage("Не удалось открепиться от тренера.")
            }
        }
    }

    override fun onCleared() {
        profileListener?.remove()
        requestListener?.remove()
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()
        onLoggedOut()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerProfileLink(
    vm: ProfileViewModel,
    trainerUid: String,
    prefix: String,
    fallbackName: String,
    linkColor: Color,
    onOpen: (AdminUserModel) -> Unit,
    modifier: Modifier = Modifier
) {
    key(trainerUid) {
        var loadAttempt by remember { mutableStateOf(0) }
        val trainer by produceState<AdminUserModel?>(null, loadAttempt) {
            val account = vm.loadTrainerAccount(trainerUid)
            value = account
            if (loadAttempt > 0) {
                if (account != null) onOpen(account)
                else vm.error = "Не удалось открыть профиль тренера."
            }
        }
        val username = trainer?.username.orEmpty().trim().removePrefix("@")
        val label = if (username.isNotBlank()) "@$username" else fallbackName
        Text(
            text = buildAnnotatedString {
                append(prefix)
                withLink(LinkAnnotation.Clickable(
                    tag = trainerUid,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor))
                ) {
                    val account = trainer
                    if (account != null) onOpen(account)
                    else loadAttempt++
                }) {
                    append(label)
                }
            },
            modifier = modifier,
            color = DenTrainPalette.MutedText
        )
    }
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null,
    onOpenTrainer: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val vm = appViewModel { ProfileViewModel(it.repository) }
    val context = LocalContext.current
    val settingsState = vm.settings?.collectAsStateWithLifecycle()?.value
    val settings = settingsState ?: SettingsEntity()

    val restAlerts = rememberNotificationAccess(TimerNotifier.CHANNEL_ID)
    val workoutAlerts = rememberNotificationAccess(WorkoutReminderNotifier.CHANNEL_ID)

    fun pickTime(currentTimeStr: String, onSelected: (String) -> Unit) {
        val parts = currentTimeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 18
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = "%02d:%02d".format(hour, minute)
                onSelected(formatted)
            },
            h, m, true
        ).show()
    }

    val account = (context.applicationContext as DenTrainApp).container
    val decodedBitmap = account.profileAvatar?.bitmap?.collectAsStateWithLifecycle()?.value

    var viewingAthleteClubDetails by remember { mutableStateOf(false) }
    var choosingClub by remember { mutableStateOf(false) }
    val hasClub = !vm.profile.clubId.isNullOrBlank()
    LaunchedEffect(hasClub) {
        if (hasClub) choosingClub = false
    }
    if (choosingClub && !hasClub) {
        var selectedClubId by remember { mutableStateOf<String?>(null) }
        val selectedClub = vm.availableClubs.firstOrNull { it.id == selectedClubId }
        val busy = vm.loadingAvailableClubs || vm.joiningClub
        val clubShape = RoundedCornerShape(14.dp)
        AlertDialog(
            onDismissRequest = { if (!vm.joiningClub) choosingClub = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = DenTrainPalette.Panel,
            title = { Text("Выберите клуб", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        busy -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        vm.availableClubs.isNotEmpty() -> LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp).selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(vm.availableClubs, key = { it.id }) { club ->
                                val selected = club.id == selectedClubId
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(clubShape)
                                        .background(DenTrainPalette.RaisedPanel)
                                        .border(
                                            1.dp,
                                            if (selected) DenTrainPalette.Lime else DenTrainPalette.SubtleBorder,
                                            clubShape
                                        )
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = { selectedClubId = club.id }
                                        )
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selected, onClick = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(club.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        if (club.address.isNotBlank()) {
                                            Text(club.address, color = DenTrainPalette.MutedText)
                                        }
                                    }
                                }
                            }
                        }
                        vm.clubSelectionError == null -> Text("Нет доступных клубов")
                    }
                    vm.clubSelectionError?.let {
                        ErrorText(it)
                        TextButton(onClick = vm::loadAvailableClubs) { Text("Обновить список") }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { selectedClub?.let(vm::joinClub) },
                        enabled = selectedClub != null && !busy,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Подтвердить")
                    }
                    TextButton(
                        onClick = { choosingClub = false },
                        enabled = !vm.joiningClub,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        )
    }
    var viewingUserAccount by remember { mutableStateOf<AdminUserModel?>(null) }
    var showDetachTrainer by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var requestToWithdraw by remember { mutableStateOf<TrainerRequestModel?>(null) }

    if (showLogout) {
        ConfirmDialog(
            title = "Выйти из аккаунта?",
            text = "Чтобы снова открыть свой дневник, войдите в аккаунт.",
            confirmText = "Выйти",
            destructive = false,
            onConfirm = {
                showLogout = false
                vm.logout(onLogout)
            },
            onDismiss = { showLogout = false }
        )
    }

    requestToWithdraw?.let { request ->
        ConfirmDialog(
            title = "Отозвать заявку?",
            text = "Заявка тренеру «${request.trainerName}» будет отозвана.",
            confirmText = "Отозвать",
            onConfirm = {
                requestToWithdraw = null
                vm.withdrawTrainerRequest(request)
            },
            onDismiss = { requestToWithdraw = null }
        )
    }

    if (showDetachTrainer) {
        ConfirmDialog(
            title = "Открепиться от тренера?",
            text = "Тренер потеряет доступ к вашим тренировкам и не сможет назначать новые.",
            confirmText = "Открепиться",
            onConfirm = {
                showDetachTrainer = false
                viewingUserAccount = null
                vm.detachTrainer()
            },
            onDismiss = { showDetachTrainer = false }
        )
    }

    vm.trainerRequestError?.let { message ->
        ConfirmDialog(
            title = "Заявка не отправлена",
            text = message,
            confirmText = "Понятно",
            destructive = false,
            dismissText = null,
            onConfirm = { vm.trainerRequestError = null },
            onDismiss = { vm.trainerRequestError = null }
        )
    }

    vm.sentRequestMessage?.let { message ->
        ConfirmDialog(
            title = "Заявка отправлена",
            text = message,
            confirmText = "Понятно",
            destructive = false,
            dismissText = null,
            onConfirm = { vm.sentRequestMessage = null },
            onDismiss = { vm.sentRequestMessage = null }
        )
    }

    if (viewingUserAccount != null) {
        val user = viewingUserAccount!!
        val currentUserId = FirebaseAuth.getInstance().uid
        var selectedWorkout by remember(user.uid) { mutableStateOf<AssignedWorkoutModel?>(null) }
        val canViewWorkouts = vm.profile.role == "admin" ||
            (vm.profile.role == "trainer" && currentUserId != null && user.trainerUid == currentUserId)
        LaunchedEffect(user.uid, canViewWorkouts) {
            if (canViewWorkouts) vm.loadUserWorkouts(user.uid)
        }
        selectedWorkout?.takeIf { canViewWorkouts }?.let { workout ->
            AssignedWorkoutDetailPanel(workout, user, onBack = { selectedWorkout = null })
            return
        }
        UserProfilePanel(
            user = user,
            onBack = { viewingUserAccount = null },
            onSelectTrainer = vm::requestTrainer,
            onDetachTrainer = if (user.uid == vm.profile.trainerUid) {
                { showDetachTrainer = true }
            } else null,
            hasPendingTrainerRequest = vm.pendingTrainerRequests.any { it.trainerUid == user.uid },
            isSendingTrainerRequest = vm.isSaving,
            onWorkoutClick = { selectedWorkout = it },
            currentUserId = currentUserId,
            viewerRole = vm.profile.role,
            athleteTrainerUid = vm.profile.trainerUid,
            athleteHasTrainer = vm.profile.trainerUid != null,
            userWorkouts = vm.userWorkouts,
            isLoadingWorkouts = vm.isLoadingWorkouts
        )
        return
    }

    if (viewingAthleteClubDetails && vm.profile.clubId != null) {
        LaunchedEffect(vm.profile.clubId) {
            vm.loadClubDetails(vm.profile.clubId!!)
        }

        var showLeaveClubConfirm by remember { mutableStateOf(false) }

        val club = vm.currentClubDetails
        val clubTrainers = remember(vm.clubMembers) { vm.clubMembers.filter { it.role == "trainer" || it.role == "admin" } }
        val clubAthletes = remember(vm.clubMembers) { vm.clubMembers.filter { it.role == "athlete" } }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1115))
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = club?.name ?: vm.profile.clubName ?: "Мой клуб",
                onBack = { viewingAthleteClubDetails = false },
                actions = {
                    IconButton(onClick = { showLeaveClubConfirm = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Покинуть клуб", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Информация о клубе
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
                    Text(club?.name ?: vm.profile.clubName ?: "Клуб", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    if (!club?.address.isNullOrBlank()) Text(club.address, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (!club?.phone.isNullOrBlank()) Text("Тел: ${club.phone}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    if (!club?.description.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Описание:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Text(club.description, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Тренеры
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
                            UserListItem(
                                user = trainer,
                                renderAsCard = false,
                                onItemClick = { viewingUserAccount = trainer }
                            )
                            if (index < clubTrainers.size - 1) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Участники
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
                            UserListItem(
                                user = athlete,
                                renderAsCard = false,
                                onItemClick = { viewingUserAccount = athlete }
                            )
                            if (index < clubAthletes.size - 1) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showLeaveClubConfirm) {
            ConfirmDialog(
                title = "Покинуть клуб?",
                text = "Вы перестанете числиться участником этого клуба.",
                confirmText = "Покинуть",
                onConfirm = {
                    showLeaveClubConfirm = false
                    vm.leaveClub()
                    viewingAthleteClubDetails = false
                },
                onDismiss = { showLeaveClubConfirm = false }
            )
        }


        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Header with Profile title, DT logo and Settings icon
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Color(0xFF13151C))
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1.0f to Color(0xFF2196F3)
                            )
                        ),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Image(
                    painter = painterResource(id = R.drawable.logo_dt),
                    contentDescription = "Логотип DenTrain",
                    modifier = Modifier.size(54.dp).align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )

                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, "Настройки", tint = Color.White)
                        }
                    }
                }
            }

            ErrorText(vm.error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (vm.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DenTrainPalette.Lime) }
                return@Column
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                ) {
                    val avatarModifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(DenTrainPalette.Panel)

                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Аватар",
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(avatarModifier, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CameraAlt, "Аватар", Modifier.size(36.dp), tint = DenTrainPalette.MutedText)
                        }
                    }
                }

                // Name ("Den1s")
                Text(
                    text = vm.profile.nickname.ifBlank { "Den1s" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                )

                Spacer(Modifier.height(8.dp))

                // Role badge: 🔵 Спортсмен
                val roleTitle = when (vm.profile.role) {
                    "admin" -> "Администратор · Тренер"
                    "trainer" -> "Тренер"
                    else -> "Спортсмен"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (vm.profile.role == "admin") Color(0xFFEF5350) else Color(0xFF2196F3))
                        )
                        Text(
                            text = roleTitle,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }

                if (vm.profile.role == "admin" && onOpenAdmin != null) {
                    Spacer(Modifier.height(12.dp))
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

                if ((vm.profile.role == "trainer" || vm.profile.role == "admin") && onOpenTrainer != null) {
                    Spacer(Modifier.height(12.dp))
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

                Spacer(Modifier.height(16.dp))

                // МОЙ ФИТНЕС-КЛУБ (с отображением тренера, если есть)
                ProfileCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Мой Фитнес-Клуб",
                            Modifier.weight(1f).padding(end = 8.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        if (hasClub) {
                            OutlinedButton(onClick = { viewingAthleteClubDetails = true }) {
                                Text("Подробнее", color = Color.White)
                            }
                        } else {
                            OutlinedButton(onClick = {
                                vm.loadAvailableClubs()
                                choosingClub = true
                            }, border = BorderStroke(1.dp, Color.White)) {
                                Text("Вступить", color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (hasClub) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Business, null, tint = Color(0xFF2196F3))
                            Spacer(Modifier.size(12.dp))
                            Text(
                                vm.profile.clubName ?: "Мой клуб",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    } else {
                        Text("Клуб не выбран", color = DenTrainPalette.MutedText, fontSize = 15.sp)
                    }
                    vm.profile.trainerUid?.let { trainerUid ->
                        Spacer(Modifier.height(8.dp))
                        TrainerProfileLink(
                            vm = vm,
                            trainerUid = trainerUid,
                            prefix = "Тренер ",
                            fallbackName = vm.profile.trainerName.orEmpty().ifBlank { "Профиль" },
                            linkColor = Color.White,
                            onOpen = { viewingUserAccount = it }
                        )
                    }
                    if (vm.profile.trainerUid == null) {
                        vm.pendingTrainerRequests.forEach { request ->
                            key(request.trainerUid) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TrainerProfileLink(
                                        vm = vm,
                                        trainerUid = request.trainerUid,
                                        prefix = "Ожидает ответа от ",
                                        fallbackName = request.trainerName.ifBlank { "тренера" },
                                        linkColor = Color(0xFF00BFFF),
                                        onOpen = { viewingUserAccount = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { requestToWithdraw = request },
                                        colors = ButtonDefaults.textButtonColors(contentColor = DenTrainPalette.MutedText)
                                    ) {
                                        Text("Отозвать", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    vm.successMsg?.let { Text(it, color = DenTrainPalette.Lime) }
                }

                Spacer(Modifier.height(16.dp))

                // ОСНОВНАЯ ИНФОРМАЦИЯ И КНОПКА ВЫХОДА ВНИЗУ
                ProfileCard {
                    ProfileInfoRow(
                        value = vm.profile.nickname.ifBlank { "Den1s" },
                        label = "Имя"
                    )
                    HorizontalDivider(color = Color(0xFF3E4150), thickness = 1.5.dp)
                    
                    val loginValue = if (vm.profile.username.isNotBlank()) "@${vm.profile.username}" else "@sak"
                    ProfileInfoRow(
                        value = loginValue,
                        label = "Никнейм"
                    )
                    HorizontalDivider(color = Color(0xFF3E4150), thickness = 1.5.dp)
                    
                    ProfileInfoRow(
                        value = FirebaseAuth.getInstance().currentUser?.email ?: "denyssavchenko10112006@gmail.com",
                        label = "Электронная почта"
                    )
                    HorizontalDivider(color = Color(0xFF3E4150), thickness = 1.5.dp)
                    
                    ProfileInfoRow(
                        value = runCatching { Fmt.shortDate(vm.profile.dobEpochDay) }.getOrDefault("—"),
                        label = "Дата рождения"
                    )

                    Spacer(Modifier.height(16.dp))

                    HorizontalDivider(color = Color(0xFF3E4150), thickness = 1.5.dp)

                    // Выйти из аккаунта
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogout = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "Выйти из аккаунта",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                // Настройки из профиля
                if (settingsState != null) {
                    SectionTitle("Настройки тренировок")
                    ProfileCard(
                        border = restAlerts.border(settings.autoRestTimer, Color.Red)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Автозапуск таймера после подхода", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "Отдых стартует сразу после клика «Сохранить подход».",
                                        fontSize = 12.sp, color = DenTrainPalette.MutedText
                                    )
                                }
                                Switch(
                                    checked = settings.autoRestTimer,
                                    onCheckedChange = { enabled ->
                                        Haptic.click(context)
                                        vm.setAutoRest(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color.White,
                                        checkedThumbColor = DenTrainPalette.Panel,
                                        checkedBorderColor = Color.White
                                    )
                                )
                            }
                            if (settings.autoRestTimer) LocalAlertSettings(restAlerts)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    ProfileCard(
                        border = if (settings.swapWeightReps) BorderStroke(1.dp, Color.White) else BorderStroke(1.dp, DenTrainPalette.SubtleBorder)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Сменить «КГ» и «Повт.» местами", fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    "Меняет порядок ввода веса и повторений в карточке.",
                                    fontSize = 12.sp, color = DenTrainPalette.MutedText
                                )
                            }
                            Switch(
                                checked = settings.swapWeightReps,
                                onCheckedChange = { enabled ->
                                    Haptic.click(context)
                                    vm.setSwapWeightReps(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color.White,
                                    checkedThumbColor = DenTrainPalette.Panel,
                                    checkedBorderColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    ProfileCard(
                        border = workoutAlerts.border(settings.reminderEnabled, Color.Red)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Напоминания о тренировках", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "По выбранным дням, даже без интернета.",
                                        fontSize = 12.sp, color = DenTrainPalette.MutedText
                                    )
                                }
                                Switch(
                                    checked = settings.reminderEnabled,
                                    onCheckedChange = { enabled ->
                                        Haptic.click(context)
                                        vm.setReminderEnabled(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color.White,
                                        checkedThumbColor = DenTrainPalette.Panel,
                                        checkedBorderColor = Color.White
                                    )
                                )
                            }

                            if (settings.reminderEnabled) LocalAlertSettings(workoutAlerts)
                            if (settings.reminderEnabled && workoutAlerts.allowed) {
                                Text("Время начала тренировки:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                OutlinedButton(
                                    onClick = {
                                        Haptic.click(context)
                                        pickTime(settings.workoutStartTime, vm::setWorkoutStartTime)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.AccessTime, null, tint = DenTrainPalette.Lime)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Время начала: ${settings.workoutStartTime}", color = Color.White)
                                }

                                Text("Дни тренировок:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                val activeDays = remember(settings.scheduledDays) {
                                    settings.scheduledDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Fmt.dayShort.forEachIndexed { index, dayName ->
                                        val dayNum = index + 1
                                        val isSel = dayNum in activeDays
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
                                                .border(
                                                    BorderStroke(1.dp, if (isSel) DenTrainPalette.Lime else DenTrainPalette.Border),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    Haptic.click(context)
                                                    vm.toggleScheduledDay(dayNum)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayName,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else DenTrainPalette.MutedText
                                            )
                                        }
                                    }
                                }

                                Text("Напоминать за:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                val activeLeads = remember(settings.reminderLeadOptions) {
                                    settings.reminderLeadOptions.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15 to "15 мин", 30 to "30 мин", 60 to "1 час", 120 to "2 часа").forEach { (min, label) ->
                                        val isSel = min in activeLeads
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
                                                .border(
                                                    BorderStroke(1.dp, if (isSel) DenTrainPalette.Lime else DenTrainPalette.Border),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    Haptic.click(context)
                                                    vm.toggleReminderLeadOption(min)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else DenTrainPalette.MutedText
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
}

@Composable
fun ProfileCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun ProfileInfoRow(value: String, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = DenTrainPalette.MutedText
        )
    }
}
