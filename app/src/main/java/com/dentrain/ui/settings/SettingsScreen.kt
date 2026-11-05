package com.dentrain.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.WorkoutAlarmActivity
import com.dentrain.data.SettingsEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.timer.WorkoutReminderNotifier
import com.dentrain.timer.WorkoutReminderScheduler
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.SectionTitle
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.ZScreen
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.theme.AccentThemes
import com.dentrain.ui.theme.DenTrainTheme
import com.dentrain.util.Haptic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: WorkoutRepository) : ViewModel() {
    val settings: StateFlow<SettingsEntity?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAccent(accent: String) = update { it.copy(accent = accent) }
    fun setDark(dark: Boolean) = update { it.copy(darkTheme = dark) }
    fun setGlassTheme(enabled: Boolean) = update { it.copy(glassTheme = enabled) }
    fun setAutoRest(enabled: Boolean) = update { it.copy(autoRestTimer = enabled) }
    fun setRestSeconds(seconds: Int) = update { it.copy(restSeconds = seconds) }

    fun setReminderEnabled(enabled: Boolean, context: Context) = update {
        val updated = it.copy(reminderEnabled = enabled)
        WorkoutReminderScheduler.sync(context)
        updated
    }

    fun setReminderMode(mode: Int, context: Context) = update {
        val updated = it.copy(reminderMode = mode)
        WorkoutReminderScheduler.sync(context)
        updated
    }

    fun setWorkoutStartTime(time: String, context: Context) = update {
        val updated = it.copy(workoutStartTime = time)
        WorkoutReminderScheduler.sync(context)
        updated
    }

    fun toggleReminderLeadOption(leadMinutes: Int, context: Context) = update {
        val currentSet = it.reminderLeadOptions.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.toMutableSet()
        if (leadMinutes in currentSet) {
            if (currentSet.size > 1) currentSet.remove(leadMinutes)
        } else {
            currentSet.add(leadMinutes)
        }
        val str = currentSet.sortedDescending().joinToString(",")
        val updated = it.copy(reminderLeadOptions = str)
        WorkoutReminderScheduler.sync(context)
        updated
    }

    private fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repo.updateSettings(change) }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.repository) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            vm.setReminderEnabled(true, context)
        }
    }

    fun toggleReminder(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setReminderEnabled(enabled, context)
        }
    }

    fun pickTime(currentTimeStr: String, onSelected: (String) -> Unit) {
        val parts = currentTimeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 18
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = "%02d:%02d".format(hour, minute)
                onSelected(formatted)
            },
            h, m, true
        ).show()
    }

    ZScreen("Настройки", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Акцентная тема")
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentOption(
                    "Голубо-синяя", Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF0D47A1))),
                    settings.accent == AccentThemes.BLUE, { vm.setAccent(AccentThemes.BLUE) }, Modifier.weight(1f).fillMaxHeight()
                )
                AccentOption(
                    "Красная", Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFB71C1C))),
                    settings.accent == AccentThemes.RED, { vm.setAccent(AccentThemes.RED) }, Modifier.weight(1f).fillMaxHeight()
                )
            }

            SectionTitle("Фон и Тема")
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BackgroundOption("Светлый", Icons.Filled.LightMode, !settings.darkTheme, { vm.setDark(false) }, Modifier.weight(1f).fillMaxHeight())
                BackgroundOption("Тёмный", Icons.Filled.DarkMode, settings.darkTheme, { vm.setDark(true) }, Modifier.weight(1f).fillMaxHeight())
            }

            ZCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Стеклянная тема ✨", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Полупрозрачное матовое стекло Apple с фоновым анимированным свечением.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.glassTheme, onCheckedChange = { enabled ->
                        Haptic.click(context)
                        vm.setGlassTheme(enabled)
                    })
                }
            }

            SectionTitle("Отдых между подходами")
            ZCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Автозапуск таймера после подхода", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Отдых будет автоматически стартовать сразу после клика «Сохранить подход».",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = settings.autoRestTimer, onCheckedChange = { enabled ->
                            Haptic.click(context)
                            vm.setAutoRest(enabled)
                        })
                    }

                    if (settings.autoRestTimer) {
                        Text("Стандартное время отдыха между подходами:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(30 to "30 с", 60 to "60 с", 90 to "90 с", 120 to "2 мин", 180 to "3 мин").forEach { (sec, label) ->
                                FilterChip(
                                    selected = settings.restSeconds == sec,
                                    onClick = {
                                        Haptic.click(context)
                                        vm.setRestSeconds(sec)
                                    },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Напоминания о тренировках")
            ZCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Напоминать о занятиях 🏋️‍♂️", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Присылает уведомления за некоторое время до тренировки.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.reminderEnabled,
                            onCheckedChange = { enabled ->
                                Haptic.click(context)
                                toggleReminder(enabled)
                            }
                        )
                    }

                    if (settings.reminderEnabled) {
                        Text("1. Основное время начала тренировки:", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(
                            onClick = {
                                Haptic.click(context)
                                pickTime(settings.workoutStartTime) { time -> vm.setWorkoutStartTime(time, context) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AccessTime, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Время начала: ${settings.workoutStartTime}")
                        }

                        Text("2. Тип оповещения:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = settings.reminderMode == 0,
                                onClick = { Haptic.click(context); vm.setReminderMode(0, context) },
                                label = { Text("🔔 Уведомление", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            FilterChip(
                                selected = settings.reminderMode == 1,
                                onClick = { Haptic.click(context); vm.setReminderMode(1, context) },
                                label = { Text("⏰ Будильник", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }

                        val selectedLeadSet = remember(settings.reminderLeadOptions) {
                            settings.reminderLeadOptions.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                        }

                        Text("3. За сколько до старта напоминать (выберите нужные):", style = MaterialTheme.typography.labelLarge)
                        val leadItems = listOf(
                            180 to "За 3 часа",
                            120 to "За 2 часа",
                            60 to "За 1 час",
                            30 to "За 30 мин",
                            15 to "За 15 мин",
                            0 to "В точное время"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(leadItems) { (min, label) ->
                                val isSel = min in selectedLeadSet
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        Haptic.click(context)
                                        vm.toggleReminderLeadOption(min, context)
                                    },
                                    label = { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        GradientButton(
                            "🔔 Проверить тестовое уведомление",
                            onClick = {
                                Haptic.click(context)
                                if (settings.reminderMode == 1) {
                                    val intent = Intent(context, WorkoutAlarmActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    context.startActivity(intent)
                                } else {
                                    WorkoutReminderNotifier.ensureChannel(context)
                                    WorkoutReminderNotifier.sendReminder(
                                        context,
                                        "Тестовое напоминание 🏋️‍♂️",
                                        "Тренировка запланирована на ${settings.workoutStartTime}!"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.NotificationsActive
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentOption(label: String, brush: Brush, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(brush))
        Text(
            label,
            Modifier.padding(top = 8.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BackgroundOption(
    label: String, icon: ImageVector,
    selected: Boolean, onClick: () -> Unit, modifier: Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(shape).clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    DenTrainTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Акцентная тема")
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentOption("Голубо-синяя", Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF0D47A1))), true, {}, Modifier.weight(1f).fillMaxHeight())
                AccentOption("Красная", Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFB71C1C))), false, {}, Modifier.weight(1f).fillMaxHeight())
            }
            SectionTitle("Фон")
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackgroundOption("Светлый", Icons.Filled.LightMode, false, {}, Modifier.weight(1f).fillMaxHeight())
                BackgroundOption("Тёмный", Icons.Filled.DarkMode, true, {}, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
