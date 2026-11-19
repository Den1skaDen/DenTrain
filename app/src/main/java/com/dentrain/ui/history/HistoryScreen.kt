package com.dentrain.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.WeekCalendarRow
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryViewModel(private val repo: WorkoutRepository) : ViewModel() {
    private val all = repo.completed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val day = MutableStateFlow<Int?>(LocalDate.now().dayOfWeek.value)
    val newestFirst = MutableStateFlow(true)

    val dayCounts: StateFlow<Map<Int, Int>> = all.map { list -> list.groupingBy { it.workout.dayOfWeek }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allCompletedDates: StateFlow<Set<LocalDate>> = all.map { list ->
        list.map { LocalDate.ofEpochDay(it.workout.dateEpochDay) }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val items: StateFlow<List<WorkoutWithExercises>> = combine(all, day, newestFirst) { list, d, newest ->
        val filtered = if (d == null) list else list.filter { it.workout.dayOfWeek == d }
        if (newest) filtered.sortedWith(compareByDescending<WorkoutWithExercises> { it.workout.dateEpochDay }.thenByDescending { it.workout.id })
        else filtered.sortedWith(compareBy<WorkoutWithExercises> { it.workout.dateEpochDay }.thenBy { it.workout.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedIds: StateFlow<Set<Long>> = repo.pinnedIds.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun selectDay(d: Int) { day.value = if (d == 0 || day.value == d) null else d }
    fun toggleSort() { newestFirst.value = !newestFirst.value }
    fun togglePin(id: Long, targetPinned: Boolean) { viewModelScope.launch { repo.setPinned(id, targetPinned) } }
}

@Composable
fun HistoryScreen(onOpenDetail: (Long) -> Unit, onSettings: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it.repository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val newestFirst by vm.newestFirst.collectAsStateWithLifecycle()
    val pinned by vm.pinnedIds.collectAsStateWithLifecycle()
    val completedDates by vm.allCompletedDates.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val selectedDate = remember(day) {
        if (day == null) today
        else today.minusDays((today.dayOfWeek.value - day!!).toLong())
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = topPadding + 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Workouts", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // 7 дней недели в одну линию (как на фото 1)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Расписание недели", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        val isAllDaysActive = day == null
                        OutlinedButton(
                            onClick = { vm.selectDay(0) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isAllDaysActive) 1.5.dp else 1.dp,
                                if (isAllDaysActive) Color(0xFFA6FF00) else Color(0xFF333644)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isAllDaysActive) Color(0xFF242732) else Color(0xFF1B1D25),
                                contentColor = if (isAllDaysActive) Color(0xFFA6FF00) else Color(0xFFA0A7B8)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
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

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (day == null) "Все тренировки" else "Тренировки: ${Fmt.dayName(day!!)}",
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = vm::toggleSort,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1B1D25),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).width(148.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Сначала ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                if (newestFirst) "новые" else "старые",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.History,
                        if (day == null) "История пуста" else "В этот день тренировок нет",
                        "Завершённые тренировки появятся здесь"
                    )
                }
            } else {
                items(items, key = { it.workout.id }) { w ->
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
    workout: WorkoutWithExercises,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: (Boolean) -> Unit
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
    val baseBorder = if (isPinned) BorderStroke(1.dp, weakGold) else BorderStroke(1.dp, borderColor)

    var cardModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(cardBg)
        .border(baseBorder, RoundedCornerShape(16.dp))

    if (isPinned) {
        cardModifier = cardModifier.border(
            width = 2.dp,
            brush = Brush.linearGradient(
                0.0f to gold,
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1.0f to gold
            ),
            shape = RoundedCornerShape(16.dp)
        )
    }

    cardModifier = cardModifier
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

@Composable
private fun DayCard(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val cardBg = Color(0xFF1B1D25)
    val borderColor = Color(0xFF282B36)
    val bg = if (selected) LocalGradient.current.brush else SolidColor(cardBg)
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.width(68.dp).clip(shape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else borderColor, shape)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("$count", color = fg, fontSize = TextUnit(22f, TextUnitType.Sp), fontWeight = FontWeight.ExtraBold)
    }
}
