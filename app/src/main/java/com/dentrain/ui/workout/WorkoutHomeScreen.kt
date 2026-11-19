package com.dentrain.ui.workout

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.dentrain.data.ExerciseDraft
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutDraft
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutStatus
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.timer.RestTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.ActiveWorkoutCard
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.WeekCalendarRow
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private data class WeeklyStatsData(
    val workoutsCount: Int,
    val setsCount: Int,
    val tonnageKg: Double,
    val streakWeeks: Int
)

data class NotificationModel(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val type: String = "info"
)

class WorkoutHomeViewModel(
    private val repo: WorkoutRepository,
    restTimer: RestTimer
) : ViewModel() {
    val restState: StateFlow<TimerState> = restTimer.state
    val planned: StateFlow<List<WorkoutWithExercises>> =
        repo.planned.map { list -> list.sortedBy { it.workout.dateEpochDay } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val active: StateFlow<WorkoutWithExercises?> =
        repo.inProgress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pinned: StateFlow<List<WorkoutWithExercises>> =
        repo.pinned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allWorkouts: StateFlow<List<WorkoutWithExercises>> =
        repo.allWorkouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<SettingsEntity> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    private val _notifications = MutableStateFlow<List<NotificationModel>>(emptyList())
    val notifications: StateFlow<List<NotificationModel>> = _notifications
    val unreadNotificationsCount: StateFlow<Int> = _notifications.map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        syncAssignedWorkouts()
        listenToNotifications()
    }

    private fun listenToNotifications() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        FirebaseFirestore.getInstance().collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents.mapNotNull { doc ->
                        NotificationModel(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            isRead = doc.getBoolean("isRead") ?: false,
                            type = doc.getString("type") ?: "info"
                        )
                    }.sortedByDescending { it.timestamp }
                    _notifications.value = list
                }
            }
    }

    fun markNotificationsAsRead() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val unread = _notifications.value.filter { !it.isRead }
        if (unread.isEmpty()) return
        
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            try {
                // Batch write to mark as read (up to 500 limits, usually fine)
                val batch = db.batch()
                unread.forEach {
                    val ref = db.collection("notifications").document(it.id)
                    batch.update(ref, "isRead", true)
                }
                batch.commit().await()
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    private fun syncAssignedWorkouts() {
        val uid = FirebaseAuth.getInstance().uid ?: return
        viewModelScope.launch {
            try {
                // Облачная синхронизация данных пользователя (выполненные тренировки и рекорды)
                repo.syncUserDataFromCloud()

                val snapshot = FirebaseFirestore.getInstance()
                    .collection("assigned_workouts")
                    .whereEqualTo("athleteUid", uid)
                    .get().await()

                if (snapshot.isEmpty) return@launch

                val existingList = repo.allWorkouts.first()

                for (doc in snapshot.documents) {
                    val title = doc.getString("title") ?: continue
                    val trainerName = doc.getString("trainerName") ?: "Тренер"
                    val dateEpochDay = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay()
                    val description = doc.getString("description") ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val exListRaw = doc.get("exercises") as? List<Map<String, Any>> ?: emptyList()

                    val alreadyExists = existingList.any {
                        it.workout.title == title && it.workout.dateEpochDay == dateEpochDay
                    }

                    if (!alreadyExists) {
                        val dayOfWeek = try { LocalDate.ofEpochDay(dateEpochDay).dayOfWeek.value } catch (e: Exception) { 1 }
                        val drafts = exListRaw.map { map ->
                            ExerciseDraft(
                                id = 0L,
                                name = map["name"] as? String ?: "Упражнение",
                                sets = (map["plannedSets"] as? Long)?.toInt() ?: 3,
                                reps = map["plannedReps"] as? String ?: "10",
                                weight = (map["plannedWeight"] as? Double) ?: (map["plannedWeight"] as? Long)?.toDouble()
                            )
                        }
                        repo.saveWorkout(
                            WorkoutDraft(
                                id = 0L,
                                title = title,
                                epochDay = dateEpochDay,
                                dayOfWeek = dayOfWeek,
                                description = "Назначено тренером: $trainerName. $description".trim(),
                                exercises = drafts
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun start(id: Long, onStarted: () -> Unit) {
        viewModelScope.launch { repo.startWorkout(id); onStarted() }
    }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteWorkout(id) } }
    fun unpin(id: Long) { viewModelScope.launch { repo.setPinned(id, false) } }

    fun updateScheduledDays(days: Set<Int>) {
        val str = days.sorted().joinToString(",")
        viewModelScope.launch { repo.updateSettings { it.copy(scheduledDays = str) } }
    }

    fun updateHomeBlocksOrder(order: String) {
        viewModelScope.launch { repo.updateSettings { it.copy(homeBlocksOrder = order) } }
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
    onOpenTimer: () -> Unit
) {
    val vm = appViewModel { WorkoutHomeViewModel(it.repository, it.restTimer) }
    val restState by vm.restState.collectAsStateWithLifecycle()
    val planned by vm.planned.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val allWorkouts by vm.allWorkouts.collectAsStateWithLifecycle()

    // Читаем профиль пользователя из Firestore напрямую (или через отдельный ViewModel, но здесь проще так для аватара и имени)
    val uid = FirebaseAuth.getInstance().uid
    var userName by remember { mutableStateOf("Athlete") }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        if (uid != null) {
            val listener = FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val nickname = snapshot.getString("nickname")
                        if (!nickname.isNullOrBlank()) userName = nickname
                        userAvatarUrl = snapshot.getString("avatarUrl") ?: snapshot.getString("avatarBase64")
                    }
                }
            onDispose { listener.remove() }
        } else {
            onDispose {}
        }
    }

    val decodedAvatar = remember(userAvatarUrl) {
        if (!userAvatarUrl.isNullOrBlank() && !userAvatarUrl!!.startsWith("http")) {
            try {
                val imageBytes = Base64.decode(userAvatarUrl, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) { null }
        } else null
    }

    var deleteId by remember { mutableStateOf<Long?>(null) }

    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }

    val workoutsByDate = remember(allWorkouts) {
        allWorkouts.groupBy { it.workout.dateEpochDay }
    }
    val workoutDates = remember(allWorkouts) {
        allWorkouts.map { LocalDate.ofEpochDay(it.workout.dateEpochDay) }.toSet()
    }
    val completedDates = remember(allWorkouts) {
        allWorkouts.filter { it.workout.status == WorkoutStatus.COMPLETED }
            .map { LocalDate.ofEpochDay(it.workout.dateEpochDay) }.toSet()
    }
    val plannedDates = remember(allWorkouts) {
        allWorkouts.filter { it.workout.status == WorkoutStatus.PLANNED || it.workout.status == WorkoutStatus.IN_PROGRESS }
            .map { LocalDate.ofEpochDay(it.workout.dateEpochDay) }.toSet()
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    var isEditMode by remember { mutableStateOf(false) }

    val notifications by vm.notifications.collectAsStateWithLifecycle()
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
        Modifier.fillMaxSize().statusBarsPadding()
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
            }
    ) {
        val blocksList = settings.homeBlocksOrder.split(",").filter { it.isNotBlank() }.takeIf { it.isNotEmpty() } ?: listOf("MAIN", "CALENDAR", "PLANNED", "PINNED")

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            blocksList.forEach { block ->
                when (block) {
                    "MAIN" -> {
                        // Header
                        item {
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
                                    Text("Good Morning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            if (unreadNotificationsCount > 0) {
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

                                    val isTimerActive = restState.running || (restState.remainingMs > 0 && restState.remainingMs < restState.totalMs)
                                    
                                    // Запуск анимации при появлении экрана
                                    var isTimerActiveAnim by remember { mutableStateOf(false) }
                                    LaunchedEffect(isTimerActive) {
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
                        
                        // Незавершённая тренировка
                        active?.let { a ->
                            item {
                                ActiveWorkoutCard(
                                    workout = a,
                                    onClick = { onOpenSession(a.workout.id) }
                                )
                            }
                        }
                    }
                    
                    "CALENDAR" -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Календарь недели", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "Сегодня: ${Fmt.dayName(today.dayOfWeek.value)}, ${today.dayOfMonth} ${Fmt.monthName(today.monthValue)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFA0A7B8)
                                    )
                                }
                                WeekCalendarRow(
                                    selectedDate = selectedDate,
                                    onDateSelected = { date ->
                                        selectedDate = date
                                        val epochDay = date.toEpochDay()
                                        val dayWorkouts = workoutsByDate[epochDay] ?: emptyList()
                                        val targetW = dayWorkouts.find { it.workout.status == WorkoutStatus.IN_PROGRESS }
                                            ?: dayWorkouts.find { it.workout.status == WorkoutStatus.PLANNED }
                                            ?: dayWorkouts.find { it.workout.status == WorkoutStatus.COMPLETED }
                                        if (targetW != null) {
                                            onOpenDetail(targetW.workout.id)
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
                        item { SectionTitle("Запланированные") }
                        if (planned.isEmpty()) {
                            item {
                                EmptyState(Icons.Filled.FitnessCenter, "Пока нет запланированных тренировок", "Создайте новую с помощью кнопки +")
                            }
                        } else {
                            item {
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
                        item { SectionTitle("Закреплённые шаблоны") }
                        if (pinned.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Filled.PushPin, "Нет закреплённых тренировок",
                                    "Закрепите занятие в Истории, чтобы быстро начинать его"
                                )
                            }
                        } else {
                            items(pinned, key = { "p${it.workout.id}" }) { w ->
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
        
        LaunchedEffect(Unit) {
            vm.markNotificationsAsRead()
        }

        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1B1D25)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Уведомления",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (notifications.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Нет новых уведомлений", color = Color(0xFFA0A7B8))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (notif.isRead) Color(0xFF242732) else Color(0xFF2C3140))
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
                                        "success" -> Color(0xFFA6FF00)
                                        "warning" -> Color(0xFFEF5350)
                                        else -> Color(0xFF2196F3)
                                    }
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(notif.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(notif.message, color = Color(0xFFA0A7B8), fontSize = 13.sp, lineHeight = 18.sp)
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

    val targetStartPadding = when {
        planned.size <= 1 -> 0.dp
        pagerState.currentPage == 0 -> 0.dp
        pagerState.currentPage == planned.size - 1 -> 28.dp
        else -> 24.dp
    }

    val targetEndPadding = when {
        planned.size <= 1 -> 0.dp
        pagerState.currentPage == 0 -> 28.dp
        pagerState.currentPage == planned.size - 1 -> 0.dp
        else -> 24.dp
    }

    val startPadding by animateDpAsState(targetStartPadding, animationSpec = tween(300), label = "startPadding")
    val endPadding by animateDpAsState(targetEndPadding, animationSpec = tween(300), label = "endPadding")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val workout = planned[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val absOffset = abs(pageOffset).coerceIn(0f, 1f)
            val alpha = 1f - (absOffset * 0.65f)

            PlannedWorkoutCard(
                workout = workout,
                isActive = isActive,
                onStart = { onStart(workout.workout.id) },
                onEdit = { onEdit(workout.workout.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = alpha }
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
    val neoGreen = Color(0xFFBFFF00)

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(145.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1D25))
            .border(BorderStroke(1.dp, Color(0xFF282B36)), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(50.dp),
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
                    color = Color(0xFFA0A7B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0xFF282B36))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = workout.workout.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "$exercisesCount ${Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений")} · $totalSetsCount ${Fmt.plural(totalSetsCount, "подход", "подхода", "подходов")}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA0A7B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            .background(if (!isActive && workout.exercises.isNotEmpty()) neoGreen else Color(0xFF242732))
                            .border(
                                1.dp,
                                if (!isActive && workout.exercises.isNotEmpty()) Color.Transparent else Color(0xFF333644),
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
                                tint = if (!isActive && workout.exercises.isNotEmpty()) Color.Black else Color(0xFFA0A7B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (!isActive) "Начать тренировку" else "Завершите текущую",
                                color = if (!isActive && workout.exercises.isNotEmpty()) Color.Black else Color(0xFFA0A7B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
    }
}

/** Карточка Закрепленной тренировки с золотым бейджем «📌 Избранный шаблон». */
@Composable
private fun PinnedWorkoutCard(
    workout: WorkoutWithExercises,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    val w = workout.workout
    val cardBg = Color(0xFF1B1D25)
    val borderColor = Color(0xFF282B36)
    val labelColor = Color(0xFFA0A7B8)
    val accentColor = Color(0xFFA6FF00) // салатовый акцент
    val datePillBg = Color(0xFF242732)
    val datePillBorder = Color(0xFF333644)

    val dayName = remember(w.dayOfWeek) { Fmt.dayName(w.dayOfWeek) }
    val dateText = remember(w.dateEpochDay) {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
        LocalDate.ofEpochDay(w.dateEpochDay).format(formatter)
    }

    val exercisesCount = workout.exercises.size
    val setsCount = workout.doneSets()
    val durationText = Fmt.durationText(w.startedAt, w.completedAt)

    val gold = Color(0xFFFFB300)
    val weakGold = gold.copy(alpha = 0.3f)
    val baseBorder = BorderStroke(1.dp, weakGold)

    var cardModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(cardBg)
        .border(baseBorder, RoundedCornerShape(16.dp))
        .border(
            width = 2.dp,
            brush = Brush.linearGradient(
                0.0f to gold,
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1.0f to gold
            ),
            shape = RoundedCornerShape(16.dp)
        )
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
                    maxLines = 1,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = ", $dateText",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor
                )
            }

            // Статистика: Упражнения | Подходы   [ Длительность в капсуле справа ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Упражнений
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = exercisesCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Разделитель
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .height(16.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Подходов
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = setsCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Fmt.plural(setsCount, "подход", "подхода", "подходов"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Капсула времени
                if (durationText != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(datePillBg)
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

/** Виджет статистики и Стрика тренировок 🔥 */
@Composable
private fun WeeklyStatsCard(stats: WeeklyStatsData) {
    ZCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔥", fontSize = 18.sp)
                Text(
                    if (stats.streakWeeks > 0) "Стрик: ${stats.streakWeeks} ${Fmt.plural(stats.streakWeeks, "неделя", "недели", "недель")} подряд!"
                    else "Стрик: начните первую неделю!",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text("За 7 дней", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatBox(
                value = if (stats.tonnageKg >= 1000) "%.1f т".format(stats.tonnageKg / 1000.0).replace('.', ',') else "${stats.tonnageKg.toInt()} кг",
                label = "Объём веса",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                value = "${stats.setsCount}",
                label = "Подходов",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                value = "${stats.workoutsCount}",
                label = "Тренировок",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color(0xFFA0A7B8), textAlign = TextAlign.Center)
    }
}

/** Карточка календаря с кликабельными днями. Ровно 3 состояния: Тренировка / Запланированная, По плану, Отдых. */
@Composable
private fun CalendarCard(
    allWorkouts: List<WorkoutWithExercises>,
    settings: SettingsEntity,
    onConfigureDays: () -> Unit,
    onOpenWorkout: (WorkoutWithExercises) -> Unit,
    onCreateForDate: (Long) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }

    val scheduledSet = remember(settings.scheduledDays) {
        settings.scheduledDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    val workoutsByDate = remember(allWorkouts) {
        allWorkouts.groupBy { it.workout.dateEpochDay }
    }

    ZCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Календарь недели", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Сегодня: ${Fmt.dayName(today.dayOfWeek.value)}, ${today.dayOfMonth} ${Fmt.monthName(today.monthValue)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA0A7B8))
            }
            IconButton(onClick = onConfigureDays) {
                Icon(Icons.Filled.CalendarMonth, "Дни тренировок", tint = Color(0xFFA6FF00))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            weekDays.forEach { date ->
                val epochDay = date.toEpochDay()
                val dayOfWeekVal = date.dayOfWeek.value
                val isToday = date == today

                val dayWorkouts = workoutsByDate[epochDay] ?: emptyList()
                val completedW = dayWorkouts.find { it.workout.status == WorkoutStatus.COMPLETED }
                val activeW = dayWorkouts.find { it.workout.status == WorkoutStatus.IN_PROGRESS }
                val plannedW = dayWorkouts.find { it.workout.status == WorkoutStatus.PLANNED }

                val hasWorkout = completedW != null || activeW != null || plannedW != null
                val isScheduled = dayOfWeekVal in scheduledSet

                val dayName = Fmt.dayShort[(dayOfWeekVal - 1).coerceIn(0, 6)]

                val bgColor = when {
                    hasWorkout -> Color(0xFFA6FF00)
                    isScheduled -> Color(0xFF242732)
                    else -> Color(0xFF1B1D25)
                }

                val textColor = when {
                    hasWorkout -> Color.Black
                    isScheduled -> Color.White
                    else -> Color(0xFFA0A7B8)
                }

                val shape = RoundedCornerShape(12.dp)
                val border = when {
                    isToday -> BorderStroke(2.dp, Color.White)
                    isScheduled -> BorderStroke(1.dp, Color(0xFF333644))
                    else -> BorderStroke(1.dp, Color(0xFF282B36))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(shape)
                        .border(border, shape)
                        .background(bgColor)
                        .clickable {
                            Haptic.click(context)
                            val targetW = activeW ?: plannedW ?: completedW
                            if (targetW != null) {
                                onOpenWorkout(targetW)
                            } else {
                                onCreateForDate(epochDay)
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 2.dp)
                ) {
                    Text(dayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("${date.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textColor)

                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasWorkout -> Color.Black
                                    isScheduled -> Color(0xFFA6FF00)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            LegendItem(Color(0xFFA6FF00), "Запланированная")
            LegendItem(Color(0xFF242732), "По плану")
            LegendItem(Color(0xFF1B1D25), "Отдых")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA0A7B8))
    }
}

/** Диалог настройки регулярных тренировочных дней. */
@Composable
private fun ScheduledDaysDialog(
    currentDaysStr: String,
    onSave: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val initialSet = remember(currentDaysStr) {
        currentDaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
    var selectedDays by remember { mutableStateOf(initialSet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тренировочные дни") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выберите регулярные дни, в которые вы обычно тренируетесь:")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..7).forEach { dayVal ->
                        val label = Fmt.dayShort[(dayVal - 1).coerceIn(0, 6)]
                        val isSel = dayVal in selectedDays
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedDays = if (isSel) selectedDays - dayVal else selectedDays + dayVal
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedDays) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun WorkoutHomeScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("Тренировка", "DenTrain") {}
            GradientButton("Создать тренировку", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            SectionTitle("Запланированные")
            ZCard {
                Text("Силовая тренировка", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Грудь, Трицепс", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Удерживайте и перетаскивайте для изменения.", fontSize = 13.sp, color = Color(0xFFA0A7B8))
        
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
                        .background(Color(0xFF1B1D25))
                        .border(BorderStroke(1.dp, Color(0xFF282B36)), RoundedCornerShape(16.dp))
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
                        Icon(Icons.Filled.DragHandle, contentDescription = "Drag", tint = Color(0xFFA6FF00))
                    }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onClose(items) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6FF00), contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Готово", fontWeight = FontWeight.Bold)
        }
    }
}
