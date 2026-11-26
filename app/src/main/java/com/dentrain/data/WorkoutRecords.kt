package com.dentrain.data

internal fun SetResultEntity.qualifiesForRecord(): Boolean =
    !isWarmup && !isTimeBased && reps > 0 && weight != null && weight.isFinite() && weight > 0

internal fun workoutRecordUpdates(
    workout: WorkoutWithExercises,
    history: List<WorkoutWithExercises>
): List<RecordUpdate> {
    val workoutId = workout.workout.id
    val exercises = workout.exercises.map { it.exercise.name.recordKey() }.toSet()
    val results = (history.filter {
        it.workout.id != workoutId && it.workout.status == WorkoutStatus.COMPLETED
    } + workout).flatMap { entry ->
        entry.exercises.filter { it.exercise.name.recordKey() in exercises }.flatMap { exercise ->
            exercise.sets.filter { it.qualifiesForRecord() }.map { set ->
                WorkoutRecordSet(
                    entry.workout.id,
                    set,
                    RecordEntity(
                        exerciseName = exercise.exercise.name,
                        weight = checkNotNull(set.weight),
                        reps = set.reps,
                        dateEpochDay = entry.workout.dateEpochDay
                    )
                )
            }
        }
    }.sortedWith(compareBy({ it.set.completedAt }, { it.set.id }))

    val best = mutableMapOf<String, MutableList<RecordEntity>>()
    val updates = mutableListOf<RecordUpdate>()
    for ((id, set, record) in results) {
        val exerciseBest = best.getOrPut(record.exerciseName.recordKey()) { mutableListOf() }
        val improved = exerciseBest.filter { record.covers(it) }
        val exceedsHistory = exerciseBest.none { it.covers(record) }
        if (id == workoutId) {
            when (set.isPersonalRecord) {
                true -> {
                    val weight = set.previousRecordWeight
                    val reps = set.previousRecordReps
                    val previous = if (weight != null && reps != null) record.copy(weight = weight, reps = reps) else null
                    updates.add(RecordUpdate(record, previous))
                }
                null -> if (exceedsHistory) {
                    val previous = improved.firstOrNull { it.reps == record.reps }
                        ?: improved.firstOrNull { it.weight == record.weight }
                        ?: improved.maxByOrNull { it.weight }
                    updates.add(RecordUpdate(record, previous))
                }
                false -> Unit
            }
        }
        if (exceedsHistory) {
            exerciseBest.removeAll(improved.toSet())
            exerciseBest.add(record)
        }
    }
    return updates.filterNot { (record) ->
        updates.any { (other) ->
            other !== record && other.exerciseName.recordKey() == record.exerciseName.recordKey() &&
                other.covers(record) && !record.covers(other)
        }
    }.distinctBy { (record) -> Triple(record.exerciseName.recordKey(), record.weight, record.reps) }
}

private data class WorkoutRecordSet(val workoutId: Long, val set: SetResultEntity, val record: RecordEntity)

private fun String.recordKey() = trim().lowercase()

private fun RecordEntity.covers(other: RecordEntity): Boolean = weight >= other.weight && reps >= other.reps
