package com.dentrain

import android.app.Application
import com.dentrain.data.AppDatabase
import com.dentrain.data.WorkoutRepository
import com.dentrain.timer.RestTimer
import com.dentrain.timer.StopwatchTimer
import com.dentrain.timer.TimerNotifier
import com.dentrain.timer.WorkoutReminderNotifier
import com.dentrain.timer.WorkoutReminderScheduler

/** Простейший «контейнер зависимостей»: один экземпляр базы, репозитория и таймера на всё приложение. */
class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val repository = WorkoutRepository(database)
    val restTimer = RestTimer(app)
    val stopwatchTimer = StopwatchTimer(app)
}

class DenTrainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TimerNotifier.ensureChannel(this)
        WorkoutReminderNotifier.ensureChannel(this)
        container = AppContainer(this)
        WorkoutReminderScheduler.sync(this)
    }
}

