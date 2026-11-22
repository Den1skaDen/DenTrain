package com.dentrain.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class WorkoutCalendar(
    val workoutsByDate: Map<Long, List<CalendarWorkout>> = emptyMap(),
    val completedDates: Set<LocalDate> = emptySet(),
    val plannedDates: Set<LocalDate> = emptySet()
)

/** Local screen data stays warm while this account is selected. Room remains the source of truth. */
class WorkoutScreenCache(repo: WorkoutRepository, scope: CoroutineScope) {
    private val started = SharingStarted.Eagerly
    val settings = repo.settings.stateIn(scope, started, SettingsEntity())
    val planned = repo.planned.stateIn(scope, started, emptyList())
    val active = repo.inProgress.stateIn(scope, started, null)
    val pinned = repo.pinned.stateIn(scope, started, emptyList())
    val history = repo.history.stateIn(scope, started, emptyList())
    val records = repo.allRecords.stateIn(scope, started, emptyList())
    val calendar = repo.calendarWorkouts.map { workouts ->
        val byDate = workouts.groupBy { it.dateEpochDay }
        val completed = mutableSetOf<LocalDate>()
        val planned = mutableSetOf<LocalDate>()
        for ((day, entries) in byDate) {
            val date = LocalDate.ofEpochDay(day)
            if (entries.any { it.status == WorkoutStatus.COMPLETED }) completed.add(date)
            if (entries.any { it.status == WorkoutStatus.PLANNED || it.status == WorkoutStatus.IN_PROGRESS }) {
                planned.add(date)
            }
        }
        WorkoutCalendar(byDate, completed, planned)
    }.stateIn(scope, started, WorkoutCalendar())
}
