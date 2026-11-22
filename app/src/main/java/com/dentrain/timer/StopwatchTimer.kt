package com.dentrain.timer

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectStopwatchState(
    val elapsedMs: Long = 0L,
    val delayRemainingMs: Long = 0L,
    val selectedDelaySec: Int = 0,
    val isDelaying: Boolean = false,
    val isRunning: Boolean = false,
    val elapsedOriginMs: Long? = null,
    val delayEndsAtMs: Long? = null,
    val laps: List<StopwatchLap> = emptyList()
) {
    fun elapsedNow(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        elapsedOriginMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: elapsedMs
}

data class StopwatchLap(val number: Int, val elapsedMs: Long, val durationMs: Long)

/**
 * Прямой секундомер с поддержкой отсрочки старта (например, 5 или 10 секунд).
 */
class StopwatchTimer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DirectStopwatchState())
    val state: StateFlow<DirectStopwatchState> = _state.asStateFlow()

    private var timerJob: Job? = null

    fun setDelay(seconds: Int) {
        _state.update { it.copy(selectedDelaySec = seconds) }
    }

    fun start() {
        val s = _state.value
        if (s.isRunning || s.isDelaying) return

        if (s.selectedDelaySec > 0 && s.elapsedMs == 0L) {
            startDelayCountdown(s.selectedDelaySec)
        } else {
            startCountUp()
        }
    }

    private fun startDelayCountdown(seconds: Int) {
        timerJob?.cancel()
        val delayTotalMs = seconds * 1000L
        val delayEndAt = SystemClock.elapsedRealtime() + delayTotalMs
        _state.update {
            it.copy(isDelaying = true, delayRemainingMs = delayTotalMs, delayEndsAtMs = delayEndAt)
        }

        timerJob = scope.launch {
            while (true) {
                val left = delayEndAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    TimerNotifier.notifyFinished(context)
                    startCountUp()
                    break
                }
                _state.update { it.copy(delayRemainingMs = left) }
                delay(50)
            }
        }
    }

    private fun startCountUp() {
        timerJob?.cancel()
        timerJob = null
        val now = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                isRunning = true,
                isDelaying = false,
                delayRemainingMs = 0L,
                delayEndsAtMs = null,
                elapsedOriginMs = now - it.elapsedMs
            )
        }
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        if (_state.value.isDelaying) {
            _state.update { it.copy(isDelaying = false, delayRemainingMs = 0L, delayEndsAtMs = null) }
        } else if (_state.value.isRunning) {
            _state.update { it.copy(elapsedMs = it.elapsedNow(), isRunning = false, elapsedOriginMs = null) }
        }
    }

    fun lap() {
        _state.update { current ->
            if (!current.isRunning) return@update current
            val elapsed = current.elapsedNow()
            val previous = current.laps.lastOrNull()?.elapsedMs ?: 0L
            if (elapsed <= previous) return@update current
            val lap = StopwatchLap(current.laps.size + 1, elapsed, elapsed - previous)
            current.copy(laps = current.laps + lap)
        }
    }

    fun reset() {
        timerJob?.cancel()
        timerJob = null
        _state.update { DirectStopwatchState(selectedDelaySec = it.selectedDelaySec) }
    }
}
