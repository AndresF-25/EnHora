package com.enhora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.absoluteValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhora.ui.theme.AppPalette
import com.enhora.ui.theme.AppPalettes
import com.enhora.ui.theme.AutoPaletteIndex
import com.enhora.ui.theme.rememberPalette
import com.enhora.ui.theme.LightSystemBars
import com.enhora.ui.theme.LocalDarkTheme
import com.enhora.ui.theme.ThemeMode
import com.enhora.ui.theme.gradientBackground
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab { HOME, CALENDAR }

/** Contenedor principal: menú lateral, pestañas Inicio / Calendario y barra inferior. */
@Composable
fun MainShell(
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
    tabIndex: Int,
    onTabIndex: (Int) -> Unit,
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    paletteIndex: Int,
    onPalette: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = tabIndex) { Tab.entries.size }
    val tab = Tab.entries[pagerState.currentPage]
    // Solo los cambios reales: la lectura inicial no debe pisar una pestaña pedida desde un aviso o el widget.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { onTabIndex(it) }
    }
    // Un aviso puede pedir abrir otra pestaña mientras la app ya está abierta.
    LaunchedEffect(tabIndex) { if (pagerState.currentPage != tabIndex) pagerState.animateScrollToPage(tabIndex) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current
    val palette = rememberPalette(paletteIndex)
    val gradient = if (dark) palette.dark else palette.light
    LightSystemBars()

    fun goTo(target: Tab) {
        scope.launch { pagerState.animateScrollToPage(target.ordinal) }
    }
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportCsv(context, uri) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // En Calendario el gesto de volver a Inicio usa el borde izquierdo; el menú se abre con el botón.
        gesturesEnabled = tab == Tab.HOME || drawerState.isOpen,
        drawerContent = {
            DrawerContent(
                userName = userName,
                dark = dark,
                themeMode = themeMode,
                onThemeMode = onThemeMode,
                paletteIndex = paletteIndex,
                onPalette = onPalette,
                onExport = {
                    scope.launch { drawerState.close() }
                    exportLauncher.launch("enhora_recordatorios_${LocalDate.now()}.csv")
                },
            )
        },
    ) {
        Column(
            Modifier.fillMaxSize().gradientBackground(gradient)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                // Al deslizar, la página que sale se desvanece y se encoge un poco; la que entra crece.
                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                Box(Modifier.graphicsLayer {
                    val f = offset.coerceIn(0f, 1f)
                    alpha = 1f - 0.5f * f
                    scaleX = 1f - 0.06f * f
                    scaleY = 1f - 0.06f * f
                }) {
                when (Tab.entries[page]) {
                    Tab.HOME -> HomeScreen(
                        vm = vm,
                        userName = userName,
                        onMenu = openMenu,
                        onAdd = { onNewTask(LocalDate.now(), null, "") },
                        onCalendar = { goTo(Tab.CALENDAR) },
                    )
                    Tab.CALENDAR -> Box(Modifier.statusBarsPadding()) {
                        CalendarScreen(
                            vm = vm,
                            userName = userName,
                            needsExactAlarm = needsExactAlarm,
                            onGrantExactAlarm = onGrantExactAlarm,
                            needsNotifications = needsNotifications,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            needsOverlay = needsOverlay,
                            onGrantOverlay = onGrantOverlay,
                            onNewTask = onNewTask,
                            onEdit = onEdit,
                            onMenu = openMenu,
                        )
                    }
                }
                }
            }
            BottomTabs(selected = tab, onSelect = ::goTo)
        }
    }
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f)).navigationBarsPadding()
            .heightIn(min = 68.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(Icons.Rounded.Home, Icons.Outlined.Home, "Inicio", selected == Tab.HOME) { onSelect(Tab.HOME) }
        // Indicador de página: avisa de que se puede deslizar entre pantallas.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                val active = t == selected
                val w by animateDpAsState(if (active) 20.dp else 6.dp, label = "dotWidth")
                val a by animateFloatAsState(if (active) 0.95f else 0.35f, label = "dotAlpha")
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(w)
                        .background(Color.White.copy(alpha = a), CircleShape),
                )
            }
        }
        TabItem(Icons.Default.CalendarMonth, Icons.Outlined.CalendarMonth, "Calendario", selected == Tab.CALENDAR) { onSelect(Tab.CALENDAR) }
    }
}

@Composable
private fun TabItem(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.55f), label = "tabColor")
    val pill by animateFloatAsState(if (selected) 0.22f else 0f, label = "tabPill")
    val iconScale by animateFloatAsState(if (selected) 1.1f else 1f, label = "tabIconScale")
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).pressClickable(pressedScale = 0.9f, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = pill)).padding(horizontal = 16.dp, vertical = 3.dp),
        ) {
            Icon(
                if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DrawerContent(
    userName: String,
    dark: Boolean,
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    paletteIndex: Int,
    onPalette: (Int) -> Unit,
    onExport: () -> Unit,
) {
    val palette = rememberPalette(paletteIndex)
    val g = if (dark) palette.dark else palette.light
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(g.top, g.mid, g.bottom)))
                    .statusBarsPadding()
                    .height(130.dp),
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text("EnHora", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(userName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Apariencia", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("Modo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Noche")
                        .forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppPalettes.forEachIndexed { i, p ->
                        val pg = if (dark) p.dark else p.light
                        Swatch(p.name, Brush.verticalGradient(listOf(pg.top, pg.mid, pg.bottom)), selected = i == paletteIndex) { onPalette(i) }
                    }
                    // Automático: el degradado recorre los colores del día.
                    Swatch(
                        "Auto",
                        Brush.sweepGradient(AppPalettes.flatMap { listOf((if (dark) it.dark else it.light).top) } +
                            (if (dark) AppPalettes.first().dark else AppPalettes.first().light).top),
                        selected = paletteIndex == AutoPaletteIndex,
                    ) { onPalette(AutoPaletteIndex) }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Datos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .pressClickable(pressedScale = 0.97f, onClick = onExport)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Exportar recordatorios", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Archivo CSV con el historial, para analizarlo fuera de la app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, brush: Brush, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(52.dp)
                .background(brush, CircleShape)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = Color.White)
        }
        Text(name, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
