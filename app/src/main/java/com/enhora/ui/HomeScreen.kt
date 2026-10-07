package com.enhora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Pantalla de inicio: saludo, flor y pendientes de hoy sobre un degradado. */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    userName: String,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onCalendar: () -> Unit,
) {
    val dimens = rememberDimens()
    val today by vm.todayEntries.collectAsState()
    val tomorrow by vm.tomorrowEntries.collectAsState()
    // Hecho o cancelado cuenta como atendido: ya no está pendiente.
    val done = today.count { it.resolved }
    val pending = today.size - done
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = today.filter { !it.resolved && it.minuteOfDay >= nowMinute }.minByOrNull { it.minuteOfDay }

    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (today.isEmpty()) 0f else done.toFloat() / today.size,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "dayProgress",
    )
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior: menú a la izquierda y nombre de la app centrado.
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                }
                Text(
                    "EnHora",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 1.5.sp,
                )
            }

            // Zona central: se reparte a lo alto de la pantalla y, si no cabe (horizontal, texto grande), se desplaza.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val h = maxHeight
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = h),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                    Text(
                        "Hola, $userName",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    DaySky(height = (h * 0.46f).coerceIn(180.dp, 320.dp))

                    // Bajo la flor: pendientes de hoy, avance del día, mañana y siguiente aviso.
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            pending.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = if (dimens.compactHeight) 56.sp else 80.sp,
                            lineHeight = if (dimens.compactHeight) 60.sp else 84.sp,
                            fontWeight = FontWeight.ExtraLight,
                        )
                        Text(
                            when {
                                today.isEmpty() -> "no tienes recordatorios hoy"
                                pending == 0 -> "¡todo listo por hoy!"
                                pending == 1 -> "recordatorio pendiente hoy"
                                else -> "recordatorios pendientes hoy"
                            },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                        )
                        if (today.isNotEmpty()) {
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.width(110.dp).height(4.dp).clip(CircleShape),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.25f),
                                )
                                Text(
                                    "$done de ${today.size} atendidos",
                                    modifier = Modifier.padding(start = 10.dp),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        val tomorrowPending = tomorrow.count { !it.resolved }
                        Text(
                            when (tomorrowPending) {
                                0 -> "Mañana: sin recordatorios"
                                1 -> "Mañana: 1 recordatorio"
                                else -> "Mañana: $tomorrowPending recordatorios"
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                        if (next != null) {
                            Text(
                                "Siguiente · ${formatTime(next.minuteOfDay)}  ${next.title}",
                                modifier = Modifier.padding(top = 4.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    }
                }
            }

            // Franja inferior: fecha de hoy y botón para añadir.
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.14f))
                    .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", EsLocale))}  ›",
                    modifier = Modifier.weight(1f).pressClickable(pressedScale = 0.97f, role = null, onClick = onCalendar),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                )
                Row(
                    Modifier.height(40.dp).background(Color.White.copy(alpha = 0.22f), CircleShape)
                        .pressClickable(pressedScale = 0.92f, onClick = onAdd).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Añadir", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
