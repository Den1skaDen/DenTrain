package com.dentrain.ui.records

import com.dentrain.data.RecordEntity
import com.dentrain.data.WorkingSetForProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class ProgressFilter(
    val exerciseName: String?,
    val repetitionFilter: String,
    val favoritesOnly: Boolean
)

internal data class ProgressPoint(val dateEpochDay: Long, val weight: Double, val reps: Int)

internal data class ProgressSeries(val filter: ProgressFilter, val points: List<ProgressPoint>)

internal fun repetitionRange(filter: String): IntRange? = when (filter) {
    "1-3" -> 1..3
    "3-5" -> 3..5
    "5-8" -> 5..8
    "8-12" -> 8..12
    "12-15" -> 12..15
    "15+" -> 15..Int.MAX_VALUE
    else -> null
}

internal fun dailyProgress(
    exerciseName: String,
    records: List<RecordEntity>,
    sets: List<WorkingSetForProgress>,
    filter: ProgressFilter,
    zone: ZoneId = ZoneId.systemDefault()
): List<ProgressPoint> {
    val allowedReps = repetitionRange(filter.repetitionFilter)
    val days = mutableMapOf<Long, ProgressPoint>()
    fun add(day: Long, weight: Double, reps: Int) {
        if (!weight.isFinite() || weight < 0 || reps <= 0) return
        if (allowedReps != null && reps !in allowedReps) return

        val best = days[day]
        if (best == null || weight > best.weight || (weight == best.weight && reps > best.reps)) {
            days[day] = ProgressPoint(day, weight, reps)
        }
    }

    for (record in records) {
        if (!record.exerciseName.trim().equals(exerciseName, ignoreCase = true)) continue
        if (filter.favoritesOnly && !record.isFavorite) continue
        if (record.dateEpochDay !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) continue
        add(record.dateEpochDay, record.weight, record.reps)
    }
    if (!filter.favoritesOnly) {
        for (set in sets) {
            val day = Instant.ofEpochMilli(set.completedAt).atZone(zone).toLocalDate().toEpochDay()
            add(day, set.weight, set.reps)
        }
    }
    return days.values.sortedBy { it.dateEpochDay }
}

internal fun progressDateFraction(day: Long, firstDay: Long, lastDay: Long): Float =
    if (firstDay == lastDay) 0.5f else ((day - firstDay).toDouble() / (lastDay - firstDay)).toFloat()

internal fun progressWeightTicks(points: List<ProgressPoint>): List<Double> {
    val min = points.minOf { it.weight }
    val max = points.maxOf { it.weight }
    val padding = maxOf((max - min) * 0.15, max * 0.04, 2.5)
    val lower = (min - padding).coerceAtLeast(0.0)
    val upper = max + padding
    if (!upper.isFinite()) return listOf(0.0, max / 2, max)

    val roughStep = (upper - lower) / 4
    val magnitude = 10.0.pow(floor(log10(roughStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= roughStep / magnitude } * magnitude
    val first = floor(lower / step).toInt()
    val last = ceil(upper / step).toInt()
    return (first..last).map { it * step }.takeIf { it.last().isFinite() }
        ?: listOf(0.0, max / 2, max)
}
