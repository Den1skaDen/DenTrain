package com.dentrain.timer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dentrain.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object WorkoutReminderNotifier {
    const val CHANNEL_ID = "workout_reminders"
    private const val NOTIFICATION_ID = 2002
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru"))

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Напоминания о тренировках", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сообщает о приближении запланированной тренировки"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun sendReminder(context: Context, reminder: WorkoutReminder, zone: ZoneId, now: Instant) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val secondsLeft = Duration.between(now, reminder.workout).seconds
        val minutesLeft = ((secondsLeft + 59) / 60).toInt()
        val title = when {
            minutesLeft <= 0 -> "Время тренировки"
            minutesLeft % 60 == 0 -> "До тренировки · ${minutesLeft / 60} ч"
            else -> "До тренировки · $minutesLeft мин"
        }
        val workout = reminder.workout.atZone(zone)
        val day = if (workout.toLocalDate() == now.atZone(zone).toLocalDate()) "Сегодня" else "Завтра"
        val message = "$day в ${workout.format(timeFormat)}"
        val notification = LocalNotifications.builder(context, CHANNEL_ID, NOTIFICATION_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(message)
            .setTimeoutAfter(Duration.between(now, reminder.workout).toMillis().coerceAtLeast(60_000L))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Разрешение могло измениться после проверки.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
