package com.relkin.carstatus.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.relkin.carstatus.CarApp
import com.relkin.carstatus.MainActivity
import com.relkin.carstatus.data.Breakdown
import com.relkin.carstatus.data.Incident
import com.relkin.carstatus.data.Item
import com.relkin.carstatus.data.LogEntry
import com.relkin.carstatus.data.Photo
import com.relkin.carstatus.data.VehicleKind
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Подэкраны поверх вкладок. */
sealed interface Route {
    data object Items : Route
    data class ItemDetail(val id: Long) : Route
    data object Breakdowns : Route
    data object Quests : Route
    data object Photos : Route
    data object Car : Route
    data object Settings : Route
    data object Garage : Route
    data object Odo : Route
    data object Tires : Route
    data object AutoBackup : Route
    data class Report(val kind: ReportKind) : Route
}

enum class ReportKind { BUYER, STATEMENT }

/** Диалоги добавления/редактирования, общие для всех экранов. */
sealed interface Edit {
    data object Odo : Edit
    data class Log(val entry: LogEntry?, val type: Int, val item: Item? = null) : Edit
    data class IncidentE(val incident: Incident?) : Edit
    data class BreakdownE(val b: Breakdown?) : Edit
    data class PhotoE(val photo: Photo) : Edit
    data object AddMenu : Edit
    /** Быстрое переключение гаража и транспорта. */
    data object Switch : Edit
}

class Ui(val app: CarApp, val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    val stack = mutableStateListOf<Route>()
    var edit by mutableStateOf<Edit?>(null)
    var tab by mutableStateOf(0)

    fun push(r: Route) { stack += r }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val repo get() = app.repo
    val prefs get() = app.prefs
    val garage get() = app.garage

    /** Перейти на другой транспорт: экраны прежнего закрываются. */
    fun switchTo(vehicleId: Long) {
        if (vehicleId == garage.s.current) return
        stack.clear(); edit = null; tab = 0
        garage.select(vehicleId)
    }

    /** Добавить транспорт в гараж — откроется экран настройки. */
    fun addVehicle(garageId: Long) {
        stack.clear(); edit = null; tab = 0
        garage.addVehicle(garageId)
    }
}

val LocalUi = staticCompositionLocalOf<Ui> { error("no ui") }

private enum class Tab(private val ru: String, private val uk: String, val icon: ImageVector) {
    STATUS("Статус", "Статус", Icons.Filled.Favorite),
    JOURNAL("Журнал", "Журнал", Icons.AutoMirrored.Filled.MenuBook),
    COSTS("Расходы", "Витрати", Icons.Filled.Payments),
    OBD("OBD2", "OBD2", Icons.Filled.Bluetooth),
    MORE("Ещё", "Ще", Icons.Filled.Menu);

    val title: String get() = tr(ru, uk)
}

/** Вкладка OBD2 — только у транспорта с диагностическим разъёмом. */
private fun tabsOf(kind: VehicleKind) = Tab.entries.filter { it != Tab.OBD || kind.hasObd }

@Composable
fun CarRoot(app: CarApp) {
    CarTheme(app.prefs) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val ui = remember { Ui(app, scope, snackbar) }
        val gs by app.garage.state.collectAsState()
        val epoch by app.garage.epoch.collectAsState()
        // смена транспорта (в т.ч. из уведомления) или восстановление копии — всё дерево экранов заново
        key(gs.current, epoch) { VehicleRoot(app, ui) }
    }
}

@Composable
private fun VehicleRoot(app: CarApp, ui: Ui) {
    run {
        val snackbar = ui.snackbar
        // экраны прежнего транспорта закрываются (до открытия пробега по уведомлению — эффекты идут по порядку)
        LaunchedEffect(Unit) { ui.stack.clear(); ui.edit = null; ui.tab = 0 }
        val snap by app.repo.snap.collectAsState()
        val currency by app.prefs.currency.state.collectAsState()
        Fmt.currency = currency

        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val pendingOpen by app.pendingOpen.collectAsState()
        LaunchedEffect(pendingOpen, snap.setupDone) {
            if (snap.setupDone) when (pendingOpen) {
                MainActivity.OPEN_ODO -> { ui.edit = Edit.Odo; app.pendingOpen.value = null }
                MainActivity.OPEN_ADD -> { ui.edit = Edit.AddMenu; app.pendingOpen.value = null }
            }
        }

        CompositionLocalProvider(LocalUi provides ui) {
            Surface(Modifier.fillMaxSize()) {
                if (!snap.setupDone) {
                    SetupScreen()
                    EditDialogs()
                    return@Surface
                }
                val tabs = tabsOf(snap.kind)
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (ui.stack.isEmpty()) NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = ui.tab == i, onClick = { ui.tab = i },
                                    icon = { Icon(t.icon, null) }, label = { Text(t.title) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(bottom = if (ui.stack.isEmpty()) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline)) {
                        Tabs(ui, tabs)
                        ui.stack.lastOrNull()?.let { r ->
                            BackHandler { ui.pop() }
                            Surface(Modifier.fillMaxSize()) { RouteScreen(r) }
                        }
                    }
                }
                EditDialogs()
            }
        }
    }
}

@Composable
private fun Tabs(ui: Ui, tabs: List<Tab>) {
    val swipe by ui.prefs.swipeNav.state.collectAsState()
    if (ui.tab !in tabs.indices) ui.tab = 0
    val pager = rememberPagerState(initialPage = ui.tab) { tabs.size }
    // вкладка ← нижняя панель
    LaunchedEffect(ui.tab) { if (pager.currentPage != ui.tab) pager.animateScrollToPage(ui.tab) }
    // нижняя панель ← свайп
    LaunchedEffect(pager.settledPage) { ui.tab = pager.settledPage }
    if (ui.tab != 0 && ui.stack.isEmpty()) BackHandler { ui.tab = 0 }
    HorizontalPager(pager, userScrollEnabled = swipe == 1, beyondViewportPageCount = 0, key = { it }) { page ->
        when (tabs[page]) {
            Tab.STATUS -> StatusScreen()
            Tab.JOURNAL -> JournalScreen()
            Tab.COSTS -> CostScreen()
            Tab.OBD -> ObdScreen()
            Tab.MORE -> MoreScreen()
        }
    }
}

@Composable
private fun RouteScreen(r: Route) {
    when (r) {
        Route.Items -> ItemsScreen()
        is Route.ItemDetail -> ItemDetailScreen(r.id)
        Route.Breakdowns -> BreakdownsScreen()
        Route.Quests -> QuestsScreen()
        Route.Photos -> PhotosScreen()
        Route.Car -> CarScreen()
        Route.Settings -> SettingsScreen()
        Route.Garage -> GarageScreen()
        Route.Odo -> OdoScreen()
        Route.Tires -> TiresScreen()
        Route.AutoBackup -> AutoBackupScreen()
        is Route.Report -> ReportScreen(r.kind)
    }
}
