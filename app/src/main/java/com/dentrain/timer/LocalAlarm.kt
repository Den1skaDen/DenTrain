package com.dentrain.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

internal object LocalAlarm {
    fun canScheduleExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, type: Int, triggerAt: Long, operation: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExactly(context)) {
            try {
                manager.setExactAndAllowWhileIdle(type, triggerAt, operation)
                return
            } catch (_: SecurityException) {
                // Доступ мог измениться после проверки.
            }
        }
        manager.setAndAllowWhileIdle(type, triggerAt, operation)
    }
}
