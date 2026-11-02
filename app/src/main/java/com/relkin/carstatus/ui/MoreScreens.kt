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
import com.relkin.carstatus.util.Lang
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.TireRepair
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
import com.relkin.carstatus.util.tr
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
            ui.toast(r.fold({ tr("Копия сохранена (фото: $it)", "Копію збережено (фото: $it)") }, { tr("Ошибка: ${it.message}", "Помилка: ${it.message}") }))
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

    TabScaffold(tr("Ещё", "Ще")) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { SectionTitle(tr("Гараж", "Гараж")) }
            item { Entry(Icons.Filled.Warehouse, tr("Гаражи и транспорт", "Гаражі й транспорт"), tr("Гаражей: ${gs.garages.size} · транспорта: ${gs.vehicles.size} · уведомления по всем", "Гаражів: ${gs.garages.size} · транспорту: ${gs.vehicles.size} · сповіщення з усіх")) { ui.push(Route.Garage) } }
            item { SectionTitle(s.car.label) }
            item { Entry(Icons.Filled.DirectionsCar, tr("Профиль", "Профіль"), tr("Вид транспорта, марка, модель, VIN, номер", "Вид транспорту, марка, модель, VIN, номер")) { ui.push(Route.Car) } }
            item { Entry(Icons.Filled.Tune, tr("Расходники и узлы", "Витратні матеріали й вузли"), tr("Список, интервалы по пробегу и времени", "Список, інтервали за пробігом і часом")) { ui.push(Route.Items) } }
            item { Entry(Icons.Filled.ReportProblem, tr("Список поломок", "Список поломок"), tr("Что нужно починить — чек-лист", "Що треба полагодити — чек-лист")) { ui.push(Route.Breakdowns) } }
            if (Quests.of(s.kind).isNotEmpty()) item { Entry(Icons.Filled.EmojiEvents, tr("Сезонные квесты", "Сезонні квести"), tr("Подготовка к зиме и сезону", "Підготовка до зими й сезону")) { ui.push(Route.Quests) } }
            item { Entry(Icons.Filled.PhotoLibrary, tr("Фото-журнал", "Фотожурнал")) { ui.push(Route.Photos) } }
            item { Entry(Icons.Filled.Speed, tr("Показания одометра", "Показники одометра")) { ui.push(Route.Odo) } }
            if (com.relkin.carstatus.data.Tires.supported(s.kind)) item {
                val on = com.relkin.carstatus.data.Tires.mounted(s)
                Entry(Icons.Filled.TireRepair, tr("Шины", "Шини"), on?.let { tr("Стоят: ", "Стоять: ") + it.label } ?: tr("Комплекты, пробег, протектор, переобувка", "Комплекти, пробіг, протектор, перевзування")) { ui.push(Route.Tires) }
            }
            item { SectionTitle(tr("Отчёты", "Звіти")) }
            item { Entry(Icons.Filled.History, tr("История для покупателя", "Історія для покупця"), tr("Полная история обслуживания одним документом (PDF)", "Повна історія обслуговування одним документом (PDF)")) { ui.push(Route.Report(ReportKind.BUYER)) } }
            item { Entry(Icons.AutoMirrored.Filled.ReceiptLong, tr("Выписка расходов", "Виписка витрат"), tr("Как банковская: операции, итоги, цена км (PDF)", "Як банківська: операції, підсумки, ціна км (PDF)")) { ui.push(Route.Report(ReportKind.STATEMENT)) } }
            item { SectionTitle(tr("Данные", "Дані")) }
            item { Entry(Icons.Filled.Backup, tr("Экспорт базы (резервная копия)", "Експорт бази (резервна копія)"), tr("Файл .zip: все гаражи, транспорт и фото", "Файл .zip: усі гаражі, транспорт і фото")) { exportLauncher.launch("carstatus-${LocalDate.now()}.zip") } }
            item {
                val days by ui.prefs.backupDays.state.collectAsState()
                val err by ui.prefs.backupError.state.collectAsState()
                Entry(
                    Icons.Filled.CloudSync, tr("Автокопия", "Автокопія"),
                    when {
                        err.isNotEmpty() -> tr("Ошибка: ", "Помилка: ") + err
                        days == 0 -> tr("Выключена — копия в папку по расписанию", "Вимкнено — копія в теку за розкладом")
                        else -> tr("Раз в ${Fmt.days(days.toLong())}", "Раз на ${Fmt.days(days.toLong())}")
                    },
                ) { ui.push(Route.AutoBackup) }
            }
            item { Entry(Icons.Filled.Restore, tr("Восстановить из копии", "Відновити з копії"), tr("Текущие данные будут заменены", "Поточні дані буде замінено")) { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } }
            item { SectionTitle(tr("Приложение", "Застосунок")) }
            item { Entry(Icons.Filled.Share, tr("Поделиться приложением (APK)", "Поділитися застосунком (APK)"), tr("Отправить установочный файл другу", "Надіслати інсталяційний файл другові")) { shareApk(ctx, ui) } }
            item { Entry(Icons.Filled.Settings, tr("Настройки", "Налаштування"), tr("Уведомления, язык, тема", "Сповіщення, мова, тема")) { ui.push(Route.Settings) } }
            item {
                ListItem(
                    headlineContent = { Text("Авто·Статус ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text(tr("Офлайн, без аккаунтов. Все данные — только на этом телефоне.", "Офлайн, без акаунтів. Усі дані — лише на цьому телефоні.")) },
                    leadingContent = { Icon(Icons.Filled.Info, null) },
                )
            }
        }
    }
    restoreAsk?.let { uri ->
        ConfirmDialog(
            tr("Восстановить из копии?", "Відновити з копії?"), tr("Все гаражи, записи и фото будут заменены данными из файла.", "Усі гаражі, записи й фото буде замінено даними з файлу."),
            confirm = tr("Восстановить", "Відновити"), onDismiss = { restoreAsk = null },
        ) {
            // ui.scope: после восстановления экраны пересоздаются, а сообщение показать нужно
            ui.scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Backup.import(ctx, ui.garage, uri) } }
                ui.toast(r.fold({ tr("Данные восстановлены", "Дані відновлено") }, { tr("Ошибка: ${it.message}", "Помилка: ${it.message}") }))
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
        ctx.startActivity(Intent.createChooser(send, tr("Поделиться приложением", "Поділитися застосунком")))
    }.onFailure { ui.toast(tr("Не удалось: ${it.message}", "Не вдалося: ${it.message}")) }
}

private fun sharePdf(ctx: Context, f: File) {
    val uri = PhotoStore.uri(ctx, f)
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, f.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(f.name, uri)
    ctx.startActivity(Intent.createChooser(send, tr("Отправить документ", "Надіслати документ")))
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
        }.onFailure { ui.toast(tr("Не удалось сформировать PDF: ${it.message}", "Не вдалося сформувати PDF: ${it.message}")) }.getOrNull()
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pdf?.file
        if (uri == null || f == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } } } }
            ui.toast(r.fold({ tr("Сохранено", "Збережено") }, { tr("Ошибка: ${it.message}", "Помилка: ${it.message}") }))
        }
    }
    SubScreen(if (kind == ReportKind.BUYER) tr("История для покупателя", "Історія для покупця") else tr("Выписка расходов", "Виписка витрат"), ui::pop) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp)) {
            if (kind == ReportKind.BUYER) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ui.prefs.reportCosts.set(1 - withCosts) }) {
                Checkbox(withCosts == 1, { ui.prefs.reportCosts.set(if (it) 1 else 0) }); Text(tr("Показывать стоимость работ", "Показувати вартість робіт"))
            } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> FilterChip(period == p, { PeriodState.period = p }, label = { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Button(onClick = { pdf?.let { sharePdf(ctx, it.file) } }, enabled = pdf != null, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Spacer(Modifier.padding(4.dp)); Text(tr("Отправить", "Надіслати")) }
                OutlinedButton(onClick = { save.launch(name) }, enabled = pdf != null, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.padding(4.dp)); Text(tr("Сохранить PDF", "Зберегти PDF")) }
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
    SubScreen(tr("Список поломок", "Список поломок"), ui::pop, fab = { FloatingActionButton(onClick = { ui.edit = Edit.BreakdownE(null) }) { Icon(Icons.Filled.Add, tr("Добавить", "Додати")) } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (s.breakdowns.isEmpty()) item { Empty(tr("Стук, скрип, не работает стеклоподъёмник? Запишите — и отмечайте по мере ремонта", "Стукіт, скрип, не працює склопідіймач? Запишіть — і позначайте в міру ремонту")) }
            items(open, key = { it.id }) { BreakdownRow(it) }
            if (done.isNotEmpty()) item { SectionTitle(tr("Исправлено (${done.size})", "Виправлено (${done.size})")) }
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
                    if (b.done) null else listOf(tr("терпит", "терпить"), tr("скоро", "скоро"), tr("срочно", "терміново"))[b.priority.coerceIn(0, 2)],
                    Fmt.day(b.createdDate), b.createdKm?.let { Fmt.km(it) },
                    b.doneDate?.let { tr("исправлено ${Fmt.day(it)}", "виправлено ${Fmt.day(it)}") }, b.note.takeIf { it.isNotBlank() },
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
    SubScreen(tr("Сезонные квесты", "Сезонні квести"), ui::pop) { pad ->
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
                                if (q.isActive(today)) tr(" · идёт сейчас", " · триває зараз") else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { done.toFloat() / q.steps.size }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Text("$done " + tr("из", "з") + " ${q.steps.size}" + if (done == q.steps.size) tr(" — квест пройден! 🏆", " — квест пройдено! 🏆") else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
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
                Text(tr("💡 Когда выгодно покупать", "💡 Коли вигідно купувати"), style = MaterialTheme.typography.titleMedium)
            }
            items(com.relkin.carstatus.data.Tips.of(s.kind), key = { "tip" + it.key }) { t ->
                val months = t.months.sorted().joinToString { java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, com.relkin.carstatus.util.Lang.locale) }
                ListItem(
                    headlineContent = { Text(t.title + if (t.isActive(today)) tr(" · сейчас", " · зараз") else "") },
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
    SubScreen(tr("Профиль", "Профіль"), ui::pop, actions = {
        androidx.compose.material3.TextButton(enabled = kmMonthV != null, onClick = {
            ui.repo.saveCar(car.copy(purchaseKm = parseKm(purchaseKm), kmPerMonth = kmMonthV ?: 0))
            ui.toast(tr("Сохранено", "Збережено")); ui.pop()
        }) { Text(tr("Сохранить", "Зберегти")) }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable
            fun F(label: String, v: String, set: (String) -> Unit) =
                OutlinedTextField(v, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            KindPicker(car.kind) { car = car.copy(kind = it) }
            if (car.kind != c.kind) Text(
                tr("Сменятся единицы и квесты. Список расходников останется прежним — недостающие пункты нового вида можно добавить в «Расходники и узлы».", "Зміняться одиниці й квести. Список витратних матеріалів залишиться попереднім — відсутні пункти нового виду можна додати в «Витратні матеріали й вузли»."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            F(tr("Название (как показывать)", "Назва (як показувати)"), car.name) { car = car.copy(name = it) }
            F(tr("Марка", "Марка"), car.make) { car = car.copy(make = it) }
            F(tr("Модель", "Модель"), car.model) { car = car.copy(model = it) }
            F(tr("Год выпуска", "Рік випуску"), car.year) { car = car.copy(year = it) }
            F(if (car.kind.isCar) "VIN" else tr("VIN / серийный номер рамы", "VIN / серійний номер рами"), car.vin) { car = car.copy(vin = it.uppercase()) }
            if (car.kind.isCar || car.kind == com.relkin.carstatus.data.VehicleKind.MOTO) F(tr("Госномер", "Держномер"), car.plate) { car = car.copy(plate = it.uppercase()) }
            if (car.kind != com.relkin.carstatus.data.VehicleKind.BICYCLE) F(if (car.kind.energy == com.relkin.carstatus.data.Energy.ELECTRIC) tr("Мотор / батарея (кВт·ч)", "Мотор / батарея (кВт·год)") else tr("Двигатель", "Двигун"), car.engine) { car = car.copy(engine = it) }
            if (car.kind.energy == com.relkin.carstatus.data.Energy.FUEL) F(tr("Топливо", "Пальне"), car.fuel) { car = car.copy(fuel = it) }
            NumField(
                kmMonth, { kmMonth = it }, tr("Пробег в месяц, пока мало истории", "Пробіг на місяць, поки мало історії"), suffix = "км", decimal = false,
                hint = tr("Пусто — ${car.kind.kmPerMonth} км (типично для вида). Когда наберётся 2+ недели показаний, считается автоматически", "Порожньо — ${car.kind.kmPerMonth} км (типово для виду). Коли набереться 2+ тижні показників, рахується автоматично"),
            )
            Text(tr("Покупка", "Купівля"), style = MaterialTheme.typography.labelLarge)
            if (car.purchaseDate != null) DateField(LocalDate.ofEpochDay(car.purchaseDate!!), { car = car.copy(purchaseDate = it.toEpochDay()) }, tr("Дата покупки", "Дата купівлі"))
            else OutlinedButton(onClick = { car = car.copy(purchaseDate = LocalDate.now().toEpochDay()) }) { Text(tr("Указать дату покупки", "Вказати дату купівлі")) }
            NumField(purchaseKm, { purchaseKm = it }, tr("Пробег при покупке", "Пробіг під час купівлі"), suffix = "км", decimal = false)
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
    val tireMode by p.tireMode.state.collectAsState()
    val costMode by p.costMode.state.collectAsState()
    val costPeriod by p.costPeriod.state.collectAsState()
    val time by p.checkTime.state.collectAsState()
    val currency by p.currency.state.collectAsState()
    val lang by p.lang.state.collectAsState()
    var pickTime by remember { mutableStateOf(false) }
    var testMode by remember { mutableStateOf(1) }

    SubScreen(tr("Настройки", "Налаштування"), ui::pop) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Интерфейс", "Інтерфейс"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Переключать вкладки свайпом", "Перемикати вкладки свайпом"))
                    Text(tr("Листайте влево-вправо между Статусом, Журналом, Расходами…", "Гортайте вліво-вправо між Статусом, Журналом, Витратами…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(swipe == 1, { p.swipeNav.set(if (it) 1 else 0) })
            }
            Text(tr("Язык", "Мова"))
            Segmented(listOf(Lang.AUTO to tr("Как в системе", "Як у системі"), Lang.RU to "Русский", Lang.UK to "Українська"), lang, { v ->
                if (v == lang) return@Segmented
                p.lang.set(v)
                Lang.apply(v)
                Reminders.createChannels(ctx)
                ctx.findActivity()?.recreate()
            })
            Text(tr("Тема", "Тема"))
            Segmented(listOf(0 to tr("Системная", "Системна"), 1 to tr("Светлая", "Світла"), 2 to tr("Тёмная", "Темна"), 3 to "AMOLED"), theme, p.theme::set)
            if (Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Цвета обоев (Material You)", "Кольори шпалер (Material You)"), Modifier.weight(1f)); Switch(dynamic == 1, { p.dynamicColor.set(if (it) 1 else 0) })
            }
            OutlinedTextField(currency, { p.currency.set(it); Fmt.currency = it }, label = { Text(tr("Валюта", "Валюта")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            HorizontalDivider()
            Text(tr("Уведомления", "Сповіщення"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (!Reminders.canNotify(ctx)) OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text(tr("Уведомления запрещены — открыть настройки", "Сповіщення заборонено — відкрити налаштування")) }
            if (!Reminders.canFullScreen(ctx) && listOf(hpMode, odoMode, questMode, tipMode, tireMode, costMode).contains(2)) OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + ctx.packageName)))
            }) { Text(tr("Разрешить полноэкранный будильник", "Дозволити повноекранний будильник")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Время ежедневной проверки", "Час щоденної перевірки"), Modifier.weight(1f))
                OutlinedButton(onClick = { pickTime = true }) { Text(Fmt.hm(time)) }
            }
            Text(tr("Износ узла (HP ниже порога)", "Знос вузла (HP нижче порогу)"))
            ModeSelector(hpMode, p.hpMode::set)
            PrefNum(p.hpThreshold, tr("Порог HP", "Поріг HP"), "%", 1..99, tr("Уведомить, когда ресурс узла опустится до этого значения", "Сповістити, коли ресурс вузла опуститься до цього значення"))
            Text(tr("Напоминание ввести пробег", "Нагадування ввести пробіг"))
            ModeSelector(odoMode, p.odoMode::set)
            PrefNum(p.odoDays, tr("Если пробег не обновляли", "Якщо пробіг не оновлювали"), tr("дн.", "дн."), 1..90)
            Text(tr("Напоминание внести расходы", "Нагадування внести витрати"))
            ModeSelector(costMode, p.costMode::set)
            if (costMode != 0) Segmented(listOf(0 to tr("Ежедневно", "Щодня"), 1 to tr("Еженедельно", "Щотижня"), 2 to tr("Ежемесячно", "Щомісяця")), costPeriod, p.costPeriod::set)
            Text(tr("Сезонные квесты", "Сезонні квести"))
            ModeSelector(questMode, p.questMode::set)
            Text(tr("Шины: протектор, возраст, не по сезону", "Шини: протектор, вік, не за сезоном"))
            ModeSelector(tireMode, p.tireMode::set)
            Text(tr("Внесезонные покупки (когда выгодно)", "Позасезонні покупки (коли вигідно)"))
            ModeSelector(tipMode, p.tipMode::set)
            Text(tr("Проверить, как выглядит:", "Перевірити, як виглядає:"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf(1 to tr("Уведомление", "Сповіщення"), 2 to tr("Будильник", "Будильник")), testMode, { testMode = it }, Modifier.weight(1f))
                Button(onClick = { Reminders.test(ctx, testMode); ui.toast(tr("Сработает через 5 секунд", "Спрацює через 5 секунд")) }) { Text(tr("Тест", "Тест")) }
            }
            OutlinedButton(onClick = { Reminders.check(ctx); ui.toast(tr("Проверка выполнена", "Перевірку виконано")) }) { Text(tr("Проверить сейчас", "Перевірити зараз")) }

            Text(
                tr("Проверка идёт по всем гаражам, где включены уведомления (Ещё → Гаражи), — не только по открытому транспорту.", "Перевірка йде по всіх гаражах, де ввімкнено сповіщення (Ще → Гаражі), — не лише по відкритому транспорту."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickTime) TimePickDialog(time, { pickTime = false }, p.checkTime::set)
}

/** Числовая настройка: локальный текст, в настройку уходят только допустимые значения. */
@Composable
fun PrefNum(pref: com.relkin.carstatus.Prefs.IntPref, label: String, suffix: String, range: IntRange, hint: String? = null) {
    var text by remember { mutableStateOf(pref.value.toString()) }
    NumField(text, { v ->
        text = v
        parseKm(v)?.toInt()?.takeIf { it in range }?.let(pref::set)
    }, label, suffix = suffix, decimal = false, hint = hint ?: tr("от ${range.first} до ${range.last}", "від ${range.first} до ${range.last}"))
}

/** Activity за контекстом Compose (может быть обёрнут). */
private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
