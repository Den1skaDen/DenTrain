package com.dentrain.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dentrain.DenTrainApp

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as DenTrainApp).container.restTimer.onAlarm()
    }
}
