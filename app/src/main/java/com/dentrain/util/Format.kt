package com.dentrain.util

import com.dentrain.data.SetResultEntity
import com.dentrain.data.WorkoutEntity
import com.dentrain.data.ExerciseWithSets
import com.dentrain.data.WorkoutWithExercises
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** Вспомогательные функции форматирования: даты, вес, склонения, время. */
object Fmt {
    val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    val dayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val monthNames = listOf("янв.", "февр.", "марта", "апр.", "мая", "июня", "июля", "авг.", "сент.", "окт.", "нояб.", "дек.")

    private val longFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'г.'", Locale.forLanguageTag("ru"))
    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    private val shortFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun dayName(day: Int): String = dayNames[(day - 1).coerceIn(0, 6)]
    fun monthName(month: Int): String = monthNames[(month - 1).coerceIn(0, 11)]


    fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortFormat)
    fun displayDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormat)

    /** «Понедельник, 19 октября 2026 г.» */
    fun longDate(w: WorkoutEntity): String =
        "${dayName(w.dayOfWeek)}, ${LocalDate.ofEpochDay(w.dateEpochDay).format(longFormat)}"

    /** 60.0 -> «60», 62.5 -> «62,5». Если веса нет — null. */
    fun weight(value: Double?): String? =
        value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }

    /** Разбор веса: допускает запятую. Возвращает null, если текст не число. */
    fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    /** «10 повторений, 60 кг» или «60 сек, 10 кг» */
    fun setLine(s: SetResultEntity): String {
        val unitText = if (s.isTimeBased) "сек" else plural(s.reps, "повторение", "повторения", "повторений")
        val countStr = "${s.reps} $unitText"
        return weight(s.weight)?.let { "$countStr, $it кг" } ?: countStr
    }

    /** Компактное представление подхода: «12 х 60 кг» или «15 сек х 20 кг» или «12» */
    fun shortSetLine(s: SetResultEntity): String {
        val countStr = if (s.isTimeBased) "${s.reps} сек" else "${s.reps}"
        return weight(s.weight)?.let { "$countStr х $it кг" } ?: countStr
    }

    /** mm:ss, остаток округляется вверх, чтобы «00:00» показывалось только в самом конце. */
    fun clock(ms: Long): String {
        val total = ceil(ms / 1000.0).toLong().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    /** mm:ss для прямого секундомера (минуты и секунды). */
    fun stopwatchClock(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }


    /** Форматирует длительность тренировки между старт/финиш (например, "45 мин 20 сек" или "1 ч 15 мин"). */
    fun durationText(startedAt: Long?, completedAt: Long?): String? {
        if (startedAt == null || completedAt == null || completedAt <= startedAt) return null
        return formatDurationMs(completedAt - startedAt)
    }

    /** Форматирует миллисекунды длительности. */
    fun formatDurationMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return when {
            hours > 0 -> "$hours ч $mins мин"
            mins > 0 -> "$mins мин $secs сек"
            else -> "$secs сек"
        }
    }

    fun summary(w: WorkoutWithExercises): String {
        val n = w.exercises.size
        val sets = w.doneSets()
        val dur = durationText(w.workout.startedAt, w.workout.completedAt)
        val durStr = if (dur != null) " · ⏱ $dur" else ""
        return "$n ${plural(n, "упражнение", "упражнения", "упражнений")} · " +
            "$sets ${plural(sets, "выполненный подход", "выполненных подхода", "выполненных подходов")}$durStr"
    }
}


fun WorkoutWithExercises.doneSets(): Int = exercises.sumOf { it.sets.size }

val ExerciseWithSets.completedWorkingSets: Int
    get() = sets.count { !it.isWarmup }

val ExerciseWithSets.totalWorkingSets: Int
    get() = maxOf(exercise.plannedSets, completedWorkingSets)

val ExerciseWithSets.isWorkingPlanComplete: Boolean
    get() = totalWorkingSets > 0 && completedWorkingSets == totalWorkingSets
