package com.dentrain.timer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dentrain.DenTrainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            (context.applicationContext as DenTrainApp).container.restTimer.reschedule()
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                WorkoutReminderScheduler.sync(context, intent.takeIf {
                    it.action == WorkoutReminderScheduler.ACTION_REMINDER
                })
            } finally {
                pending.finish()
            }
        }
    }
}
