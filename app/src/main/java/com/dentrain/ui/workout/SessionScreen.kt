package com.dentrain.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import com.dentrain.util.doneSets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val workout: StateFlow<WorkoutWithExercises?> =
        repo.observeWorkout(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timerState: StateFlow<TimerState> = timer.state
    private val completedWorkouts = repo.completed.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveSetAndCheckRecord(
        exercise: ExerciseEntity,
        doneCount: Int,
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
        if (weightText.isNotBlank() && (weight == null || weight < 0)) {
            onResult("Вес — число не меньше 0", null)
            return
        }

        viewModelScope.launch {
            repo.addSet(exercise.id, doneCount + 1, reps, weight, isTimeBased, isWarmup)
            val s = settings.value
            if (s != null && s.autoRestTimer) timer.restart(s.restSeconds)

            var newRecord: RecordEntity? = null
            if (!isWarmup && weight != null && weight > 0) {
                val today = LocalDate.now().toEpochDay()
                val rec = RecordEntity(
                    exerciseName = exercise.name,
                    weight = weight,
                    reps = reps,
                    dateEpochDay = today,
                    comment = ""
                )
                val (isNew, saved) = repo.saveOrUpdateRecord(rec)
                if (isNew) {
                    newRecord = saved
                }
            }
            onResult(null, newRecord)
        }
    }

    /** Сравнение прогресса с последней тренировкой в ЭТОТ ЖЕ ДЕНЬ НЕДЕЛИ (например, среда -> среда). */
    fun getSameDayGrowthComparison(): GrowthComparisonResult {
        val currentW = workout.value ?: return GrowthComparisonResult("", null, emptyList())
        val currentDayOfWeek = currentW.workout.dayOfWeek

        val prevW = completedWorkouts.value
            .filter { it.workout.dayOfWeek == currentDayOfWeek && it.workout.dateEpochDay < currentW.workout.dateEpochDay }
            .maxByOrNull { it.workout.dateEpochDay }

        if (prevW == null) {
            return GrowthComparisonResult(
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

        return GrowthComparisonResult(
            dayOfWeekName = Fmt.dayName(currentDayOfWeek),
            prevDateEpochDay = prevW.workout.dateEpochDay,
            items = items
        )
    }

    fun updateSet(set: SetResultEntity, reps: Int, weight: Double?, isWarmup: Boolean = set.isWarmup) {
        viewModelScope.launch { repo.updateSet(set, reps, weight, isWarmup) }
    }
    fun deleteSet(set: SetResultEntity) { viewModelScope.launch { repo.deleteSet(set) } }
    fun addExtraSet(e: ExerciseEntity) { viewModelScope.launch { repo.addPlannedSet(e) } }
    fun removePlannedSet(e: ExerciseEntity) { viewModelScope.launch { repo.removePlannedSet(e) } }
    fun addExerciseToWorkout(name: String) { viewModelScope.launch { repo.addExerciseToWorkout(workoutId, name) } }
    fun goTo(index: Int) { viewModelScope.launch { repo.setCurrentExercise(workoutId, index) } }
    fun skipRest() = timer.reset()

    fun updateDescription(desc: String) {
        viewModelScope.launch { repo.updateWorkoutDescription(workoutId, desc) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
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

@Composable
private fun sessionTfColors(isFilled: Boolean, hasError: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF1B1D25),
    unfocusedContainerColor = Color(0xFF1B1D25),
    focusedBorderColor = if (hasError) Color(0xFFFF5252) else Color(0xFFA6FF00),
    unfocusedBorderColor = if (hasError) Color(0xFFFF5252) else if (isFilled) Color.White else Color(0xFF333644),
    focusedLabelColor = if (hasError) Color(0xFFFF5252) else Color(0xFFA6FF00),
    unfocusedLabelColor = if (hasError) Color(0xFFFF5252) else if (isFilled) Color(0xFFE0E0E6) else Color(0xFFA0A7B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = if (hasError) Color(0xFFFF5252) else Color(0xFFA6FF00)
)

@Composable
private fun LiveDurationText(startedAt: Long) {
    var elapsedSeconds by remember { mutableLongStateOf((System.currentTimeMillis() - startedAt) / 1000L) }
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
    val workout by vm.workout.collectAsStateWithLifecycle()
    val timer by vm.timerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFinish by remember { mutableStateOf(false) }
    var showGoalAccomplished by remember { mutableStateOf(false) }
    var showGrowthComparison by remember { mutableStateOf<GrowthComparisonResult?>(null) }
    var sessionRecordCelebration by remember { mutableStateOf<RecordEntity?>(null) }
    var editing by remember { mutableStateOf<SetResultEntity?>(null) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var isInputWarmup by remember { mutableStateOf(false) }
    var showQuickAddExercise by remember { mutableStateOf(false) }

    // Режим тренировки: Стандартный (false) или Круговой (true)
    var isCircuitMode by remember { mutableStateOf(false) }
    // Режим единиц: Повторения (false) или Секунды (true)
    var isTimeBased by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Текущая тренировка",
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
                        // Стандартная (⚡)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (!isCircuitMode) Color(0xFF242732) else Color(0xFF1B1D25))
                                .border(
                                    BorderStroke(1.dp, if (!isCircuitMode) Color(0xFFA6FF00) else Color(0xFF333644)),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    Haptic.click(context)
                                    isCircuitMode = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(text = "⚡", fontSize = 11.sp)
                                Text(
                                    text = "стандарт",
                                    fontSize = 11.sp,
                                    fontWeight = if (!isCircuitMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isCircuitMode) Color.White else Color(0xFFA0A7B8)
                                )
                            }
                        }

                        // Круговая (🔁)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCircuitMode) Color(0xFF242732) else Color(0xFF1B1D25))
                                .border(
                                    BorderStroke(1.dp, if (isCircuitMode) Color(0xFFA6FF00) else Color(0xFF333644)),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    Haptic.click(context)
                                    isCircuitMode = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(text = "🔁", fontSize = 11.sp)
                                Text(
                                    text = "круговой",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCircuitMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCircuitMode) Color.White else Color(0xFFA0A7B8)
                                )
                            }
                        }

                        IconButton(onClick = {
                            Haptic.click(context)
                            showFinish = true
                        }) {
                            Icon(Icons.Filled.Flag, "Завершить", tint = Color(0xFFFF5252))
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
                CircularProgressIndicator(color = Color(0xFFA6FF00))
            }
            return@Scaffold
        }
        val exercises = w.exercises.sortedBy { it.exercise.position }
        if (exercises.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                Text("В тренировке нет упражнений", color = Color.White)
            }
            return@Scaffold
        }
        val index = w.workout.currentExerciseIndex.coerceIn(0, exercises.lastIndex)
        val current = exercises[index]
        val ex = current.exercise
        val sets = current.sets.sortedBy { it.setNumber }
        val warmupSets = remember(sets) { sets.filter { it.isWarmup } }
        val workingSets = remember(sets) { sets.filter { !it.isWarmup } }
        val workingDoneCount = workingSets.size
        val allDone = workingDoneCount >= ex.plannedSets
        val isLast = index == exercises.lastIndex

        val totalPlannedWorkingSets = remember(exercises) {
            exercises.sumOf { maxOf(1, it.exercise.plannedSets) }
        }
        val currentCompletedWorkingSets = remember(exercises, index, workingSets.size) {
            var count = 0
            for (i in 0 until index) {
                val doneWorkingCount = exercises[i].sets.count { !it.isWarmup }
                val plannedCount = maxOf(1, exercises[i].exercise.plannedSets)
                count += doneWorkingCount.coerceAtMost(plannedCount)
            }
            count += workingSets.size.coerceAtMost(ex.plannedSets)
            count
        }

        val targetProgress = remember(currentCompletedWorkingSets, totalPlannedWorkingSets) {
            if (totalPlannedWorkingSets == 0) 0f
            else (currentCompletedWorkingSets.toFloat() / totalPlannedWorkingSets).coerceIn(0f, 1f)
        }

        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "smoothWorkoutProgress"
        )

        LaunchedEffect(isLast, allDone) {
            if (isLast && allDone) {
                Haptic.success(context)
                showGoalAccomplished = true
            }
        }

        val startedAt = w.workout.startedAt ?: System.currentTimeMillis()

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Шапка сессии: Время, Название тренировки
                item {
                    SessionCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Время занятий:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA0A7B8)
                            )
                            LiveDurationText(startedAt = startedAt)
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = w.workout.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Упражнение ${index + 1} из ${exercises.size}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Подход 2 из 3 в обводке: обе цифры зеленые (#A6FF00)
                            if (allDone) {
                                Text(
                                    text = "Подходы выполнены ✅",
                                    fontSize = 13.sp,
                                    color = Color(0xFFA6FF00),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF242732))
                                        .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Подход ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = "${workingSets.size + 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA6FF00))
                                    Text(text = " из ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = "${ex.plannedSets}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFA6FF00))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Единый гладкий прогресс-бар
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFA6FF00),
                            trackColor = Color(0xFF242732)
                        )
                    }
                }

                // Карточка текущего упражнения (золотая обводка, без левой полоски)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1D25))
                            .border(BorderStroke(1.5.dp, Color(0xFFFFB300)), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = ex.name,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(4.dp))
                            val plan = buildList {
                                add("${ex.plannedSets} ${Fmt.plural(ex.plannedSets, "подход", "подхода", "подходов")}")
                                ex.plannedReps?.let { add("$it повт.") }
                                Fmt.weight(ex.plannedWeight)?.let { add("$it кг") }
                            }.joinToString(" · ")
                            Text(
                                text = "План: $plan",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFA0A7B8)
                            )
                        }
                    }
                }

                // Карточка таймера отдыха с плавной анимацией появления/исчезновения
                item {
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
                                .background(Color(0xFF242732))
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
                                        .background(Color(0xFF1B1D25))
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

                // Список выполненных подходов (Единая карточка: разминка + рабочие)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1D25))
                            .border(BorderStroke(1.dp, Color(0xFFA6FF00)), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            // 1. Блок РАЗМИНКА (если есть разминочные подходы)
                            if (warmupSets.isNotEmpty()) {
                                Text(
                                    text = "Разминка:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA0A7B8)
                                )
                                Spacer(Modifier.height(6.dp))

                                warmupSets.forEachIndexed { i, s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF242732))
                                            .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                            .clickable {
                                                Haptic.click(context)
                                                editing = s
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF6C7287),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Р${i + 1} — ",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFA0A7B8)
                                        )
                                        Text(
                                            text = Fmt.shortSetLine(s),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFA0A7B8)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                // Полупрозрачная разделяющая линия
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFF333644).copy(alpha = 0.5f))
                                )
                                Spacer(Modifier.height(10.dp))
                            }

                            // 2. Блок РАБОЧИЕ ПОДХОДЫ
                            Text(
                                text = "Рабочие подходы:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))

                            val totalCount = maxOf(ex.plannedSets, workingSets.size)
                            for (n in 1..totalCount) {
                                val done = workingSets.getOrNull(n - 1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (done != null) Color(0xFF242732) else Color(0xFF1B1D25))
                                        .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                        .then(if (done != null) Modifier.clickable {
                                            Haptic.click(context)
                                            editing = done
                                        } else Modifier)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .alpha(if (done == null && n != workingSets.size + 1) 0.5f else 1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            done != null -> Icons.Filled.CheckCircle
                                            n == workingSets.size + 1 -> Icons.Filled.Edit
                                            else -> Icons.Filled.RadioButtonUnchecked
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            done != null -> Color(0xFFA6FF00)
                                            n == workingSets.size + 1 -> Color(0xFF00B0FF)
                                            else -> Color(0xFF555B6E)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "№ $n — ",
                                        fontSize = 13.sp,
                                        fontWeight = if (done != null) FontWeight.Bold else FontWeight.Medium,
                                        color = if (done != null) Color.White else Color(0xFFA0A7B8)
                                    )
                                    Text(
                                        text = when {
                                            done != null -> Fmt.shortSetLine(done)
                                            n == workingSets.size + 1 -> "введите результат ниже"
                                            else -> "ожидает выполнения"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = if (done != null) FontWeight.Bold else FontWeight.Medium,
                                        color = if (done != null) Color.White else Color(0xFFA0A7B8)
                                    )
                                }
                            }

                            if (sets.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "нажми на подход, чтобы изменить его",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA0A7B8)
                                )
                            }
                        }
                    }
                }

                // Форма ввода результата подхода
                item {
                    if (!allDone) {
                        var reps by remember(ex.id, sets.size) {
                            mutableStateOf(workingSets.lastOrNull()?.reps?.toString() ?: Regex("\\d+").find(ex.plannedReps ?: "")?.value ?: "")
                        }
                        var weight by remember(ex.id, sets.size) {
                            mutableStateOf(Fmt.weight(workingSets.lastOrNull()?.weight ?: ex.plannedWeight) ?: "")
                        }
                        var error by remember(ex.id, sets.size) { mutableStateOf<String?>(null) }
                        val blueAccent = Color(0xFF00B0FF)

                        SessionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isInputWarmup) "Результат разминки Р${warmupSets.size + 1}"
                                               else "Результат подхода № ${workingSets.size + 1}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    // Переключатель Повт. / Сек.
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (!isTimeBased) Color(0xFF242732) else Color(0xFF1B1D25))
                                                .border(BorderStroke(1.dp, if (!isTimeBased) Color(0xFFA6FF00) else Color(0xFF333644)), CircleShape)
                                                .clickable {
                                                    Haptic.click(context)
                                                    isTimeBased = false
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Повт.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isTimeBased) Color.White else Color(0xFFA0A7B8))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isTimeBased) Color(0xFF242732) else Color(0xFF1B1D25))
                                                .border(BorderStroke(1.dp, if (isTimeBased) Color(0xFFA6FF00) else Color(0xFF333644)), CircleShape)
                                                .clickable {
                                                    Haptic.click(context)
                                                    isTimeBased = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("⏱ Сек.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isTimeBased) Color.White else Color(0xFFA0A7B8))
                                        }
                                    }
                                }

                                // Сине-голубой переключатель [ Разминка | Рабочий подход ]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(if (isInputWarmup) blueAccent.copy(alpha = 0.2f) else Color(0xFF242732))
                                            .border(BorderStroke(1.dp, if (isInputWarmup) blueAccent else Color(0xFF333644)), CircleShape)
                                            .clickable {
                                                Haptic.click(context)
                                                isInputWarmup = true
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Разминка",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isInputWarmup) blueAccent else Color(0xFFA0A7B8)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(if (!isInputWarmup) blueAccent.copy(alpha = 0.2f) else Color(0xFF242732))
                                            .border(BorderStroke(1.dp, if (!isInputWarmup) blueAccent else Color(0xFF333644)), CircleShape)
                                            .clickable {
                                                Haptic.click(context)
                                                isInputWarmup = false
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Рабочий подход",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isInputWarmup) blueAccent else Color(0xFFA0A7B8)
                                        )
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
                                        colors = sessionTfColors(isFilled = reps.isNotBlank(), hasError = error != null),
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
                                        colors = sessionTfColors(isFilled = weight.isNotBlank(), hasError = error != null),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (error != null) {
                                    ErrorText(error, Modifier.padding(top = 4.dp))
                                }

                                Button(
                                    onClick = {
                                        vm.saveSetAndCheckRecord(ex, sets.size, reps, weight, isTimeBased, isInputWarmup) { err, record ->
                                            if (err == null) {
                                                if (record != null) {
                                                    Haptic.success(context)
                                                    sessionRecordCelebration = record
                                                } else {
                                                    Haptic.click(context)
                                                }

                                                if (isCircuitMode && exercises.size > 1) {
                                                    for (offset in 1 until exercises.size) {
                                                        val nextIdx = (index + offset) % exercises.size
                                                        val nextEx = exercises[nextIdx]
                                                        val nextWorkingDone = nextEx.sets.count { !it.isWarmup }
                                                        if (nextWorkingDone < nextEx.exercise.plannedSets) {
                                                            vm.goTo(nextIdx)
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                            error = err
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isInputWarmup) blueAccent else Color(0xFFA6FF00),
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
                    } else {
                        Button(
                            onClick = {
                                Haptic.click(context)
                                vm.addExtraSet(ex)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF242732),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFA6FF00), modifier = Modifier.size(18.dp))
                                Text("Добавить ещё подход", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Кнопки управления подходом и упражнениями (+ разминка, + подход, - подход, + упражнение)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "+ разминка"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color(0xFF00B0FF)), RoundedCornerShape(10.dp))
                                .clickable {
                                    Haptic.click(context)
                                    isInputWarmup = true
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Text("+ разминка", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B0FF))
                        }

                        // "+ подход"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                .clickable {
                                    Haptic.click(context)
                                    vm.addExtraSet(ex)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ подход", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // "- подход"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(10.dp))
                                .clickable {
                                    Haptic.click(context)
                                    vm.removePlannedSet(ex)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("- подход", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // "+ упражнение"
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242732))
                                .border(BorderStroke(1.dp, Color(0xFFA6FF00)), RoundedCornerShape(10.dp))
                                .clickable {
                                    Haptic.click(context)
                                    showQuickAddExercise = true
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ упражнение", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA6FF00))
                        }
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Заметки к тренировке",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF242732))
                                    .border(BorderStroke(1.dp, Color(0xFFA6FF00)), RoundedCornerShape(8.dp))
                                    .clickable {
                                        Haptic.click(context)
                                        showNotesDialog = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (w.workout.description.isNotBlank()) "Изменить" else "Добавить",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF242732))
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
                                color = if (w.workout.description.isNotBlank()) Color.White else Color(0xFFA0A7B8)
                            )
                        }
                    }
                }

                // Следующее упражнение с белой обводкой и стрелкой вниз
                item {
                    if (!isLast) {
                        val next = exercises[index + 1].exercise
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFFA0A7B8),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1B1D25))
                                    .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Следующее упражнение:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFA0A7B8)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = next.name,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "подходов ${next.plannedSets}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFA0A7B8)
                                    )
                                }
                            }
                        }
                    }
                }

                // Кнопки навигации по упражнениям (Назад и Пропустить)
                item {
                    if (index > 0 || !isLast) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index > 0) {
                                Button(
                                    onClick = {
                                        Haptic.click(context)
                                        vm.goTo(index - 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF242732),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Назад", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            if (!isLast) {
                                Button(
                                    onClick = {
                                        Haptic.click(context)
                                        vm.goTo(index + 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF242732),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(40.dp)
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
                                            Icons.Filled.ArrowForward,
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
                    showGrowthComparison = vm.getSameDayGrowthComparison()
                },
                onDismiss = { showGoalAccomplished = false }
            )
        }

        showGrowthComparison?.let { comp ->
            WorkoutProgressGrowthDialog(
                comparison = comp,
                onFinish = {
                    showGrowthComparison = null
                    vm.finish(onFinished)
                }
            )
        }

        if (showFinish) {
            ConfirmDialog(
                title = "Завершить тренировку?",
                text = "Выполнено подходов: ${w.doneSets()}. Тренировка будет сохранена в истории.",
                confirmText = "Завершить",
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

        if (showQuickAddExercise) {
            QuickAddExerciseDialog(
                onAdd = { name -> vm.addExerciseToWorkout(name) },
                onDismiss = { showQuickAddExercise = false }
            )
        }
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D25)),
            border = BorderStroke(1.dp, Color(0xFF282B36)),
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
                    label = { Text("Название упражнения", color = Color(0xFFA0A7B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA6FF00),
                        unfocusedBorderColor = Color(0xFF333644),
                        focusedContainerColor = Color(0xFF242732),
                        unfocusedContainerColor = Color(0xFF242732)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = Color(0xFFA0A7B8))
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(name.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA6FF00),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D25)),
            border = BorderStroke(1.dp, Color(0xFF282B36)),
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
                        .background(Color(0xFF242732))
                        .border(BorderStroke(1.5.dp, notesBorderBrush), RoundedCornerShape(12.dp))
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(300) },
                        placeholder = { Text("Напишите заметку к занятию...", color = Color(0xFFA0A7B8)) },
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
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFA0A7B8))
                    ) {
                        Text("Отмена", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onSave(text.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA6FF00),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D25)),
            border = BorderStroke(1.dp, Color(0xFF282B36)),
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
                    color = Color(0xFFA6FF00),
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
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA6FF00),
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
    onFinish: () -> Unit
) {
    val scale = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
        )
    }

    Dialog(onDismissRequest = onFinish) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D25)),
            border = BorderStroke(1.dp, Color(0xFF282B36)),
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
                        .background(Color(0xFF242732))
                        .border(BorderStroke(1.dp, Color(0xFFA6FF00)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📈", fontSize = 36.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ВАШ РОСТ И ПРОГРЕСС! 📈",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFA6FF00),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (comparison.prevDateEpochDay != null)
                            "Сравнение с последней тренировкой (${comparison.dayOfWeekName}, ${Fmt.shortDate(comparison.prevDateEpochDay)})"
                        else "Это ваша первая сохранённая тренировка в этот день недели (${comparison.dayOfWeekName})!",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFA0A7B8)
                    )
                }

                if (comparison.items.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF242732))
                            .border(BorderStroke(1.dp, Color(0xFF333644)), RoundedCornerShape(14.dp))
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
                                        color = Color(0xFFA0A7B8)
                                    )
                                }
                                val percentText = if (item.percent >= 0) "+%.1f%%".format(item.percent).replace('.', ',')
                                else "%.1f%%".format(item.percent).replace('.', ',')

                                val badgeColor = if (item.percent >= 0) Color(0xFFA6FF00) else Color(0xFFFF5252)

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
                        color = Color(0xFFA0A7B8),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA6FF00),
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

        drawCircle(color = Color(0xFFFF5252), radius = targetRadius, center = Offset(targetX, centerY))
        drawCircle(color = Color.White, radius = targetRadius * 0.65f, center = Offset(targetX, centerY))
        drawCircle(color = Color(0xFFFF5252), radius = targetRadius * 0.35f, center = Offset(targetX, centerY))

        val arrowStartX = 20.dp.toPx()
        val currentArrowX = arrowStartX + (targetX - arrowStartX) * progress

        val path = Path().apply {
            moveTo(arrowStartX, centerY)
            lineTo(currentArrowX, centerY)
        }
        drawPath(
            path = path,
            color = Color(0xFFA6FF00),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(
            color = Color(0xFFA6FF00),
            radius = 6.dp.toPx(),
            center = Offset(currentArrowX, centerY)
        )
    }
}
