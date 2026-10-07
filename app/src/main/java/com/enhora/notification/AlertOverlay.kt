package com.enhora.notification

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.enhora.EnHoraApp
import com.enhora.data.AlertMode
import com.enhora.data.Status
import com.enhora.ui.ReminderBanner
import com.enhora.ui.theme.EnHoraTheme
import com.enhora.ui.theme.ThemeMode
import com.enhora.ui.theme.rememberPalette
import com.enhora.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * El mismo aviso propio de la app ([ReminderBanner]) pero sobre cualquier otra pantalla, para cuando la app
 * no está en uso. Necesita el permiso "Mostrar sobre otras apps"; sin él (o con el móvil bloqueado)
 * el aviso sale como notificación del sistema.
 */
object AlertOverlay {
    private val main = Handler(Looper.getMainLooper())
    private val alerts = mutableStateListOf<ReminderAlert>()
    private var view: ComposeView? = null

    fun canShow(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    fun show(context: Context, alert: ReminderAlert) {
        val app = context.applicationContext
        main.post {
            if (alerts.none { it.key == alert.key }) {
                alerts += alert
                notifyUser(app, alert.alertMode)
            }
            attach(app)
        }
    }

    private fun attach(context: Context) {
        if (view != null) return
        val owner = OverlayOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val app = context as EnHoraApp
                val dark = when (app.userPrefs.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> isSystemInDarkTheme()
                }
                EnHoraTheme(dark = dark, palette = rememberPalette(app.userPrefs.paletteIndex)) {
                    ReminderBanner(
                        alerts = alerts,
                        onDone = { resolve(app, it, Status.DONE) },
                        onCancel = { resolve(app, it, Status.CANCELED) },
                        onSnooze = { alert, minutes -> snooze(app, alert, minutes) },
                        onIgnore = { escalate(app, it) },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        context.getSystemService(WindowManager::class.java).addView(composeView, params)
        view = composeView
    }

    /** Quita la ventana cuando ya no queda ningún aviso. */
    private fun removeIfEmpty(context: Context) {
        if (alerts.isNotEmpty()) return
        main.post {
            if (alerts.isNotEmpty()) return@post
            view?.let { context.getSystemService(WindowManager::class.java).removeView(it) }
            view = null
        }
    }

    private fun resolve(app: EnHoraApp, alert: ReminderAlert, status: Int) {
        alerts.removeAll { it.key == alert.key }
        removeIfEmpty(app)
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.resolve(alert.reminderId, alert.epochDay, status)
            TodayWidget.refreshSoon(app)
        }
    }

    private fun snooze(app: EnHoraApp, alert: ReminderAlert, minutes: Int) {
        alerts.removeAll { it.key == alert.key }
        removeIfEmpty(app)
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.snooze(alert.reminderId, alert.epochDay, minutes)
            TodayWidget.refreshSoon(app)
        }
    }

    /** Aviso ignorado: pasa a la notificación del sistema para no perderse. */
    private fun escalate(app: EnHoraApp, alert: ReminderAlert) {
        alerts.removeAll { it.key == alert.key }
        removeIfEmpty(app)
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = app.repository.getReminder(alert.reminderId) ?: return@launch
            val tw = app.repository.get(reminder.taskId) ?: return@launch
            if (!app.repository.isResolved(tw.task.id, LocalDate.ofEpochDay(alert.epochDay), reminder.minuteOfDay)) {
                NotificationHelper.show(app, tw.task, reminder, alert.epochDay)
            }
        }
    }

    /** Sonido y vibración según el modo del recordatorio, como lo haría su notificación. */
    private fun notifyUser(context: Context, mode: Int) {
        if (mode == AlertMode.SILENT) return
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        if (mode == AlertMode.SOUND) {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    /** Ciclo de vida mínimo que Compose necesita para vivir fuera de una actividad. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}
