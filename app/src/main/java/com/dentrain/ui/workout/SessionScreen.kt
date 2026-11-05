package com.dentrain.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.ConfettiOverlay
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.RecordCelebrationDialog
import com.dentrain.ui.components.SetEditDialog
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.ZScreen
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.DenTrainTheme
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
            repo.addSet(exercise.id, doneCount + 1, reps, weight, isTimeBased)
            val s = settings.value
            if (s != null && s.autoRestTimer) timer.restart(s.restSeconds)

            var newRecord: RecordEntity? = null
            if (weight != null && weight > 0) {
                val today = LocalDate.now().toEpochDay()
                val rec = RecordEntity(
                    exerciseName = exercise.name,
                    weight = weight,
                    reps = reps,
                    dateEpochDay = today,
                    comment = "Из тренировки",
                    isFromWorkout = true
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
            val currentKg = currentExItem.sets.sumOf { (it.weight ?: 0.0) * it.reps }

            val prevExItem = prevW.exercises.find { it.exercise.name.equals(exName, ignoreCase = true) }
            val prevKg = prevExItem?.sets?.sumOf { (it.weight ?: 0.0) * it.reps } ?: 0.0

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

    fun updateSet(set: SetResultEntity, reps: Int, weight: Double?) {
        viewModelScope.launch { repo.updateSet(set, reps, weight) }
    }
    fun deleteSet(set: SetResultEntity) { viewModelScope.launch { repo.deleteSet(set) } }
    fun addExtraSet(e: ExerciseEntity) { viewModelScope.launch { repo.addPlannedSet(e) } }
    fun goTo(index: Int) { viewModelScope.launch { repo.setCurrentExercise(workoutId, index) } }
    fun skipRest() = timer.reset()

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.finishWorkout(workoutId)
            timer.reset()
            onDone()
        }
    }
}

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

    // Режим тренировки: Стандартный (0) или Круговой (1)
    var isCircuitMode by remember { mutableStateOf(false) }
    // Режим единиц: Повторения (false) или Секунды (true)
    var isTimeBased by remember { mutableStateOf(false) }

    ZScreen("Тренировка", onBack) { pad ->
        val w = workout
        if (w == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@ZScreen
        }
        val exercises = w.exercises.sortedBy { it.exercise.position }
        if (exercises.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text("В тренировке нет упражнений") }
            return@ZScreen
        }
        val index = w.workout.currentExerciseIndex.coerceIn(0, exercises.lastIndex)
        val current = exercises[index]
        val ex = current.exercise
        val sets = current.sets.sortedBy { it.setNumber }
        val allDone = sets.size >= ex.plannedSets
        val isLast = index == exercises.lastIndex

        val totalPlannedSets = remember(exercises) {
            exercises.sumOf { maxOf(1, it.exercise.plannedSets) }
        }
        val currentCompletedSets = remember(exercises, index, sets.size) {
            var count = 0
            for (i in 0 until index) {
                val doneCount = exercises[i].sets.size
                val plannedCount = maxOf(1, exercises[i].exercise.plannedSets)
                count += doneCount.coerceAtMost(plannedCount)
            }
            count += sets.size.coerceAtMost(ex.plannedSets)
            count
        }

        val targetProgress = remember(currentCompletedSets, totalPlannedSets) {
            if (totalPlannedSets == 0) 0f
            else (currentCompletedSets.toFloat() / totalPlannedSets).coerceIn(0f, 1f)
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
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w.workout.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LiveDurationText(startedAt = startedAt)
                }

                // Переключатель режима: Стандартный / Круговой
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isCircuitMode,
                        onClick = { Haptic.click(context); isCircuitMode = false },
                        label = { Text("🏋️‍♂️ Стандартная", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isCircuitMode,
                        onClick = { Haptic.click(context); isCircuitMode = true },
                        label = { Text("🔄 Круговая", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Упражнение ${index + 1} из ${exercises.size}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        if (allDone) "Подходы выполнены" else "Подход ${sets.size + 1} из ${ex.plannedSets}",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                ZCard(gradient = true) {
                    Text(ex.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    val plan = buildList {
                        add("${ex.plannedSets} ${Fmt.plural(ex.plannedSets, "подход", "подхода", "подходов")}")
                        ex.plannedReps?.let { add("$it повт.") }
                        Fmt.weight(ex.plannedWeight)?.let { add("$it кг") }
                    }.joinToString(" · ")
                    Text("План: $plan", color = Color.White.copy(alpha = 0.9f))
                }

                if (timer.running || timer.finished) {
                    ZCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (timer.finished) "  Отдых окончен — пора на следующий подход" else "  Отдых: ${Fmt.clock(timer.remainingMs)}",
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { Haptic.click(context); vm.skipRest() }) {
                                Text(if (timer.finished) "Скрыть" else "Пропустить")
                            }
                        }
                    }
                }

                ZCard {
                    for (n in 1..maxOf(ex.plannedSets, sets.size)) {
                        val done = sets.getOrNull(n - 1)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .then(if (done != null) Modifier.clickable { Haptic.click(context); editing = done } else Modifier)
                                .alpha(if (done == null && n != sets.size + 1) 0.55f else 1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    done != null -> Icons.Filled.CheckCircle
                                    n == sets.size + 1 -> Icons.Filled.Edit
                                    else -> Icons.Filled.RadioButtonUnchecked
                                }, null,
                                tint = if (done != null || n == sets.size + 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "  Подход № $n — " + when {
                                    done != null -> Fmt.setLine(done)
                                    n == sets.size + 1 -> "введите результат"
                                    else -> "ожидает выполнения"
                                },
                                fontWeight = if (n == sets.size + 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    if (sets.isNotEmpty()) Text(
                        "Нажмите на выполненный подход, чтобы исправить его",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!allDone) {
                    var reps by remember(ex.id, sets.size) {
                        mutableStateOf(sets.lastOrNull()?.reps?.toString() ?: Regex("\\d+").find(ex.plannedReps ?: "")?.value ?: "")
                    }
                    var weight by remember(ex.id, sets.size) {
                        mutableStateOf(Fmt.weight(sets.lastOrNull()?.weight ?: ex.plannedWeight) ?: "")
                    }
                    var error by remember(ex.id, sets.size) { mutableStateOf<String?>(null) }
                    ZCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Результат подхода № ${sets.size + 1}", fontWeight = FontWeight.Bold)

                            // Переключатель типа единиц: Повторения / Секунды
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = !isTimeBased,
                                    onClick = { Haptic.click(context); isTimeBased = false },
                                    label = { Text("Повт.", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = isTimeBased,
                                    onClick = { Haptic.click(context); isTimeBased = true },
                                    label = { Text("⏱ Сек.", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reps, onValueChange = { reps = it.filter(Char::isDigit).take(4) },
                                label = { Text(if (isTimeBased) "Секунды" else "Повторения") }, singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                label = { Text("Вес, кг") }, singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ErrorText(error, Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                        GradientButton(
                            "Сохранить подход",
                            onClick = {
                                vm.saveSetAndCheckRecord(ex, sets.size, reps, weight, isTimeBased) { err, record ->
                                    if (err == null) {
                                        if (record != null) {
                                            Haptic.success(context)
                                            sessionRecordCelebration = record
                                        } else {
                                            Haptic.click(context)
                                        }

                                        // Круговой переход к следующему упражнению
                                        if (isCircuitMode && exercises.size > 1) {
                                            for (offset in 1 until exercises.size) {
                                                val nextIdx = (index + offset) % exercises.size
                                                val nextEx = exercises[nextIdx]
                                                if (nextEx.sets.size < nextEx.exercise.plannedSets) {
                                                    vm.goTo(nextIdx)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    error = err
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Check
                        )
                    }
                } else {
                    OutlinedButton(onClick = { Haptic.click(context); vm.addExtraSet(ex) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null); Text("  Добавить ещё подход")
                    }
                }

                if (!isLast) {
                    val next = exercises[index + 1].exercise
                    ZCard {
                        Text("Следующее упражнение", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Запланировано подходов: ${next.plannedSets}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) OutlinedButton(onClick = { Haptic.click(context); vm.goTo(index - 1) }) { Text("Назад") }
                    if (!isLast) {
                        if (allDone) GradientButton("Следующее упражнение", { Haptic.click(context); vm.goTo(index + 1) }, Modifier.weight(1f), icon = Icons.Filled.ArrowForward)
                        else OutlinedButton(onClick = { Haptic.click(context); vm.goTo(index + 1) }, modifier = Modifier.weight(1f)) { Text("Пропустить упражнение") }
                    } else {
                        GradientButton("Завершить тренировку", {
                            Haptic.success(context)
                            showGoalAccomplished = true
                        }, Modifier.weight(1f), icon = Icons.Filled.Flag)
                    }
                }
                if (!isLast) TextButton(onClick = { Haptic.click(context); showFinish = true }, modifier = Modifier.fillMaxWidth()) { Text("Завершить тренировку досрочно") }
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

        // Анимация 1: «Цель выполнена: Стрела в яблочко»
        if (showGoalAccomplished) {
            GoalAccomplishedDialog(
                onContinue = {
                    showGoalAccomplished = false
                    showGrowthComparison = vm.getSameDayGrowthComparison()
                },
                onDismiss = { showGoalAccomplished = false }
            )
        }

        // Анимация 2: «Ваш рост и прогресс в % по сравнению с прошлым разом»
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
                "Завершить тренировку?",
                "Выполнено подходов: ${w.doneSets()}. Тренировка будет сохранена в истории.",
                confirmText = "Завершить",
                onConfirm = { showFinish = false; vm.finish(onFinished) },
                onDismiss = { showFinish = false }
            )
        }

        editing?.let { set ->
            SetEditDialog(
                set = set,
                onSave = { r, wt -> vm.updateSet(set, r, wt); editing = null },
                onDelete = { vm.deleteSet(set); editing = null },
                onDismiss = { editing = null }
            )
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
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
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    ArrowTargetAnimation(progress = animProgress.value)
                }

                Text(
                    "Отличная работа! Все запланированные упражнения и подходы выполнены!",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )

                GradientButton(
                    "Посмотреть прогресс 📈",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Check
                )
            }
        }
    }
}

/** Анимированное окно результатов прироста в % по сравнению с прошлым днём (например, Среда - Среда). */
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
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📈", fontSize = 38.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ВАШ РОСТ И ПРОГРЕСС! 📈",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (comparison.prevDateEpochDay != null)
                            "Сравнение с последней тренировкой (${comparison.dayOfWeekName}, ${Fmt.shortDate(comparison.prevDateEpochDay)})"
                        else "Это ваша первая сохранённая тренировка в этот день недели (${comparison.dayOfWeekName})!",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (comparison.items.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Объём: ${item.currentKg.toInt()} кг (был ${item.prevKg.toInt()} кг)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val percentText = if (item.percent >= 0) "+%.1f%%".format(item.percent).replace('.', ',')
                                else "%.1f%%".format(item.percent).replace('.', ',')

                                val badgeColor = if (item.percent >= 0) Color(0xFF00E676) else MaterialTheme.colorScheme.error

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        percentText,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (comparison.prevDateEpochDay != null) {
                    Text(
                        "Все упражнения выполнены точно по плану!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                GradientButton(
                    "Сохранить и к итогам 🏆",
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Check
                )
            }
        }
    }
}

@Composable
private fun ArrowTargetAnimation(progress: Float) {
    val errorColor = MaterialTheme.colorScheme.error
    val brush = LocalGradient.current.brush

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerY = h / 2

        val targetX = w * 0.82f
        val targetRadius = 32.dp.toPx()

        drawCircle(color = errorColor, radius = targetRadius, center = Offset(targetX, centerY))
        drawCircle(color = Color.White, radius = targetRadius * 0.65f, center = Offset(targetX, centerY))
        drawCircle(color = errorColor, radius = targetRadius * 0.35f, center = Offset(targetX, centerY))
        drawCircle(color = Color(0xFFFFD700), radius = targetRadius * 0.15f, center = Offset(targetX, centerY))

        val flightProgress = (progress / 0.7f).coerceAtMost(1f)
        val arrowStartX = w * 0.05f
        val currentArrowX = arrowStartX + (targetX - arrowStartX) * flightProgress

        if (flightProgress > 0f) {
            drawLine(
                brush = brush,
                start = Offset(arrowStartX, centerY),
                end = Offset(currentArrowX, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val arrowLength = 36.dp.toPx()
        val arrowHeadX = currentArrowX
        val arrowTailX = currentArrowX - arrowLength

        drawLine(
            color = Color(0xFF8D6E63),
            start = Offset(arrowTailX, centerY),
            end = Offset(arrowHeadX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        val path = Path().apply {
            moveTo(arrowHeadX, centerY)
            lineTo(arrowHeadX - 10.dp.toPx(), centerY - 6.dp.toPx())
            lineTo(arrowHeadX - 10.dp.toPx(), centerY + 6.dp.toPx())
            close()
        }
        drawPath(path, color = Color(0xFF424242))

        drawLine(color = errorColor, start = Offset(arrowTailX, centerY), end = Offset(arrowTailX - 6.dp.toPx(), centerY - 6.dp.toPx()), strokeWidth = 3.dp.toPx())
        drawLine(color = errorColor, start = Offset(arrowTailX, centerY), end = Offset(arrowTailX - 6.dp.toPx(), centerY + 6.dp.toPx()), strokeWidth = 3.dp.toPx())

        if (progress > 0.7f) {
            val hitProgress = (progress - 0.7f) / 0.3f
            val burstRadius = targetRadius * 1.6f * hitProgress
            val alpha = (1f - hitProgress).coerceIn(0f, 1f)

            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = alpha),
                radius = burstRadius,
                center = Offset(targetX, centerY),
                style = Stroke(width = 4.dp.toPx())
            )

            val sparkDist = targetRadius * (0.8f + hitProgress * 0.9f)
            for (angleDeg in listOf(0, 45, 90, 135, 180, 225, 270, 315)) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val sparkX = targetX + sparkDist * Math.cos(rad).toFloat()
                val sparkY = centerY + sparkDist * Math.sin(rad).toFloat()
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = alpha),
                    radius = 4.dp.toPx(),
                    center = Offset(sparkX, sparkY)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutProgressGrowthPreview() {
    DenTrainTheme {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📈", fontSize = 38.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ВАШ РОСТ И ПРОГРЕСС! 📈",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Сравнение с последней тренировкой (Среда, 14 окт.)",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            ExerciseGrowthItem("Жим штанги лежа", 800.0, 640.0, 25.0),
                            ExerciseGrowthItem("Приседания со штангой", 1200.0, 1067.0, 12.5),
                            ExerciseGrowthItem("Тяга верхнего блока", 450.0, 400.0, 12.5)
                        ).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Объём: ${item.currentKg.toInt()} кг (был ${item.prevKg.toInt()} кг)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val percentText = "+%.1f%%".format(item.percent).replace('.', ',')
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        percentText,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00E676),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    GradientButton(
                        "Сохранить и к итогам 🏆",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Check
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveDurationText(startedAt: Long) {
    var liveDurationMs by remember { mutableLongStateOf(System.currentTimeMillis() - startedAt) }

    LaunchedEffect(startedAt) {
        while (true) {
            liveDurationMs = System.currentTimeMillis() - startedAt
            delay(1000)
        }
    }

    Text(
        "⏱ ${Fmt.formatDurationMs(liveDurationMs)}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

