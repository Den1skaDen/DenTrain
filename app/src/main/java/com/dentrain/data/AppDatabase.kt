package com.dentrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkoutEntity::class, ExerciseEntity::class, SetResultEntity::class,
        PinnedWorkoutEntity::class, RecordEntity::class, SettingsEntity::class, DiaryLink::class
    ],
    version = 15,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun recordDao(): RecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        const val LEGACY_FILE_NAME = "dentrain.db"

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN homeBlocksOrder TEXT NOT NULL DEFAULT 'MAIN,CALENDAR,PLANNED,PINNED'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE records_v10 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exerciseName TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, comment TEXT NOT NULL)")
                db.execSQL("INSERT INTO records_v10 SELECT id, exerciseName, weight, reps, dateEpochDay, comment FROM records")
                db.execSQL("DROP TABLE records")
                db.execSQL("ALTER TABLE records_v10 RENAME TO records")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN swapWeightReps INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE set_results ADD COLUMN isWarmup INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN cloudId TEXT")
                db.execSQL("ALTER TABLE workouts ADD COLUMN assignmentId TEXT")
                db.execSQL("CREATE TABLE diary_links (documentId TEXT NOT NULL PRIMARY KEY, localId INTEGER NOT NULL, digest TEXT)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX index_workouts_status_dateEpochDay ON workouts (status, dateEpochDay)")
                db.execSQL("CREATE INDEX index_workouts_cloudId ON workouts (cloudId)")
                db.execSQL("CREATE INDEX index_workouts_assignmentId ON workouts (assignmentId)")
                db.execSQL("CREATE INDEX index_records_dateEpochDay ON records (dateEpochDay)")
                db.execSQL("CREATE INDEX index_records_weight ON records (weight)")
                db.execSQL("CREATE INDEX index_set_results_exerciseId_setNumber ON set_results (exerciseId, setNumber)")
                db.execSQL("DROP INDEX index_set_results_exerciseId")
            }
        }

        fun create(context: Context, name: String = LEGACY_FILE_NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
                )
                .build()
    }
}

