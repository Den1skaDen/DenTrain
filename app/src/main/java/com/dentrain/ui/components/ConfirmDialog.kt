package com.dentrain.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dentrain.ui.theme.DenTrainPalette

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissText: String? = "Отмена",
    onSecondary: (() -> Unit)? = null,
    secondaryDestructive: Boolean = false,
    properties: DialogProperties = DialogProperties()
) {
    ConfirmDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = destructive,
        dismissText = dismissText,
        onSecondary = onSecondary,
        secondaryDestructive = secondaryDestructive,
        properties = properties,
        content = { Text(text) }
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissText: String? = "Отмена",
    onSecondary: (() -> Unit)? = null,
    secondaryDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = properties,
        shape = RoundedCornerShape(20.dp),
        containerColor = DenTrainPalette.Panel,
        title = { Text(title) },
        text = content,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) colors.error else colors.primary,
                    contentColor = if (destructive) colors.background else colors.onPrimary
                )
            ) { Text(confirmText) }
        },
        dismissButton = {
            if (dismissText != null) {
                TextButton(onClick = onSecondary ?: onDismiss) {
                    Text(dismissText, color = if (secondaryDestructive) colors.error else colors.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
internal fun DeleteProfileDialog(target: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val code = rememberSaveable { (1000..9999).random().toString() }
    var input by rememberSaveable { mutableStateOf("") }
    ConfirmDialog(
        title = "Удалить профиль?",
        confirmText = "Удалить",
        confirmEnabled = input == code,
        onConfirm = onDelete,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Профиль $target будет удалён из DenTrain.")
            Text("Для подтверждения введите код $code.")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { digit -> digit in '0'..'9' }.take(4) },
                label = { Text("Код подтверждения") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun DiscardChangesDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Выйти без сохранения?",
        text = "Изменения в этой форме будут потеряны.",
        confirmText = "Выйти",
        onConfirm = onDiscard,
        dismissText = "Продолжить редактирование",
        onDismiss = onDismiss
    )
}

@Composable
internal fun OperationErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    ConfirmDialog(
        title = "Не удалось выполнить действие",
        text = message,
        confirmText = "Понятно",
        destructive = false,
        dismissText = null,
        onConfirm = onDismiss,
        onDismiss = onDismiss
    )
}
