package com.dentrain.ui.workout

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutStatus
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class WeeklyStatsData(
    val workoutsCount: Int,
    val setsCount: Int,
    val tonnageKg: Double,
    val streakWeeks: Int
)

class WorkoutHomeViewModel(private val repo: WorkoutRepository) : ViewModel() {
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

    fun start(id: Long, onStarted: () -> Unit) {
        viewModelScope.launch { repo.startWorkout(id); onStarted() }
    }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteWorkout(id) } }
    fun unpin(id: Long) { viewModelScope.launch { repo.setPinned(id, false) } }

    fun updateScheduledDays(days: Set<Int>) {
        val str = days.sorted().joinToString(",")
        viewModelScope.launch { repo.updateSettings { it.copy(scheduledDays = str) } }
    }
}

@Composable
fun WorkoutHomeScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val vm = appViewModel { WorkoutHomeViewModel(it.repository) }
    val planned by vm.planned.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val allWorkouts by vm.allWorkouts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deleteId by remember { mutableStateOf<Long?>(null) }
    var showDaysDialog by remember { mutableStateOf(false) }

    val weeklyStats = remember(allWorkouts) {
        val today = LocalDate.now()
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong()).toEpochDay()
        val endOfWeek = startOfWeek + 6

        val thisWeekCompleted = allWorkouts.filter {
            it.workout.status == WorkoutStatus.COMPLETED &&
                it.workout.dateEpochDay in startOfWeek..endOfWeek
        }

        var totalKg = 0.0
        var totalSets = 0
        thisWeekCompleted.forEach { w ->
            w.exercises.forEach { ex ->
                ex.sets.forEach { s ->
                    totalSets++
                    val wt = s.weight ?: 0.0
                    totalKg += wt * s.reps
                }
            }
        }

        val completedEpochDays = allWorkouts.filter { it.workout.status == WorkoutStatus.COMPLETED }
            .map { it.workout.dateEpochDay }
            .toSet()

        var streakWeeks = 0
        var currentCheckWeekStart = startOfWeek
        while (true) {
            val weekRange = currentCheckWeekStart..(currentCheckWeekStart + 6)
            val hasWorkoutInWeek = completedEpochDays.any { it in weekRange }
            if (hasWorkoutInWeek) {
                streakWeeks++
                currentCheckWeekStart -= 7
            } else {
                if (currentCheckWeekStart == startOfWeek && completedEpochDays.isNotEmpty()) {
                    currentCheckWeekStart -= 7
                    continue
                }
                break
            }
        }

        WeeklyStatsData(
            workoutsCount = thisWeekCompleted.size,
            setsCount = totalSets,
            tonnageKg = totalKg,
            streakWeeks = streakWeeks
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Незавершённая тренировка
            active?.let { a ->
                item {
                    ZCard(gradient = true, onClick = { Haptic.click(context); onOpenSession(a.workout.id) }) {
                        Text("ТРЕНИРОВКА В ПРОЦЕССЕ", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        Text(a.workout.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Выполнено подходов: ${a.exercises.sumOf { it.sets.size }}", color = Color.White.copy(alpha = 0.9f))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                            Text("Продолжить", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Статистика за неделю и стрик 🔥
            item {
                WeeklyStatsCard(stats = weeklyStats)
            }

            // Календарь текущей недели
            item {
                CalendarCard(
                    allWorkouts = allWorkouts,
                    settings = settings,
                    onConfigureDays = { Haptic.click(context); showDaysDialog = true },
                    onOpenWorkout = { targetW ->
                        when (targetW.workout.status) {
                            WorkoutStatus.IN_PROGRESS -> onOpenSession(targetW.workout.id)
                            WorkoutStatus.COMPLETED -> onOpenDetail(targetW.workout.id)
                            else -> onEdit(targetW.workout.id)
                        }
                    },
                    onCreateForDate = { onCreate() }
                )
            }

            item { GradientButton("Создать тренировку", { Haptic.click(context); onCreate() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add) }

            item { SectionTitle("Запланированные") }
            if (planned.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.FitnessCenter, "Пока нет запланированных тренировок", "Нажмите «Создать тренировку»")
                }
            } else {
                items(planned, key = { it.workout.id }) { w ->
                    PlannedWorkoutCard(
                        workout = w,
                        isActive = active != null,
                        onStart = { vm.start(w.workout.id) { onOpenSession(w.workout.id) } },
                        onEdit = { onEdit(w.workout.id) },
                        onDelete = { deleteId = w.workout.id }
                    )
                }
            }

            item { SectionTitle("Закреплённые тренировки") }
            if (pinned.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.PushPin, "Нет закреплённых тренировок",
                        "Закрепите занятие на вкладке «История», чтобы быстро сравнивать результаты"
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

    deleteId?.let { id ->
        ConfirmDialog(
            "Удалить тренировку?", "Запланированная тренировка будет удалена без возможности восстановления.",
            onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null }
        )
    }

    if (showDaysDialog) {
        ScheduledDaysDialog(
            currentDaysStr = settings.scheduledDays,
            onSave = { newDays ->
                Haptic.click(context)
                vm.updateScheduledDays(newDays)
                showDaysDialog = false
            },
            onDismiss = { showDaysDialog = false }
        )
    }
}

/** Карточка Запланированной тренировки с синим левым бордюром-акцентом и бейджем даты. */
@Composable
private fun PlannedWorkoutCard(
    workout: WorkoutWithExercises,
    isActive: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val todayEpoch = remember { LocalDate.now().toEpochDay() }

    val dateBadgeText = when (workout.workout.dateEpochDay) {
        todayEpoch -> "📅 Сегодня"
        todayEpoch + 1 -> "📅 Завтра"
        else -> "📅 ${Fmt.shortDate(workout.workout.dateEpochDay)}"
    }

    ZCard {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        workout.workout.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            dateBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                val n = workout.exercises.size
                Text(
                    "$n ${Fmt.plural(n, "упражнение", "упражнения", "упражнений")}: " +
                        workout.exercises.sortedBy { it.exercise.position }.joinToString(", ") { it.exercise.name },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradientButton(
                        if (!isActive) "Начать тренировку" else "Сначала завершите текущую",
                        onClick = {
                            Haptic.click(context)
                            onStart()
                        },
                        enabled = !isActive && workout.exercises.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { Haptic.click(context); onEdit() }) { Icon(Icons.Filled.Edit, "Изменить") }
                    IconButton(onClick = { Haptic.click(context); onDelete() }) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
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
    val context = LocalContext.current

    ZCard(onClick = { Haptic.click(context); onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "📌 Избранный шаблон",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB300)
                    )
                }

                IconButton(
                    onClick = { Haptic.click(context); onUnpin() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.PushPin, "Открепить", tint = Color(0xFFFFB300))
                }
            }

            Text(workout.workout.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

            Text(
                "⏱ Последний раз: ${Fmt.shortDate(workout.workout.dateEpochDay)} · ${Fmt.summary(workout)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
                Text("Календарь недели", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Сегодня: ${Fmt.dayName(today.dayOfWeek.value)}, ${today.dayOfMonth} ${Fmt.monthName(today.monthValue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onConfigureDays) {
                Icon(Icons.Filled.CalendarMonth, "Дни тренировок", tint = MaterialTheme.colorScheme.primary)
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
                    hasWorkout -> MaterialTheme.colorScheme.primary
                    isScheduled -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

                val textColor = when {
                    hasWorkout -> Color.White
                    isScheduled -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                val shape = RoundedCornerShape(12.dp)
                val border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(shape)
                        .then(if (border != null) Modifier.border(border, shape) else Modifier)
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
                                    hasWorkout -> Color.White
                                    isScheduled -> MaterialTheme.colorScheme.primary
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
            LegendItem(MaterialTheme.colorScheme.primary, "Запланированная")
            LegendItem(MaterialTheme.colorScheme.primaryContainer, "По плану")
            LegendItem(MaterialTheme.colorScheme.surfaceVariant, "Отдых")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            WeeklyStatsCard(stats = WeeklyStatsData(workoutsCount = 3, setsCount = 28, tonnageKg = 3450.0, streakWeeks = 3))
            CalendarCard(
                allWorkouts = emptyList(),
                settings = SettingsEntity(),
                onConfigureDays = {},
                onOpenWorkout = {},
                onCreateForDate = {}
            )
            GradientButton("Создать тренировку", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            SectionTitle("Запланированные")
            ZCard {
                Text("Силовая тренировка", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Грудь, Трицепс", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
