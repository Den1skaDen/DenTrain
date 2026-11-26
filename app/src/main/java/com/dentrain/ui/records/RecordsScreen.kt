package com.dentrain.ui.records

import com.dentrain.util.userMessage
import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.OperationErrorDialog
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.dentrain.ui.icons.filled.FilterList
import com.dentrain.ui.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import com.dentrain.ui.icons.outlined.CalendarToday
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.BETTER_RECORD_EXISTS
import com.dentrain.data.RecordEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutScreenCache
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.EmptyState
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.RecordCelebrationDialog
import com.dentrain.ui.components.MetricNumber
import com.dentrain.ui.components.rememberMetricNumberWidth
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class RecordSort { DATE, NAME }

class RecordsViewModel(private val repo: WorkoutRepository, cache: WorkoutScreenCache) : ViewModel() {
    private val all = cache.records
    val filterName = MutableStateFlow<String?>(null)
    val showFavoritesOnly = MutableStateFlow(false)
    val repRangeFilter = MutableStateFlow("Все") // "Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+"
    val sort = MutableStateFlow(RecordSort.DATE)

    val names: StateFlow<List<String>> = all.map { list -> list.map { it.exerciseName }.distinct().sortedBy { it.lowercase() } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visible: StateFlow<List<RecordEntity>> = combine(all, filterName, repRangeFilter, sort, showFavoritesOnly) { list, fName, repRange, s, favsOnly ->
        var filtered = list

        if (favsOnly) {
            filtered = filtered.filter { it.isFavorite }
        }

        if (fName != null) {
            filtered = filtered.filter { it.exerciseName.equals(fName, ignoreCase = true) }
        }

        val reps = repetitionRange(repRange)
        if (reps != null) filtered = filtered.filter { it.reps in reps }

        when (s) {
            RecordSort.DATE -> filtered
            RecordSort.NAME -> filtered.sortedWith(compareBy<RecordEntity> { it.exerciseName.lowercase() }.thenByDescending { it.dateEpochDay })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val progress = combine(filterName, repRangeFilter, showFavoritesOnly, ::ProgressFilter)
        .flatMapLatest { filter ->
            val name = filter.exerciseName?.trim()
            if (name.isNullOrEmpty()) {
                flowOf(ProgressSeries(filter, emptyList()))
            } else {
                val sets = if (filter.favoritesOnly) flowOf(emptyList())
                    else repo.observeWorkingSetsForProgress(name)
                combine(all, sets) { records, workingSets ->
                    ProgressSeries(filter, dailyProgress(name, records, workingSets, filter))
                }.onStart { emit(ProgressSeries(filter, emptyList())) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    suspend fun save(id: Long, name: String, weightText: String, repsText: String, epochDay: Long, comment: String): String? {
        if (name.isBlank()) return "Введите название упражнения"
        val weight = Fmt.parseWeight(weightText)
        if (weight == null || !weight.isFinite() || weight < 0) return "Вес — число не меньше 0"
        val reps = repsText.trim().toIntOrNull()
        if (reps == null || reps < 1) return "Повторений должно быть не меньше 1"

        val record = RecordEntity(id, name.trim(), weight, reps, epochDay, comment.trim())
        return try {
            if (id != 0L) repo.editRecord(record)
            else if (repo.saveOrUpdateRecord(record) == null) BETTER_RECORD_EXISTS
            else null
        } catch (failure: Exception) {
            failure.userMessage("Не удалось сохранить рекорд.")
        }
    }

    fun delete(id: Long) { perform("Не удалось удалить рекорд.") { repo.deleteRecord(id) } }

    fun toggleFavorite(record: RecordEntity) { perform("Не удалось обновить избранное.") { repo.toggleRecordFavorite(record) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordsScreen(vm: RecordsViewModel, onSettings: () -> Unit) {
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val records by vm.visible.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val filterName by vm.filterName.collectAsStateWithLifecycle()
    val repRangeFilter by vm.repRangeFilter.collectAsStateWithLifecycle()
    val favoritesOnly by vm.showFavoritesOnly.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val chart = progress?.takeIf { it.filter == ProgressFilter(filterName, repRangeFilter, favoritesOnly) }
    val context = LocalContext.current

    var editing by remember { mutableStateOf<RecordEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecordEntity?>(null) }
    var celebrationData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }

    val weightWidth = rememberMetricNumberWidth(3, 26.sp, FontWeight.ExtraBold, 26.sp)
    val repsWidth = rememberMetricNumberWidth(2, 26.sp, FontWeight.ExtraBold, 26.sp)

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
                isFavoritesOnly = favoritesOnly,
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
                if (chart != null && chart.points.isNotEmpty()) {
                    item(key = "record_progress", contentType = "chart") { ProgressChart(chart) }
                }

                if (records.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(Icons.Filled.EmojiEvents, "Рекордов не найдено", "Попробуйте изменить фильтры")
                    }
                } else {
                    items(records, key = { it.id }, contentType = { "record" }) { r ->
                        RecordCard(
                            record = r,
                            weightWidth = weightWidth,
                            repsWidth = repsWidth,
                            onEdit = { editing = r },
                            onFavoriteToggle = { vm.toggleFavorite(r) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        ConfirmDialog(
            title = "Фильтр повторений",
            confirmText = "Закрыть",
            destructive = false,
            dismissText = null,
            onConfirm = { showFilterDialog = false },
            onDismiss = { showFilterDialog = false }
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 4
            ) {
                for (range in listOf("Все", "1-3", "3-5", "5-8", "8-12", "12-15", "15+")) {
                    val selected = repRangeFilter == range
                    FilterChip(
                        selected = selected,
                        onClick = {
                            Haptic.click(context)
                            vm.repRangeFilter.value = range
                            showFilterDialog = false
                        },
                        label = {
                            Text(
                                range.replace('-', '–'),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DenTrainPalette.RaisedPanel,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = DenTrainPalette.Lime.copy(alpha = 0.15f),
                            selectedLabelColor = DenTrainPalette.Lime
                        ),
                        border = BorderStroke(1.dp, if (selected) DenTrainPalette.Lime else DenTrainPalette.Border)
                    )
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
            exerciseNames = names,
            onSuccess = { name, wt, reps ->
                Haptic.success(context)
                celebrationData = Triple(name, wt, reps)
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { record ->
        RecordDialog(
            record = record,
            vm = vm,
            exerciseNames = names,
            onDelete = { deleting = record },
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
            onConfirm = {
                Haptic.click(context)
                vm.delete(r.id)
                editing = null
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun RecordDialog(
    record: RecordEntity?,
    vm: RecordsViewModel,
    exerciseNames: List<String>,
    onSuccess: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(record?.exerciseName ?: "") }
    var weight by rememberSaveable { mutableStateOf(Fmt.weight(record?.weight) ?: "") }
    var reps by rememberSaveable { mutableStateOf(record?.reps?.toString() ?: "") }
    var date by rememberSaveable { mutableLongStateOf(record?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var comment by rememberSaveable { mutableStateOf(record?.comment ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showExercises by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val suggestions = remember(exerciseNames, name) {
        exerciseNames.filter { it.contains(name.trim(), ignoreCase = true) }
    }
    val scope = rememberCoroutineScope()
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = DenTrainPalette.RaisedPanel,
        unfocusedContainerColor = DenTrainPalette.RaisedPanel,
        focusedBorderColor = DenTrainPalette.Lime,
        unfocusedBorderColor = DenTrainPalette.Border,
        focusedLabelColor = DenTrainPalette.Lime,
        unfocusedLabelColor = DenTrainPalette.MutedText
    )

    ConfirmDialog(
        title = if (record == null) "Новый рекорд" else "Изменить рекорд",
        confirmText = "Сохранить",
        confirmEnabled = !saving,
        destructive = false,
        onDismiss = { if (!saving) onDismiss() },
        titleAction = onDelete?.let { delete ->
            {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        delete()
                    },
                    enabled = !saving
                ) {
                    Icon(Icons.Filled.Delete, "Удалить рекорд", tint = DenTrainPalette.Error)
                }
            }
        },
        onConfirm = {
            saving = true
            val submittedName = name
            val submittedWeight = weight
            val submittedReps = reps
            val submittedDate = date
            val submittedComment = comment
            scope.launch {
                try {
                    error = vm.save(record?.id ?: 0L, submittedName, submittedWeight, submittedReps, submittedDate, submittedComment)
                    if (error == null) {
                        if (record == null ||
                            !record.exerciseName.trim().equals(submittedName.trim(), ignoreCase = true) ||
                            Fmt.parseWeight(submittedWeight)?.let { it > record.weight } == true ||
                            submittedReps.toIntOrNull()?.let { it > record.reps } == true
                        ) {
                            onSuccess(submittedName.trim(), submittedWeight, submittedReps)
                        }
                        onDismiss()
                    }
                } finally {
                    saving = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.replace('\n', ' ').take(maxOf(60, record?.exerciseName?.length ?: 0))
                    showExercises = true
                },
                label = { Text("Упражнение") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().onFocusChanged { showExercises = it.isFocused },
                shape = fieldShape,
                colors = fieldColors
            )

            if (showExercises && suggestions.isNotEmpty()) {
                ExerciseSuggestions(suggestions, Modifier.fillMaxWidth(), selected = name) { exercise ->
                    Haptic.click(context)
                    name = exercise
                    showExercises = false
                    focusManager.clearFocus()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                    label = { Text("Вес, кг") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(maxOf(3, record?.reps?.toString()?.length ?: 0)) },
                    label = { Text("Повторы") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
            }
            DateField(date, { date = it }, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(200) },
                label = { Text("Комментарий") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors
            )
            ErrorText(error)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordCard(
    record: RecordEntity,
    weightWidth: Dp,
    repsWidth: Dp,
    onEdit: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val limeColor = DenTrainPalette.Lime
    val labelColor = DenTrainPalette.MutedText
    val datePillBg = DenTrainPalette.RaisedPanel
    val datePillBorder = DenTrainPalette.Border
    val weightText = remember(record.weight) { Fmt.weight(record.weight) ?: "0" }
    val dateText = remember(record.dateEpochDay) { Fmt.shortDate(record.dateEpochDay) }
    val editRecord by rememberUpdatedState(onEdit)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DenTrainPalette.Panel, shape)
            .border(1.dp, DenTrainPalette.SubtleBorder, shape)
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
                        editRecord()
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
                    maxLines = 2,
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.Bottom) {
                    MetricNumber(
                        text = weightText,
                        minDigits = 3,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = limeColor,
                        lineHeight = 26.sp,
                        minWidth = weightWidth
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

                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.Bottom) {
                    MetricNumber(
                        text = record.reps.toString(),
                        minDigits = 2,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 26.sp,
                        minWidth = repsWidth
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = Fmt.plural(record.reps, "повторение", "повторения", "повторений"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .background(datePillBg, CircleShape)
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
                            text = dateText,
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

private const val VisibleExerciseSuggestions = 5
private val exerciseSuggestionHeight = 48.dp

@Composable
private fun ExerciseSuggestions(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val shape = RoundedCornerShape(16.dp)
    val showScrollbar = suggestions.size > VisibleExerciseSuggestions
    Card(
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.dp, DenTrainPalette.Border),
        colors = CardDefaults.cardColors(containerColor = DenTrainPalette.RaisedPanel),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .heightIn(max = exerciseSuggestionHeight * VisibleExerciseSuggestions)
                .then(if (showScrollbar) Modifier.exerciseSuggestionScrollbar(listState) else Modifier),
            contentPadding = PaddingValues(start = 4.dp, end = if (showScrollbar) 14.dp else 4.dp)
        ) {
            items(suggestions, key = { it }) { exercise ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(exerciseSuggestionHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(exercise) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        exercise,
                        color = if (exercise == selected) DenTrainPalette.Lime else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Modifier.exerciseSuggestionScrollbar(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val rowHeight = exerciseSuggestionHeight.roundToPx()
    val contentHeight = state.layoutInfo.totalItemsCount * rowHeight.toFloat()
    val scrollRange = contentHeight - size.height
    val trackHeight = size.height - 8.dp.toPx()
    if (scrollRange > 0f && trackHeight > 0f) {
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = (trackHeight * size.height / contentHeight)
            .coerceIn(minOf(16.dp.toPx(), trackHeight), trackHeight)
        val scrollOffset = state.firstVisibleItemIndex * rowHeight + state.firstVisibleItemScrollOffset
        val progress = (scrollOffset / scrollRange).coerceIn(0f, 1f)
        drawRoundRect(
            color = DenTrainPalette.MutedText,
            topLeft = Offset(
                size.width - thumbWidth - 5.dp.toPx(),
                4.dp.toPx() + progress * (trackHeight - thumbHeight)
            ),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2)
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
                    .background(DenTrainPalette.RaisedPanel, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(Icons.Filled.Add, "Добавить рекорд", tint = Color.White)
            }
        }

        // Search Field (BasicTextField для точного контроля высоты и плавной анимации)
        val searchBorderColor by animateColorAsState(
            targetValue = if (isFocused) DenTrainPalette.Lime else DenTrainPalette.Border,
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
                    .background(DenTrainPalette.Panel, RoundedCornerShape(24.dp))
                    .border(1.dp, searchBorderColor, RoundedCornerShape(24.dp))
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused && text.isNotBlank()) expanded = true
                    },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(DenTrainPalette.Lime),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Поиск рекордов",
                            tint = DenTrainPalette.MutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (text.isEmpty()) {
                                Text("Поиск", color = DenTrainPalette.MutedText, fontSize = 15.sp, maxLines = 1)
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
                    offset = IntOffset(0, with(LocalDensity.current) { 52.dp.roundToPx() }),
                    properties = PopupProperties(focusable = false)
                ) {
                    val density = LocalDensity.current
                    val widthDp = with(density) { textFieldWidth.toDp() }
                    ExerciseSuggestions(suggestions, Modifier.width(widthDp)) { name ->
                        text = name
                        expanded = false
                        onQueryChange(name)
                        focusManager.clearFocus()
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
                val favBtnBg = DenTrainPalette.RaisedPanel
                val favBtnBorder = if (isFavoritesOnly) Color(0xFF00B0FF).copy(alpha = 0.5f) else DenTrainPalette.Border
                
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
                                tint = DenTrainPalette.MutedText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // [Filter]
                val filterBtnBg = if (isFilterActive) DenTrainPalette.Lime.copy(alpha = 0.15f) else DenTrainPalette.RaisedPanel
                val filterBtnBorder = if (isFilterActive) DenTrainPalette.Lime else DenTrainPalette.Border
                val filterIconColor = if (isFilterActive) DenTrainPalette.Lime else Color.White

                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(filterBtnBg, CircleShape)
                        .border(1.dp, filterBtnBorder, CircleShape)
                ) {
                    Icon(Icons.Filled.FilterList, "Фильтр повторений", tint = filterIconColor)
                }
            }
        }
    }
}
