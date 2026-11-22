package com.dentrain.ui.stopwatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.WorkoutRepository
import com.dentrain.timer.DirectStopwatchState
import com.dentrain.timer.RestTimer
import com.dentrain.timer.StopwatchLap
import com.dentrain.timer.StopwatchTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.DenTrainPalette
import com.dentrain.util.Fmt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

class StopwatchViewModel(
    private val repo: WorkoutRepository,
    private val restTimer: RestTimer,
    private val directTimer: StopwatchTimer
) : ViewModel() {
    val restState = restTimer.state
    val directState = directTimer.state

    init {
        viewModelScope.launch { restTimer.ensureConfigured(repo.settings.first().restSeconds) }
    }

    fun selectRest(seconds: Int) {
        restTimer.setDuration(seconds)
        viewModelScope.launch { repo.updateSettings { it.copy(restSeconds = seconds) } }
    }

    fun startRest() = restTimer.start()
    fun pauseRest() = restTimer.pause()
    fun resetRest() = restTimer.reset()
    fun selectDelay(seconds: Int) = directTimer.setDelay(seconds)
    fun startDirect() = directTimer.start()
    fun pauseDirect() = directTimer.pause()
    fun resetDirect() = directTimer.reset()
    fun lap() = directTimer.lap()
}

private val accent = DenTrainPalette.NeonGreen
private val cardColor = Color(0xFF15161A)
private val cardBorder = Color(0xFF27292F)
private val controlBorder = Color(0xFF3A3C43)
private val secondaryText = Color(0xFFBCC1D0)
private val cardShape = RoundedCornerShape(16.dp)
private val dialStroke = 12.dp
private val clockStyle = TextStyle(fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
private val restPresets = listOf(30, 60, 90, 120, 180)
private val delayPresets = listOf(0, 3, 5, 10, 15)
private const val SectionEnterMs = 350
private const val SectionExitMs = 500

@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    val vm = appViewModel { StopwatchViewModel(it.repository, it.restTimer, it.stopwatchTimer) }
    var stopwatchSelected by rememberSaveable { mutableStateOf(false) }
    val modeState = rememberSaveableStateHolder()

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(DenTrainPalette.Panel)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", modifier = Modifier.size(21.dp))
            }
            Text("Таймер", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        ModeSelector(stopwatchSelected) { stopwatchSelected = it }

        AnimatedContent(
            targetState = stopwatchSelected,
            transitionSpec = {
                (fadeIn(tween(SectionEnterMs)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform { initial, target ->
                        tween(if (target.height >= initial.height) SectionEnterMs else SectionExitMs)
                    })
            },
            label = "Timer mode content"
        ) { showStopwatch ->
            modeState.SaveableStateProvider(showStopwatch) {
                if (showStopwatch) DirectStopwatchContent(vm) else RestTimerContent(vm)
            }
        }
    }
}

@Composable
private fun ModeSelector(stopwatchSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val transition = updateTransition(stopwatchSelected, label = "Timer mode")
    // The trailing edge moves first, narrowing the highlight before it grows into the new tab.
    val left = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 280, delayMillis = if (targetState) 0 else 140) },
        label = "Highlight left"
    ) { if (it) 0.5f else 0f }
    val right = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 280, delayMillis = if (targetState) 140 else 0) },
        label = "Highlight right"
    ) { if (it) 1f else 0.5f }

    Box(
        Modifier.fillMaxWidth().clip(cardShape).background(cardColor)
            .border(1.dp, cardBorder, cardShape).selectableGroup().padding(2.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val width = ((right.value - left.value) * size.width).coerceAtLeast(0f)
            drawRoundRect(
                color = accent,
                topLeft = Offset(left.value * size.width, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(minOf(13.dp.toPx(), width / 2f, size.height / 2f))
            )
        }
        Row {
            ModeTab("Таймер отдыха", Icons.Filled.HourglassEmpty, !stopwatchSelected, Modifier.weight(1f)) { onSelect(false) }
            ModeTab("Секундомер", Icons.Outlined.Timer, stopwatchSelected, Modifier.weight(1f)) { onSelect(true) }
        }
    }
}

@Composable
private fun ModeTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (selected) Color.Black else secondaryText,
        tween(160, delayMillis = if (selected) 170 else 0),
        label = "Timer tab text"
    )
    Row(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(13.dp))
            .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RestTimerContent(vm: StopwatchViewModel) {
    val current = vm.restState.collectAsStateWithLifecycle()
    val running by remember(current) { derivedStateOf { current.value.running } }
    val canResume by remember(current) { derivedStateOf { current.value.remainingMs in 1 until current.value.totalMs } }
    val total by remember(current) { derivedStateOf { current.value.totalMs } }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startRest() }

    fun start() {
        val state = current.value
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && state.remainingMs == state.totalMs) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startRest()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DialCard(dial = { RestDial(current) }) {
            StartButton(running, canResume, Modifier.weight(1f)) { if (running) vm.pauseRest() else start() }
            TimerButton("Сброс", vm::resetRest, Modifier.weight(1f), icon = Icons.Filled.Refresh)
        }
        RestDurationCard(total, vm::selectRest)
    }
}

@Composable
private fun RestDial(current: State<TimerState>) {
    val deadline by remember(current) { derivedStateOf { current.value.endsAtElapsedMs } }
    val total by remember(current) { derivedStateOf { current.value.totalMs } }
    val stationaryMs by remember(current) {
        derivedStateOf { current.value.remainingMs.takeUnless { current.value.running } }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val progress = remember {
        mutableFloatStateOf(restFraction(current.value.remainingMs, current.value.totalMs))
    }
    LaunchedEffect(lifecycle, deadline, total, stationaryMs) {
        val endsAt = deadline
        if (endsAt != null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    withFrameNanos {
                        progress.floatValue = restFraction(endsAt - SystemClock.elapsedRealtime(), total)
                    }
                }
            }
        } else {
            val target = restFraction(stationaryMs ?: 0L, total)
            if (stationaryMs == total) {
                animate(progress.floatValue, target, animationSpec = tween(SectionEnterMs)) { value, _ ->
                    progress.floatValue = value
                }
            } else {
                progress.floatValue = target
            }
        }
    }

    DialBox {
        Canvas(Modifier.matchParentSize()) {
            val fraction = progress.floatValue
            val stroke = dialStroke.toPx()
            val diameter = size.width - stroke
            drawCircle(controlBorder, radius = diameter / 2f, style = Stroke(stroke))
            if (fraction >= 1f) {
                drawCircle(accent, radius = diameter / 2f, style = Stroke(stroke))
            } else if (fraction > 0f) {
                drawArc(
                    accent, -90f, 360f * fraction, false,
                    Offset(stroke / 2f, stroke / 2f), Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        RestReadout(current)
    }
}

@Composable
private fun RestReadout(current: State<TimerState>) {
    val seconds by remember(current) {
        derivedStateOf { (current.value.remainingMs + 999L) / 1000L }
    }
    val status by remember(current) {
        derivedStateOf {
            val state = current.value
            when {
                state.finished -> "Отдых окончен!"
                state.running -> "Идёт отдых"
                state.remainingMs in 1 until state.totalMs -> "Пауза"
                else -> "Готов к старту"
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Fmt.clock(seconds * 1000), style = clockStyle, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold)
        Text(status, color = secondaryText, fontSize = 15.sp)
    }
}

private fun restFraction(remainingMs: Long, totalMs: Long): Float =
    if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

@Composable
private fun RestDurationCard(totalMs: Long, onSelect: (Int) -> Unit) {
    var customRest by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun applyDuration() {
        val seconds = customRest.toIntOrNull()
        if (seconds == null || seconds !in 5..3600) {
            error = true
        } else {
            onSelect(seconds)
            customRest = ""
            error = false
            focus.clearFocus()
        }
    }

    TimerCard {
        Text("Время отдыха", Modifier.fillMaxWidth(), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            restPresets.forEach { seconds ->
                PresetChip("$seconds с", totalMs == seconds * 1000L, Modifier.weight(1f)) { onSelect(seconds) }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val shape = RoundedCornerShape(10.dp)
            BasicTextField(
                value = customRest,
                onValueChange = { customRest = it.filter { char -> char in '0'..'9' }.take(4); error = false },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyDuration() }),
                modifier = Modifier.weight(1f).clip(shape).background(Color(0xFF191B1F))
                    .border(1.dp, if (error) MaterialTheme.colorScheme.error else controlBorder, shape),
                decorationBox = { input ->
                    Box(Modifier.heightIn(min = 46.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (customRest.isEmpty()) {
                            Text("Своё время, секунд", color = secondaryText, fontSize = 14.sp)
                        }
                        input()
                    }
                }
            )
            TimerButton("Задать", ::applyDuration, Modifier.widthIn(min = 76.dp))
        }
        if (error) {
            Text("Введите от 5 до 3600 секунд", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DirectStopwatchContent(vm: StopwatchViewModel) {
    val current = vm.directState.collectAsStateWithLifecycle()
    val state by current
    val active = state.isRunning || state.isDelaying
    val hasElapsed = state.isRunning || state.elapsedMs > 0L
    val reveal = animateFloatAsState(
        if (hasElapsed) 1f else 0f,
        tween(if (hasElapsed) 1450 else 650, easing = LinearEasing),
        label = "Stopwatch segments"
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DialCard(dial = { DirectDial(state, reveal) }) {
            StartButton(active, state.elapsedMs > 0L, Modifier.weight(1f)) {
                if (active) vm.pauseDirect() else vm.startDirect()
            }
            TimerButton(
                if (state.isRunning) "Круг" else "Сброс",
                if (state.isRunning) vm::lap else vm::resetDirect,
                Modifier.weight(1f),
                icon = Icons.Filled.Refresh
            )
        }

        TimerCard {
            Text("Отсрочка старта", Modifier.fillMaxWidth(), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                delayPresets.forEach { seconds ->
                    PresetChip(
                        if (seconds == 0) "Выкл." else "$seconds с",
                        state.selectedDelaySec == seconds,
                        Modifier.weight(1f),
                        enabled = !state.isDelaying
                    ) { vm.selectDelay(seconds) }
                }
            }
        }

        AnimatedVisibility(
            visible = state.laps.isNotEmpty(),
            enter = fadeIn(tween(SectionEnterMs)) + expandVertically(tween(SectionEnterMs)),
            exit = fadeOut(tween(SectionExitMs)) + shrinkVertically(tween(SectionExitMs))
        ) {
            Laps(state.laps)
        }
    }
}

@Composable
private fun DirectDial(state: DirectStopwatchState, reveal: State<Float>) {
    val clock = rememberFrameClock(state.isRunning || state.isDelaying)
    val delayEndAt = state.delayEndsAtMs
    DialBox {
        Canvas(Modifier.matchParentSize()) {
            val stroke = dialStroke.toPx()
            val diameter = size.width - stroke
            val origin = Offset(stroke / 2f, stroke / 2f)
            val bounds = Size(diameter, diameter)
            val segments = 12
            val step = 360f / segments
            val fill = reveal.value * segments
            if (fill < 1f) {
                drawCircle(controlBorder, radius = diameter / 2f, alpha = 1f - fill, style = Stroke(stroke))
            }
            if (fill > 0f) {
                val segmentStroke = Stroke(stroke, cap = StrokeCap.Round)
                repeat(segments) { index ->
                    val fraction = (fill - index).coerceIn(0f, 1f)
                    val start = -90f - (index * step + 5f)
                    drawArc(
                        controlBorder, start - 20f, 20f, false, origin, bounds,
                        alpha = fill.coerceAtMost(1f), style = segmentStroke
                    )
                    if (fraction > 0f) {
                        val sweep = 20f * fraction
                        drawArc(
                            accent, start - sweep, sweep, false, origin, bounds,
                            alpha = (fraction * 3f).coerceAtMost(1f),
                            style = segmentStroke
                        )
                    }
                }
            }
            if (delayEndAt != null) {
                val fraction = restFraction(delayEndAt - clock.value, state.selectedDelaySec * 1000L)
                if (fraction > 0f) {
                    drawArc(accent, -90f, 360f * fraction, false, origin, bounds, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        if (delayEndAt != null) {
            val seconds by remember(clock, delayEndAt) {
                derivedStateOf { ceil((delayEndAt - clock.value).coerceAtLeast(0L) / 1000.0).toInt() }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$seconds с", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                Text("Отсрочка старта", color = secondaryText, fontSize = 14.sp)
            }
        } else {
            StopwatchReadout(state, clock)
        }
    }
}

@Composable
private fun StopwatchReadout(state: DirectStopwatchState, clock: State<Long>) {
    val hundredths by remember(clock, state.elapsedOriginMs, state.elapsedMs) {
        derivedStateOf { state.elapsedNow(clock.value) / 10 }
    }
    val seconds = hundredths / 100L
    val wholeTime = remember(seconds) { Fmt.stopwatchClock(seconds * 1000L) }
    val time = buildAnnotatedString {
        append(wholeTime)
        withStyle(SpanStyle(fontSize = 25.sp)) { append(".${(hundredths % 100).toString().padStart(2, '0')}") }
    }
    Text(
        time, style = clockStyle, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun Laps(laps: List<StopwatchLap>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Круги", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        if (laps.isNotEmpty()) {
            TimerCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    laps.asReversed().forEachIndexed { index, lap ->
                        if (index > 0) HorizontalDivider(color = cardBorder)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                Modifier.widthIn(min = 26.dp).background(controlBorder, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(lap.number.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Text(
                                Fmt.stopwatchClock(lap.durationMs) + "." +
                                    (lap.durationMs / 10 % 100).toString().padStart(2, '0'),
                                style = clockStyle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberFrameClock(active: Boolean): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember(active) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycle, active) {
        if (active) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    withFrameNanos { clock.longValue = SystemClock.elapsedRealtime() }
                }
            }
        }
    }
    return clock
}

@Composable
private fun DialBox(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.widthIn(max = minOf(250.dp, maxWidth * if (maxWidth < 280.dp) 0.83f else 0.72f))
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun DialCard(
    dial: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    TimerCard {
        dial()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = actions
        )
    }
}

@Composable
private fun TimerCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(cardColor)
            .border(1.dp, cardBorder, cardShape).padding(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { content() }
}

@Composable
private fun StartButton(active: Boolean, canResume: Boolean, modifier: Modifier, onClick: () -> Unit) {
    TimerButton(
        when {
            active -> "Пауза"
            canResume -> "Продолжить"
            else -> "Старт"
        },
        onClick, modifier, primary = true,
        icon = if (active) Icons.Filled.Pause else Icons.Filled.PlayArrow
    )
}

@Composable
private fun TimerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null
) {
    val tint = if (primary) Color.Black else accent
    Row(
        modifier.heightIn(min = 44.dp).clip(cardShape)
            .then(if (primary) Modifier.background(accent) else Modifier.border(1.dp, controlBorder, cardShape))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(19.dp), tint = tint)
        Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val outline by animateColorAsState(if (selected) accent else controlBorder, tween(200), label = "Preset border")
    Box(
        modifier.heightIn(min = 38.dp).clip(shape)
            .background(if (selected) Color(0xFF1C1E22) else cardColor)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (selected) MaterialTheme.colorScheme.onBackground else secondaryText,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
        )
    }
}
