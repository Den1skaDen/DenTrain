package com.dentrain.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dentrain.data.SetResultEntity
import com.dentrain.util.Fmt

/** Исправление ошибочно введённого подхода (используется на экране тренировки и в истории). */
@Composable
fun SetEditDialog(
    set: SetResultEntity,
    onSave: (reps: Int, weight: Double?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var weight by remember { mutableStateOf(Fmt.weight(set.weight) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подход № ${set.setNumber}") },
        text = {
            Column {
                Row {
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it.filter(Char::isDigit).take(4) },
                        label = { Text("Повт. / Сек.") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                        label = { Text("Вес, кг") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
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
                    else -> onSave(r, w)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
