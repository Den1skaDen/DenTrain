package com.dentrain.ui.history

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.OperationErrorDialog
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutListEntry
import com.dentrain.data.WorkoutScreenCache
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.WeekCalendarRow
import com.dentrain.ui.components.WorkoutListCount
import com.dentrain.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HistoryViewModel(private val repo: WorkoutRepository, cache: WorkoutScreenCache) : ViewModel() {
    private val all = cache.history
    val day = MutableStateFlow<Int?>(LocalDate.now().dayOfWeek.value)
    val newestFirst = MutableStateFlow(true)

    val calendar = cache.calendar

    val items: StateFlow<List<WorkoutListEntry>> = combine(all, day, newestFirst) { list, d, newest ->
        val filtered = if (d == null) list else list.filter { it.workout.dayOfWeek == d }
        if (newest) filtered else filtered.asReversed()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pinnedIds: StateFlow<Set<Long>> = repo.pinnedIds.map { it.toSet() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    fun selectDay(d: Int) { day.value = if (d == 0 || day.value == d) null else d }
    fun toggleSort() { newestFirst.value = !newestFirst.value }
    fun togglePin(id: Long, targetPinned: Boolean) { perform("Не удалось обновить закрепление тренировки.") { repo.setPinned(id, targetPinned) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpenDetail: (Long) -> Unit, onSettings: () -> Unit) {
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val items by vm.items.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val newestFirst by vm.newestFirst.collectAsStateWithLifecycle()
    val pinned by vm.pinnedIds.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val completedDates = calendar.completedDates

    val today = remember { LocalDate.now() }
    val selectedDate = remember(day) {
        if (day == null) today
        else today.minusDays((today.dayOfWeek.value - day!!).toLong())
    }

    val topPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "title", contentType = "title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = topPadding + 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Workouts", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // 7 дней недели в одну линию (как на фото 1)
            item(key = "calendar", contentType = "calendar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Расписание недели",
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val isAllDaysActive = day == null
                        OutlinedButton(
                            onClick = { vm.selectDay(0) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isAllDaysActive) 1.5.dp else 1.dp,
                                if (isAllDaysActive) DenTrainPalette.Lime else DenTrainPalette.Border
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isAllDaysActive) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel,
                                contentColor = if (isAllDaysActive) DenTrainPalette.Lime else DenTrainPalette.MutedText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 32.dp)
                        ) {
                            Text(
                                "Все дни",
                                fontSize = 12.sp,
                                fontWeight = if (isAllDaysActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                    WeekCalendarRow(
                        selectedDate = selectedDate,
                        onDateSelected = { date -> vm.selectDay(date.dayOfWeek.value) },
                        completedDates = completedDates,
                        showSelection = day != null,
                        selectionColor = Color(0xFF00B0FF)
                    )
                }
            }

            item(key = "sort", contentType = "sort") {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (day == null) "Все тренировки" else "Тренировки: ${Fmt.dayName(day!!)}",
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = vm::toggleSort,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DenTrainPalette.Panel,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 32.dp).width(148.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (newestFirst) "Сначала новые" else "Сначала старые",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyState(
                        Icons.Filled.History,
                        if (day == null) "История пуста" else "В этот день тренировок нет",
                        "Завершённые тренировки появятся здесь"
                    )
                }
            } else {
                items(items, key = { it.workout.id }, contentType = { "workout" }) { w ->
                    val isPinned = w.workout.id in pinned

                    HistoryWorkoutCard(
                        workout = w,
                        isPinned = isPinned,
                        onClick = { onOpenDetail(w.workout.id) },
                        onTogglePin = { vm.togglePin(w.workout.id, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryWorkoutCard(
    workout: WorkoutListEntry,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: (Boolean) -> Unit
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
    val borderColor = if (isPinned) gold.copy(alpha = 0.3f) else DenTrainPalette.SubtleBorder
    val pinnedBorder = if (isPinned) {
        Modifier.border(2.dp, remember {
            Brush.linearGradient(0f to gold, 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to gold)
        }, shape)
    } else Modifier
    val cardModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(DenTrainPalette.Panel)
        .border(1.dp, borderColor, shape)
        .then(pinnedBorder)
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
                    onClick = { onTogglePin(!isPinned) },
                    modifier = Modifier.size(40.dp) // увеличен размер области нажатия
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = if (isPinned) "Открепить" else "Закрепить",
                        tint = if (isPinned) gold else labelColor,
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
                WorkoutListCount(exercisesCount, Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений"), labelColor)

                // Разделитель
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .height(16.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Подходов
                WorkoutListCount(setsCount, Fmt.plural(setsCount, "подход", "подхода", "подходов"), labelColor)

                Spacer(Modifier.weight(1f))

                // Капсула времени
                if (durationText != null) {
                    Box(
                        modifier = Modifier
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
