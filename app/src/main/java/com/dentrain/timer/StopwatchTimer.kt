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
    val isRunning: Boolean = false
)

/**
 * Прямой секундомер с поддержкой отсрочки старта (например, 5 или 10 секунд).
 */
class StopwatchTimer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DirectStopwatchState())
    val state: StateFlow<DirectStopwatchState> = _state.asStateFlow()

    private var startRealtime = 0L
    private var baseElapsedMs = 0L
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
        _state.update { it.copy(isDelaying = true, delayRemainingMs = delayTotalMs) }

        timerJob = scope.launch {
            while (true) {
                val left = delayEndAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    _state.update { it.copy(isDelaying = false, delayRemainingMs = 0L) }
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
        startRealtime = SystemClock.elapsedRealtime()
        baseElapsedMs = _state.value.elapsedMs
        _state.update { it.copy(isRunning = true, isDelaying = false) }

        timerJob = scope.launch {
            while (true) {
                val currentElapsed = baseElapsedMs + (SystemClock.elapsedRealtime() - startRealtime)
                _state.update { it.copy(elapsedMs = currentElapsed) }
                delay(50)
            }
        }
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        if (_state.value.isDelaying) {
            _state.update { it.copy(isDelaying = false, delayRemainingMs = 0L) }
        } else if (_state.value.isRunning) {
            val currentElapsed = baseElapsedMs + (SystemClock.elapsedRealtime() - startRealtime)
            _state.update { it.copy(elapsedMs = currentElapsed, isRunning = false) }
        }
    }

    fun reset() {
        timerJob?.cancel()
        timerJob = null
        baseElapsedMs = 0L
        _state.update {
            it.copy(
                elapsedMs = 0L,
                delayRemainingMs = 0L,
                isDelaying = false,
                isRunning = false
            )
        }
    }
}
