package com.dentrain.ui.stopwatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.WorkoutRepository
import com.dentrain.timer.DirectStopwatchState
import com.dentrain.timer.RestTimer
import com.dentrain.timer.StopwatchTimer
import com.dentrain.timer.TimerState
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

class StopwatchViewModel(
    private val repo: WorkoutRepository,
    private val restTimer: RestTimer,
    private val directTimer: StopwatchTimer
) : ViewModel() {
    val restState: StateFlow<TimerState> = restTimer.state
    val directState: StateFlow<DirectStopwatchState> = directTimer.state

    val selectedRestSeconds: StateFlow<Int> = repo.settings.map { it.restSeconds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 90)

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
}

private val restPresets = listOf(30, 60, 90, 120, 180)
private val delayPresets = listOf(0, 3, 5, 10, 15)

@Composable
fun StopwatchScreen(onSettings: () -> Unit) {
    val vm = appViewModel { StopwatchViewModel(it.repository, it.restTimer, it.stopwatchTimer) }
    val restState by vm.restState.collectAsStateWithLifecycle()
    val directState by vm.directState.collectAsStateWithLifecycle()
    val selectedRest by vm.selectedRestSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) }
    var customRest by remember { mutableStateOf("") }
    var customRestError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startRest() }
    fun startRestWithPermission() {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs && !restState.running && restState.remainingMs == restState.totalMs) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.startRest()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Таймер отдыха") },
                    leadingIcon = { Icon(Icons.Filled.HourglassBottom, null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("Прямой секундомер") },
                    leadingIcon = { Icon(Icons.Filled.Timer, null) },
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeTab == 0) {
                    RestTimerRing(
                        remainingMs = restState.remainingMs,
                        totalMs = restState.totalMs,
                        finished = restState.finished,
                        running = restState.running
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (restState.running) {
                            GradientButton("Пауза", vm::pauseRest, Modifier.weight(1f), icon = Icons.Filled.Pause)
                        } else {
                            val resume = restState.remainingMs in 1 until restState.totalMs
                            GradientButton(if (resume) "Продолжить" else "Старт", { startRestWithPermission() }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                        }
                        OutlinedButton(onClick = vm::resetRest, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, null); Text("  Сброс")
                        }
                    }

                    SectionTitle("Время отдыха", Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(restPresets) { sec ->
                            FilterChip(
                                selected = selectedRest == sec && restState.totalMs == sec * 1000L,
                                onClick = { vm.selectRest(sec) },
                                label = { Text("$sec с") }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customRest, onValueChange = { customRest = it.filter(Char::isDigit).take(4); customRestError = null },
                            label = { Text("Своё время, секунд") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = {
                            val sec = customRest.toIntOrNull()
                            if (sec == null || sec < 5 || sec > 3600) customRestError = "Введите от 5 до 3600 секунд"
                            else { vm.selectRest(sec); customRest = "" }
                        }) { Text("Задать") }
                    }
                    ErrorText(customRestError, Modifier.fillMaxWidth())

                } else {
                    DirectStopwatchRing(
                        elapsedMs = directState.elapsedMs,
                        isRunning = directState.isRunning,
                        isDelaying = directState.isDelaying,
                        delayRemainingMs = directState.delayRemainingMs,
                        selectedDelaySec = directState.selectedDelaySec
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (directState.isRunning || directState.isDelaying) {
                            GradientButton("Пауза", vm::pauseDirect, Modifier.weight(1f), icon = Icons.Filled.Pause)
                        } else {
                            val resume = directState.elapsedMs > 0
                            GradientButton(
                                if (resume) "Продолжить" else "Старт",
                                vm::startDirect,
                                Modifier.weight(1f),
                                icon = Icons.Filled.PlayArrow
                            )
                        }
                        OutlinedButton(onClick = vm::resetDirect, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, null); Text("  Сброс")
                        }
                    }

                    SectionTitle("Отсрочка старта", Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(delayPresets) { sec ->
                            FilterChip(
                                selected = directState.selectedDelaySec == sec,
                                onClick = { vm.selectDelay(sec) },
                                label = { Text(if (sec == 0) "Без задержки" else "$sec с") }
                            )
                        }
                    }

                    if (directState.selectedDelaySec > 0) {
                        ZCard {
                            Text("Отсрочка старта включена: ${directState.selectedDelaySec} сек.", fontWeight = FontWeight.SemiBold)
                            Text(
                                "После нажатия «Старт» начнётся обратный отсчёт ${directState.selectedDelaySec} с, а затем сразу запустится секундомер.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Изолированный графический ринг таймера отдыха (перерисовывается ТОЛЬКО сам кольцевой таймер). */
@Composable
private fun RestTimerRing(remainingMs: Long, totalMs: Long, finished: Boolean, running: Boolean) {
    val progress = if (totalMs > 0) remainingMs.toFloat() / totalMs else 0f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val brush = LocalGradient.current.brush

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(brush, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Fmt.clock(remainingMs), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when {
                    finished -> "Отдых окончен!"
                    running -> "Идёт отдых"
                    remainingMs in 1 until totalMs -> "Пауза"
                    else -> "Готов к старту"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Изолированный графический ринг прямого секундомера (перерисовывается ТОЛЬКО сам кольцевой секундомер). */
@Composable
private fun DirectStopwatchRing(
    elapsedMs: Long,
    isRunning: Boolean,
    isDelaying: Boolean,
    delayRemainingMs: Long,
    selectedDelaySec: Int
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val brush = LocalGradient.current.brush

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (isRunning) {
                drawArc(brush, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else {
                drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            if (isDelaying) {
                val delayTotal = (selectedDelaySec * 1000L).coerceAtLeast(1)
                val progress = delayRemainingMs.toFloat() / delayTotal
                drawArc(brush, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isDelaying) {
                val delaySec = ceil(delayRemainingMs / 1000.0).toInt()
                Text("$delaySec с", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("Отсрочка старта...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(Fmt.stopwatchClock(elapsedMs), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        isRunning -> "Секундомер запущен"
                        elapsedMs > 0 -> "Пауза"
                        else -> "Готов к старту"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StopwatchScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("Секундомер и таймер", "Отдых и отсчёт времени") {}
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = {}, label = { Text("Таймер отдыха") })
                FilterChip(selected = true, onClick = {}, label = { Text("Прямой секундомер") })
            }
            ZCard {
                Text("00:15.4", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Text("Секундомер запущен", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
