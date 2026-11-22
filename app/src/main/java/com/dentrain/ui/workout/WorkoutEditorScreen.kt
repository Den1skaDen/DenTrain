package com.dentrain.ui.workout

import kotlinx.coroutines.CancellationException
import com.dentrain.util.UserFacingException
import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.OperationErrorDialog
import androidx.compose.runtime.saveable.rememberSaveable
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.DiscardChangesDialog
import com.dentrain.ui.components.ConfirmDialog
import androidx.activity.compose.BackHandler
import com.dentrain.ui.components.EditorTextField
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.ExerciseDraft
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutDraft
import com.dentrain.data.WorkoutRepository
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.WorkoutDateSelector
import com.dentrain.ui.components.workoutFieldColors
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Одно упражнение в форме. Поля — Compose-состояние, поэтому ввод не пересоздаёт список. */
class ExerciseForm(
    val uid: Long,
    val id: Long = 0,
    name: String = "",
    sets: String = "3",
    reps: String = "",
    weight: String = ""
) {
    var name by mutableStateOf(name)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)

    var nameError by mutableStateOf<String?>(null)
    var setsError by mutableStateOf<String?>(null)

    fun onNameChange(value: String) {
        name = value.take(60)
        if (name.isNotBlank()) {
            nameError = null
        }
    }

    fun onSetsChange(value: String) {
        sets = value.filter(Char::isDigit).take(2)
        if (sets.isNotBlank() && (sets.toIntOrNull() ?: 0) in 1..20) {
            setsError = null
        }
    }

    fun onRepsChange(value: String) {
        val digits = value.filter(Char::isDigit).take(3)
        val num = digits.toIntOrNull()
        reps = if (num != null && num > 999) "999" else digits
    }
}

class EditorViewModel(private val workoutId: Long, private val repo: WorkoutRepository) : ViewModel() {
    var title by mutableStateOf("")
    var titleError by mutableStateOf<String?>(null)
    var description by mutableStateOf("")
    var epochDay by mutableLongStateOf(LocalDate.now().toEpochDay())
    var dayOfWeek by mutableIntStateOf(LocalDate.now().dayOfWeek.value)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(workoutId == 0L)
    var loadFailed by mutableStateOf(false)
        private set
    val exercises = mutableStateListOf<ExerciseForm>()
    val recentExerciseNames = repo.recentExerciseNames.stateIn(viewModelScope, SharingStarted.Lazily, emptyList<String>())
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Lazily, SettingsEntity())
    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    private var nextUid = 1L
    private data class FormFields(
        val title: String,
        val description: String,
        val epochDay: Long,
        val dayOfWeek: Int,
        val exercises: List<List<String>>
    )

    private var originalFields: FormFields? = null

    private fun formFields() = FormFields(
        title, description, epochDay, dayOfWeek,
        exercises.map { listOf(it.uid.toString(), it.name, it.sets, it.reps, it.weight) }
    )

    val hasChanges: Boolean get() = loaded && formFields() != originalFields

    init {
        if (workoutId == 0L) {
            exercises.add(ExerciseForm(nextUid++))
            originalFields = formFields()
        } else {
            perform("Не удалось открыть тренировку.") {
                val workout = try {
                    repo.getWorkout(workoutId) ?: throw UserFacingException("Тренировка больше недоступна.")
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    loadFailed = true
                    throw failure
                }
                workout.let { w ->
                    title = w.workout.title
                    description = w.workout.description
                    epochDay = w.workout.dateEpochDay
                    dayOfWeek = w.workout.dayOfWeek
                    w.exercises.sortedBy { it.exercise.position }.forEach {
                        exercises.add(
                            ExerciseForm(
                                nextUid++, it.exercise.id, it.exercise.name,
                                it.exercise.plannedSets.toString(), it.exercise.plannedReps ?: "",
                                Fmt.weight(it.exercise.plannedWeight) ?: ""
                            )
                        )
                    }
                }
                originalFields = formFields()
                loaded = true
            }
        }
    }

    fun onTitleChange(value: String) {
        title = value.take(72)
        if (title.isNotBlank()) {
            titleError = null
            error = null
        }
    }

    fun changeDate(day: Long) {
        epochDay = day
        dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek.value
    }

    fun changeDayOfWeek(targetDayOfWeek: Int) {
        dayOfWeek = targetDayOfWeek
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek.value
        var daysToAdd = targetDayOfWeek - currentDayOfWeek
        if (daysToAdd < 0) {
            daysToAdd += 7
        }
        epochDay = today.plusDays(daysToAdd.toLong()).toEpochDay()
    }

    fun addExercise() { exercises.add(ExerciseForm(nextUid++)) }
    fun removeExercise(index: Int) { if (index in exercises.indices) exercises.removeAt(index) }

    fun move(index: Int, delta: Int) {
        val j = index + delta
        if (index in exercises.indices && j in exercises.indices) {
            val t = exercises[index]; exercises[index] = exercises[j]; exercises[j] = t
        }
    }

    fun validateAndSave(
        onDone: () -> Unit,
        onErrorFound: (targetItemIndex: Int, targetField: String, exerciseUid: Long?) -> Unit
    ) {
        if (title.isBlank()) {
            titleError = "Введите название тренировки"
            error = "Введите название тренировки"
            onErrorFound(0, "title", null)
            return
        }

        if (exercises.isEmpty()) {
            error = "Добавьте хотя бы одно упражнение"
            return
        }

        for ((i, e) in exercises.withIndex()) {
            val n = i + 1
            if (e.name.isBlank()) {
                e.nameError = "Введите название упражнения"
                error = "Упражнение $n: введите название"
                onErrorFound(i + 2, "name", e.uid)
                return
            }
            val setsNum = e.sets.trim().toIntOrNull()
            if (setsNum == null || setsNum < 1 || setsNum > 20) {
                e.setsError = "Подходов должно быть от 1 до 20"
                error = "Упражнение $n: подходов должно быть от 1 до 20"
                onErrorFound(i + 2, "sets", e.uid)
                return
            }
            val weight = Fmt.parseWeight(e.weight)
            if (e.weight.isNotBlank() && (weight == null || !weight.isFinite() || weight < 0)) {
                error = "Упражнение $n: укажите вес числом от 0 или оставьте поле пустым."
                return
            }
        }

        error = null
        val drafts = mutableListOf<ExerciseDraft>()
        exercises.forEach { e ->
            val weight = if (e.weight.isBlank()) null else Fmt.parseWeight(e.weight)
            drafts.add(ExerciseDraft(e.id, e.name.trim(), e.sets.trim().toInt(), e.reps.trim().ifBlank { null }, weight))
        }
        perform("Не удалось сохранить тренировку.") {
            repo.saveWorkout(WorkoutDraft(workoutId, title.trim(), epochDay, dayOfWeek, description.trim(), drafts))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (workoutId != 0L) {
            perform("Не удалось удалить тренировку.") {
                repo.deleteWorkout(workoutId)
                onDone()
            }
        }
    }
}

@Composable
private fun EditorCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutEditorScreen(workoutId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "editor$workoutId") { EditorViewModel(workoutId, it.repository) }
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val recentExerciseNames by vm.recentExerciseNames.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val handleBack = { if (vm.hasChanges) showDiscard = true else onBack() }
    BackHandler(onBack = handleBack)

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Удалить тренировку?",
            text = "Тренировка и записанные подходы будут удалены. Восстановить их не получится.",
            onConfirm = { showDelete = false; vm.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }

    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            delay(350)
            focusManager.clearFocus()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val titleFocusRequester = remember { FocusRequester() }
    val nameFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val setsFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (workoutId == 0L) "Новая тренировка" else "Редактирование",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    BorderedBackButton(onClick = handleBack)
                },
                actions = {
                    if (workoutId != 0L) {
                        IconButton(onClick = { showDelete = true }, enabled = vm.loaded) {
                            Icon(Icons.Filled.Delete, "Удалить", tint = DenTrainPalette.Error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->
        if (vm.loadFailed) {
            ErrorText("Не удалось открыть тренировку.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        if (!vm.loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = DenTrainPalette.Lime)
            }
            return@Scaffold
        }

        val tfShape = RoundedCornerShape(12.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize().animateContentSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка основных настроек
            item {
                EditorCard {
                    Text(
                        "Основные настройки",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val dateText = remember(vm.epochDay) {
                        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
                        LocalDate.ofEpochDay(vm.epochDay).format(formatter)
                    }
                    val dayName = remember(vm.dayOfWeek) { Fmt.dayName(vm.dayOfWeek) }

                    WorkoutDateSelector(dayName, dateText, vm.epochDay, vm::changeDate)

                    if (vm.titleError != null) {
                        Text(
                            text = vm.titleError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    EditorTextField(
                        value = vm.title,
                        onValueChange = vm::onTitleChange,
                        label = "Название (напр., Грудь и трицепс)",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.focusRequester(titleFocusRequester),
                        hasError = vm.titleError != null
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("День недели", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DenTrainPalette.MutedText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Fmt.dayShort.forEachIndexed { i, name ->
                            val selected = vm.dayOfWeek == i + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DenTrainPalette.RaisedPanel)
                                    .border(
                                        BorderStroke(
                                            if (selected) 1.5.dp else 1.dp,
                                            if (selected) DenTrainPalette.Lime else DenTrainPalette.Border
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { vm.changeDayOfWeek(i + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else DenTrainPalette.MutedText
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    EditorTextField(
                        value = vm.description,
                        onValueChange = { vm.description = it.take(300) },
                        label = "Описание (необязательно)",
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            item {
                Text(
                    "План тренировки:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Карточки упражнений
            itemsIndexed(vm.exercises, key = { _, e -> e.uid }) { index, e ->
                val nameFocus = nameFocusRequesters.getOrPut(e.uid) { FocusRequester() }
                val setsFocus = setsFocusRequesters.getOrPut(e.uid) { FocusRequester() }

                EditorCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Упражнение ${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { vm.move(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    "Выше",
                                    tint = if (index > 0) Color.White else Color(0xFF444858),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { vm.move(index, 1) },
                                enabled = index < vm.exercises.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowDownward,
                                    "Ниже",
                                    tint = if (index < vm.exercises.lastIndex) Color.White else Color(0xFF444858),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { vm.removeExercise(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    "Удалить",
                                    tint = DenTrainPalette.Error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (e.nameError != null) {
                        Text(
                            text = e.nameError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    var isNameFocused by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = e.name,
                        onValueChange = e::onNameChange,
                        label = { Text("Название упражнения") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                            .onFocusChanged { isNameFocused = it.isFocused },
                        colors = workoutFieldColors(isFilled = e.name.isNotBlank(), hasError = e.nameError != null),
                        shape = tfShape
                    )

                    // Интерактивный поиск / автодополнение упражнений из базы
                    val filteredSuggestions = remember(e.name, recentExerciseNames) {
                        if (e.name.isBlank()) {
                            recentExerciseNames.take(8)
                        } else {
                            recentExerciseNames.filter {
                                it.contains(e.name, ignoreCase = true)
                            }.take(8)
                        }
                    }

                    val showSuggestions = isNameFocused && filteredSuggestions.isNotEmpty()

                    AnimatedVisibility(
                        visible = showSuggestions,
                        enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                        exit = fadeOut(tween(500)) + shrinkVertically(tween(500))
                    ) {
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSuggestions, key = { it }) { item ->
                                val isSel = e.name.equals(item, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) Color(0xFF2A2415) else DenTrainPalette.RaisedPanel)
                                        .border(
                                            BorderStroke(
                                                if (isSel) 1.5.dp else 1.dp,
                                                if (isSel) Color(0xFFFFB300) else DenTrainPalette.Border
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { e.onNameChange(item) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color(0xFFFFD54F) else DenTrainPalette.MutedText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (e.setsError != null) {
                        Text(
                            text = e.setsError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = e.sets,
                            onValueChange = e::onSetsChange,
                            label = { Text("Подходы", fontSize = 11.sp, maxLines = 1) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(setsFocus),
                            colors = workoutFieldColors(isFilled = e.sets.isNotBlank(), hasError = e.setsError != null),
                            shape = tfShape
                        )

                        if (settings.swapWeightReps) {
                            // Сначала вес
                            OutlinedTextField(
                                value = e.weight,
                                onValueChange = { e.weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                label = { Text("Вес, кг", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.weight.isNotBlank()),
                                shape = tfShape
                            )
                            // Затем повторы
                            OutlinedTextField(
                                value = e.reps,
                                onValueChange = e::onRepsChange,
                                label = { Text("Повторы", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.reps.isNotBlank()),
                                shape = tfShape
                            )
                        } else {
                            // Сначала повторы (Стандартно)
                            OutlinedTextField(
                                value = e.reps,
                                onValueChange = e::onRepsChange,
                                label = { Text("Повторы", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.reps.isNotBlank()),
                                shape = tfShape
                            )
                            OutlinedTextField(
                                value = e.weight,
                                onValueChange = { e.weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                label = { Text("Вес, кг", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.weight.isNotBlank()),
                                shape = tfShape
                            )
                        }
                    }
                }
            }

            // Кнопка добавить упражнение
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                        .clickable { vm.addExercise() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = DenTrainPalette.Lime)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Добавить упражнение",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DenTrainPalette.Lime
                        )
                    }
                }
            }

            // Кнопка сохранить
            item {
                ErrorText(vm.error, Modifier.padding(bottom = 8.dp))
                Button(
                    onClick = {
                        vm.validateAndSave(
                            onDone = onBack,
                            onErrorFound = { itemIndex, field, exerciseUid ->
                                coroutineScope.launch {
                                    listState.animateScrollToItem(itemIndex)
                                    when (field) {
                                        "title" -> titleFocusRequester.requestFocus()
                                        "name" -> exerciseUid?.let { nameFocusRequesters[it]?.requestFocus() }
                                        "sets" -> exerciseUid?.let { setsFocusRequesters[it]?.requestFocus() }
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DenTrainPalette.Lime,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Сохранить тренировку",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
