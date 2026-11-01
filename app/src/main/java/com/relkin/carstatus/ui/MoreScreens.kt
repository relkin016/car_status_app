package com.relkin.carstatus.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.relkin.carstatus.util.Doc
import com.relkin.carstatus.util.Pdf
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.BuildConfig
import com.relkin.carstatus.data.Backup
import com.relkin.carstatus.data.Breakdown
import com.relkin.carstatus.data.Costs
import com.relkin.carstatus.data.Health
import com.relkin.carstatus.data.Quests
import com.relkin.carstatus.data.Reports
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun MoreScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreAsk by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Backup.export(ctx, ui.garage, uri) } }
            ui.toast(r.fold({ "Копия сохранена (фото: $it)" }, { "Ошибка: ${it.message}" }))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreAsk = uri }
    val s by ui.repo.snap.collectAsState()
    val gs by ui.garage.state.collectAsState()

    @Composable
    fun Entry(icon: ImageVector, title: String, sub: String? = null, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = sub?.let { { Text(it) } },
            leadingContent = { Icon(icon, null) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }

    TabScaffold("Ещё") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { SectionTitle("Гараж") }
            item { Entry(Icons.Filled.Warehouse, "Гаражи и транспорт", "Гаражей: ${gs.garages.size} · транспорта: ${gs.vehicles.size} · уведомления по всем") { ui.push(Route.Garage) } }
            item { SectionTitle(s.car.label) }
            item { Entry(Icons.Filled.DirectionsCar, "Профиль", "Вид транспорта, марка, модель, VIN, номер") { ui.push(Route.Car) } }
            item { Entry(Icons.Filled.Tune, "Расходники и узлы", "Список, интервалы по пробегу и времени") { ui.push(Route.Items) } }
            item { Entry(Icons.Filled.ReportProblem, "Список поломок", "Что нужно починить — чек-лист") { ui.push(Route.Breakdowns) } }
            if (Quests.of(s.kind).isNotEmpty()) item { Entry(Icons.Filled.EmojiEvents, "Сезонные квесты", "Подготовка к зиме и сезону") { ui.push(Route.Quests) } }
            item { Entry(Icons.Filled.PhotoLibrary, "Фото-журнал") { ui.push(Route.Photos) } }
            item { Entry(Icons.Filled.Speed, "Показания одометра") { ui.push(Route.Odo) } }
            item { SectionTitle("Отчёты") }
            item { Entry(Icons.Filled.History, "История для покупателя", "Полная история обслуживания одним документом (PDF)") { ui.push(Route.Report(ReportKind.BUYER)) } }
            item { Entry(Icons.AutoMirrored.Filled.ReceiptLong, "Выписка расходов", "Как банковская: операции, итоги, цена км (PDF)") { ui.push(Route.Report(ReportKind.STATEMENT)) } }
            item { SectionTitle("Данные") }
            item { Entry(Icons.Filled.Backup, "Экспорт базы (резервная копия)", "Файл .zip: все гаражи, транспорт и фото") { exportLauncher.launch("carstatus-${LocalDate.now()}.zip") } }
            item { Entry(Icons.Filled.Restore, "Восстановить из копии", "Текущие данные будут заменены") { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } }
            item { SectionTitle("Приложение") }
            item { Entry(Icons.Filled.Share, "Поделиться приложением (APK)", "Отправить установочный файл другу") { shareApk(ctx, ui) } }
            item { Entry(Icons.Filled.Settings, "Настройки", "Уведомления, свайпы, тема") { ui.push(Route.Settings) } }
            item {
                ListItem(
                    headlineContent = { Text("Авто·Статус ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Офлайн, без аккаунтов. Все данные — только на этом телефоне.") },
                    leadingContent = { Icon(Icons.Filled.Info, null) },
                )
            }
        }
    }
    restoreAsk?.let { uri ->
        ConfirmDialog(
            "Восстановить из копии?", "Все гаражи, записи и фото будут заменены данными из файла.",
            confirm = "Восстановить", onDismiss = { restoreAsk = null },
        ) {
            // ui.scope: после восстановления экраны пересоздаются, а сообщение показать нужно
            ui.scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Backup.import(ctx, ui.garage, uri) } }
                ui.toast(r.fold({ "Данные восстановлены" }, { "Ошибка: ${it.message}" }))
            }
        }
    }
}

private fun shareApk(ctx: Context, ui: Ui) {
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
        val out = File(dir, "CarStatus-${BuildConfig.VERSION_NAME}.apk")
        File(ctx.applicationInfo.sourceDir).copyTo(out, overwrite = true)
        val uri = PhotoStore.uri(ctx, out)
        val send = Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, out.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(out.name, uri)
        ctx.startActivity(Intent.createChooser(send, "Поделиться приложением"))
    }.onFailure { ui.toast("Не удалось: ${it.message}") }
}

private fun sharePdf(ctx: Context, f: File) {
    val uri = PhotoStore.uri(ctx, f)
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, f.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(f.name, uri)
    ctx.startActivity(Intent.createChooser(send, "Отправить документ"))
}

/** Готовый PDF и его страницы картинками для предпросмотра. */
private class RenderedPdf(val file: File, val pages: List<Bitmap>)

private val pdfLock = Any()

private fun renderPdf(ctx: Context, name: String, doc: Doc): RenderedPdf = synchronized(pdfLock) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, name)
    val tmp = File(dir, "$name.tmp")
    tmp.outputStream().use { Pdf.write(doc, it) }
    tmp.renameTo(f)
    val pages = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            (0 until r.pageCount).map { i ->
                r.openPage(i).use { p ->
                    val w = 1080
                    Bitmap.createBitmap(w, w * p.height / p.width, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
    RenderedPdf(f, pages)
}

@Composable
fun ReportScreen(kind: ReportKind) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by ui.repo.snap.collectAsState()
    val withCosts by ui.prefs.reportCosts.state.collectAsState()
    val period = PeriodState.period
    val name = (if (kind == ReportKind.BUYER) "history" else "statement") + "-${LocalDate.now()}.pdf"
    val pdf by produceState<RenderedPdf?>(null, s, withCosts, period, kind) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val doc = when (kind) {
                    ReportKind.BUYER -> Reports.buyerHistory(s, Health.of(s, s.fallbackKmPerDay), BuildConfig.VERSION_NAME, withCosts == 1)
                    ReportKind.STATEMENT -> Reports.statement(s, Costs.report(s, period.from()), BuildConfig.VERSION_NAME)
                }
                renderPdf(ctx, name, doc)
            }
        }.onFailure { ui.toast("Не удалось сформировать PDF: ${it.message}") }.getOrNull()
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pdf?.file
        if (uri == null || f == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } } } }
            ui.toast(r.fold({ "Сохранено" }, { "Ошибка: ${it.message}" }))
        }
    }
    SubScreen(if (kind == ReportKind.BUYER) "История для покупателя" else "Выписка расходов", ui::pop) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp)) {
            if (kind == ReportKind.BUYER) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ui.prefs.reportCosts.set(1 - withCosts) }) {
                Checkbox(withCosts == 1, { ui.prefs.reportCosts.set(if (it) 1 else 0) }); Text("Показывать стоимость работ")
            } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> FilterChip(period == p, { PeriodState.period = p }, label = { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Button(onClick = { pdf?.let { sharePdf(ctx, it.file) } }, enabled = pdf != null, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Spacer(Modifier.padding(4.dp)); Text("Отправить") }
                OutlinedButton(onClick = { save.launch(name) }, enabled = pdf != null, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.padding(4.dp)); Text("Сохранить PDF") }
            }
            val pages = pdf?.pages
            if (pages == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(pages) { bmp ->
                    Card(elevation = CardDefaults.cardElevation(2.dp)) {
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

@Composable
fun BreakdownsScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val open = s.breakdowns.filter { !it.done }
    val done = s.breakdowns.filter { it.done }
    SubScreen("Список поломок", ui::pop, fab = { FloatingActionButton(onClick = { ui.edit = Edit.BreakdownE(null) }) { Icon(Icons.Filled.Add, "Добавить") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (s.breakdowns.isEmpty()) item { Empty("Стук, скрип, не работает стеклоподъёмник? Запишите — и отмечайте по мере ремонта") }
            items(open, key = { it.id }) { BreakdownRow(it) }
            if (done.isNotEmpty()) item { SectionTitle("Исправлено (${done.size})") }
            items(done, key = { it.id }) { BreakdownRow(it) }
        }
    }
}

@Composable
private fun BreakdownRow(b: Breakdown) {
    val ui = LocalUi.current
    val dc = LocalData.current
    ListItem(
        leadingContent = { Checkbox(b.done, { ui.repo.setBreakdownDone(b, it) }) },
        headlineContent = { Text(b.title, textDecoration = if (b.done) TextDecoration.LineThrough else null) },
        supportingContent = {
            Text(
                listOfNotNull(
                    if (b.done) null else listOf("терпит", "скоро", "срочно")[b.priority.coerceIn(0, 2)],
                    Fmt.day(b.createdDate), b.createdKm?.let { Fmt.km(it) },
                    b.doneDate?.let { "исправлено ${Fmt.day(it)}" }, b.note.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
            )
        },
        trailingContent = { if (!b.done) Dot(listOf(dc.good, dc.warning, dc.critical)[b.priority.coerceIn(0, 2)]) },
        modifier = Modifier.clickable { ui.edit = Edit.BreakdownE(b) },
    )
    HorizontalDivider()
}

@Composable
fun QuestsScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val today = LocalDate.now()
    val quests = Quests.of(s.kind)
    SubScreen("Сезонные квесты", ui::pop) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // активные — сверху
            items(quests.sortedByDescending { it.isActive(today) }, key = { it.key }) { q ->
                val season = q.season(today)
                val done = q.steps.count { q.stepKey(season, it) in s.questDone }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${q.emoji} ${q.title}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Сезон: ${q.start.dayOfMonth}.${"%02d".format(q.start.monthValue)} — ${q.end.dayOfMonth}.${"%02d".format(q.end.monthValue)}" +
                                if (q.isActive(today)) " · идёт сейчас" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { done.toFloat() / q.steps.size }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Text("$done из ${q.steps.size}" + if (done == q.steps.size) " — квест пройден! 🏆" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        q.steps.forEach { st ->
                            val key = q.stepKey(season, st)
                            val checked = key in s.questDone
                            Row(Modifier.fillMaxWidth().clickable { ui.repo.setQuestStep(key, !checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked, { ui.repo.setQuestStep(key, it) })
                                Column(Modifier.padding(top = 12.dp)) {
                                    Text(st.title, textDecoration = if (checked) TextDecoration.LineThrough else null)
                                    if (st.hint.isNotBlank()) Text(st.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("💡 Когда выгодно покупать", style = MaterialTheme.typography.titleMedium)
            }
            items(com.relkin.carstatus.data.Tips.of(s.kind), key = { "tip" + it.key }) { t ->
                val months = t.months.sorted().joinToString { java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.forLanguageTag("ru")) }
                ListItem(
                    headlineContent = { Text(t.title + if (t.isActive(today)) " · сейчас" else "") },
                    supportingContent = { Text("$months. ${t.text}") },
                )
            }
        }
    }
}

@Composable
fun CarScreen() {
    val ui = LocalUi.current
    val c = ui.repo.s.car
    var car by remember { mutableStateOf(c) }
    var purchaseKm by remember { mutableStateOf(c.purchaseKm?.toString() ?: "") }
    var kmMonth by remember { mutableStateOf(c.kmPerMonth.takeIf { it > 0 }?.toString() ?: "") }
    val kmMonthV = if (kmMonth.isBlank()) 0 else parseKm(kmMonth)?.toInt()
    SubScreen("Профиль", ui::pop, actions = {
        androidx.compose.material3.TextButton(enabled = kmMonthV != null, onClick = {
            ui.repo.saveCar(car.copy(purchaseKm = parseKm(purchaseKm), kmPerMonth = kmMonthV ?: 0))
            ui.toast("Сохранено"); ui.pop()
        }) { Text("Сохранить") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable
            fun F(label: String, v: String, set: (String) -> Unit) =
                OutlinedTextField(v, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            KindPicker(car.kind) { car = car.copy(kind = it) }
            if (car.kind != c.kind) Text(
                "Сменятся единицы и квесты. Список расходников останется прежним — недостающие пункты нового вида можно добавить в «Расходники и узлы».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            F("Название (как показывать)", car.name) { car = car.copy(name = it) }
            F("Марка", car.make) { car = car.copy(make = it) }
            F("Модель", car.model) { car = car.copy(model = it) }
            F("Год выпуска", car.year) { car = car.copy(year = it) }
            F(if (car.kind.isCar) "VIN" else "VIN / серийный номер рамы", car.vin) { car = car.copy(vin = it.uppercase()) }
            if (car.kind.isCar || car.kind == com.relkin.carstatus.data.VehicleKind.MOTO) F("Госномер", car.plate) { car = car.copy(plate = it.uppercase()) }
            if (car.kind != com.relkin.carstatus.data.VehicleKind.BICYCLE) F(if (car.kind.energy == com.relkin.carstatus.data.Energy.ELECTRIC) "Мотор / батарея (кВт·ч)" else "Двигатель", car.engine) { car = car.copy(engine = it) }
            if (car.kind.energy == com.relkin.carstatus.data.Energy.FUEL) F("Топливо", car.fuel) { car = car.copy(fuel = it) }
            NumField(
                kmMonth, { kmMonth = it }, "Пробег в месяц, пока мало истории", suffix = "км", decimal = false,
                hint = "Пусто — ${car.kind.kmPerMonth} км (типично для вида). Когда наберётся 2+ недели показаний, считается автоматически",
            )
            Text("Покупка", style = MaterialTheme.typography.labelLarge)
            if (car.purchaseDate != null) DateField(LocalDate.ofEpochDay(car.purchaseDate!!), { car = car.copy(purchaseDate = it.toEpochDay()) }, "Дата покупки")
            else OutlinedButton(onClick = { car = car.copy(purchaseDate = LocalDate.now().toEpochDay()) }) { Text("Указать дату покупки") }
            NumField(purchaseKm, { purchaseKm = it }, "Пробег при покупке", suffix = "км", decimal = false)
        }
    }
}

@Composable
fun SettingsScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val p = ui.prefs
    val theme by p.theme.state.collectAsState()
    val dynamic by p.dynamicColor.state.collectAsState()
    val swipe by p.swipeNav.state.collectAsState()
    val hpMode by p.hpMode.state.collectAsState()
    val odoMode by p.odoMode.state.collectAsState()
    val questMode by p.questMode.state.collectAsState()
    val tipMode by p.tipMode.state.collectAsState()
    val time by p.checkTime.state.collectAsState()
    val currency by p.currency.state.collectAsState()
    var pickTime by remember { mutableStateOf(false) }
    var testMode by remember { mutableStateOf(1) }

    SubScreen("Настройки", ui::pop) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Интерфейс", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Переключать вкладки свайпом")
                    Text("Листайте влево-вправо между Статусом, Журналом, Расходами…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(swipe == 1, { p.swipeNav.set(if (it) 1 else 0) })
            }
            Text("Тема")
            Segmented(listOf(0 to "Системная", 1 to "Светлая", 2 to "Тёмная"), theme, p.theme::set)
            if (Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Цвета обоев (Material You)", Modifier.weight(1f)); Switch(dynamic == 1, { p.dynamicColor.set(if (it) 1 else 0) })
            }
            OutlinedTextField(currency, { p.currency.set(it); Fmt.currency = it }, label = { Text("Валюта") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            HorizontalDivider()
            Text("Уведомления", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (!Reminders.canNotify(ctx)) OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text("Уведомления запрещены — открыть настройки") }
            if (!Reminders.canFullScreen(ctx) && listOf(hpMode, odoMode, questMode, tipMode).contains(2)) OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + ctx.packageName)))
            }) { Text("Разрешить полноэкранный будильник") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Время ежедневной проверки", Modifier.weight(1f))
                OutlinedButton(onClick = { pickTime = true }) { Text(Fmt.hm(time)) }
            }
            Text("Износ узла (HP ниже порога)")
            ModeSelector(hpMode, p.hpMode::set)
            PrefNum(p.hpThreshold, "Порог HP", "%", 1..99, "Уведомить, когда ресурс узла опустится до этого значения")
            Text("Напоминание ввести пробег")
            ModeSelector(odoMode, p.odoMode::set)
            PrefNum(p.odoDays, "Если пробег не обновляли", "дн.", 1..90)
            Text("Сезонные квесты")
            ModeSelector(questMode, p.questMode::set)
            Text("Внесезонные покупки (когда выгодно)")
            ModeSelector(tipMode, p.tipMode::set)
            Text("Проверить, как выглядит:", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf(1 to "Уведомление", 2 to "Будильник"), testMode, { testMode = it }, Modifier.weight(1f))
                Button(onClick = { Reminders.test(ctx, testMode); ui.toast("Сработает через 5 секунд") }) { Text("Тест") }
            }
            OutlinedButton(onClick = { Reminders.check(ctx); ui.toast("Проверка выполнена") }) { Text("Проверить сейчас") }

            Text(
                "Проверка идёт по всем гаражам, где включены уведомления (Ещё → Гаражи), — не только по открытому транспорту.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickTime) TimePickDialog(time, { pickTime = false }, p.checkTime::set)
}

/** Числовая настройка: локальный текст, в настройку уходят только допустимые значения. */
@Composable
private fun PrefNum(pref: com.relkin.carstatus.Prefs.IntPref, label: String, suffix: String, range: IntRange, hint: String? = null) {
    var text by remember { mutableStateOf(pref.value.toString()) }
    NumField(text, { v ->
        text = v
        parseKm(v)?.toInt()?.takeIf { it in range }?.let(pref::set)
    }, label, suffix = suffix, decimal = false, hint = hint ?: "от ${range.first} до ${range.last}")
}
