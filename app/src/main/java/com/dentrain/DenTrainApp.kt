package com.dentrain

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.dentrain.data.DiarySync
import kotlinx.coroutines.*
import java.util.UUID
import com.dentrain.data.AppDatabase
import com.dentrain.data.ProfileAvatarCache
import com.dentrain.data.WorkoutRepository
import com.dentrain.data.WorkoutDelivery
import com.dentrain.data.WorkoutScreenCache
import com.dentrain.timer.RestTimer
import com.dentrain.timer.StopwatchTimer
import com.dentrain.timer.TimerNotifier
import com.dentrain.timer.WorkoutReminderNotifier
import com.dentrain.timer.WorkoutReminderSchedule
import com.dentrain.timer.WorkoutReminderScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/** Простейший «контейнер зависимостей»: один экземпляр базы, репозитория и таймера на всё приложение. */
class AppContainer(app: Application, val uid: String?, val databaseName: String) {
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(app, databaseName)
    val repository = WorkoutRepository(database, uid)
    val screenCache by lazy { WorkoutScreenCache(repository, screenScope) }
    internal val profileAvatar by lazy { uid?.let { ProfileAvatarCache(app, it, screenScope) } }
    val workoutDelivery by lazy { WorkoutDelivery(app) }
    val sync by lazy { uid?.let { DiarySync(database, it, repository) } }
    val restTimer = RestTimer(app, databaseName)
    val stopwatchTimer = StopwatchTimer(app)

    fun stopScreenCache() = screenScope.cancel()
}

class DenTrainApp : Application() {
    private val prefs by lazy { getSharedPreferences("diary_accounts", MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var reminderJob: Job? = null
    private var selected by mutableStateOf<AppContainer?>(null)
    val container: AppContainer get() = requireNotNull(selected)
    var needsDiaryChoice by mutableStateOf(false)
        private set

    private fun select(uid: String?) {
        val accountKey = uid?.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() } ?: "guest"
        val legacyOwner = prefs.getString("legacyOwner", null)
        needsDiaryChoice = uid != null && legacyOwner == null && getDatabasePath(AppDatabase.LEGACY_FILE_NAME).exists() &&
            !prefs.contains("skip_$accountKey")
        val name = if (uid != null && uid == legacyOwner) AppDatabase.LEGACY_FILE_NAME else "diary-$accountKey.db"
        if (selected?.databaseName == name && selected?.uid == uid) {
            if (!needsDiaryChoice && syncJob == null) startSync(container)
            return
        }
        syncJob?.cancel()
        syncJob = null
        reminderJob?.cancel()
        val previous = selected
        previous?.let {
            it.restTimer.close()
            it.stopwatchTimer.reset()
            it.stopScreenCache()
            WorkoutReminderNotifier.cancel(this)
        }
        selected = AppContainer(this, uid, name)
        val account = container
        if (!needsDiaryChoice) startSync(container)
        reminderJob = scope.launch(Dispatchers.Main.immediate) {
            // При холодном запуске по сигналу receiver сам обработает текущий будильник.
            account.repository.settings.map { WorkoutReminderSchedule.from(it) }
                .distinctUntilChanged().drop(if (previous == null) 1 else 0)
                .collect { WorkoutReminderScheduler.sync(this@DenTrainApp) }
        }
    }

    private fun startSync(account: AppContainer) {
        syncJob = account.uid?.let { scope.launch { account.sync?.run() } }
    }

    fun chooseLegacyDiary(import: Boolean) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        if (uid != container.uid || !needsDiaryChoice) return
        val key = UUID.nameUUIDFromBytes(uid.toByteArray()).toString()
        val edit = prefs.edit()
        if (import) edit.putString("legacyOwner", uid) else edit.putBoolean("skip_$key", true)
        check(edit.commit())
        select(uid)
    }

    override fun onCreate() {
        super.onCreate()
        TimerNotifier.ensureChannel(this)
        WorkoutReminderNotifier.ensureChannel(this)
        val auth = FirebaseAuth.getInstance()
        select(auth.uid)
        auth.addAuthStateListener { select(it.uid) }
    }
}

