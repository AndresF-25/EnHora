package com.enhora.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.enhora.EnHoraApp
import com.enhora.MainActivity
import com.enhora.R
import com.enhora.data.Agenda
import com.enhora.ui.currentHour
import com.enhora.ui.renderSkyBitmap
import com.enhora.ui.theme.AppPalettes
import com.enhora.ui.theme.ThemeMode
import com.enhora.ui.theme.autoPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Widget de pantalla de inicio. Su fondo es el mismo cielo de la app (el sol entra por un lado y sale
 * por el otro, la luna por la noche) con el color del tema en ese momento. A la izquierda, el número
 * de pendientes de hoy en grande; a la derecha, dos columnas: "Próximos" (tarjetas con una franja de detalle del color de su
 * franja horaria, deslizable) y "Hechos" (lo atendido hoy). Tocar uno abre la app en la tarjeta de su día.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        refreshAsync(context)

    override fun onEnabled(context: Context) = scheduleRefresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAsync(context)
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { update(context) } finally { pending.finish() }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.enhora.widget.REFRESH"
        private const val REFRESH_EVERY_MS = 15 * 60_000L
        private const val MIN_WIDTH_FOR_DONE_DP = 220

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, TodayWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** El sol se mueve con el reloj: se redibuja cada 15 min mientras haya un widget colocado. */
        private fun scheduleRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java).setInexactRepeating(
                AlarmManager.RTC, System.currentTimeMillis() + REFRESH_EVERY_MS, REFRESH_EVERY_MS, refreshIntent(context),
            )
        }

        /** Reenvía el estado tras una breve espera, cuando el lanzador ya vuelve a estar visible y escuchando. */
        fun refreshSoon(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(1200)
                update(context)
            }
        }

        /** Redibuja todos los widgets colocados (si no hay ninguno, no hace nada). */
        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return

            val app = context.applicationContext as EnHoraApp
            val (tasks, completions) = app.repository.snapshot()
            val entries = Agenda.entriesOn(tasks, completions.toSet(), LocalDate.now())
            val pendingCount = entries.count { !it.resolved }

            val hour = currentHour()
            val palette = AppPalettes.getOrNull(app.userPrefs.paletteIndex) ?: autoPalette(hour)
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (app.userPrefs.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDark
            }
            val gradient = if (dark) palette.dark else palette.light
            val density = context.resources.displayMetrics.density
            val stamp = System.currentTimeMillis()

            // Tocar el número o un hueco abre la app; tocar un recordatorio la abre en su tarjeta.
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val itemTemplate = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(120)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70).coerceAtLeast(70)
                val views = RemoteViews(context.packageName, R.layout.widget_today)

                // Fondo: el cielo, a la medida del widget y con un tope para no pasarse de memoria
                val scale = minOf(density, 900f / widthDp)
                views.setImageViewBitmap(
                    R.id.widget_bg,
                    renderSkyBitmap((widthDp * scale).toInt(), (heightDp * scale).toInt(), scale, hour, gradient, cornerDp = 24f),
                )

                // Número grande de pendientes de hoy
                val label = when {
                    entries.isEmpty() -> "sin recordatorios"
                    pendingCount == 0 -> "todo listo"
                    pendingCount == 1 -> "pendiente"
                    else -> "pendientes"
                }
                views.setTextViewText(R.id.widget_count, pendingCount.toString())
                views.setTextViewText(R.id.widget_label, label)

                // Dos listas deslizables (próximos / hechos); cada widget y columna tiene su adaptador (la URI lo distingue)
                fun adapter(done: Boolean) = Intent(context, TodayWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(TodayWidgetService.EXTRA_DONE_COLUMN, done)
                    // Cada actualización usa una dirección nueva: así el lanzador recarga la lista en vez de repintar la anterior.
                    data = Uri.fromParts("enhora", "widget/$id/${if (done) "done" else "next"}/$stamp", null)
                }
                views.setRemoteAdapter(R.id.widget_list_next, adapter(done = false))
                views.setEmptyView(R.id.widget_list_next, R.id.widget_empty_next)
                views.setPendingIntentTemplate(R.id.widget_list_next, itemTemplate)
                views.setRemoteAdapter(R.id.widget_list_done, adapter(done = true))
                views.setEmptyView(R.id.widget_list_done, R.id.widget_empty_done)
                views.setPendingIntentTemplate(R.id.widget_list_done, itemTemplate)
                // En un widget angosto, "Hechos" no cabe: se oculta para no aplastar las tarjetas.
                views.setViewVisibility(R.id.widget_done_column, if (widthDp < MIN_WIDTH_FOR_DONE_DP) View.GONE else View.VISIBLE)
                views.setOnClickPendingIntent(R.id.widget_left, open)
                views.setOnClickPendingIntent(R.id.widget_empty_next, open)
                views.setOnClickPendingIntent(R.id.widget_empty_done, open)

                manager.updateAppWidget(id, views)
                // Con la app abierta el sistema puede dejar sin aplicar el texto de la actualización completa: se refuerza en parcial.
                manager.partiallyUpdateAppWidget(
                    id,
                    RemoteViews(context.packageName, R.layout.widget_today).apply {
                        setTextViewText(R.id.widget_count, pendingCount.toString())
                        setTextViewText(R.id.widget_label, label)
                    },
                )
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_next)
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_done)
            }
            // Si el lanzador no estaba listo para la primera orden, la segunda llega cuando ya puede repintar las listas.
            kotlinx.coroutines.delay(1500)
            ids.forEach { id ->
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_next)
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_done)
            }
        }
    }
}
