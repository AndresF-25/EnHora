package com.enhora

import android.app.Application
import com.enhora.alarm.AlarmScheduler
import com.enhora.data.AppDatabase
import com.enhora.data.TaskRepository
import com.enhora.data.UserPrefs
import com.enhora.notification.NotificationHelper
import com.enhora.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class EnHoraApp : Application() {
    val userPrefs by lazy { UserPrefs(this) }
    val scheduler by lazy { AlarmScheduler(this) }
    val repository by lazy { TaskRepository(AppDatabase.create(this).taskDao(), scheduler) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        // Cualquier cambio en los recordatorios (desde la app, una notificación o el widget) refresca el widget.
        appScope.launch {
            combine(repository.tasks, repository.completions) { _, _ -> }
                .debounce(400)
                .collect { TodayWidget.update(this@EnHoraApp) }
        }
    }
}
