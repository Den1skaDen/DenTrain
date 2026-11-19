package com.dentrain.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Вспомогательные функции тактильного отклика (вибрации при действиях пользователя). */
object Haptic {
    /** Лёгкий клик при нажатии кнопок или переключении вкладок. */
    fun click(context: Context) {
        runCatching {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(20L)
            }
        }
    }

    /** Двойной торжественный клик при завершении цели или рекорде. */
    fun success(context: Context) {
        runCatching {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 50, 70, 100), -1)
            }
        }
    }
}
