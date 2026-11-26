package com.dentrain.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dentrain.DenTrainApp
import com.dentrain.data.SettingsEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class WorkoutReminderSchedule(
    val start: LocalTime,
    val days: Set<Int>,
    val leads: Set<Long>
) {
    fun next(now: Instant, zone: ZoneId): WorkoutReminder? {
        val today = now.atZone(zone).toLocalDate()
        return (0L..8L).asSequence()
            .flatMap { offset ->
                val date = today.plusDays(offset)
                leads.asSequence().mapNotNull { occurrence(date, it, zone) }
            }
            .filter { it.trigger.isAfter(now) }
            .minByOrNull { it.trigger }
    }

    fun occurrence(date: LocalDate, lead: Long, zone: ZoneId): WorkoutReminder? {
        if (date.dayOfWeek.value !in days || lead !in leads) return null
        val workout = date.atTime(start).atZone(zone).toInstant()
        return WorkoutReminder(date, lead, workout.minusSeconds(lead * 60), workout)
    }

    companion object {
        fun from(settings: SettingsEntity): WorkoutReminderSchedule? {
            if (!settings.reminderEnabled) return null
            val parts = settings.workoutStartTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 18
            val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
            val days = settings.scheduledDays.split(",")
                .mapNotNull { it.trim().toIntOrNull()?.takeIf { day -> day in 1..7 } }
                .toSet().ifEmpty { (1..7).toSet() }
            val leads = settings.reminderLeadOptions.split(",")
                .mapNotNull { it.trim().toLongOrNull()?.takeIf { lead -> lead in 0..180 } }
                .toSet().ifEmpty { setOf(60L) }
            return WorkoutReminderSchedule(LocalTime.of(hour, minute), days, leads)
        }
    }
}

internal data class WorkoutReminder(
    val date: LocalDate,
    val leadMinutes: Long,
    val trigger: Instant,
    val workout: Instant
)

internal object WorkoutReminderScheduler {
    const val ACTION_REMINDER = "com.dentrain.action.WORKOUT_REMINDER"
    private const val EXTRA_ACCOUNT = "account"
    private const val EXTRA_DATE = "workoutDate"
    private const val EXTRA_LEAD = "leadMinutes"
    private const val EXTRA_TRIGGER = "triggerMillis"
    private const val REQUEST_CODE = 2002
    private val mutex = Mutex()

    suspend fun sync(context: Context, delivery: Intent? = null) = mutex.withLock {
        val app = context.applicationContext as DenTrainApp
        val account = app.container
        val schedule = WorkoutReminderSchedule.from(account.database.settingsDao().getOnce() ?: SettingsEntity())
        if (account !== app.container) return@withLock

        val manager = context.getSystemService(AlarmManager::class.java)
        val baseIntent = Intent(context, WorkoutReminderReceiver::class.java).setAction(ACTION_REMINDER)
        PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, WorkoutReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let(manager::cancel)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        if (delivery != null && schedule != null && delivery.getStringExtra(EXTRA_ACCOUNT) == account.databaseName) {
            val date = delivery.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
            val lead = delivery.getLongExtra(EXTRA_LEAD, -1)
            val occurrence = if (date in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) {
                schedule.occurrence(LocalDate.ofEpochDay(date), lead, zone)
            } else null
            if (occurrence != null &&
                occurrence.trigger.toEpochMilli() == delivery.getLongExtra(EXTRA_TRIGGER, 0) &&
                !now.isBefore(occurrence.trigger) &&
                (now.isBefore(occurrence.workout) || (lead == 0L && now.isBefore(occurrence.workout.plusSeconds(60))))
            ) {
                WorkoutReminderNotifier.sendReminder(context, occurrence, zone, now)
            }
        }

        val next = schedule?.next(now, zone)
        if (next == null) {
            PendingIntent.getBroadcast(context, REQUEST_CODE, baseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let(manager::cancel)
            WorkoutReminderNotifier.cancel(context)
            return@withLock
        }
        val intent = baseIntent
            .putExtra(EXTRA_ACCOUNT, account.databaseName)
            .putExtra(EXTRA_DATE, next.date.toEpochDay())
            .putExtra(EXTRA_LEAD, next.leadMinutes)
            .putExtra(EXTRA_TRIGGER, next.trigger.toEpochMilli())
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        LocalAlarm.schedule(context, AlarmManager.RTC_WAKEUP, next.trigger.toEpochMilli(), pending)
    }
}
