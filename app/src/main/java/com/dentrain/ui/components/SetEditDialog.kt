package com.dentrain.ui.components

import com.dentrain.ui.theme.DenTrainPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.data.SetResultEntity
import com.dentrain.util.Fmt

/** Исправление ошибочно введённого подхода (используется на экране тренировки и в истории). */
@Composable
fun SetEditDialog(
    set: SetResultEntity,
    onSave: (reps: Int, weight: Double?, isWarmup: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var weight by remember { mutableStateOf(Fmt.weight(set.weight) ?: "") }
    var isWarmup by remember { mutableStateOf(set.isWarmup) }
    var error by remember { mutableStateOf<String?>(null) }

    val blueAccent = Color(0xFF00B0FF)

    val accent = if (isWarmup) blueAccent else DenTrainPalette.Lime
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        focusedLabelColor = accent,
        unfocusedBorderColor = DenTrainPalette.Border,
        unfocusedLabelColor = DenTrainPalette.MutedText,
        cursorColor = accent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DenTrainPalette.Panel,
        title = { Text("Изменить подход", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (!isWarmup) DenTrainPalette.Lime.copy(alpha = 0.2f) else DenTrainPalette.RaisedPanel)
                            .border(BorderStroke(1.dp, if (!isWarmup) DenTrainPalette.Lime else DenTrainPalette.Border), CircleShape)
                            .clickable { isWarmup = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Рабочий",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isWarmup) DenTrainPalette.Lime else DenTrainPalette.MutedText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (isWarmup) blueAccent.copy(alpha = 0.2f) else DenTrainPalette.RaisedPanel)
                            .border(BorderStroke(1.dp, if (isWarmup) blueAccent else DenTrainPalette.Border), CircleShape)
                            .clickable { isWarmup = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Разминка",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWarmup) blueAccent else DenTrainPalette.MutedText
                        )
                    }
                }

                Row {
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it.filter(Char::isDigit).take(4) },
                        label = { Text(if (set.isTimeBased) "Секунды" else "Повторения") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                        label = { Text("Вес, кг") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                }
                Spacer(Modifier.height(8.dp))
                ErrorText(error)
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить этот подход", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = reps.toIntOrNull()
                val w = if (weight.isBlank()) null else Fmt.parseWeight(weight)
                when {
                    r == null || r < 1 -> error = "Число должно быть не меньше 1"
                    weight.isNotBlank() && (w == null || w < 0) -> error = "Вес — число не меньше 0"
                    else -> onSave(r, w, isWarmup)
                }
            }) { Text("Сохранить", color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = DenTrainPalette.MutedText) } }
    )
}
