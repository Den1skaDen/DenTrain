package com.dentrain.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Статусы тренировки. Хранятся числом, чтобы запросы к базе были простыми. */
object WorkoutStatus {
    const val PLANNED = 0      // создана, ещё не начата (шаблон будущего занятия)
    const val IN_PROGRESS = 1  // выполняется сейчас (можно продолжить после закрытия приложения)
    const val COMPLETED = 2    // завершена, лежит в истории
}

/** Тренировка. Каждое занятие — отдельная запись со своей датой и своими результатами. */
@Entity(
    tableName = "workouts",
    indices = [Index(value = ["status", "dateEpochDay"]), Index("cloudId"), Index("assignmentId")]
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateEpochDay: Long,          // дата как LocalDate.toEpochDay()
    val dayOfWeek: Int,              // 1 = понедельник ... 7 = воскресенье
    val description: String = "",
    val status: Int = WorkoutStatus.PLANNED,
    val currentExerciseIndex: Int = 0, // на каком упражнении остановились (для продолжения)
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val cloudId: String? = null,
    val assignmentId: String? = null
)

/** Упражнение внутри тренировки. Здесь хранится ПЛАН (подходы, повторения, вес). */
@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val name: String,
    val plannedSets: Int,
    val plannedReps: String? = null,   // строка, чтобы можно было писать "8-10"
    val plannedWeight: Double? = null
)

/** Фактический результат одного подхода. Отделён от плана. */
@Entity(
    tableName = "set_results",
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["exerciseId", "setNumber"])]
)
data class SetResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weight: Double? = null,
    val isTimeBased: Boolean = false, // true = секунды, false = повторения
    val isWarmup: Boolean = false,    // true = разминочный подход (не учитывается в рекордах и прогрессе)
    val completedAt: Long = System.currentTimeMillis()
)


/** Закреплённая тренировка. Хранится только ссылка — копии данных не создаются. */
@Entity(
    tableName = "pinned_workouts",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PinnedWorkoutEntity(
    @PrimaryKey val workoutId: Long,
    val pinnedAt: Long = System.currentTimeMillis()
)

/** Личный рекорд. Значения хранятся ровно такими, как ввёл пользователь. */
@Entity(tableName = "records", indices = [Index("dateEpochDay"), Index("weight")])
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val dateEpochDay: Long,
    val comment: String = "",
    val isFavorite: Boolean = false
)


/** Настройки пользователя (одна строка с id = 1). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val accent: String = "BLUE",      // "BLUE" или "RED"
    val darkTheme: Boolean = false,
    val glassTheme: Boolean = true,   // Стеклянная тема Apple Glassmorphism
    val restSeconds: Int = 180,       // 3 минуты по умолчанию
    val autoRestTimer: Boolean = true, // Включен по умолчанию
    val scheduledDays: String = "1,3,5", // Дни недели "1,3,5" (Пн, Ср, Пт)
    val reminderEnabled: Boolean = false,
    val reminderMode: Int = 0,         // 0 = Уведомление, 1 = Будильник
    val workoutStartTime: String = "18:00", // Основное время начала тренировки
    val reminderLeadOptions: String = "60,15", // Интервалы напоминаний: "180,120,60,30,15,0"
    val homeBlocksOrder: String = "MAIN,CALENDAR,PLANNED,PINNED",
    val swapWeightReps: Boolean = false // Меняет порядок: сначала вес, затем повторения
)






// ---------- Связи для чтения ----------

data class CalendarWorkout(val id: Long, val dateEpochDay: Long, val status: Int)

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val sets: List<SetResultEntity>
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = ExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<ExerciseWithSets>
)
