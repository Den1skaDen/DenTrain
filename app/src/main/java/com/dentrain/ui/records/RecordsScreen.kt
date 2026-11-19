package com.dentrain.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.R
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class RecordSort { DATE, NAME }

class RecordsViewModel(private val repo: WorkoutRepository) : ViewModel() {
    private val all = repo.allRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filterName = MutableStateFlow<String?>(null)
    val showFavoritesOnly = MutableStateFlow(false)
    val repRangeFilter = MutableStateFlow("Все") // "Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+"
    val sort = MutableStateFlow(RecordSort.DATE)

    val names: StateFlow<List<String>> = all.map { list -> list.map { it.exerciseName }.distinct().sortedBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visible: StateFlow<List<RecordEntity>> = combine(all, filterName, repRangeFilter, sort, showFavoritesOnly) { list, fName, repRange, s, favsOnly ->
        var filtered = list

        if (favsOnly) {
            filtered = filtered.filter { it.isFavorite }
        }

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
            repo.saveOrUpdateRecord(RecordEntity(id, name.trim(), weight, reps, epochDay, comment.trim()))
        }
        return null
    }

    fun delete(id: Long) { viewModelScope.launch { repo.deleteRecord(id) } }

    fun toggleFavorite(record: RecordEntity) { viewModelScope.launch { repo.toggleRecordFavorite(record) } }
}

@Composable
fun RecordsScreen(onSettings: () -> Unit) {
    val vm = appViewModel { RecordsViewModel(it.repository) }
    val records by vm.visible.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val filterName by vm.filterName.collectAsStateWithLifecycle()
    val repRangeFilter by vm.repRangeFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<RecordEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecordEntity?>(null) }
    var celebrationData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(names, filterName) {
        // Убрали сброс filterName при вводе нового текста, иначе клавиатура закрывается
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Text(
                text = "Records",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = topPadding + 16.dp, start = 16.dp, end = 16.dp)
            )

            RecordSearchHeader(
                names = names,
                currentQuery = filterName,
                isFavoritesOnly = vm.showFavoritesOnly.collectAsStateWithLifecycle().value,
                isFilterActive = repRangeFilter != "Все",
                onQueryChange = { vm.filterName.value = it },
                onAddClick = { creating = true },
                onFilterClick = { showFilterDialog = true },
                onFavoriteFilterToggle = { vm.showFavoritesOnly.value = !vm.showFavoritesOnly.value }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filterName != null && records.size >= 2) item { ProgressChart(records) }

                if (records.isEmpty()) {
                    item { EmptyState(Icons.Filled.EmojiEvents, "Рекордов не найдено", "Попробуйте изменить фильтры") }
                } else {
                    items(records, key = { it.id }) { r ->
                        RecordCard(
                            record = r,
                            onEdit = { editing = r },
                            onDelete = { deleting = r },
                            onFavoriteToggle = { vm.toggleFavorite(r) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Фильтр повторений", style = MaterialTheme.typography.titleLarge) },
            text = {
                val repRanges = listOf("Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(repRanges) { range ->
                        FilterChip(
                            selected = repRangeFilter == range,
                            onClick = {
                                Haptic.click(context)
                                vm.repRangeFilter.value = range
                                showFilterDialog = false
                            },
                            label = { Text(range, fontSize = 14.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("Закрыть")
                }
            },
            containerColor = Color(0xFF1B1D25),
            titleContentColor = Color.White
        )
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
            recentNames = names,
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
            recentNames = names,
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
    recentNames: List<String>,
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

                // Быстрые карточки упражнений из недавних
                if (recentNames.isNotEmpty()) {
                    Text("Выбрать из недавних (по клику):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(recentNames) { item ->
                            FilterChip(
                                selected = name == item,
                                onClick = {
                                    Haptic.click(context)
                                    name = item
                                },
                                label = { Text("✏️ $item", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCard(
    record: RecordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardBg = Color(0xFF1B1D25)
    val borderColor = Color(0xFF282B36)
    val limeColor = Color(0xFFA6FF00)
    val labelColor = Color(0xFFA0A7B8)
    val datePillBg = Color(0xFF242732)
    val datePillBorder = Color(0xFF333644)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val change = withTimeoutOrNull(2500) {
                        var current: PointerInputChange? = down
                        while (current != null && current.pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            current = event.changes.firstOrNull { it.id == down.id }
                            if (current != null) {
                                val distance = (current.position - down.position).getDistance()
                                if (distance > viewConfiguration.touchSlop) {
                                    break
                                }
                            }
                        }
                        current
                    }
                    if (change == null) {
                        Haptic.click(context)
                        onEdit()
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 1. Название упражнения
                Text(
                    text = record.exerciseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                // Сердечко
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            Haptic.click(context)
                            onFavoriteToggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (record.isFavorite) {
                        // Сине-голубое внутри
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Favorite Filled",
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.size(24.dp)
                        )
                        // Снаружи белая окантовка
                        Icon(
                            imageVector = Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite Outline",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        // Еле видимая окантовка
                        Icon(
                            imageVector = Icons.Filled.FavoriteBorder,
                            contentDescription = "Not Favorite",
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Блок показателей: 80 кг  |  8 повторений   [Дата капсула справа]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Вес + кг (на одной базовой линии)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Fmt.weight(record.weight) ?: "0",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = limeColor,
                        lineHeight = 26.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "кг",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = limeColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Вертикальная черта-разделитель |
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .height(20.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Повторения: 8 повторений
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = record.reps.toString(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 26.sp
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "повторений",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Компактная капсула даты максимально справа
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
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = Fmt.shortDate(record.dateEpochDay),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor
                        )
                    }
                }
            }

            if (record.comment.isNotBlank()) {
                Text(
                    text = record.comment,
                    fontSize = 12.sp,
                    color = labelColor,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton("Добавить рекорд", {}, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            RecordCard(
                record = RecordEntity(
                    id = 1,
                    exerciseName = "Жим штанги лёжа",
                    weight = 80.0,
                    reps = 8,
                    dateEpochDay = LocalDate.of(2024, 6, 14).toEpochDay(),
                    comment = "",
                    isFavorite = true
                ),
                onEdit = {},
                onDelete = {},
                onFavoriteToggle = {}
            )
        }
    }
}

@Composable
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()
    val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val needScrollbar = state.layoutInfo.visibleItemsInfo.size < state.layoutInfo.totalItemsCount
    
    if (needScrollbar) {
        val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight
        drawRoundRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(this.size.width - width.toPx() - 4.dp.toPx(), scrollbarOffsetY),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordSearchHeader(
    names: List<String>,
    currentQuery: String?,
    isFavoritesOnly: Boolean,
    isFilterActive: Boolean,
    onQueryChange: (String?) -> Unit,
    onAddClick: () -> Unit,
    onFilterClick: () -> Unit,
    onFavoriteFilterToggle: () -> Unit
) {
    var text by remember(currentQuery) { mutableStateOf(currentQuery ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var isFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus()
            isFocused = false
            expanded = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding + 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // [+] Добавить
        AnimatedVisibility(
            visible = !isFocused,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF222B3D), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Filled.Add, "Добавить", tint = Color.White)
            }
        }

        // Search Field (BasicTextField для точного контроля высоты и плавной анимации)
        val searchBorderColor by animateColorAsState(
            targetValue = if (isFocused) Color.White else Color(0xFF333644),
            animationSpec = tween(300),
            label = "searchBorder"
        )

        var textFieldWidth by remember { mutableStateOf(0) }

        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    expanded = true
                    if (it.isBlank()) {
                        onQueryChange(null)
                    } else {
                        onQueryChange(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onGloballyPositioned { textFieldWidth = it.size.width }
                    .background(Color(0xFF1B1D25), RoundedCornerShape(24.dp))
                    .border(1.dp, searchBorderColor, RoundedCornerShape(24.dp))
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused && text.isNotBlank()) expanded = true
                    },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFFFFB300)),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Поиск",
                            tint = Color(0xFFA0A7B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (text.isEmpty()) {
                                Text("Найти рекорд", color = Color(0xFFA0A7B8), fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            val suggestions = names.filter { it.contains(text, ignoreCase = true) }
            
            if (expanded && suggestions.isNotEmpty()) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, 150),
                    properties = PopupProperties(focusable = false)
                ) {
                    val density = LocalDensity.current
                    val widthDp = with(density) { textFieldWidth.toDp() }
                    Card(
                        modifier = Modifier
                            .width(widthDp)
                            .heightIn(max = (5 * 48).dp)
                            .border(1.dp, Color(0xFF333644), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222B3D)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .simpleVerticalScrollbar(listState)
                        ) {
                            items(suggestions) { name ->
                                DropdownMenuItem(
                                    text = { Text(name, color = Color.White) },
                                    onClick = {
                                        text = name
                                        expanded = false
                                        onQueryChange(name)
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isFocused,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // [Favorite Filter]
                val favBtnBg = if (isFavoritesOnly) Color(0xFF333D55) else Color(0xFF222B3D)
                val favBtnBorder = if (isFavoritesOnly) Color(0xFF00B0FF).copy(alpha = 0.5f) else Color(0xFF333644)
                
                IconButton(
                    onClick = onFavoriteFilterToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(favBtnBg, CircleShape)
                        .border(1.dp, favBtnBorder, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isFavoritesOnly) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favorite Filled",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite Outline",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = "Not Favorite",
                                tint = Color(0xFFA0A7B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // [Filter]
                val filterBtnBg = if (isFilterActive) Color(0xFF333D55) else Color(0xFF222B3D)
                val filterBtnBorder = if (isFilterActive) Color(0xFF00B0FF).copy(alpha = 0.5f) else Color(0xFF333644)
                val filterIconColor = if (isFilterActive) Color(0xFF00B0FF) else Color.White

                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(filterBtnBg, CircleShape)
                        .border(1.dp, filterBtnBorder, CircleShape)
                ) {
                    Icon(Icons.Filled.FilterList, "Фильтр", tint = filterIconColor)
                }
            }
        }
    }
}
