package com.dentrain.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dentrain.data.ExerciseCatalog
import com.dentrain.data.ExerciseDraft
import com.dentrain.data.WorkoutDraft
import com.dentrain.data.WorkoutRepository
import com.dentrain.ui.components.DateField
import com.dentrain.ui.components.ErrorText
import com.dentrain.ui.components.GradientButton
import com.dentrain.ui.components.ZCard
import com.dentrain.ui.components.ZScreen
import com.dentrain.ui.components.appViewModel
import com.dentrain.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Одно упражнение в форме. Поля — Compose-состояние, поэтому ввод не пересоздаёт список. */
class ExerciseForm(
    val uid: Long,
    val id: Long = 0,
    name: String = "",
    sets: String = "3",
    reps: String = "",
    weight: String = ""
) {
    var name by mutableStateOf(name)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
}

class EditorViewModel(private val workoutId: Long, private val repo: WorkoutRepository) : ViewModel() {
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var epochDay by mutableLongStateOf(LocalDate.now().toEpochDay())
    var dayOfWeek by mutableIntStateOf(LocalDate.now().dayOfWeek.value)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(workoutId == 0L)
    val exercises = mutableStateListOf<ExerciseForm>()
    private var nextUid = 1L

    init {
        if (workoutId == 0L) {
            exercises.add(ExerciseForm(nextUid++))
        } else {
            viewModelScope.launch {
                repo.getWorkout(workoutId)?.let { w ->
                    title = w.workout.title
                    description = w.workout.description
                    epochDay = w.workout.dateEpochDay
                    dayOfWeek = w.workout.dayOfWeek
                    w.exercises.sortedBy { it.exercise.position }.forEach {
                        exercises.add(
                            ExerciseForm(
                                nextUid++, it.exercise.id, it.exercise.name,
                                it.exercise.plannedSets.toString(), it.exercise.plannedReps ?: "",
                                Fmt.weight(it.exercise.plannedWeight) ?: ""
                            )
                        )
                    }
                }
                loaded = true
            }
        }
    }

    /** При смене даты день недели подставляется автоматически. */
    fun changeDate(day: Long) {
        epochDay = day
        dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek.value
    }

    /** При смене дня недели автоматически вычисляется ближайшая дата этого дня недели! */
    fun changeDayOfWeek(targetDayOfWeek: Int) {
        dayOfWeek = targetDayOfWeek
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek.value
        var daysToAdd = targetDayOfWeek - currentDayOfWeek
        if (daysToAdd < 0) {
            daysToAdd += 7
        }
        epochDay = today.plusDays(daysToAdd.toLong()).toEpochDay()
    }

    fun addExercise() { exercises.add(ExerciseForm(nextUid++)) }
    fun removeExercise(index: Int) { if (index in exercises.indices) exercises.removeAt(index) }

    fun move(index: Int, delta: Int) {
        val j = index + delta
        if (index in exercises.indices && j in exercises.indices) {
            val t = exercises[index]; exercises[index] = exercises[j]; exercises[j] = t
        }
    }

    fun save(onDone: () -> Unit) {
        if (title.isBlank()) { error = "Введите название тренировки"; return }
        if (exercises.isEmpty()) { error = "Добавьте хотя бы одно упражнение"; return }
        val drafts = mutableListOf<ExerciseDraft>()
        exercises.forEachIndexed { i, e ->
            val n = i + 1
            if (e.name.isBlank()) { error = "Упражнение $n: введите название"; return }
            val sets = e.sets.trim().toIntOrNull()
            if (sets == null || sets < 1 || sets > 20) { error = "Упражнение $n: подходов должно быть от 1 до 20"; return }
            val weight = if (e.weight.isBlank()) null else Fmt.parseWeight(e.weight)
            if (e.weight.isNotBlank() && (weight == null || weight < 0)) {
                error = "Упражнение $n: вес должен быть числом не меньше 0"; return
            }
            drafts.add(ExerciseDraft(e.id, e.name.trim(), sets, e.reps.trim().ifBlank { null }, weight))
        }
        error = null
        viewModelScope.launch {
            repo.saveWorkout(WorkoutDraft(workoutId, title.trim(), epochDay, dayOfWeek, description.trim(), drafts))
            onDone()
        }
    }
}

@Composable
fun WorkoutEditorScreen(workoutId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "editor$workoutId") { EditorViewModel(workoutId, it.repository) }

    ZScreen(if (workoutId == 0L) "Новая тренировка" else "Редактирование", onBack) { pad ->
        if (!vm.loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@ZScreen
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = vm.title, onValueChange = { vm.title = it.take(60) },
                    label = { Text("Название, например «Грудь и трицепс»") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item { DateField(vm.epochDay, vm::changeDate, Modifier.fillMaxWidth()) }
            item {
                Column {
                    Text("День недели (авто-выбор ближайшей даты)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(Fmt.dayShort) { i, name ->
                            FilterChip(
                                selected = vm.dayOfWeek == i + 1,
                                onClick = { vm.changeDayOfWeek(i + 1) },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = vm.description, onValueChange = { vm.description = it.take(300) },
                    label = { Text("Описание (необязательно)") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
            }
            item { Text("Упражнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            itemsIndexed(vm.exercises, key = { _, e -> e.uid }) { index, e ->
                ZCard {
                    OutlinedTextField(
                        value = e.name, onValueChange = { e.name = it.take(60) },
                        label = { Text("Упражнение ${index + 1}") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Быстрый выбор из каталога упражнений
                    LazyRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ExerciseCatalog.defaultExercises) { item ->
                            FilterChip(
                                selected = e.name == item.name,
                                onClick = { e.name = item.name },
                                label = { Text("✏️ " + item.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = e.sets, onValueChange = { e.sets = it.filter(Char::isDigit).take(2) },
                            label = { Text("Подходов") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = e.reps, onValueChange = { e.reps = it.take(10) },
                            label = { Text("Повторы") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = e.weight, onValueChange = { e.weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                            label = { Text("Вес, кг") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                        IconButton(onClick = { vm.move(index, 1) }, enabled = index < vm.exercises.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                        IconButton(onClick = { vm.removeExercise(index) }) {
                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = vm::addExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Text("  Добавить упражнение")
                }
            }
            item { ErrorText(vm.error) }
            item {
                GradientButton("Сохранить тренировку", onClick = { vm.save(onBack) },
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Check)
            }
        }
    }
}
