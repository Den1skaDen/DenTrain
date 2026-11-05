package com.dentrain.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryViewModel(private val repo: WorkoutRepository) : ViewModel() {
    private val all = repo.completed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val day = MutableStateFlow<Int?>(LocalDate.now().dayOfWeek.value)
    val newestFirst = MutableStateFlow(true)

    val dayCounts: StateFlow<Map<Int, Int>> = all.map { list -> list.groupingBy { it.workout.dayOfWeek }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val items: StateFlow<List<WorkoutWithExercises>> = combine(all, day, newestFirst) { list, d, newest ->
        val filtered = if (d == null) list else list.filter { it.workout.dayOfWeek == d }
        if (newest) filtered.sortedWith(compareByDescending<WorkoutWithExercises> { it.workout.dateEpochDay }.thenByDescending { it.workout.id })
        else filtered.sortedWith(compareBy<WorkoutWithExercises> { it.workout.dateEpochDay }.thenBy { it.workout.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedIds: StateFlow<Set<Long>> = repo.pinnedIds.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun selectDay(d: Int) { day.value = if (d == 0 || day.value == d) null else d }
    fun toggleSort() { newestFirst.value = !newestFirst.value }
    fun togglePin(id: Long, pinned: Boolean) { viewModelScope.launch { repo.setPinned(id, !pinned) } }
}

@Composable
fun HistoryScreen(onOpenDetail: (Long) -> Unit, onSettings: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it.repository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val counts by vm.dayCounts.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val newestFirst by vm.newestFirst.collectAsStateWithLifecycle()
    val pinned by vm.pinnedIds.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        DayCard("Все", counts.values.sum(), day == null) { vm.selectDay(0) }
                    }
                    itemsIndexed(Fmt.dayNames) { i, name ->
                        DayCard(Fmt.dayShort[i], counts[i + 1] ?: 0, day == i + 1) { vm.selectDay(i + 1) }
                    }
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
                    TextButton(onClick = vm::toggleSort) {
                        Icon(Icons.Filled.SwapVert, null)
                        Text(if (newestFirst) " Сначала новые" else " Сначала старые")
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
                    val longDate = remember(w.workout.dateEpochDay, w.workout.dayOfWeek) { Fmt.longDate(w.workout) }
                    val summaryText = remember(w.workout.id, w.exercises.size, w.workout.completedAt) { Fmt.summary(w) }

                    ZCard(onClick = { onOpenDetail(w.workout.id) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isPinned) {
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
                                        onClick = { vm.togglePin(w.workout.id, true) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = "Открепить",
                                            tint = Color(0xFFFFB300)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.workout.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(longDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(summaryText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (!isPinned) {
                                    IconButton(onClick = { vm.togglePin(w.workout.id, false) }) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = "Закрепить тренировку",
                                            tint = MaterialTheme.colorScheme.outline
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

@Composable
private fun DayCard(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) LocalGradient.current.brush else SolidColor(MaterialTheme.colorScheme.surface)
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.width(68.dp).clip(shape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("$count", color = fg, fontSize = TextUnit(22f, TextUnitType.Sp), fontWeight = FontWeight.ExtraBold)
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZCard {
                Text("Силовая тренировка", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("19 октября 2026 г.")
            }
        }
    }
}
