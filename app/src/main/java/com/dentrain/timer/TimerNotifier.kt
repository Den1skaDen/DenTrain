package com.dentrain.timer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.dentrain.R

internal object TimerNotifier {
    const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1001
    private const val STOPWATCH_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Таймер отдыха", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сообщает об окончании отдыха между подходами"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyFinished(context: Context) {
        if (!notify(context, NOTIFICATION_ID, "Отдых окончен", "Пора к следующему подходу") && inForeground()) {
            playInAppCue(context)
        }
    }

    fun notifyStopwatchStarted(context: Context) {
        if (inForeground()) {
            playInAppCue(context)
        } else {
            notify(context, STOPWATCH_NOTIFICATION_ID, "Секундомер запущен", "Отсчёт времени начался")
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, title: String, message: String): Boolean {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val notification = LocalNotifications.builder(context, CHANNEL_ID, id)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .build()
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun inForeground() =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    @Suppress("DEPRECATION")
    private fun playInAppCue(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 280), -1))
        }
        runCatching {
            RingtoneManager.getRingtone(
                context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )?.play()
        }
    }
}
