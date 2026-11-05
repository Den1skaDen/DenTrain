package com.dentrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutEntity::class, ExerciseEntity::class, SetResultEntity::class,
        PinnedWorkoutEntity::class, RecordEntity::class, SettingsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun recordDao(): RecordDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dentrain.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

