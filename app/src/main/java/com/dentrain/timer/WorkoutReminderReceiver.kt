package com.dentrain.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dentrain.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Приёмник напоминания о тренировке.
 */
class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = (context.applicationContext as com.dentrain.DenTrainApp).container.database
            val settings = db.settingsDao().getOnce()

            if (settings != null && settings.reminderEnabled) {
                WorkoutReminderNotifier.ensureChannel(context)
                WorkoutReminderNotifier.sendReminder(
                    context,
                    "Напоминание о тренировке 🏋️‍♂️",
                    "Через час запланированная тренировка, приходи!"
                )
            }
            WorkoutReminderScheduler.sync(context)
        }
    }
}
