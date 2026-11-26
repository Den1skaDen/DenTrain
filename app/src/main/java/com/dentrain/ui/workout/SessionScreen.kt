package com.dentrain.ui.workout

import com.dentrain.util.userMessage
import com.dentrain.ui.components.OperationErrorDialog
import com.dentrain.ui.components.CircularWorkoutProgress
import com.dentrain.data.ExerciseWithSets
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton
import com.dentrain.ui.icons.filled.Remove
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
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
import com.dentrain.ui.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.dentrain.ui.icons.filled.RadioButtonUnchecked
import com.dentrain.ui.icons.filled.Timer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.dentrain.data.RecordUpdate
import com.dentrain.ui.components.launchUserAction
import com.dentrain.data.SetResultEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.timer.RestTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.BorderedBackButton
import com.dentrain.ui.components.ConfirmDialog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SessionViewModel(
    private val workoutId: Long,
    private val repo: WorkoutRepository,
    private val timer: RestTimer
) : ViewModel() {
    internal var sessionRecordCelebration by mutableStateOf<RecordUpdate?>(null)
    var finishAfterRecord = false
    var savingSet by mutableStateOf(false)
        private set
    var finishing by mutableStateOf(false)
        private set
    var operationError by mutableStateOf<String?>(null)

    private fun perform(fallback: String, action: suspend () -> Unit) {
        launchUserAction(fallback, { operationError = it }, action)
    }

    val workout: StateFlow<WorkoutWithExercises?> =
        repo.observeWorkout(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timerState: StateFlow<TimerState> = timer.state

    internal fun saveSetAndCheckRecord(
        exercise: ExerciseEntity,
        repsText: String,
        weightText: String,
        isTimeBased: Boolean,
        isWarmup: Boolean = false,
        onResult: (String?, RecordUpdate?) -> Unit
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

        if (savingSet || finishing) return
        savingSet = true
        perform("Не удалось записать подход.") {
            try {
                val newRecord = repo.addSetWithRecord(exercise.id, reps, weight, isTimeBased, isWarmup)
                val s = settings.value
                if (s != null && s.autoRestTimer) timer.restart(s.restSeconds)
                onResult(null, newRecord)
            } finally {
                savingSet = false
            }
        }
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
        if (finishing) return
        finishing = true
        perform("Не удалось завершить тренировку.") {
            try {
                repo.finishWorkout(workoutId)
                timer.reset()
                onDone()
            } finally {
                finishing = false
            }
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
internal fun SessionScreen(workoutId: Long, onBack: () -> Unit, onFinished: () -> Unit) {
    val vm = appViewModel(key = "session$workoutId") { SessionViewModel(workoutId, it.repository, it.restTimer) }
    OperationErrorDialog(vm.operationError) { vm.operationError = null }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFinish by remember { mutableStateOf(false) }
    val finish = { vm.finish(onFinished) }
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

                        IconButton(enabled = !vm.savingSet && !vm.finishing, onClick = {
                            Haptic.click(context)
                            val entries = workout?.exercises.orEmpty()
                            if (entries.isNotEmpty() && entries.all { it.completedWorkingSets >= it.totalWorkingSets }) {
                                finish()
                            } else {
                                showFinish = true
                            }
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
        if (showFinish) {
            ConfirmDialog(
                title = "Завершить тренировку?",
                text = "Выполнено подходов: ${w.doneSets()}. Тренировка будет сохранена в истории.",
                confirmText = "Завершить",
                destructive = false,
                onConfirm = { showFinish = false; finish() },
                onDismiss = { showFinish = false }
            )
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
                    Column {
                        SessionCard {
                            val tabTransition = updateTransition(isInputWarmup, label = "set type")
                            val indicatorPosition by tabTransition.animateFloat(
                                transitionSpec = { tween(SetTabAnimationMillis) },
                                label = "set tab indicator position"
                            ) { warmup -> if (warmup) 1f else 0f }
                            val indicatorColor by tabTransition.animateColor(
                                transitionSpec = { tween(SetTabAnimationMillis) },
                                label = "set tab indicator color"
                            ) { warmup -> if (warmup) WarmupAccent else DenTrainPalette.Lime }

                            Row(Modifier.fillMaxWidth().selectableGroup()) {
                                SessionSetTab("Рабочий", !isInputWarmup, Color.White) {
                                    Haptic.click(context)
                                    isInputWarmup = false
                                }
                                SessionSetTab("Разминка", isInputWarmup, WarmupAccent) {
                                    Haptic.click(context)
                                    isInputWarmup = true
                                }
                            }
                            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                                val tabWidth = size.width / 2f
                                drawRect(DenTrainPalette.Border)
                                drawRect(
                                    color = indicatorColor,
                                    topLeft = Offset(tabWidth * indicatorPosition, 0f),
                                    size = Size(tabWidth, size.height)
                                )
                            }

                            tabTransition.AnimatedContent(
                                modifier = Modifier.fillMaxWidth(),
                                transitionSpec = {
                                    (fadeIn(tween(240, delayMillis = 120)) togetherWith fadeOut(tween(180)))
                                        .using(SizeTransform { _, _ -> tween(SetTabAnimationMillis) })
                                }
                            ) { warmup ->
                                val visibleSets = if (warmup) warmupSets else workingSets
                                val totalCount = if (warmup) {
                                    val defaultSlots = if (ex.plannedSets == 2) 2 else 3
                                    if (warmupSets.size < defaultSlots) warmupSets.size + 1 else warmupSets.size
                                } else {
                                    maxOf(ex.plannedSets, workingDoneCount)
                                }
                                val rowCount = updateTransition(totalCount, label = "set row count")
                                Column(Modifier.fillMaxWidth()) {
                                    Spacer(Modifier.height(8.dp))
                                    repeat(maxOf(rowCount.currentState, rowCount.targetState)) { position ->
                                        rowCount.AnimatedVisibility(
                                            visible = { count -> position < count },
                                            enter = expandVertically(tween(SetLayoutAnimationMillis), expandFrom = Alignment.Top) +
                                                fadeIn(tween(SetLayoutAnimationMillis)),
                                            exit = shrinkVertically(tween(SetLayoutAnimationMillis), shrinkTowards = Alignment.Top) +
                                                fadeOut(tween(SetLayoutAnimationMillis))
                                        ) {
                                            SessionSetRow(
                                                result = visibleSets.getOrNull(position),
                                                swapWeightReps = settings?.swapWeightReps == true,
                                                number = position + 1,
                                                isWarmup = warmup,
                                                isCurrent = position == visibleSets.size,
                                                onEdit = {
                                                    Haptic.click(context)
                                                    editing = it
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = !allDone || isInputWarmup,
                            enter = expandVertically(tween(SetLayoutAnimationMillis), expandFrom = Alignment.Top) +
                                fadeIn(tween(SetLayoutAnimationMillis)),
                            exit = shrinkVertically(tween(SetLayoutAnimationMillis), shrinkTowards = Alignment.Top) +
                                fadeOut(tween(SetLayoutAnimationMillis))
                        ) {
                            val lastResult = if (isInputWarmup) warmupSets.lastOrNull() else workingSets.lastOrNull()
                            var reps by remember(ex.id, sets.size, isInputWarmup) {
                                mutableStateOf(lastResult?.reps?.toString() ?: Regex("\\d+").find(ex.plannedReps ?: "")?.value ?: "")
                            }
                            var weight by remember(ex.id, sets.size, isInputWarmup) {
                                mutableStateOf(Fmt.weight(if (lastResult == null) ex.plannedWeight else lastResult.weight) ?: "")
                            }
                            var error by remember(ex.id, sets.size, isInputWarmup) { mutableStateOf<String?>(null) }
                            val accent = if (isInputWarmup) WarmupAccent else DenTrainPalette.Lime

                            SessionCard(modifier = Modifier.padding(top = 16.dp)) {
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
                                        val repsField: @Composable () -> Unit = {
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
                                        }
                                        val weightField: @Composable () -> Unit = {
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
                                        if (settings?.swapWeightReps == true) {
                                            weightField()
                                            repsField()
                                        } else {
                                            repsField()
                                            weightField()
                                        }
                                    }

                                    if (error != null) {
                                        ErrorText(error, Modifier.padding(top = 4.dp))
                                    }

                                    Button(
                                        enabled = !vm.savingSet && !vm.finishing,
                                        onClick = {
                                            val completesPlan = !isInputWarmup && !allDone &&
                                                completedWorkingSets + 1 == totalPlannedWorkingSets
                                            vm.saveSetAndCheckRecord(ex, reps, weight, isTimeBased, isInputWarmup) { err, record ->
                                                if (err == null) {
                                                    if (record != null) {
                                                        vm.finishAfterRecord = completesPlan
                                                        Haptic.success(context)
                                                        vm.sessionRecordCelebration = record
                                                    } else {
                                                        Haptic.click(context)
                                                        if (completesPlan) finish()
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

        }

        vm.sessionRecordCelebration?.let { (record, previous) ->
            RecordCelebrationDialog(
                exerciseName = record.exerciseName,
                weightText = Fmt.weight(record.weight).orEmpty(),
                repsText = record.reps.toString(),
                previous = previous,
                onDismiss = {
                    vm.sessionRecordCelebration = null
                    if (vm.finishAfterRecord) {
                        vm.finishAfterRecord = false
                        finish()
                    }
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

        editing?.let { set ->
            SetEditDialog(
                set = set,
                swapWeightReps = settings?.swapWeightReps == true,
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
private const val SetTabAnimationMillis = 360
private const val SetLayoutAnimationMillis = 320

@Composable
private fun RowScope.SessionSetTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
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
            color = if (selected) selectedColor else DenTrainPalette.MutedText
        )
    }
}

@Composable
private fun SessionSetRow(
    result: SetResultEntity?,
    swapWeightReps: Boolean,
    number: Int,
    isWarmup: Boolean,
    isCurrent: Boolean,
    onEdit: (SetResultEntity) -> Unit
) {
    val borderColor by animateColorAsState(
        when {
            !isCurrent -> DenTrainPalette.Border
            isWarmup -> WarmupAccent
            else -> DenTrainPalette.Lime
        },
        animationSpec = tween(260), label = "current set border"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (result != null) DenTrainPalette.RaisedPanel else DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
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
            text = if (isWarmup) "Р$number" else "№ $number",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DenTrainPalette.MutedText
        )
        Text(
            text = when {
                result != null -> Fmt.shortSetLine(result, swapWeightReps)
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
internal fun NotesEditDialog(
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
