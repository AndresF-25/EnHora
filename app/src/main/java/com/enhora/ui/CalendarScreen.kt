package com.enhora.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import com.enhora.data.Entry
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhora.data.DaySummary
import com.enhora.data.Slot
import com.enhora.ui.theme.OnGradientTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    vm: MainViewModel,
    userName: String,
    needsExactAlarm: Boolean,
    onGrantExactAlarm: () -> Unit,
    needsNotifications: Boolean,
    onOpenNotificationSettings: () -> Unit,
    needsOverlay: Boolean,
    onGrantOverlay: () -> Unit,
    onNewTask: (day: LocalDate, minute: Int?, title: String) -> Unit,
    onEdit: (taskId: Long) -> Unit,
    onMenu: () -> Unit,
) {
    val month by vm.visibleMonth.collectAsState()
    val selected by vm.selectedDay.collectAsState()
    val summary by vm.monthSummary.collectAsState()
    val days by vm.upcomingDays.collectAsState()
    val overdue by vm.overdue.collectAsState()

    var overdueOpen by remember { mutableStateOf(false) }
    // Deshacer: tras completar o cancelar desde una tarjeta se ofrece volver atrás unos segundos.
    val snackbar = remember { SnackbarHostState() }
    var undo by remember { mutableStateOf<Triple<Entry, Int, String>?>(null) }
    LaunchedEffect(undo) {
        val change = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(change.third, actionLabel = "Deshacer", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.restore(change.first, change.second)
        undo = null
    }
    val onToggle: (Entry, Boolean) -> Unit = { e, done ->
        undo = Triple(e, e.status, if (done) "Completado" else "Marcado como pendiente")
        vm.setDone(e, done)
    }
    val onCancelEntry: (Entry, Boolean) -> Unit = { e, canceled ->
        undo = Triple(e, e.status, if (canceled) "Cancelado" else "Restaurado")
        vm.setCanceled(e, canceled)
    }
    var sheetOpen by remember { mutableStateOf(false) }
    var quickSlot by remember { mutableStateOf<Slot?>(null) }
    val entries by vm.dayEntries.collectAsState()
    val listState = rememberLazyListState()
    val dims = rememberDimens()
    val compact = dims.compactHeight
    // Horizontal en un móvil: calendario a la izquierda y tarjetas a la derecha.
    val wide = compact && dims.widthDp > dims.heightDp * 1.3f

    // Al tocar un día de la cuadrícula la lista de tarjetas se desplaza sola hasta ese día.
    var scrollReq by remember { mutableIntStateOf(0) }
    val topItems = if (compact && !wide) 2 else 0 // cabecera y calendario van dentro de la lista
    LaunchedEffect(scrollReq, month, days.size) {
        val i = days.indexOfFirst { it.day == selected }
        if (i >= 0) listState.animateScrollToItem(i + topItems)
    }
    // Venir desde el widget o un aviso: se lleva la lista a la tarjeta del recordatorio y se resalta unos segundos.
    val focus = vm.focus.value
    LaunchedEffect(focus) {
        if (focus == null) return@LaunchedEffect
        val i = days.indexOfFirst { it.day == focus.day }
        if (i >= 0) listState.animateScrollToItem(i + topItems)
        kotlinx.coroutines.delay(6000)
        if (vm.focus.value == focus) vm.focus.value = null
    }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }

    OnGradientTheme {
        Scaffold(
            containerColor = Color.Transparent,
            // El contenedor principal ya aplica los márgenes del sistema (barra de estado y de navegación).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        data,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        actionColor = Color.White,
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onNewTask(selected, null, "") },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nueva", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                )
            },
        ) { padding ->
            // Cabecera con el menú y el saludo. En pantallas bajas se desplaza con la lista.
            val header: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú")
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            "Bienvenido",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            userName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Avisos del sistema: notificaciones, alarmas exactas y atrasadas.
            val notices: @Composable () -> Unit = {
                if (needsNotifications) {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Las notificaciones están desactivadas: no recibirás avisos fuera de la app.")
                            TextButton(onClick = onOpenNotificationSettings) { Text("Activar") }
                        }
                    }
                }
                if (needsOverlay) {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Permite mostrar sobre otras apps para ver el aviso de EnHora aunque no la estés usando.")
                            TextButton(onClick = onGrantOverlay) { Text("Permitir") }
                        }
                    }
                }
                if (needsExactAlarm) {
                    Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Activa las alarmas exactas para que los avisos lleguen a la hora.")
                            TextButton(onClick = onGrantExactAlarm) { Text("Permitir") }
                        }
                    }
                }
                if (overdue.isNotEmpty()) {
                    Card(
                        onClick = { overdueOpen = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                if (overdue.size == 1) "1 atrasada" else "${overdue.size} atrasadas",
                                modifier = Modifier.padding(start = 12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

            }
            // El calendario queda fijo; solo se desplazan las tarjetas, que se desvanecen al subir bajo él.
            // En pantallas bajas no cabrían los dos: en vertical el calendario pasa a ser parte de la lista
            // y en horizontal se colocan lado a lado.
            val calendar: @Composable (Modifier) -> Unit = { mod ->
                CalendarCard(
                    month = month,
                    selected = selected,
                    summary = summary,
                    onMonth = { vm.showMonth(it) },
                    onSelect = { vm.selectDay(it); scrollReq++ },
                    onToday = { vm.goToday() },
                    modifier = mod,
                )
            }
            val fadeHeight = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            val cards: @Composable (Modifier, Boolean) -> Unit = { cardsModifier, inlineTop ->
                LazyColumn(
                    state = listState,
                    modifier = cardsModifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Máscara: los primeros píxeles bajo el calendario se vuelven transparentes.
                            // En reposo no se aplica: solo se desvanece lo que ya se ha desplazado bajo el calendario.
                            val scrolled = if (listState.firstVisibleItemIndex > 0) 1f
                            else (listState.firstVisibleItemScrollOffset / fadeHeight).coerceIn(0f, 1f)
                            if (scrolled > 0f && !inlineTop) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 1f - scrolled),
                                        (fadeHeight / size.height).coerceIn(0.01f, 1f) to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (inlineTop) {
                        item(key = "header") { header() }
                        item(key = "calendar") { calendar(Modifier) }
                    }
                    if (days.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No hay recordatorios este mes.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(days, key = { it.day.toEpochDay() }) { d ->
                        val key = d.day.toEpochDay()
                        DayCard(
                            day = d.day,
                            entries = d.entries,
                            isSelected = d.day == selected,
                            onToggle = onToggle,
                            onCancel = onCancelEntry,
                            onEdit = onEdit,
                            onAdd = { onNewTask(d.day, null, "") },
                            onOpenDay = { vm.selectDay(d.day); sheetOpen = true },
                            focus = focus?.takeIf { it.day == d.day },
                            modifier = Modifier.animateItem().graphicsLayer {
                                // Al acercarse al borde superior la tarjeta se encoge y se aleja un poco.
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                if (info != null) {
                                    val f = (1f - (info.offset + info.size) / (info.size * 0.9f)).coerceIn(0f, 1f)
                                    val sc = 1f - 0.08f * f
                                    scaleX = sc; scaleY = sc
                                    alpha = 1f - 0.6f * f
                                    translationY = 14.dp.toPx() * f
                                }
                            },
                        )
                    }
                }
            }
            if (wide) {
                Row(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.weight(0.46f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        header()
                        notices()
                        calendar(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        Spacer(Modifier.height(88.dp))
                    }
                    cards(Modifier.weight(0.54f).fillMaxHeight(), false)
                }
            } else {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (!compact) header()
                    notices()
                    if (!compact) calendar(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    cards(Modifier.weight(1f).fillMaxWidth(), compact)
                }
            }
        }
    }

    if (sheetOpen) {
        DaySheet(
            day = selected,
            entries = entries,
            overdue = overdue,
            onDismiss = { sheetOpen = false },
            onToggle = vm::setDone,
            onEdit = { sheetOpen = false; onEdit(it) },
            onQuickAdd = { quickSlot = it },
        )
    }
    quickSlot?.let { slot ->
        QuickAddDialog(
            slot = slot,
            day = selected,
            onDismiss = { quickSlot = null },
            onSave = { title, minute ->
                vm.save(com.enhora.data.Task(title = title, epochDay = selected.toEpochDay()), listOf(minute))
                quickSlot = null
            },
            onMore = { title, minute ->
                quickSlot = null
                sheetOpen = false
                onNewTask(selected, minute, title)
            },
        )
    }
    if (overdueOpen) {
        OverdueSheet(
            overdue = overdue,
            onDismiss = { overdueOpen = false },
            onToggle = vm::setDone,
            onEdit = { overdueOpen = false; onEdit(it) },
        )
    }
}

/**
 * Calendario de cristal: selector de mes y cuadrícula. Se cambia de mes con las flechas,
 * tocando el mes vecino o deslizando a izquierda/derecha. Lo usan la pantalla principal y el selector de fecha.
 */
@Composable
fun CalendarCard(
    month: YearMonth,
    selected: LocalDate,
    summary: Map<LocalDate, DaySummary>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    Card(
        modifier = modifier.fillMaxWidth().pointerInput(month) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    if (drag <= -threshold) onMonth(month.plusMonths(1))
                    else if (drag >= threshold) onMonth(month.minusMonths(1))
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
            )
        },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            MonthSelector(month = month, onMonth = onMonth, onToday = onToday)
            WeekdayRow()
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it * dir } + fadeOut())
                },
                label = "monthGrid",
            ) { m ->
                MonthGrid(
                    month = m,
                    selected = selected,
                    summary = summary,
                    onSelect = onSelect,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun YearMonth.shortName(): String =
    month.getDisplayName(TextStyle.SHORT, EsLocale).trimEnd('.').uppercase().take(3)

/** Selector `NOV ‹ DIC › ENE`: el mes central en grande y los vecinos atenuados, pulsables. */
@Composable
private fun MonthSelector(month: YearMonth, onMonth: (YearMonth) -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            month.minusMonths(1).shortName(),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .pressClickable(pressedScale = 0.92f, role = null) { onMonth(month.minusMonths(1)) }
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir } + fadeOut())
            },
            modifier = Modifier.weight(1.2f),
            label = "monthTitle",
        ) { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(m.shortName(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Text(
                    m.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
        Text(
            month.plusMonths(1).shortName(),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .pressClickable(pressedScale = 0.92f, role = null) { onMonth(month.plusMonths(1)) }
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
    // Aparece y desaparece con animación para que la cuadrícula no dé un salto.
    AnimatedVisibility(visible = month != YearMonth.now()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Ir a hoy",
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .pressClickable(onClick = onToday).padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun WeekdayRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        DayOfWeek.entries.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.NARROW, EsLocale).uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    summary: Map<LocalDate, DaySummary>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    val today = remember { LocalDate.now() }
    Column(modifier.fillMaxWidth()) {
        repeat(weeks) { w ->
            Row(Modifier.height((42 * rememberDimens().fs).dp).fillMaxWidth()) {
                repeat(7) { c ->
                    val dayNumber = w * 7 + c - offset + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            summary = summary[date],
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    summary: DaySummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent, label = "cellBg")
    val ring by animateFloatAsState(if (isToday && !isSelected) 1f else 0f, label = "cellRing")
    Column(
        modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (ring > 0f) Modifier.border(1.5.dp, scheme.primary.copy(alpha = ring), RoundedCornerShape(16.dp)) else Modifier)
            .pressClickable(pressedScale = 0.88f, haptic = false, role = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        if (summary != null) {
            val allDone = summary.resolved == summary.total
            val color = when {
                isSelected -> scheme.onSurface
                allDone -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> scheme.primary
            }
            Row(
                Modifier.padding(top = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(minOf(summary.total, 3)) {
                    Box(Modifier.size(5.dp).background(color, CircleShape))
                }
                if (summary.total > 3) {
                    Text("+${summary.total - 3}", fontSize = 9.sp, color = color)
                }
            }
        }
    }
}
