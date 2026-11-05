package com.dentrain.ui.records

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.ExerciseCatalog
import com.dentrain.data.RecordEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.RecordCelebrationDialog
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class RecordSort { DATE, NAME }

class RecordsViewModel(private val repo: WorkoutRepository) : ViewModel() {
    private val all = repo.allRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filterName = MutableStateFlow<String?>(null)
    val sourceFilter = MutableStateFlow(0) // 0 = Все, 1 = Из тренировок, 2 = Ручной ввод
    val repRangeFilter = MutableStateFlow("Все") // "Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+"
    val sort = MutableStateFlow(RecordSort.DATE)

    val names: StateFlow<List<String>> = all.map { list -> list.map { it.exerciseName }.distinct().sortedBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visible: StateFlow<List<RecordEntity>> = combine(all, filterName, sourceFilter, repRangeFilter, sort) { list, fName, src, repRange, s ->
        var filtered = list
        if (src == 1) filtered = filtered.filter { it.isFromWorkout }
        else if (src == 2) filtered = filtered.filter { !it.isFromWorkout }

        if (fName != null) {
            filtered = filtered.filter { it.exerciseName.equals(fName, ignoreCase = true) }
        }

        when (repRange) {
            "1-3" -> filtered = filtered.filter { it.reps in 1..3 }
            "3-5" -> filtered = filtered.filter { it.reps in 3..5 }
            "5-8" -> filtered = filtered.filter { it.reps in 5..8 }
            "8-12" -> filtered = filtered.filter { it.reps in 8..12 }
            "12-15" -> filtered = filtered.filter { it.reps in 12..15 }
            "15+" -> filtered = filtered.filter { it.reps >= 15 }
        }

        when (s) {
            RecordSort.DATE -> filtered.sortedByDescending { it.dateEpochDay }
            RecordSort.NAME -> filtered.sortedWith(compareBy<RecordEntity> { it.exerciseName.lowercase() }.thenByDescending { it.dateEpochDay })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(id: Long, name: String, weightText: String, repsText: String, epochDay: Long, comment: String): String? {
        if (name.isBlank()) return "Введите название упражнения"
        val weight = Fmt.parseWeight(weightText)
        if (weight == null || weight < 0) return "Вес — число не меньше 0"
        val reps = repsText.trim().toIntOrNull()
        if (reps == null || reps < 1) return "Повторений должно быть не меньше 1"

        viewModelScope.launch {
            repo.saveOrUpdateRecord(RecordEntity(id, name.trim(), weight, reps, epochDay, comment.trim(), isFromWorkout = false))
        }
        return null
    }

    fun delete(id: Long) { viewModelScope.launch { repo.deleteRecord(id) } }
}

@Composable
fun RecordsScreen(onSettings: () -> Unit) {
    val vm = appViewModel { RecordsViewModel(it.repository) }
    val records by vm.visible.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val filterName by vm.filterName.collectAsStateWithLifecycle()
    val sourceFilter by vm.sourceFilter.collectAsStateWithLifecycle()
    val repRangeFilter by vm.repRangeFilter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<RecordEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecordEntity?>(null) }
    var celebrationData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(names, filterName) {
        if (filterName != null && names.none { it.equals(filterName, true) }) vm.filterName.value = null
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GradientButton("Добавить рекорд", {
                    Haptic.click(context)
                    creating = true
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            }

            // Переключатель источника рекордов: Все / Из тренировок / Ручные
            item {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = sourceFilter == 0,
                        onClick = { Haptic.click(context); vm.sourceFilter.value = 0 },
                        label = { Text("Все", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FilterChip(
                        selected = sourceFilter == 1,
                        onClick = { Haptic.click(context); vm.sourceFilter.value = 1 },
                        label = { Text("Тренировки", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FilterChip(
                        selected = sourceFilter == 2,
                        onClick = { Haptic.click(context); vm.sourceFilter.value = 2 },
                        label = { Text("Вручную", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Фильтр по диапазону повторений
            item {
                Text("Диапазон повторений:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val repRanges = listOf("Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(repRanges) { range ->
                        FilterChip(
                            selected = repRangeFilter == range,
                            onClick = {
                                Haptic.click(context)
                                vm.repRangeFilter.value = range
                            },
                            label = { Text(range, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (names.isNotEmpty()) {
                item {
                    Text("Фильтр по упражнению", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filterName == null, onClick = { Haptic.click(context); vm.filterName.value = null }, label = { Text("Все", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                        items(names) { n -> FilterChip(selected = filterName.equals(n, true), onClick = { Haptic.click(context); vm.filterName.value = n }, label = { Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
                    }
                }
            }

            if (filterName != null && records.size >= 2) item { ProgressChart(records) }

            if (records.isEmpty()) {
                item { EmptyState(Icons.Filled.EmojiEvents, "Рекордов не найдено", "Попробуйте изменить выбранные фильтры повторений или источника") }
            } else {
                items(records, key = { it.id }) { r ->
                    ZCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(r.exerciseName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (r.isFromWorkout) {
                                        Text("🏋️‍♂️", fontSize = 12.sp)
                                    }
                                }
                                Text("Вес: ${Fmt.weight(r.weight)} кг", fontWeight = FontWeight.SemiBold)
                                Text("Повторения: ${r.reps}")
                                Text("Дата: ${Fmt.shortDate(r.dateEpochDay)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { Haptic.click(context); editing = r }) { Icon(Icons.Filled.Edit, "Изменить") }
                            IconButton(onClick = { Haptic.click(context); deleting = r }) { Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    celebrationData?.let { (name, wt, reps) ->
        RecordCelebrationDialog(
            exerciseName = name,
            weightText = wt,
            repsText = reps,
            onDismiss = { celebrationData = null }
        )
    }

    if (creating) {
        RecordDialog(
            record = null,
            vm = vm,
            onSuccess = { name, wt, reps ->
                Haptic.success(context)
                celebrationData = Triple(name, wt, reps)
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let {
        RecordDialog(
            record = it,
            vm = vm,
            onSuccess = { name, wt, reps ->
                Haptic.success(context)
                celebrationData = Triple(name, wt, reps)
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { r ->
        ConfirmDialog(
            "Удалить рекорд?", "«${r.exerciseName}», ${Fmt.weight(r.weight)} кг × ${r.reps}",
            onConfirm = { Haptic.click(context); vm.delete(r.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

/** Диалоговое окно добавления/редактирования рекорда с кликабельной библиотекой упражнений. */
@Composable
private fun RecordDialog(
    record: RecordEntity?,
    vm: RecordsViewModel,
    onSuccess: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(record?.exerciseName ?: "") }
    var weight by remember { mutableStateOf(Fmt.weight(record?.weight) ?: "") }
    var reps by remember { mutableStateOf(record?.reps?.toString() ?: "") }
    var date by remember { mutableLongStateOf(record?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var comment by remember { mutableStateOf(record?.comment ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf("Все") }

    val catalogExercises = remember(selectedCategory) {
        if (selectedCategory == "Все") ExerciseCatalog.defaultExercises
        else ExerciseCatalog.defaultExercises.filter { it.category == selectedCategory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "Новый рекорд" else "Изменить рекорд") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Упражнение") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Каталог мышц
                Text("Выбрать из каталога (по клику):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ExerciseCatalog.categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { Haptic.click(context); selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                // Быстрые карточки упражнений из каталога
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(catalogExercises) { item ->
                        FilterChip(
                            selected = name == item.name,
                            onClick = {
                                Haptic.click(context)
                                name = item.name
                            },
                            label = { Text("✏️ " + item.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        weight, { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                        label = { Text("Вес, кг") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    OutlinedTextField(
                        reps, { reps = it.filter(Char::isDigit).take(3) },
                        label = { Text("Повторения") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                DateField(date, { date = it }, Modifier.fillMaxWidth())
                OutlinedTextField(comment, { comment = it.take(200) }, label = { Text("Комментарий (необязательно)") }, modifier = Modifier.fillMaxWidth())
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.save(record?.id ?: 0L, name, weight, reps, date, comment)
                if (err == null) {
                    onSuccess(name, weight, reps)
                    onDismiss()
                } else error = err
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Линия изменения рабочего веса по датам. Точки — настоящие записи пользователя. */
@Composable
private fun ProgressChart(records: List<RecordEntity>) {
    val points = records.sortedBy { it.dateEpochDay }
    val minW = points.minOf { it.weight }
    val maxW = points.maxOf { it.weight }
    val brush = LocalGradient.current.brush
    val dot = MaterialTheme.colorScheme.primary
    ZCard {
        Text("Динамика рабочего веса", fontWeight = FontWeight.Bold)
        Text("От ${Fmt.weight(minW)} до ${Fmt.weight(maxW)} кг · записей: ${points.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val pad = 12.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            val range = (maxW - minW).takeIf { it > 0 } ?: 1.0
            fun pos(i: Int): Offset {
                val x = pad + w * i / (points.size - 1)
                val y = if (maxW == minW) size.height / 2 else pad + h * (1f - ((points[i].weight - minW) / range).toFloat())
                return Offset(x, y)
            }
            val path = Path().apply { points.indices.forEach { i -> pos(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
            drawPath(path, brush, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            points.indices.forEach { i -> drawCircle(dot, 5.dp.toPx(), pos(i)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Fmt.shortDate(points.first().dateEpochDay), style = MaterialTheme.typography.labelSmall)
            Text(Fmt.shortDate(points.last().dateEpochDay), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton("Добавить рекорд", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            ZCard {
                Text("Жим штанги лежа", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Вес: 100 кг")
                Text("Повторения: 10")
            }
        }
    }
}
