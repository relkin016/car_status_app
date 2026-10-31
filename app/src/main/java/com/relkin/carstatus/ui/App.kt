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
import com.relkin.carstatus.util.Fmt
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
    data object Odo : Route
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
}

val LocalUi = staticCompositionLocalOf<Ui> { error("no ui") }

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Статус", Icons.Filled.Favorite),
    Tab("Журнал", Icons.AutoMirrored.Filled.MenuBook),
    Tab("Расходы", Icons.Filled.Payments),
    Tab("OBD2", Icons.Filled.Bluetooth),
    Tab("Ещё", Icons.Filled.Menu),
)

@Composable
fun CarRoot(app: CarApp) {
    CarTheme(app.prefs) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val ui = remember { Ui(app, scope, snackbar) }
        val snap by app.repo.snap.collectAsState()
        val currency by app.prefs.currency.state.collectAsState()
        Fmt.currency = currency

        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val pendingOpen by app.pendingOpen.collectAsState()
        LaunchedEffect(pendingOpen, snap.setupDone) {
            if (pendingOpen == MainActivity.OPEN_ODO && snap.setupDone) {
                ui.edit = Edit.Odo
                app.pendingOpen.value = null
            }
        }

        CompositionLocalProvider(LocalUi provides ui) {
            Surface(Modifier.fillMaxSize()) {
                if (!snap.setupDone) {
                    SetupScreen()
                    return@Surface
                }
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
                        Tabs(ui)
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
private fun Tabs(ui: Ui) {
    val swipe by ui.prefs.swipeNav.state.collectAsState()
    val pager = rememberPagerState(initialPage = ui.tab) { tabs.size }
    // вкладка ← нижняя панель
    LaunchedEffect(ui.tab) { if (pager.currentPage != ui.tab) pager.animateScrollToPage(ui.tab) }
    // нижняя панель ← свайп
    LaunchedEffect(pager.settledPage) { ui.tab = pager.settledPage }
    if (ui.tab != 0 && ui.stack.isEmpty()) BackHandler { ui.tab = 0 }
    HorizontalPager(pager, userScrollEnabled = swipe == 1, beyondViewportPageCount = 0, key = { it }) { page ->
        when (page) {
            0 -> StatusScreen()
            1 -> JournalScreen()
            2 -> CostScreen()
            3 -> ObdScreen()
            else -> MoreScreen()
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
        Route.Odo -> OdoScreen()
        is Route.Report -> ReportScreen(r.kind)
    }
}
