package com.dentrain.ui.workout

import com.dentrain.util.userMessage
import com.dentrain.ui.components.launchUserAction
import com.dentrain.ui.components.OperationErrorDialog
import com.dentrain.ui.components.CircularWorkoutProgress
import com.dentrain.data.ExerciseWithSets
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.ExerciseEntity
import com.dentrain.data.RecordEntity
import com.dentrain.data.SetResultEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.timer.RestTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.ConfettiOverlay
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.RecordCelebrationDialog
import com.dentrain.ui.components.SetEditDialog
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.isWorkingPlanComplete
import com.dentrain.util.totalWorkingSets
import com.dentrain.util.completedWorkingSets
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExerciseGrowthItem(
    val name: String,
    val currentKg: Double,
    val prevKg: Double,
    val percent: Double
)

data class GrowthComparisonResult(
    val dayOfWeekName: String,
    val prevDateEpochDay: Long?,
    val items: List<ExerciseGrowthItem>
)

class SessionViewModel(
    private val workoutId: Long,
    private val repo: WorkoutRepository,
    private val timer: RestTimer
) : ViewModel() {
    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    val workout: StateFlow<WorkoutWithExercises?> =
        repo.observeWorkout(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timerState: StateFlow<TimerState> = timer.state

    fun saveSetAndCheckRecord(
        exercise: ExerciseEntity,
        repsText: String,
        weightText: String,
        isTimeBased: Boolean,
        isWarmup: Boolean = false,
        onResult: (String?, RecordEntity?) -> Unit
    ) {
        val reps = repsText.trim().toIntOrNull()
        if (reps == null || reps < 1) {
            onResult("Введите число (не меньше 1)", null)
            return
        }
        val weight = if (weightText.isBlank()) null else Fmt.parseWeight(weightText)
        if (weightText.isNotBlank() && (weight == null || !weight.isFinite() || weight < 0)) {
            onResult("Вес — число не меньше 0", null)
            return
        }

        perform("Не удалось записать подход.") {
            val newRecord = repo.addSet(exercise.id, reps, weight, isTimeBased, isWarmup)
            val s = settings.value
            if (s != null && s.autoRestTimer) timer.restart(s.restSeconds)
            onResult(null, newRecord)
        }
    }

    /** Сравнение прогресса с последней тренировкой в ЭТОТ ЖЕ ДЕНЬ НЕДЕЛИ (например, среда -> среда). */
    suspend fun getSameDayGrowthComparison(currentW: WorkoutWithExercises): GrowthComparisonResult = withContext(Dispatchers.Default) {
        val currentDayOfWeek = currentW.workout.dayOfWeek

        val prevW = repo.previousWorkout(currentDayOfWeek, currentW.workout.dateEpochDay)

        if (prevW == null) {
            return@withContext GrowthComparisonResult(
                dayOfWeekName = Fmt.dayName(currentDayOfWeek),
                prevDateEpochDay = null,
                items = emptyList()
            )
        }

        val items = mutableListOf<ExerciseGrowthItem>()
        currentW.exercises.forEach { currentExItem ->
            val exName = currentExItem.exercise.name
            val currentKg = currentExItem.sets.filter { !it.isWarmup }.sumOf { (it.weight ?: 0.0) * it.reps }

            val prevExItem = prevW.exercises.find { it.exercise.name.equals(exName, ignoreCase = true) }
            val prevKg = prevExItem?.sets?.filter { !it.isWarmup }?.sumOf { (it.weight ?: 0.0) * it.reps } ?: 0.0

            if (currentKg > 0 && prevKg > 0) {
                val pct = ((currentKg - prevKg) / prevKg) * 100.0
                items.add(ExerciseGrowthItem(exName, currentKg, prevKg, pct))
            } else if (currentKg > 0 && prevKg == 0.0) {
                items.add(ExerciseGrowthItem(exName, currentKg, 0.0, 100.0))
            }
        }

        GrowthComparisonResult(
            dayOfWeekName = Fmt.dayName(currentDayOfWeek),
            prevDateEpochDay = prevW.workout.dateEpochDay,
            items = items
        )
    }

    fun updateSet(set: SetResultEntity, reps: Int, weight: Double?, isWarmup: Boolean = set.isWarmup) {
        perform("Не удалось изменить подход.") { repo.updateSet(set, reps, weight, isWarmup) }
    }
    fun deleteSet(set: SetResultEntity) { perform("Не удалось удалить подход.") { repo.deleteSet(set) } }
    fun addExtraSet(exerciseId: Long) { perform("Не удалось добавить подход.") { repo.addPlannedSet(exerciseId) } }
    fun removePlannedSet(exerciseId: Long) {
        perform("Не удалось убрать подход из плана.") { repo.removePlannedSet(exerciseId) }
    }
    fun deleteExercise(exerciseId: Long) {
        perform("Не удалось удалить упражнение.") { repo.deleteSessionExercise(workoutId, exerciseId) }
    }
    fun addExerciseToWorkout(name: String) { perform("Не удалось добавить упражнение.") { repo.addExerciseToWorkout(workoutId, name) } }
    fun goTo(index: Int) { perform("Не удалось переключить упражнение.") { repo.setCurrentExercise(workoutId, index) } }
    fun skipRest() = timer.reset()

    fun updateDescription(desc: String) {
        perform("Не удалось сохранить заметки.") { repo.updateWorkoutDescription(workoutId, desc) }
    }

    fun finish(onDone: () -> Unit) {
        perform("Не удалось завершить тренировку.") {
            repo.finishWorkout(workoutId)
            timer.reset()
            onDone()
        }
    }
}

@Composable
private fun SessionCard(
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

@Composable
private fun sessionTfColors(isFilled: Boolean, hasError: Boolean, accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DenTrainPalette.Panel,
    unfocusedContainerColor = DenTrainPalette.Panel,
    focusedBorderColor = if (hasError) DenTrainPalette.Error else accent,
    unfocusedBorderColor = if (hasError) DenTrainPalette.Error else if (isFilled) Color.White else DenTrainPalette.Border,
    focusedLabelColor = if (hasError) DenTrainPalette.Error else accent,
    unfocusedLabelColor = if (hasError) DenTrainPalette.Error else if (isFilled) Color(0xFFE0E0E6) else DenTrainPalette.MutedText,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = if (hasError) DenTrainPalette.Error else accent
)

@Composable
private fun LiveDurationText(startedAt: Long) {
    var elapsedSeconds by remember(startedAt) { mutableLongStateOf(((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)) }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(1000L)
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        }
    }
    Text(
        text = Fmt.clock(elapsedSeconds * 1000L),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color.White,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionScreen(workoutId: Long, onBack: () -> Unit, onFinished: () -> Unit) {
    val vm = appViewModel(key = "session$workoutId") { SessionViewModel(workoutId, it.repository, it.restTimer) }
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showFinish by remember { mutableStateOf(false) }
    var showGoalAccomplished by remember { mutableStateOf(false) }
    var showGrowthComparison by remember { mutableStateOf<GrowthComparisonResult?>(null) }
    var sessionRecordCelebration by remember { mutableStateOf<RecordEntity?>(null) }
    var editing by remember { mutableStateOf<SetResultEntity?>(null) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showQuickAddExercise by remember { mutableStateOf(false) }
    var exercisePendingDeletion by rememberSaveable { mutableStateOf<Long?>(null) }

    // Режим единиц: Повторения (false) или Секунды (true)
    var isTimeBased by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workout?.workout?.title ?: "Текущая тренировка",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    BorderedBackButton(onClick = onBack)
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        workout?.workout?.startedAt?.let { LiveDurationText(it) }

                        IconButton(onClick = {
                            Haptic.click(context)
                            showFinish = true
                        }) {
                            Icon(Icons.Filled.Flag, "Завершить", tint = DenTrainPalette.Error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->
        val w = workout
        if (w == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = DenTrainPalette.Lime)
            }
            return@Scaffold
        }
        val exercises = remember(w.exercises) { w.exercises.sortedBy { it.exercise.position } }
        val addExercise = {
            Haptic.click(context)
            showQuickAddExercise = true
        }
        if (showQuickAddExercise) {
            QuickAddExerciseDialog(
                onAdd = vm::addExerciseToWorkout,
                onDismiss = { showQuickAddExercise = false }
            )
        }
        if (exercises.isEmpty()) {
            Box(Modifier.padding(pad).padding(16.dp)) {
                SessionProgressCard(
                    workoutId = workoutId,
                    exercises = emptyList(),
                    currentIndex = 0,
                    completedSets = 0,
                    plannedSets = 0,
                    onExerciseClick = vm::goTo,
                    onAddExercise = addExercise
                )
            }
            return@Scaffold
        }
        val index = w.workout.currentExerciseIndex.coerceIn(0, exercises.lastIndex)
        val current = exercises[index]
        val ex = current.exercise
        var isInputWarmup by rememberSaveable(ex.id) { mutableStateOf(false) }
        val sets = remember(current.sets) { current.sets.sortedBy { it.setNumber } }
        val warmupSets = remember(sets) { sets.filter { it.isWarmup } }
        val workingSets = remember(sets) { sets.filter { !it.isWarmup } }
        val workingDoneCount = workingSets.size
        val allDone = workingDoneCount >= ex.plannedSets
        val isLast = index == exercises.lastIndex

        val totalPlannedWorkingSets = remember(exercises) { exercises.sumOf { it.totalWorkingSets } }
        val completedWorkingSets = remember(exercises) { exercises.sumOf { it.completedWorkingSets } }
        val workoutDone = totalPlannedWorkingSets > 0 && completedWorkingSets == totalPlannedWorkingSets

        LaunchedEffect(workoutDone) {
            if (workoutDone) {
                Haptic.success(context)
                showGoalAccomplished = true
            }
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SessionProgressCard(
                        workoutId = workoutId,
                        exercises = exercises,
                        currentIndex = index,
                        completedSets = completedWorkingSets,
                        plannedSets = totalPlannedWorkingSets,
                        onExerciseClick = { tappedIndex ->
                            Haptic.click(context)
                            vm.goTo(tappedIndex)
                        },
                        onAddExercise = addExercise
                    )
                }

                // Карточка текущего упражнения (золотая обводка, без левой полоски)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DenTrainPalette.Panel)
                            .border(BorderStroke(1.5.dp, Color(0xFFFFB300)), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = ex.name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Карточка таймера отдыха с плавной анимацией появления/исчезновения
                item {
                    val timer by vm.timerState.collectAsStateWithLifecycle()
                    val timerCyan = Color(0xFF00BFFF)
                    AnimatedVisibility(
                        visible = timer.running || timer.finished,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(BorderStroke(1.dp, timerCyan), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Timer, contentDescription = null, tint = timerCyan, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (timer.finished) "Отдых окончен — пора на следующий подход!" else "Отдых: ${Fmt.clock(timer.remainingMs)}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DenTrainPalette.Panel)
                                        .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                                        .clickable {
                                            Haptic.click(context)
                                            vm.skipRest()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (timer.finished) "Скрыть" else "Пропустить",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SessionCard {
                        Row(Modifier.fillMaxWidth().selectableGroup()) {
                            SessionSetTab("Рабочий", !isInputWarmup, DenTrainPalette.Lime) {
                                Haptic.click(context)
                                isInputWarmup = false
                            }
                            SessionSetTab("Разминка", isInputWarmup, WarmupAccent) {
                                Haptic.click(context)
                                isInputWarmup = true
                            }
                        }

                        val visibleSets = if (isInputWarmup) warmupSets else workingSets
                        val totalCount = if (isInputWarmup) warmupSets.size else maxOf(ex.plannedSets, workingDoneCount)
                        if (totalCount > 0) Spacer(Modifier.height(8.dp))
                        repeat(totalCount) { position ->
                            SessionSetRow(
                                result = visibleSets.getOrNull(position),
                                isWarmup = isInputWarmup,
                                isCurrent = position == visibleSets.size,
                                onEdit = {
                                    Haptic.click(context)
                                    editing = it
                                }
                            )
                        }
                    }
                }

                // Форма ввода результата подхода
                item {
                    if (!allDone || isInputWarmup) {
                        val lastResult = if (isInputWarmup) warmupSets.lastOrNull() else workingSets.lastOrNull()
                        var reps by remember(ex.id, sets.size, isInputWarmup) {
                            mutableStateOf(lastResult?.reps?.toString() ?: Regex("\\d+").find(ex.plannedReps ?: "")?.value ?: "")
                        }
                        var weight by remember(ex.id, sets.size, isInputWarmup) {
                            mutableStateOf(Fmt.weight(if (lastResult == null) ex.plannedWeight else lastResult.weight) ?: "")
                        }
                        var error by remember(ex.id, sets.size, isInputWarmup) { mutableStateOf<String?>(null) }
                        val accent = if (isInputWarmup) WarmupAccent else DenTrainPalette.Lime

                        SessionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isInputWarmup) "Результат разминки" else "Результат подхода",
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    // Переключатель Повт. / Сек.
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (!isTimeBased) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
                                                .border(BorderStroke(1.dp, if (!isTimeBased) accent else DenTrainPalette.Border), CircleShape)
                                                .clickable {
                                                    Haptic.click(context)
                                                    isTimeBased = false
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Повт.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isTimeBased) Color.White else DenTrainPalette.MutedText)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isTimeBased) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
                                                .border(BorderStroke(1.dp, if (isTimeBased) accent else DenTrainPalette.Border), CircleShape)
                                                .clickable {
                                                    Haptic.click(context)
                                                    isTimeBased = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("⏱ Сек.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isTimeBased) Color.White else DenTrainPalette.MutedText)
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = reps,
                                        onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                                        label = { Text(if (isTimeBased) "Секунды" else "Повторения") },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = sessionTfColors(isFilled = reps.isNotBlank(), hasError = error != null, accent = accent),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = weight,
                                        onValueChange = { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                        label = { Text("Вес, кг") },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = sessionTfColors(isFilled = weight.isNotBlank(), hasError = error != null, accent = accent),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (error != null) {
                                    ErrorText(error, Modifier.padding(top = 4.dp))
                                }

                                Button(
                                    onClick = {
                                        vm.saveSetAndCheckRecord(ex, reps, weight, isTimeBased, isInputWarmup) { err, record ->
                                            if (err == null) {
                                                if (record != null) {
                                                    Haptic.success(context)
                                                    sessionRecordCelebration = record
                                                } else {
                                                    Haptic.click(context)
                                                }
                                            }
                                            error = err
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (isInputWarmup) "Сохранить разминку" else "Сохранить подход",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionActionButton(
                            label = "Добавить подход",
                            icon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                Haptic.click(context)
                                isInputWarmup = false
                                vm.addExtraSet(ex.id)
                            }
                        )
                        SessionActionButton(
                            label = "Удалить текущий",
                            icon = Icons.Filled.Remove,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            enabled = !isInputWarmup && ex.plannedSets > workingDoneCount,
                            muted = true,
                            onClick = {
                                Haptic.click(context)
                                if (ex.plannedSets == 1) {
                                    exercisePendingDeletion = ex.id
                                } else {
                                    vm.removePlannedSet(ex.id)
                                }
                            }
                        )
                    }
                }

                // Заметки к тренировке (только для чтения, откроется окно при нажатии)
                item {
                    val notesBorderBrush = remember {
                        Brush.linearGradient(
                            0.00f to Color(0xFFFFB300),               // Золотой угол справа сверху
                            0.20f to Color.White.copy(alpha = 0.30f),  // Постепенно угасает после скругления в беловато-прозрачный
                            0.80f to Color.White.copy(alpha = 0.30f),  // Обычная беловато-прозрачная окантовка
                            1.00f to Color(0xFFFFB300),               // Золотой угол слева снизу
                            start = Offset(Float.POSITIVE_INFINITY, 0f),
                            end = Offset(0f, Float.POSITIVE_INFINITY)
                        )
                    }

                    SessionCard {
                        Text(
                            text = "Заметки к тренировке",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(BorderStroke(1.5.dp, notesBorderBrush), RoundedCornerShape(12.dp))
                                .clickable {
                                    Haptic.click(context)
                                    showNotesDialog = true
                                }
                                .padding(14.dp)
                        ) {
                            Text(
                                text = w.workout.description.ifBlank { "Напишите заметку к занятию..." },
                                fontSize = 14.sp,
                                color = if (w.workout.description.isNotBlank()) Color.White else DenTrainPalette.MutedText
                            )
                        }
                    }
                }

                // Переход к следующему упражнению
                item {
                    if (!isLast) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    Haptic.click(context)
                                    vm.goTo(index + 1)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DenTrainPalette.RaisedPanel,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Пропустить",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ConfettiOverlay(visible = showGoalAccomplished)
        }

        sessionRecordCelebration?.let { rec ->
            RecordCelebrationDialog(
                exerciseName = rec.exerciseName,
                weightText = Fmt.weight(rec.weight) ?: "",
                repsText = rec.reps.toString(),
                onDismiss = { sessionRecordCelebration = null }
            )
        }

        if (showGoalAccomplished) {
            GoalAccomplishedDialog(
                onContinue = {
                    showGoalAccomplished = false
                    scope.launch {
                        try {
                            showGrowthComparison = vm.getSameDayGrowthComparison(w)
                        } catch (failure: Exception) {
                            vm.operationError = failure.userMessage("Не удалось сравнить результаты тренировок.")
                        }
                    }
                },
                onDismiss = { showGoalAccomplished = false }
            )
        }

        showGrowthComparison?.let { comp ->
            WorkoutProgressGrowthDialog(
                comparison = comp,
                onDismiss = { showGrowthComparison = null },
                onFinish = {
                    showGrowthComparison = null
                    vm.finish(onFinished)
                }
            )
        }

        exercisePendingDeletion?.let { exerciseId ->
            ConfirmDialog(
                title = "Удалить упражнение?",
                text = "Упражнение и его подходы будут удалены из текущей тренировки.",
                confirmText = "Удалить",
                onConfirm = {
                    exercisePendingDeletion = null
                    vm.deleteExercise(exerciseId)
                },
                onDismiss = { exercisePendingDeletion = null }
            )
        }

        if (showFinish) {
            ConfirmDialog(
                title = "Завершить тренировку?",
                text = "Выполнено подходов: ${w.doneSets()}. Тренировка будет сохранена в истории.",
                confirmText = "Завершить",
                destructive = false,
                onConfirm = { showFinish = false; vm.finish(onFinished) },
                onDismiss = { showFinish = false }
            )
        }

        editing?.let { set ->
            SetEditDialog(
                set = set,
                onSave = { r, wt, isW -> vm.updateSet(set, r, wt, isW); editing = null },
                onDelete = { vm.deleteSet(set); editing = null },
                onDismiss = { editing = null }
            )
        }

        if (showNotesDialog) {
            NotesEditDialog(
                initialNotes = w.workout.description,
                onSave = { newNotes -> vm.updateDescription(newNotes) },
                onDismiss = { showNotesDialog = false }
            )
        }

    }
}

@Composable
private fun SessionProgressCard(
    workoutId: Long,
    exercises: List<ExerciseWithSets>,
    currentIndex: Int,
    completedSets: Int,
    plannedSets: Int,
    onExerciseClick: (Int) -> Unit,
    onAddExercise: () -> Unit
) {
    SessionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "ПРОГРЕСС",
                    color = DenTrainPalette.MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$completedSets из $plannedSets",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("рабочих подходов", color = DenTrainPalette.MutedText, fontSize = 13.sp)
            }
            CircularWorkoutProgress(
                completedSets = completedSets,
                totalPlannedSets = plannedSets,
                workoutId = workoutId,
                size = 72.dp
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            exercises.forEachIndexed { index, entry ->
                val selected = index == currentIndex
                val planned = entry.exercise.plannedSets
                val completed = entry.isWorkingPlanComplete
                val showNextArrow = selected && index < exercises.lastIndex
                Box(Modifier.zIndex(if (selected) 1f else 0f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) DenTrainPalette.RaisedPanel else Color.Transparent)
                            .selectable(selected = selected, onClick = { onExerciseClick(index) })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            color = if (selected) DenTrainPalette.Lime else DenTrainPalette.MutedText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Column {
                            Text(
                                entry.exercise.name,
                                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (selected) Color.White else DenTrainPalette.MutedText,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                if (selected) "Сейчас" else if (completed) "Выполнено" else "$planned ${Fmt.plural(planned, "подход", "подхода", "подходов")}",
                                color = DenTrainPalette.MutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (showNextArrow) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 16.dp)
                                .size(28.dp)
                                .background(DenTrainPalette.RaisedPanel, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = DenTrainPalette.Lime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (selected) {
                        ActiveExerciseOutline(entry.exercise.id, showNextArrow, Modifier.matchParentSize())
                    }
                }
                if (showNextArrow) Spacer(Modifier.height(12.dp))
            }
        }
        if (exercises.isNotEmpty()) Spacer(Modifier.height(12.dp))
        SessionActionButton(
            label = "Добавить упражнение",
            icon = Icons.Filled.Add,
            onClick = onAddExercise
        )
    }
}

@Composable
private fun ActiveExerciseOutline(exerciseId: Long, hasNext: Boolean, modifier: Modifier) {
    val reveal = remember(exerciseId, hasNext) { Animatable(0f) }
    LaunchedEffect(reveal) {
        reveal.animateTo(1f, tween(durationMillis = 850))
    }
    Spacer(
        modifier.drawWithCache {
            val center = size.width / 2
            val inset = 1.dp.toPx()
            val corner = 12.dp.toPx().coerceAtMost(minOf(center, size.height / 2))
            val right = size.width - inset
            val bottom = size.height - inset
            val arrowRadius = 14.dp.toPx()
            val arrowCenterY = size.height + 2.dp.toPx()
            val halfOutline = Path().apply {
                moveTo(center, inset)
                lineTo(right - corner, inset)
                quadraticTo(right, inset, right, inset + corner)
                lineTo(right, bottom - corner)
                quadraticTo(right, bottom, right - corner, bottom)
                if (hasNext) {
                    val edge = center + arrowRadius
                    val shoulder = 5.dp.toPx()
                    lineTo(edge + shoulder, bottom)
                    quadraticTo(edge, bottom, edge, arrowCenterY)
                    // The final arc follows the lower edge of the arrow badge.
                    arcTo(
                        androidx.compose.ui.geometry.Rect(
                            center - arrowRadius, arrowCenterY - arrowRadius,
                            center + arrowRadius, arrowCenterY + arrowRadius
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                } else {
                    lineTo(center, bottom)
                }
            }
            val measure = PathMeasure().apply { setPath(halfOutline, false) }
            val segment = Path()
            val glow = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val outline = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                val progress = reveal.value
                if (progress > 0f) {
                    segment.reset()
                    measure.getSegment(0f, measure.length * progress, segment)
                    val accent = DenTrainPalette.Lime
                    drawPath(segment, accent.copy(alpha = 0.15f), style = glow)
                    drawPath(segment, accent, style = outline)
                    scale(scaleX = -1f, scaleY = 1f) {
                        drawPath(segment, accent.copy(alpha = 0.15f), style = glow)
                        drawPath(segment, accent, style = outline)
                    }
                }
            }
        }
    )
}

private val WarmupAccent = Color(0xFF00B0FF)

@Composable
private fun RowScope.SessionSetTab(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 12.dp),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !selected -> DenTrainPalette.MutedText
                accent == WarmupAccent -> accent
                else -> Color.White
            }
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(if (selected) accent else DenTrainPalette.Border)
        )
    }
}

@Composable
private fun SessionSetRow(
    result: SetResultEntity?,
    isWarmup: Boolean,
    isCurrent: Boolean,
    onEdit: (SetResultEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (result != null) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, DenTrainPalette.Border), RoundedCornerShape(10.dp))
            .then(if (result != null) Modifier.clickable { onEdit(result) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (result != null || isCurrent) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when {
                result != null -> Icons.Filled.CheckCircle
                isCurrent -> Icons.Filled.Edit
                else -> Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when {
                isWarmup -> WarmupAccent
                result != null || isCurrent -> DenTrainPalette.Lime
                else -> DenTrainPalette.MutedText
            },
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = when {
                result != null -> Fmt.shortSetLine(result)
                isCurrent -> "введите результат ниже"
                else -> "ожидает выполнения"
            },
            fontSize = 13.sp,
            fontWeight = if (result != null) FontWeight.Bold else FontWeight.Medium,
            color = if (result != null) Color.White else DenTrainPalette.MutedText
        )
    }
}

@Composable
private fun SessionActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) DenTrainPalette.Border else DenTrainPalette.SubtleBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DenTrainPalette.RaisedPanel,
            contentColor = if (muted) DenTrainPalette.MutedText.copy(alpha = 0.78f) else Color.White,
            disabledContainerColor = DenTrainPalette.Panel,
            disabledContentColor = DenTrainPalette.MutedText.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickAddExerciseDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DenTrainPalette.Panel),
            border = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Добавить упражнение",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название упражнения", color = DenTrainPalette.MutedText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = DenTrainPalette.Lime,
                        unfocusedBorderColor = DenTrainPalette.Border,
                        focusedContainerColor = DenTrainPalette.RaisedPanel,
                        unfocusedContainerColor = DenTrainPalette.RaisedPanel
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = DenTrainPalette.MutedText)
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(name.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DenTrainPalette.Lime,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Добавить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesEditDialog(
    initialNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialNotes) }

    val notesBorderBrush = remember {
        Brush.linearGradient(
            0.00f to Color(0xFFFFB300),
            0.20f to Color.White.copy(alpha = 0.30f),
            0.80f to Color.White.copy(alpha = 0.30f),
            1.00f to Color(0xFFFFB300),
            start = Offset(Float.POSITIVE_INFINITY, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DenTrainPalette.Panel),
            border = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Заметки к тренировке",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DenTrainPalette.RaisedPanel)
                        .border(BorderStroke(1.5.dp, notesBorderBrush), RoundedCornerShape(12.dp))
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(300) },
                        placeholder = { Text("Напишите заметку к занятию...", color = DenTrainPalette.MutedText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = DenTrainPalette.MutedText)
                    ) {
                        Text("Отмена", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onSave(text.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DenTrainPalette.Lime,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalAccomplishedDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1300, easing = LinearEasing)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DenTrainPalette.Panel),
            border = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "ЦЕЛЬ ВЫПОЛНЕНА! 🎯",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DenTrainPalette.Lime,
                    textAlign = TextAlign.Center
                )

                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    ArrowTargetAnimation(progress = animProgress.value)
                }

                Text(
                    "Отличная работа! Все запланированные упражнения и подходы выполнены!",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DenTrainPalette.Lime,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Посмотреть прогресс 📈", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutProgressGrowthDialog(
    comparison: GrowthComparisonResult,
    onDismiss: () -> Unit,
    onFinish: () -> Unit
) {
    val scale = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DenTrainPalette.Panel),
            border = BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .scale(scale.value)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(DenTrainPalette.RaisedPanel)
                        .border(BorderStroke(1.dp, DenTrainPalette.Lime), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📈", fontSize = 36.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ВАШ РОСТ И ПРОГРЕСС! 📈",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = DenTrainPalette.Lime,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (comparison.prevDateEpochDay != null)
                            "Сравнение с последней тренировкой (${comparison.dayOfWeekName}, ${Fmt.shortDate(comparison.prevDateEpochDay)})"
                        else "Это ваша первая сохранённая тренировка в этот день недели (${comparison.dayOfWeekName})!",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = DenTrainPalette.MutedText
                    )
                }

                if (comparison.items.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DenTrainPalette.RaisedPanel)
                            .border(BorderStroke(1.dp, DenTrainPalette.Border), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        comparison.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    Text(
                                        "Объём: ${item.currentKg.toInt()} кг (был ${item.prevKg.toInt()} кг)",
                                        fontSize = 11.sp,
                                        color = DenTrainPalette.MutedText
                                    )
                                }
                                val percentText = if (item.percent >= 0) "+%.1f%%".format(item.percent).replace('.', ',')
                                else "%.1f%%".format(item.percent).replace('.', ',')

                                val badgeColor = if (item.percent >= 0) DenTrainPalette.Lime else DenTrainPalette.Error

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, badgeColor), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        percentText,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (comparison.prevDateEpochDay != null) {
                    Text(
                        "Все упражнения выполнены точно по плану!",
                        fontSize = 14.sp,
                        color = DenTrainPalette.MutedText,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DenTrainPalette.Lime,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Сохранить и к итогам 🏆", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowTargetAnimation(progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerY = h / 2

        val targetX = w * 0.82f
        val targetRadius = 32.dp.toPx()

        drawCircle(color = DenTrainPalette.Error, radius = targetRadius, center = Offset(targetX, centerY))
        drawCircle(color = Color.White, radius = targetRadius * 0.65f, center = Offset(targetX, centerY))
        drawCircle(color = DenTrainPalette.Error, radius = targetRadius * 0.35f, center = Offset(targetX, centerY))

        val arrowStartX = 20.dp.toPx()
        val currentArrowX = arrowStartX + (targetX - arrowStartX) * progress

        val path = Path().apply {
            moveTo(arrowStartX, centerY)
            lineTo(currentArrowX, centerY)
        }
        drawPath(
            path = path,
            color = DenTrainPalette.Lime,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(
            color = DenTrainPalette.Lime,
            radius = 6.dp.toPx(),
            center = Offset(currentArrowX, centerY)
        )
    }
}
