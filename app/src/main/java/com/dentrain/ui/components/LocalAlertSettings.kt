package com.dentrain.ui.components

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dentrain.timer.LocalAlarm
import com.dentrain.ui.theme.DenTrainPalette

internal class NotificationAccess(val allowed: Boolean, val request: () -> Unit) {
    fun border(enabled: Boolean, blockedColor: Color): BorderStroke = BorderStroke(
        1.dp,
        when {
            !enabled -> DenTrainPalette.SubtleBorder
            allowed -> Color.White
            else -> blockedColor
        }
    )
}

@Composable
internal fun rememberNotificationAccess(channelId: String): NotificationAccess {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
    val permission = Manifest.permission.POST_NOTIFICATIONS
    fun appCanNotify() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun canNotify() = appCanNotify() &&
        manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE

    var allowed by remember(channelId) { mutableStateOf(canNotify()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = canNotify()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = canNotify() }

    return NotificationAccess(allowed) {
        val appEnabled = appCanNotify()
        when {
            appEnabled -> context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId))
            Build.VERSION.SDK_INT >= 33 &&
                (!requested || activity?.shouldShowRequestPermissionRationale(permission) == true) -> {
                requested = true
                launcher.launch(permission)
            }
            else -> context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
}

@Composable
internal fun LocalAlertSettings(access: NotificationAccess) {
    if (!access.allowed) {
        TextButton(onClick = access.request) {
            Text("Разрешить отправлять уведомления")
        }
        return
    }

    val context = LocalContext.current
    var exact by remember { mutableStateOf(LocalAlarm.canScheduleExactly(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exact = LocalAlarm.canScheduleExactly(context)
    }
    if (!exact) Column {
        TextButton(onClick = {
            context.startActivity(Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null)
            ))
        }) {
            Text("Разрешить точные сигналы")
        }
        Text(
            text = "Без этого доступа Android может задержать уведомление в фоне.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
