package com.dentrain.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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

data class TimerState(
    val totalMs: Long = 0,
    val remainingMs: Long = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val runId: Long? = null,
    val endsAtElapsedMs: Long? = null
)

/**
 * Таймер отдыха.
 *
 * Ключевая идея: оставшееся время НЕ уменьшается «на 1 каждую секунду».
 * Мы запоминаем момент окончания по системным часам (elapsedRealtime) и каждый тик
 * считаем остаток как endAt - now. Поэтому, если приложение свернули или корутину
 * задержала система, время всё равно будет верным.
 *
 * Дополнительно ставится AlarmManager-будильник: если процесс приложения будет
 * остановлен системой, по окончании отдыха всё равно придёт вибрация/уведомление.
 */
class RestTimer(private val context: Context, accountKey: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("rest_timer_$accountKey", Context.MODE_PRIVATE)
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var endAt = 0L          // момент окончания по SystemClock.elapsedRealtime()
    private var tickJob: Job? = null
    private var lastRunId = 0L
    private var closed = false

    init {
        val savedEnd = prefs.getLong(EXTRA_DEADLINE, 0L)
        val total = prefs.getLong("total", 0L)
        if (savedEnd > 0 && total > 0 && bootCount >= 0 && prefs.getInt("boot", -1) == bootCount) {
            endAt = savedEnd
            _state.value = TimerState(
                totalMs = total,
                remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0),
                running = true,
                runId = ++lastRunId,
                endsAtElapsedMs = endAt
            )
            if (endAt > SystemClock.elapsedRealtime()) reschedule()
            startTicking()
        } else if (savedEnd > 0) {
            prefs.edit().clear().apply()
        }
    }

    /** Задать длительность. Останавливает и сбрасывает текущий отсчёт. */
    fun setDuration(seconds: Int) {
        if (closed) return
        stopTicking()
        val ms = seconds * 1000L
        _state.value = TimerState(totalMs = ms, remainingMs = ms)
    }

    /** Установить длительность только если она ещё не задана (при первом открытии). */
    fun ensureConfigured(seconds: Int) {
        if (_state.value.totalMs == 0L) setDuration(seconds)
    }

    fun start() {
        val s = _state.value
        if (closed || s.running || s.totalMs <= 0) return
        val remaining = if (s.remainingMs <= 0) s.totalMs else s.remainingMs
        endAt = SystemClock.elapsedRealtime() + remaining
        val runId = if (s.remainingMs <= 0) ++lastRunId else s.runId ?: ++lastRunId
        TimerNotifier.cancel(context)
        prefs.edit()
            .putLong(EXTRA_DEADLINE, endAt)
            .putLong("total", s.totalMs)
            .putInt("boot", bootCount)
            .apply()
        _state.value = s.copy(
            remainingMs = remaining,
            running = true,
            finished = false,
            runId = runId,
            endsAtElapsedMs = endAt
        )
        scheduleAlarm(endAt)
        startTicking()
    }

    fun pause() {
        if (!_state.value.running) return
        val left = endAt - SystemClock.elapsedRealtime()
        if (left <= 0) {
            finish()
            return
        }
        stopTicking()
        _state.update { it.copy(remainingMs = left, running = false, endsAtElapsedMs = null) }
    }

    fun reset() {
        if (closed) return
        stopTicking()
        TimerNotifier.cancel(context)
        _state.update { TimerState(totalMs = it.totalMs, remainingMs = it.totalMs) }
    }

    /** Запуск отдыха «с нуля» на заданное число секунд (после сохранения подхода). */
    fun restart(seconds: Int) {
        setDuration(seconds)
        start()
    }

    internal fun onAlarm(deadline: Long) {
        if (_state.value.running && deadline == endAt && SystemClock.elapsedRealtime() >= endAt) finish()
    }

    internal fun reschedule() {
        if (_state.value.running) scheduleAlarm(endAt)
    }

    internal fun close() {
        reset()
        closed = true
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    finish()
                    break
                }
                _state.update { it.copy(remainingMs = left) }
                delay(100)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
        cancelAlarm()
        if (prefs.contains(EXTRA_DEADLINE)) prefs.edit().clear().apply()
    }

    private fun finish() {
        stopTicking()
        _state.update { it.copy(remainingMs = 0, running = false, finished = true, endsAtElapsedMs = null) }
        TimerNotifier.notifyFinished(context)
    }

    // ----- будильник на случай, если процесс будет остановлен -----

    private fun alarmIntent() = Intent(context, TimerAlarmReceiver::class.java)

    private fun scheduleAlarm(triggerAtElapsed: Long) {
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent().putExtra(EXTRA_DEADLINE, triggerAtElapsed),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        LocalAlarm.schedule(context, AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pending)
    }

    private fun cancelAlarm() {
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }

    companion object {
        internal const val EXTRA_DEADLINE = "deadlineElapsed"
    }
}
