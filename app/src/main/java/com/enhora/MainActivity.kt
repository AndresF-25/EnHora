package com.enhora

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.enhora.data.Status
import com.enhora.notification.NotificationHelper
import com.enhora.notification.ReminderAlerts
import com.enhora.ui.ReminderBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.enhora.ui.MainShell
import com.enhora.ui.theme.rememberPalette
import com.enhora.ui.theme.ThemeMode
import com.enhora.ui.theme.EnHoraTheme
import com.enhora.ui.MainViewModel
import com.enhora.ui.TaskEditScreen
import com.enhora.ui.WelcomeScreen
import java.time.LocalDate

private data class OpenTarget(val day: Long, val taskId: Long?, val minute: Int?)

private sealed interface Screen {
    data object Home : Screen
    data class Edit(val taskId: Long?, val day: LocalDate, val minute: Int?, val title: String) : Screen
}

class MainActivity : ComponentActivity() {
    private var needsExact by mutableStateOf(false)
    private var needsNotifications by mutableStateOf(false)
    private var needsOverlay by mutableStateOf(false)
    /** Día (epochDay) al que abrir el calendario cuando se toca un aviso; null si no hay ninguno. */
    private var openTarget by mutableStateOf<OpenTarget?>(null)

    private var viewModel: MainViewModel? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTarget = targetFrom(intent)
        val app = application as EnHoraApp
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = MainViewModel(app.repository) as T
        })[MainViewModel::class.java]
        viewModel = vm
        // Cura las alarmas por si algo (reinicio, ahorro de batería, actualización) las borró.
        lifecycleScope.launch(Dispatchers.IO) { app.repository.rescheduleAll() }

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var themeMode by remember { mutableIntStateOf(app.userPrefs.themeMode) }
            var paletteIndex by remember { mutableIntStateOf(app.userPrefs.paletteIndex) }
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            EnHoraTheme(dark = dark, palette = rememberPalette(paletteIndex)) {
                var userName by remember { mutableStateOf(app.userPrefs.name) }
                if (userName == null) {
                    WelcomeScreen(onContinue = { app.userPrefs.name = it; userName = it })
                    return@EnHoraTheme
                }
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var tabIndex by remember { mutableIntStateOf(0) }
                // Avisos que llegan con la app a la vista: se muestran en la ventana propia.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    ReminderAlerts.events.collect { vm.addAlert(it) }
                }
                androidx.compose.runtime.LaunchedEffect(openTarget) {
                    openTarget?.let {
                        val day = LocalDate.ofEpochDay(it.day)
                        if (it.taskId != null && it.minute != null) vm.focusReminder(it.taskId, day, it.minute)
                        else vm.selectDay(day)
                        tabIndex = 1
                        screen = Screen.Home
                        openTarget = null
                    }
                }
                BackHandler(enabled = screen is Screen.Edit) { screen = Screen.Home }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                // Home ↔ Edición: la pantalla de edición entra desde la derecha; al volver sale hacia ella.
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (targetState is Screen.Edit) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 6 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> MainShell(
                            vm = vm,
                            userName = userName.orEmpty(),
                            needsExactAlarm = needsExact,
                            needsNotifications = needsNotifications,
                            needsOverlay = needsOverlay,
                            onGrantOverlay = {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    .setData(android.net.Uri.parse("package:$packageName")))
                            },
                            onOpenNotificationSettings = {
                                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            },
                            onGrantExactAlarm = {
                                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:$packageName")))
                            },
                            onNewTask = { day, minute, title -> screen = Screen.Edit(null, day, minute, title) },
                            onEdit = { screen = Screen.Edit(it, vm.selectedDay.value, null, "") },
                            tabIndex = tabIndex,
                            onTabIndex = { tabIndex = it },
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it; app.userPrefs.themeMode = it },
                            paletteIndex = paletteIndex,
                            onPalette = { paletteIndex = it; app.userPrefs.paletteIndex = it },
                        )
                        is Screen.Edit -> TaskEditScreen(
                            vm = vm, taskId = s.taskId, initialDay = s.day,
                            initialMinute = s.minute, initialTitle = s.title,
                            onClose = { screen = Screen.Home },
                        )
                    }
                }
                ReminderBanner(
                    alerts = vm.alerts,
                    onDone = { vm.resolveAlert(it, Status.DONE) },
                    onCancel = { vm.resolveAlert(it, Status.CANCELED) },
                    onSnooze = { alert, minutes -> vm.snoozeAlert(alert, minutes) },
                    onIgnore = { vm.escalateAlert(this@MainActivity, it) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                }
            }
        }
    }

    /** Con la app abierta el sistema retrasa las actualizaciones del widget: al salir se vuelve a enviar el estado actual. */
    override fun onStop() {
        super.onStop()
        com.enhora.widget.TodayWidget.refreshSoon(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        targetFrom(intent)?.let { openTarget = it }
    }

    /** Lo que pide abrir un aviso o el widget: un día y, si lo traen, el recordatorio concreto. */
    private fun targetFrom(intent: Intent?): OpenTarget? {
        if (intent == null || !intent.hasExtra(NotificationHelper.EXTRA_OPEN_DAY)) return null
        val task = intent.getLongExtra(NotificationHelper.EXTRA_OPEN_TASK, -1L).takeIf { it >= 0 }
        val minute = intent.getIntExtra(NotificationHelper.EXTRA_OPEN_MINUTE, -1).takeIf { it >= 0 }
        return OpenTarget(intent.getLongExtra(NotificationHelper.EXTRA_OPEN_DAY, 0), task, minute)
    }

    override fun onResume() {
        super.onResume()
        ReminderAlerts.appVisible = true
        needsExact = !(application as EnHoraApp).scheduler.canScheduleExact()
        needsNotifications = !NotificationHelper.canNotify(this)
        needsOverlay = !Settings.canDrawOverlays(this)
    }

    override fun onPause() {
        super.onPause()
        ReminderAlerts.appVisible = false
        // Lo que no se atendió en la ventana propia sigue vivo como notificación del sistema.
        viewModel?.escalateAll(this)
    }
}
