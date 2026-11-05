package com.dentrain.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dentrain.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Менеджер планирования системных будильников относительно основного времени начала тренировки.
 */
object WorkoutReminderScheduler {

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WorkoutReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseTime(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 18
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalTime.of(h, m)
    }

    fun sync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.create(context)
            val settings = db.settingsDao().getOnce()
            if (settings == null || !settings.reminderEnabled) {
                cancel(context)
                return@launch
            }

            val now = LocalDateTime.now()
            val startTime = parseTime(settings.workoutStartTime)
            val leadMinutesList = settings.reminderLeadOptions
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .ifEmpty { listOf(30L) }

            var nextTriggerMillis: Long? = null

            for (dateOffset in 0L..7L) {
                val checkDate = LocalDate.now().plusDays(dateOffset)
                for (leadMin in leadMinutesList) {
                    val reminderTime = startTime.minusMinutes(leadMin)
                    val dateTime = LocalDateTime.of(checkDate, reminderTime)
                    if (dateTime.isAfter(now)) {
                        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (nextTriggerMillis == null || millis < nextTriggerMillis) {
                            nextTriggerMillis = millis
                        }
                    }
                }
                if (nextTriggerMillis != null) break
            }

            val triggerMillis = nextTriggerMillis ?: return@launch
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
            val pendingIntent = getPendingIntent(context)

            if (settings.reminderMode == 1) {
                // Системный будильник Android со значком ⏰ в строке состояния
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(getPendingIntent(context))
    }
}
