package com.dentrain.ui.workout

import com.dentrain.util.userMessage
import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.OperationErrorDialog
import com.dentrain.ui.theme.DenTrainPalette
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dentrain.R
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutStatus
import com.dentrain.data.WorkoutListEntry
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.timer.RestTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.ActiveWorkoutCard
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.WeekCalendarRow
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.ui.components.WorkoutListCount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import com.dentrain.ui.components.rememberAvatarBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.dentrain.data.WorkoutDelivery
import com.dentrain.data.activeTrainerRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.MetadataChanges
import java.time.Instant
import java.time.ZoneId
import androidx.compose.material3.TextButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class NotificationModel(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val type: String = "info",
    val opensTrainerRequests: Boolean = false
)

class WorkoutHomeViewModel(
    private val repo: WorkoutRepository,
    restTimer: RestTimer
) : ViewModel() {
    val restState: StateFlow<TimerState> = restTimer.state.map { state ->
        state.copy(remainingMs = ((state.remainingMs + 999) / 1_000) * 1_000)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), restTimer.state.value)
    var lastShownRestRunId: Long? = null
    private val _notifications = MutableStateFlow<List<NotificationModel>>(emptyList())
    val notifications: StateFlow<List<NotificationModel>> = _notifications
    val unreadNotificationsCount: StateFlow<Int> = _notifications.map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _notificationError = MutableStateFlow<String?>(null)
    val notificationError: StateFlow<String?> = _notificationError
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        syncAssignedWorkouts()
        listenToNotifications()
    }

    private fun listenToNotifications() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        notificationListener = FirebaseFirestore.getInstance().collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) _notificationError.value = failure.userMessage("Не удалось загрузить уведомления.")
                if (snapshot != null) {
                    _notificationError.value = null
                    _notifications.value = snapshot.documents.filterNot {
                        it.getTimestamp("expiresAt")?.toDate()?.time?.let { expiry -> expiry <= System.currentTimeMillis() } == true ||
                            (it.contains("event") && it.getTimestamp("occurredAt") == null)
                    }
                        .map { it.toNotification() }.sortedByDescending { it.timestamp }
                }
            }
    }

    private fun DocumentSnapshot.toNotification(): NotificationModel {
        val subject = getString("subject").orEmpty()
        val event = getString("event")
        val (title, message) = when (event) {
            "request_received" -> "Новая заявка" to "Заявка на тренировки от спортсмена $subject."
            "request_sent" -> "Заявка отправлена" to "Ваша заявка тренеру $subject отправлена."
            "accepted" -> "Заявка принята" to "$subject теперь ваш тренер."
            "rejected" -> "Заявка отклонена" to "$subject отклонил вашу заявку."
            "assigned" -> "Новая тренировка" to "«$subject» появится в плане тренировок."
            "received" -> "Тренировка получена" to "«$subject» сохранена в дневнике ученика."
            else -> getString("title").orEmpty() to getString("message").orEmpty()
        }
        val type = when (event) {
            "received", "accepted", "request_sent" -> "success"
            "rejected" -> "warning"
            else -> getString("type") ?: "info"
        }
        return NotificationModel(
            id, title, message,
            getTimestamp("occurredAt")?.toDate()?.time ?: getLong("timestamp") ?: 0L,
            getBoolean("isRead") ?: false, type,
            opensTrainerRequests = event == "request_received"
        )
    }

    fun markNotificationsAsRead(id: String? = null) {
        val unread = _notifications.value.filter { !it.isRead && (id == null || it.id == id) }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            try {
                val batch = db.batch()
                unread.forEach { batch.update(db.collection("notifications").document(it.id), "isRead", true) }
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                _notificationError.value = failure.userMessage("Не удалось отметить уведомления прочитанными.")
            }
        }
    }

    private fun syncAssignedWorkouts() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val db = FirebaseFirestore.getInstance()
        viewModelScope.launch(Dispatchers.IO) {
            callbackFlow {
                val listener = db.collection("assigned_workouts")
                    .whereEqualTo("athleteUid", uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                        if (failure == null && snapshot != null) trySend(snapshot)
                    }
                awaitClose { listener.remove() }
            }.collect { snapshot ->
                for (doc in snapshot.documents) {
                    try {
                        WorkoutDelivery.importAssignment(db, uid, repo, doc)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("WorkoutDelivery", "Could not import or confirm assignment", e)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        notificationListener?.remove()
    }

    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    fun start(id: Long, onStarted: () -> Unit) {
        perform("Не удалось начать тренировку.") { repo.startWorkout(id); onStarted() }
    }
    fun delete(id: Long) { perform("Не удалось удалить тренировку.") { repo.deleteWorkout(id) } }
    fun unpin(id: Long) { perform("Не удалось открепить тренировку.") { repo.setPinned(id, false) } }

    fun updateHomeBlocksOrder(order: String) {
        perform("Не удалось сохранить порядок блоков.") { repo.updateSettings { it.copy(homeBlocksOrder = order) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHomeScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenTrainerRequests: () -> Unit
) {
    val vm = appViewModel { WorkoutHomeViewModel(it.repository, it.restTimer) }
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val account = (LocalContext.current.applicationContext as com.dentrain.DenTrainApp).container
    val sync = account.sync
    val syncStatus = sync?.status?.collectAsStateWithLifecycle()?.value.orEmpty()
    val planned by account.screenCache.planned.collectAsStateWithLifecycle()
    val active by account.screenCache.active.collectAsStateWithLifecycle()
    val pinned by account.screenCache.pinned.collectAsStateWithLifecycle()
    val calendar by account.screenCache.calendar.collectAsStateWithLifecycle()

    // Читаем профиль пользователя из Firestore напрямую (или через отдельный ViewModel, но здесь проще так для аватара и имени)
    val uid = FirebaseAuth.getInstance().uid
    var userName by remember(uid) { mutableStateOf("Athlete") }
    var userAvatarUrl by remember(uid) { mutableStateOf<String?>(null) }
    var canReceiveRequests by remember(uid) { mutableStateOf(false) }
    var pendingRequests by remember(uid, canReceiveRequests) { mutableStateOf(0) }

    DisposableEffect(uid) {
        if (uid != null) {
            val listener = FirebaseFirestore.getInstance().collection("profiles").document(uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val nickname = snapshot.getString("nickname")
                        if (!nickname.isNullOrBlank()) userName = nickname
                        userAvatarUrl = snapshot.getString("avatarUrl") ?: snapshot.getString("avatarBase64")
                    }
                    if (snapshot != null) {
                        canReceiveRequests = snapshot.getString("role") in setOf("trainer", "admin")
                    }
                }
            onDispose { listener.remove() }
        } else {
            onDispose {}
        }
    }

    DisposableEffect(uid, canReceiveRequests) {
        val requests = if (uid != null && canReceiveRequests) {
            FirebaseFirestore.getInstance().collection("trainer_requests")
                .whereEqualTo("trainerUid", uid).whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) pendingRequests = snapshot.documents.count { it.activeTrainerRequest() != null }
                }
        } else null
        onDispose { requests?.remove() }
    }

    val avatarSource = userAvatarUrl
    val decodedAvatar = rememberAvatarBitmap(avatarSource)

    var deleteId by remember { mutableStateOf<Long?>(null) }

    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }

    val (workoutsByDate, completedDates, plannedDates) = calendar

    val settings by account.screenCache.settings.collectAsStateWithLifecycle()
    var isEditMode by remember { mutableStateOf(false) }

    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val notificationError by vm.notificationError.collectAsStateWithLifecycle()
    val unreadNotificationsCount by vm.unreadNotificationsCount.collectAsStateWithLifecycle()
    var showNotificationsSheet by remember { mutableStateOf(false) }

    if (isEditMode) {
        val blocks = settings.homeBlocksOrder.split(",").filter { it.isNotBlank() }.takeIf { it.isNotEmpty() } ?: listOf("MAIN", "CALENDAR", "PLANNED", "PINNED")
        EditBlocksMode(
            blocksOrder = blocks,
            onClose = { newOrder ->
                isEditMode = false
                vm.updateHomeBlocksOrder(newOrder.joinToString(","))
            }
        )
        return
    }

    Box(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isCancelled = false
                    try {
                        withTimeout(3000L) {
                            while(true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.any { it.isConsumed || !it.pressed }) {
                                    isCancelled = true
                                    break
                                }
                            }
                        }
                    } catch (e: PointerEventTimeoutCancellationException) {
                        if (!isCancelled) {
                            isEditMode = true
                            down.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val blocksList = remember(settings.homeBlocksOrder) {
            settings.homeBlocksOrder.split(",").filter { it.isNotBlank() }.distinct()
                .ifEmpty { listOf("MAIN", "CALENDAR", "PLANNED", "PINNED") }
        }

        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (syncStatus.isNotEmpty()) item(key = "sync", contentType = "sync") {
                Text(syncStatus, color = MaterialTheme.colorScheme.error)
            }
            blocksList.forEach { block ->
                when (block) {
                    "MAIN" -> {
                        // Header
                        item(key = "header", contentType = "header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar placeholder or actual image
                                val avatarModifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                if (!userAvatarUrl.isNullOrBlank() && userAvatarUrl!!.startsWith("http")) {
                                    AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = "Profile",
                                        modifier = avatarModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (decodedAvatar != null) {
                                    Image(
                                        bitmap = decodedAvatar.asImageBitmap(),
                                        contentDescription = "Profile",
                                        modifier = avatarModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                
                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Good Morning",
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val sun by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.greeting_sun))
                                        LottieAnimation(
                                            composition = sun,
                                            modifier = Modifier.padding(start = 4.dp).size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = userName,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(onClick = { showNotificationsSheet = true }) {
                                        Box {
                                            Icon(Icons.Outlined.Notifications, contentDescription = "Уведомления", tint = MaterialTheme.colorScheme.onSurface)
                                            if (unreadNotificationsCount > 0 || pendingRequests > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Red)
                                                        .align(Alignment.TopEnd)
                                                )
                                            }
                                        }
                                    }

                                    val restState by vm.restState.collectAsStateWithLifecycle()
                                    val runId = restState.runId
                                    val isTimerActive = runId != null && restState.remainingMs > 0 && !restState.finished
                                    key(runId ?: vm.lastShownRestRunId) {
                                        var isTimerActiveAnim by remember {
                                            mutableStateOf(isTimerActive && vm.lastShownRestRunId == runId)
                                        }
                                        LaunchedEffect(isTimerActive) {
                                            if (isTimerActive) vm.lastShownRestRunId = runId
                                            isTimerActiveAnim = isTimerActive
                                        }

                                        val timerColor by animateColorAsState(
                                            if (isTimerActiveAnim) Color(0xFF00BFFF) else Color.Transparent,
                                            animationSpec = tween(1000),
                                            label = "borderColor"
                                        )
                                        val iconColor by animateColorAsState(
                                            if (isTimerActiveAnim) Color(0xFF00BFFF) else MaterialTheme.colorScheme.onSurface,
                                            animationSpec = tween(1000),
                                            label = "iconColor"
                                        )
                                        val hPad by animateDpAsState(
                                            if (isTimerActiveAnim) 12.dp else 8.dp,
                                            animationSpec = tween(1000),
                                            label = "hPad"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .border(1.dp, timerColor, RoundedCornerShape(50))
                                                .clickable { onOpenTimer() }
                                                .padding(horizontal = hPad, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Timer,
                                                    contentDescription = "Таймер",
                                                    tint = iconColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                AnimatedVisibility(
                                                    visible = isTimerActiveAnim,
                                                    enter = fadeIn(tween(1000)) + expandHorizontally(tween(1000)),
                                                    exit = fadeOut(tween(1000)) + shrinkHorizontally(tween(1000))
                                                ) {
                                                    Text(
                                                        text = Fmt.clock(restState.remainingMs),
                                                        color = Color(0xFF00BFFF),
                                                        fontWeight = FontWeight.Bold,
                                                        style = LocalTextStyle.current.copy(
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        modifier = Modifier.padding(start = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Незавершённая тренировка
                        active?.let { a ->
                            item(key = "active", contentType = "active") {
                                ActiveWorkoutCard(
                                    workout = a,
                                    onClick = { onOpenSession(a.workout.id) }
                                )
                            }
                        }
                    }
                    
                    "CALENDAR" -> {
                        item(key = "calendar", contentType = "calendar") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WeekCalendarHeading(today)
                                WeekCalendarRow(
                                    selectedDate = selectedDate,
                                    onDateSelected = { date ->
                                        selectedDate = date
                                        val epochDay = date.toEpochDay()
                                        val dayWorkouts = workoutsByDate[epochDay] ?: emptyList()
                                        val targetW = dayWorkouts.find { it.status == WorkoutStatus.IN_PROGRESS }
                                            ?: dayWorkouts.find { it.status == WorkoutStatus.PLANNED }
                                            ?: dayWorkouts.find { it.status == WorkoutStatus.COMPLETED }
                                        if (targetW != null) {
                                            onOpenDetail(targetW.id)
                                        }
                                    },
                                    completedDates = completedDates,
                                    plannedDates = plannedDates,
                                    showSelection = false
                                )
                            }
                        }
                    }
                    
                    "PLANNED" -> {
                        item(key = "planned_title", contentType = "title") { SectionTitle("Запланированные") }
                        if (planned.isEmpty()) {
                            item(key = "planned_empty", contentType = "empty") {
                                EmptyState(Icons.Filled.FitnessCenter, "Пока нет запланированных тренировок", "Создайте новую с помощью кнопки +")
                            }
                        } else {
                            item(key = "planned_carousel", contentType = "carousel") {
                                PlannedWorkoutsCarousel(
                                    planned = planned,
                                    isActive = active != null,
                                    onStart = { id -> vm.start(id) { onOpenSession(id) } },
                                    onEdit = { id -> onEdit(id) }
                                )
                            }
                        }
                    }
                    
                    "PINNED" -> {
                        item(key = "pinned_title", contentType = "title") { SectionTitle("Закреплённые шаблоны") }
                        if (pinned.isEmpty()) {
                            item(key = "pinned_empty", contentType = "empty") {
                                EmptyState(
                                    Icons.Filled.PushPin, "Нет закреплённых тренировок",
                                    "Закрепите занятие в Истории, чтобы быстро начинать его"
                                )
                            }
                        } else {
                            items(pinned, key = { it.workout.id }, contentType = { "pinned" }) { w ->
                                PinnedWorkoutCard(
                                    workout = w,
                                    onClick = { onOpenDetail(w.workout.id) },
                                    onUnpin = { vm.unpin(w.workout.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            "Удалить тренировку?", "Запланированная тренировка будет удалена без возможности восстановления.",
            onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null }
        )
    }

    if (showNotificationsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        
        var unreadOnly by remember { mutableStateOf(false) }
        val visibleNotifications = if (unreadOnly) notifications.filterNot { it.isRead } else notifications
        val timeFormat = remember { DateTimeFormatter.ofPattern("dd.MM · HH:mm").withZone(ZoneId.systemDefault()) }

        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState,
            containerColor = DenTrainPalette.Panel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Уведомления", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = { vm.markNotificationsAsRead() }, enabled = unreadNotificationsCount > 0) {
                        Text("Прочитать все")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { unreadOnly = false }, enabled = unreadOnly) { Text("Все") }
                    TextButton(onClick = { unreadOnly = true }, enabled = !unreadOnly) {
                        Text("Непрочитанные ($unreadNotificationsCount)")
                    }
                }
                notificationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (pendingRequests > 0) {
                    TextButton(
                        onClick = {
                            showNotificationsSheet = false
                            onOpenTrainerRequests()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Заявки на рассмотрении · $pendingRequests")
                    }
                }

                if (visibleNotifications.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (unreadOnly) "Всё прочитано" else "Уведомлений пока нет", color = DenTrainPalette.MutedText)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(visibleNotifications, key = { it.id }) { notif ->
                            val opensRequests = notif.opensTrainerRequests && canReceiveRequests
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (notif.isRead) DenTrainPalette.RaisedPanel else Color(0xFF2C3140))
                                    .clickable(enabled = !notif.isRead || opensRequests) {
                                        if (!notif.isRead) vm.markNotificationsAsRead(notif.id)
                                        if (opensRequests) {
                                            showNotificationsSheet = false
                                            onOpenTrainerRequests()
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF13151C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = when (notif.type) {
                                        "success" -> Icons.Filled.Star
                                        "warning" -> Icons.Filled.Lock
                                        else -> Icons.Outlined.Notifications
                                    }
                                    val tint = when (notif.type) {
                                        "success" -> DenTrainPalette.Lime
                                        "warning" -> Color(0xFFEF5350)
                                        else -> Color(0xFF2196F3)
                                    }
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(notif.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(notif.message, color = DenTrainPalette.MutedText, fontSize = 13.sp, lineHeight = 18.sp)
                                    if (notif.timestamp > 0) {
                                        Text(timeFormat.format(Instant.ofEpochMilli(notif.timestamp)), color = DenTrainPalette.MutedText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val calendarDateFormat = DateTimeFormatter.ofPattern("d\u00A0MMMM", Locale.forLanguageTag("ru"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCalendarHeading(today: LocalDate) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Календарь недели",
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Сегодня: ${Fmt.dayName(today.dayOfWeek.value)}, ${today.format(calendarDateFormat)}",
            modifier = Modifier.align(Alignment.CenterVertically),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = DenTrainPalette.MutedText
        )
    }
}

/** Карусель запланированных тренировок с плавным пролистыванием, боковыми стрелками и приглушением невыбранных карточек. */
@Composable
private fun PlannedWorkoutsCarousel(
    planned: List<WorkoutWithExercises>,
    isActive: Boolean,
    onStart: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { planned.size })
    val coroutineScope = rememberCoroutineScope()

    val edgePadding = if (planned.size > 1) 24.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = edgePadding),
            key = { planned[it].workout.id },
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val workout = planned[page]
            PlannedWorkoutCard(
                workout = workout,
                isActive = isActive,
                onStart = { onStart(workout.workout.id) },
                onEdit = { onEdit(workout.workout.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val pageOffset = pagerState.currentPage - page + pagerState.currentPageOffsetFraction
                        alpha = 1f - abs(pageOffset).coerceIn(0f, 1f) * 0.65f
                    }
            )
        }

        if (pagerState.currentPage > 0) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage - 1,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-6).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F222C))
                    .border(BorderStroke(1.dp, Color(0xFF383C4D)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Предыдущая тренировка",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (pagerState.currentPage < planned.size - 1) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F222C))
                    .border(BorderStroke(1.dp, Color(0xFF383C4D)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Следующая тренировка",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Карточка Запланированной тренировки с фирменной датой слева и информацией о количестве упражнений и подходов. */
@Composable
private fun PlannedWorkoutCard(
    workout: WorkoutWithExercises,
    isActive: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val neoGreen = DenTrainPalette.NeonGreen

    val date = remember(workout.workout.dateEpochDay) {
        LocalDate.ofEpochDay(workout.workout.dateEpochDay)
    }

    val dayOfWeekName = remember(date) {
        Fmt.dayShort[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
    }

    val monthNamesGenitive = remember {
        listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    }
    val monthName = remember(date) {
        monthNamesGenitive[(date.monthValue - 1).coerceIn(0, 11)]
    }
    val dayOfMonth = date.dayOfMonth.toString()

    val exercisesCount = workout.exercises.size
    val totalSetsCount = remember(workout) {
        workout.exercises.sumOf { it.exercise.plannedSets.coerceAtLeast(1) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        val stackDate = maxWidth < 360.dp && LocalDensity.current.fontScale > 1.3f
        val details: @Composable (Modifier) -> Unit = { detailsModifier ->
            Column(
                modifier = detailsModifier,
                verticalArrangement = if (stackDate) Arrangement.Top else Arrangement.SpaceBetween
            ) {
                Text(
                    text = workout.workout.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = if (stackDate) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "$exercisesCount ${Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений")} · $totalSetsCount ${Fmt.plural(totalSetsCount, "подход", "подхода", "подходов")}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    color = DenTrainPalette.MutedText,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isActive && workout.exercises.isNotEmpty()) neoGreen else DenTrainPalette.RaisedPanel)
                            .border(
                                1.dp,
                                if (!isActive && workout.exercises.isNotEmpty()) Color.Transparent else DenTrainPalette.Border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isActive && workout.exercises.isNotEmpty()) {
                                Haptic.click(context)
                                onStart()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (!isActive) Icons.Filled.PlayArrow else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (!isActive && workout.exercises.isNotEmpty()) Color.Black else DenTrainPalette.MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (!isActive) "Начать тренировку" else "Завершите текущую",
                                color = if (!isActive && workout.exercises.isNotEmpty()) Color.Black else DenTrainPalette.MutedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isActive) 11.sp else 13.sp,
                                maxLines = if (isActive) 1 else Int.MAX_VALUE
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { Haptic.click(context); onEdit() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Изменить",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        if (stackDate) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = neoGreen, fontWeight = FontWeight.Bold)) {
                            append(dayOfWeekName)
                        }
                        append("  ")
                        withStyle(SpanStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)) {
                            append(dayOfMonth)
                        }
                        append(" ")
                        append(monthName)
                    },
                    color = DenTrainPalette.MutedText,
                    fontSize = 15.sp
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(DenTrainPalette.SubtleBorder))
                details(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 121.dp).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 50.dp, max = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dayOfWeekName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = neoGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dayOfMonth,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = monthName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = DenTrainPalette.MutedText,
                        textAlign = TextAlign.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(DenTrainPalette.SubtleBorder)
                )

                details(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Карточка Закрепленной тренировки с золотым бейджем «📌 Избранный шаблон». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnedWorkoutCard(
    workout: WorkoutListEntry,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    val w = workout.workout
    val labelColor = DenTrainPalette.MutedText
    val accentColor = DenTrainPalette.Lime // салатовый акцент
    val datePillBg = DenTrainPalette.RaisedPanel
    val datePillBorder = DenTrainPalette.Border

    val dayName = remember(w.dayOfWeek) { Fmt.dayName(w.dayOfWeek) }
    val dateText = remember(w.dateEpochDay) { Fmt.displayDate(w.dateEpochDay) }

    val exercisesCount = workout.exercisesCount
    val setsCount = workout.setsCount
    val durationText = remember(w.startedAt, w.completedAt) { Fmt.durationText(w.startedAt, w.completedAt) }

    val gold = Color(0xFFFFB300)
    val shape = RoundedCornerShape(16.dp)
    val pinnedBorder = remember {
        Brush.linearGradient(0f to gold, 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to gold)
    }
    val cardModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(DenTrainPalette.Panel)
        .border(1.dp, gold.copy(alpha = 0.3f), shape)
        .border(2.dp, pinnedBorder, shape)
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Название тренировки и крупная кнопка пина
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = w.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Кнопка закрепить/открепить (крупная и кликабельная)
                IconButton(
                    onClick = { onUnpin() },
                    modifier = Modifier.size(40.dp) // увеличен размер области нажатия
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Открепить",
                        tint = gold,
                        modifier = Modifier.size(20.dp) // сама иконка тоже стала крупнее
                    )
                }
            }

            // День недели и дата (День выделен)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                        append(dayName)
                    }
                    append(", ")
                    append(dateText)
                },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )

            // Статистика: Упражнения | Подходы   [ Длительность в капсуле справа ]
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Упражнений
                    WorkoutListCount(exercisesCount, Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений"), labelColor)

                    // Подходов
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .height(16.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                        WorkoutListCount(setsCount, Fmt.plural(setsCount, "подход", "подхода", "подходов"), labelColor)
                    }
                }

                // Капсула времени
                if (durationText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 4.dp)
                            .background(datePillBg, CircleShape)
                            .border(BorderStroke(1.dp, datePillBorder), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = labelColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = durationText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = labelColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditBlocksMode(
    blocksOrder: List<String>,
    onClose: (List<String>) -> Unit
) {
    var items by remember { mutableStateOf(blocksOrder) }
    
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    val itemHeight = 70.dp
    val spacing = 8.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val totalItemHeight = itemHeightPx + spacingPx
    
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Text("Порядок блоков", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Удерживайте и перетаскивайте для изменения.", fontSize = 13.sp, color = DenTrainPalette.MutedText)
        
        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                val isDragging = draggedIndex == index
                val zIndex = if (isDragging) 1f else 0f
                val yOffset = if (isDragging) dragOffsetY else 0f
                
                val animatedOffset by animateDpAsState(targetValue = (index * (70 + 8)).dp, label = "offset")

                val displayTitle = when(item) {
                    "MAIN" -> "Главный"
                    "CALENDAR" -> "Календарь"
                    "PLANNED" -> "Запланированные"
                    "PINNED" -> "Закрепленные"
                    else -> item
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .offset(y = if (isDragging) animatedOffset + with(density) { yOffset.toDp() } else animatedOffset)
                        .zIndex(zIndex)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedIndex = index },
                                onDragEnd = {
                                    draggedIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggedIndex = -1
                                    dragOffsetY = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val currentY = (index * totalItemHeight) + dragOffsetY
                                val targetIndex = (currentY / totalItemHeight).roundToInt().coerceIn(0, items.size - 1)
                                if (targetIndex != index) {
                                    val newItems = items.toMutableList()
                                    val draggedItem = newItems.removeAt(index)
                                    newItems.add(targetIndex, draggedItem)
                                    items = newItems
                                    
                                    dragOffsetY -= (targetIndex - index) * totalItemHeight
                                    draggedIndex = targetIndex
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(displayTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Icon(Icons.Filled.DragHandle, contentDescription = "Drag", tint = DenTrainPalette.Lime)
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onClose(items) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DenTrainPalette.Lime, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Готово", fontWeight = FontWeight.Bold)
        }
    }
}
