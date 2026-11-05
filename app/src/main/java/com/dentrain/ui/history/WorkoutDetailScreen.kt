package com.dentrain.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dentrain.data.SetResultEntity
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutStatus
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.ui.components.ConfirmDialog
import com.dentrain.ui.components.SetEditDialog
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.ZScreen
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val id: Long, private val repo: WorkoutRepository) : ViewModel() {
    val workout: StateFlow<WorkoutWithExercises?> =
        repo.observeWorkout(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pinned: StateFlow<Boolean> = repo.pinnedIds.map { id in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePin() { viewModelScope.launch { repo.setPinned(id, !pinned.value) } }
    fun delete(onDone: () -> Unit) { viewModelScope.launch { repo.deleteWorkout(id); onDone() } }
    fun repeat(onDone: (Long) -> Unit) { viewModelScope.launch { onDone(repo.repeatWorkout(id)) } }
    fun updateSet(set: SetResultEntity, reps: Int, weight: Double?) { viewModelScope.launch { repo.updateSet(set, reps, weight) } }
    fun deleteSet(set: SetResultEntity) { viewModelScope.launch { repo.deleteSet(set) } }
}

/**
 * Просмотр тренировки. По умолчанию РЕЖИМ ПРОСМОТРА: данные менять нельзя, чтобы случайно
 * не испортить историю. Исправления включаются отдельной кнопкой-карандашом.
 */
@Composable
fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit, onEdit: () -> Unit, onRepeated: (Long) -> Unit) {
    val vm = appViewModel(key = "detail$workoutId") { DetailViewModel(workoutId, it.repository) }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    var editMode by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetResultEntity?>(null) }

    ZScreen(
        "Тренировка", onBack,
        actions = {
            IconButton(onClick = { editMode = !editMode }) {
                Icon(if (editMode) Icons.Filled.Visibility else Icons.Filled.EditNote, if (editMode) "Режим просмотра" else "Исправить результаты")
            }
            IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
        }
    ) { pad ->
        val w = workout
        if (w == null) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@ZScreen
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Явное отличие просмотра от выполнения
            ZCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (editMode) Icons.Filled.Edit else Icons.Filled.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (editMode) "  Режим исправления: нажмите на подход, чтобы изменить"
                        else "  Режим просмотра: результаты не изменятся",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column {
                Text(w.workout.title, fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.ExtraBold)
                Text(Fmt.longDate(w.workout), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.summary(w), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (w.workout.description.isNotBlank()) Text(w.workout.description, modifier = Modifier.padding(top = 4.dp))
            }

            w.exercises.sortedBy { it.exercise.position }.forEach { item ->
                ZCard {
                    Text(item.exercise.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val plan = buildList {
                        add("${item.exercise.plannedSets} подх.")
                        item.exercise.plannedReps?.let { add("$it повт.") }
                        Fmt.weight(item.exercise.plannedWeight)?.let { add("$it кг") }
                    }.joinToString(" · ")
                    Text("План: $plan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val sets = item.sets.sortedBy { it.setNumber }
                    if (sets.isEmpty()) {
                        Text("Подходы не выполнены", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sets.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp)
                                .then(if (editMode) Modifier.clickable { editingSet = s } else Modifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Подход № ${s.setNumber} — ${Fmt.setLine(s)}", modifier = Modifier.weight(1f))
                            if (editMode) Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (w.workout.status == WorkoutStatus.COMPLETED) {
                OutlinedButton(onClick = vm::togglePin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PushPin, null)
                    Text(if (pinned) "  Открепить тренировку" else "  Закрепить тренировку")
                }
                OutlinedButton(onClick = { vm.repeat(onRepeated) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, null); Text("  Повторить как новую тренировку")
                }
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, null); Text("  Изменить название, дату и план")
            }
        }

        if (showDelete) {
            ConfirmDialog(
                "Удалить тренировку?", "Тренировка и все её результаты будут удалены без возможности восстановления.",
                onConfirm = { showDelete = false; vm.delete(onBack) }, onDismiss = { showDelete = false }
            )
        }
        editingSet?.let { set ->
            SetEditDialog(
                set = set,
                onSave = { r, wt -> vm.updateSet(set, r, wt); editingSet = null },
                onDelete = { vm.deleteSet(set); editingSet = null },
                onDismiss = { editingSet = null }
            )
        }
    }
}
