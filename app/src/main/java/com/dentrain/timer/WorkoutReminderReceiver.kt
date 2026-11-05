package com.dentrain.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dentrain.WorkoutAlarmActivity
import com.dentrain.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Приёмник системного будильника напоминания о тренировке.
 */
class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.create(context)
            val settings = db.settingsDao().getOnce()

            if (settings != null && settings.reminderEnabled) {
                WorkoutReminderNotifier.ensureChannel(context)
                if (settings.reminderMode == 1) {
                    // Прямой запуск полноэкранного окна будильника
                    runCatching {
                        val alarmIntent = Intent(context, WorkoutAlarmActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(alarmIntent)
                    }
                    // Полноэкранный интент на заблокированном экране
                    WorkoutReminderNotifier.sendAlarmNotification(
                        context,
                        "НАПОМИНАНИЕ О ТРЕНИРОВКЕ! ⏰",
                        "Звонит будильник о вашей тренировке."
                    )
                } else {
                    // Режим «Обычное уведомление в шторку»
                    WorkoutReminderNotifier.sendReminder(
                        context,
                        "Время тренировки! 🏋️‍♂️",
                        "У вас запланировано занятие. Пора сделать шаг к своей цели!"
                    )
                }
            }
            WorkoutReminderScheduler.sync(context)
        }
    }
}
