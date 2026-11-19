package com.dentrain.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.SettingsEntity
import com.dentrain.data.SetResultEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutStatus
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.SetEditDialog
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetailViewModel(private val id: Long, private val repo: WorkoutRepository) : ViewModel() {
    val workout: StateFlow<WorkoutWithExercises?> =
        repo.observeWorkout(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pinned: StateFlow<Boolean> = repo.pinnedIds.map { id in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val settings: StateFlow<SettingsEntity> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    fun togglePin() { viewModelScope.launch { repo.setPinned(id, !pinned.value) } }
    fun delete(onDone: () -> Unit) { viewModelScope.launch { repo.deleteWorkout(id); onDone() } }
    fun repeat(onDone: (Long) -> Unit) { viewModelScope.launch { onDone(repo.repeatWorkout(id)) } }
    fun updateSet(set: SetResultEntity, reps: Int, weight: Double?, isWarmup: Boolean = set.isWarmup) { viewModelScope.launch { repo.updateSet(set, reps, weight, isWarmup) } }
    fun deleteSet(set: SetResultEntity) { viewModelScope.launch { repo.deleteSet(set) } }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1D25))
            .border(BorderStroke(1.dp, Color(0xFF282B36)), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Просмотр завершённой / запланированной тренировки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRepeated: (Long) -> Unit
) {
    val context = LocalContext.current
    val vm = appViewModel(key = "detail$workoutId") { DetailViewModel(workoutId, it.repository) }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var editMode by remember { mutableStateOf(false) }
    var hasChanges by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetResultEntity?>(null) }

    val handleBack = {
        if (hasChanges) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (workout?.workout?.status == WorkoutStatus.COMPLETED) "Завершённая тренировка" else "Просмотр тренировки",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    BorderedBackButton(onClick = { handleBack() })
                },
                actions = {
                    IconButton(onClick = {
                        Haptic.click(context)
                        editMode = !editMode
                    }) {
                        Icon(
                            imageVector = if (editMode) Icons.Filled.Visibility else Icons.Filled.EditNote,
                            contentDescription = if (editMode) "Режим просмотра" else "Исправить результаты",
                            tint = if (editMode) Color(0xFFA6FF00) else Color.White
                        )
                    }
                    IconButton(onClick = {
                        Haptic.click(context)
                        showDelete = true
                    }) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = Color(0xFFFF5252))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->
        val w = workout
        if (w == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFA6FF00))
            }
            return@Scaffold
        }

        val dayName = remember(w.workout.dayOfWeek) { Fmt.dayName(w.workout.dayOfWeek) }
        val dateText = remember(w.workout.dateEpochDay) {
            val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
            LocalDate.ofEpochDay(w.workout.dateEpochDay).format(formatter)
        }

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Плашка режима вынесена перед карточкой с названием тренировки
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (editMode) Color(0xFF2A2E1A) else Color(0xFF242732))
                            .border(BorderStroke(1.dp, Color(0xFFA6FF00)), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (editMode) "Режим редактирования" else "Режим просмотра",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Карточка с главной информацией о тренировке
            item {
                DetailCard {
                    Text(
                        text = w.workout.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(8.dp))

                    // День и дата
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA6FF00)
                        )
                        Text(
                            text = ", $dateText",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA0A7B8)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Статистика по упражнениям и подходам с белой окантовкой
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val exercisesCount = w.exercises.size
                        val doneSetsCount = w.doneSets()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$exercisesCount ${Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$doneSetsCount ${Fmt.plural(doneSetsCount, "подход", "подхода", "подходов")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // Прямоугольник заметки с заметной золотой окантовкой и БЕЛЫМ текстом
                    if (w.workout.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.5.dp, Color(0xFFFFB300)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Заметки",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFB300)
                                )
                                Text(
                                    text = w.workout.description,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Упражнения и подходы
            items(w.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { item ->
                DetailCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.exercise.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(Modifier.width(8.dp))

                        // План зафиксирован справа сверху: "В планах ... подх."
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color(0xFF333644)), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "В планах ${item.exercise.plannedSets} подх.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA0A7B8)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val sets = item.sets.sortedBy { it.setNumber }
                    if (sets.isEmpty()) {
                        Text(
                            text = "Подходы пока не выполнены",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA0A7B8),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sets.forEach { s ->
                                val isW = s.isWarmup
                                val numText = if (isW) "Р${s.setNumber}" else "№ ${s.setNumber}"
                                val repsTextColor = if (isW) Color(0xFFA0A7B8) else Color.White
                                val weightTextColor = if (isW) Color(0xFFA0A7B8) else Color(0xFFA6FF00)
                                val textWeight = if (isW) FontWeight.Medium else FontWeight.ExtraBold

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF242732))
                                        .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                        .then(if (editMode) Modifier.clickable {
                                            Haptic.click(context)
                                            editingSet = s
                                        } else Modifier)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Номер подхода
                                        Text(
                                            text = numText,
                                            fontSize = 12.sp,
                                            fontWeight = if (isW) FontWeight.Normal else FontWeight.Bold,
                                            color = Color(0xFFA0A7B8),
                                            modifier = Modifier.width(36.dp)
                                        )

                                        if (settings.swapWeightReps) {
                                            // Сначала вес
                                            if (s.weight != null && s.weight > 0) {
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text(
                                                        text = Fmt.weight(s.weight) ?: "",
                                                        fontSize = 20.sp,
                                                        fontWeight = textWeight,
                                                        color = weightTextColor
                                                    )
                                                    Spacer(Modifier.width(3.dp))
                                                    Text(
                                                        text = "кг",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = repsTextColor,
                                                        modifier = Modifier.padding(bottom = 0.5.dp)
                                                    )
                                                }

                                                Spacer(Modifier.width(6.dp))

                                                // Прямая линия на ВСЮ ВЫСОТУ карточки
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .fillMaxHeight()
                                                        .background(Color.White.copy(alpha = 0.35f))
                                                )

                                                Spacer(Modifier.width(2.dp))
                                            }

                                            // Затем повторения
                                            Box(
                                                modifier = Modifier
                                                    .width(78.dp)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Box(
                                                        modifier = Modifier.width(36.dp),
                                                        contentAlignment = Alignment.BottomStart
                                                    ) {
                                                        Text(
                                                            text = "${s.reps}",
                                                            fontSize = 19.sp,
                                                            fontWeight = textWeight,
                                                            color = repsTextColor
                                                        )
                                                    }
                                                    Text(
                                                        text = if (s.isTimeBased) "сек" else "повт.",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = repsTextColor,
                                                        modifier = Modifier.padding(bottom = 0.5.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Сначала повторения (Стандартно)
                                            Box(
                                                modifier = Modifier
                                                    .width(78.dp)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Box(
                                                        modifier = Modifier.width(36.dp),
                                                        contentAlignment = Alignment.BottomStart
                                                    ) {
                                                        Text(
                                                            text = "${s.reps}",
                                                            fontSize = 19.sp,
                                                            fontWeight = textWeight,
                                                            color = repsTextColor
                                                        )
                                                    }
                                                    Text(
                                                        text = if (s.isTimeBased) "сек" else "повт.",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = repsTextColor,
                                                        modifier = Modifier.padding(bottom = 0.5.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(2.dp))

                                            // Прямая линия на ВСЮ ВЫСОТУ карточки вплотную
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .fillMaxHeight()
                                                    .background(Color.White.copy(alpha = 0.35f))
                                            )

                                            Spacer(Modifier.width(6.dp))

                                            // Затем вес
                                            if (s.weight != null && s.weight > 0) {
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text(
                                                        text = Fmt.weight(s.weight) ?: "",
                                                        fontSize = 20.sp,
                                                        fontWeight = textWeight,
                                                        color = weightTextColor
                                                    )
                                                    Spacer(Modifier.width(3.dp))
                                                    Text(
                                                        text = "кг",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = repsTextColor,
                                                        modifier = Modifier.padding(bottom = 0.5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (editMode) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Изменить подход",
                                            tint = Color(0xFFA6FF00),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Блок кнопок в конце: слева Закрепить (белая обводка), по центру Повторить (с зеленым краем), справа Изменить (белая обводка)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Левее: Закрепить / Открепить (белая обводка)
                    Button(
                        onClick = {
                            Haptic.click(context)
                            vm.togglePin()
                            hasChanges = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF242732),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = if (pinned) Color(0xFFFFB300) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (pinned) "Открепить" else "Закрепить",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // По центру: Повторить (оформлена с зеленым краем #A6FF00)
                    Button(
                        onClick = {
                            Haptic.click(context)
                            vm.repeat(onRepeated)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E281E),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFFA6FF00)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color(0xFFA6FF00), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Повторить",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // Справа: Изменить (белая обводка)
                    Button(
                        onClick = {
                            Haptic.click(context)
                            onEdit()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF242732),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Изменить",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Подтверждение при выходе: "Хотите применить изменения?"
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            containerColor = Color(0xFF1B1D25),
            title = {
                Text(
                    text = "Хотите применить изменения?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF242732),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Да", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        onBack()
                    }
                ) {
                    Text("Нет", color = Color(0xFFA0A7B8), fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Удалить тренировку?",
            text = "Тренировка и все её результаты будут удалены без возможности восстановления.",
            onConfirm = { showDelete = false; vm.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }

    editingSet?.let { set ->
        SetEditDialog(
            set = set,
            onSave = { r, wt, isW ->
                vm.updateSet(set, r, wt, isW)
                editingSet = null
                hasChanges = true
            },
            onDelete = {
                vm.deleteSet(set)
                editingSet = null
                hasChanges = true
            },
            onDismiss = { editingSet = null }
        )
    }
}
