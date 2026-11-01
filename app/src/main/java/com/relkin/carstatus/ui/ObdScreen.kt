package com.relkin.carstatus.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.Incident
import com.relkin.carstatus.data.OdoSource
import com.relkin.carstatus.obd.Dtc
import com.relkin.carstatus.obd.DtcResult
import com.relkin.carstatus.obd.LiveData
import com.relkin.carstatus.obd.ObdState
import com.relkin.carstatus.obd.OdoRead
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ObdScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val obd = ui.app.obd
    val state by obd.state.collectAsState()
    val address by ui.prefs.obdAddress.state.collectAsState()
    val autoOdo by ui.prefs.obdAutoOdo.state.collectAsState()
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(obd.hasPermission()) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var dtc by remember { mutableStateOf<DtcResult?>(null) }
    var live by remember { mutableStateOf<LiveData?>(null) }
    var liveOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pickDevice by remember { mutableStateOf(false) }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label
            try { block() } catch (t: Throwable) { ui.toast(tr("Ошибка: ${t.message ?: t.javaClass.simpleName}", "Помилка: ${t.message ?: t.javaClass.simpleName}")) }
            busy = null
        }
    }

    suspend fun readOdo(silent: Boolean) {
        when (val r = obd.readOdometer(ui.repo.s.currentKm)) {
            is OdoRead.Odometer -> { ui.repo.addOdo(r.km, source = OdoSource.OBD); ui.toast(tr("Пробег с одометра: ${Fmt.km(r.km)}", "Пробіг з одометра: ${Fmt.km(r.km)}")) }
            is OdoRead.Estimated -> if (r.km > ui.repo.s.currentKm) {
                ui.repo.addOdo(r.km, source = OdoSource.OBD); ui.toast(tr("Пробег по OBD: ${Fmt.km(r.km)}", "Пробіг за OBD: ${Fmt.km(r.km)}"))
            } else if (!silent) ui.toast(tr("Пробег не изменился", "Пробіг не змінився"))
            OdoRead.BaseSet -> if (!silent) ui.toast(tr("Машина не отдаёт одометр. Точка отсчёта сохранена — дальше пробег будет считаться по OBD", "Авто не віддає одометр. Точку відліку збережено — далі пробіг рахуватиметься за OBD"))
            OdoRead.Unsupported -> if (!silent) ui.toast(tr("ЭБУ не сообщает пробег — вводите вручную", "ЕБК не повідомляє пробіг — вводьте вручну"))
        }
    }

    // Автоматически: после подключения — прочитать пробег
    LaunchedEffect(state) {
        if (state is ObdState.Connected && autoOdo == 1) runCatching { readOdo(true) }
    }
    LaunchedEffect(liveOn, state) {
        while (liveOn && state is ObdState.Connected && isActive) {
            live = runCatching { obd.live() }.getOrNull()
            delay(800)
        }
    }

    TabScaffold(tr("OBD2-диагностика", "OBD2-діагностика")) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Адаптер ELM327 (Bluetooth)", "Адаптер ELM327 (Bluetooth)"), style = MaterialTheme.typography.titleMedium)
                        when {
                            !granted -> {
                                Text(tr("Нужно разрешение на подключение к Bluetooth-устройствам.", "Потрібен дозвіл на підключення до Bluetooth-пристроїв."))
                                Button(onClick = { if (Build.VERSION.SDK_INT >= 31) perm.launch(Manifest.permission.BLUETOOTH_CONNECT) else granted = true }) { Text(tr("Разрешить", "Дозволити")) }
                            }
                            !obd.bluetoothOn -> {
                                Text(tr("Bluetooth выключен.", "Bluetooth вимкнено."))
                                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(tr("Настройки Bluetooth", "Налаштування Bluetooth")) }
                            }
                            else -> {
                                val dev = obd.bonded().firstOrNull { it.address == address }
                                Text(
                                    when (val st = state) {
                                        ObdState.Off -> tr("Не подключено", "Не підключено") + (dev?.let { " · ${it.name}" } ?: "")
                                        ObdState.Connecting -> tr("Подключение…", "Підключення…")
                                        is ObdState.Connected -> tr("Подключено: ${st.name}\nПротокол: ${st.protocol}", "Підключено: ${st.name}\nПротокол: ${st.protocol}")
                                        is ObdState.Failed -> tr("Ошибка: ${st.message}", "Помилка: ${st.message}")
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state is ObdState.Connected) OutlinedButton(onClick = { obd.disconnect(); liveOn = false }) { Text(tr("Отключить", "Відключити")) }
                                    else Button(
                                        enabled = state != ObdState.Connecting,
                                        onClick = { if (address.isEmpty()) pickDevice = true else scope.launch { obd.connect(address) } },
                                    ) { Text(tr("Подключить", "Підключити")) }
                                    OutlinedButton(onClick = { pickDevice = true }) { Text(tr("Адаптер…", "Адаптер…")) }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("Читать пробег при подключении", "Зчитувати пробіг під час підключення"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(autoOdo == 1, { ui.prefs.obdAutoOdo.set(if (it) 1 else 0) })
                                }
                            }
                        }
                        Text(
                            tr("Сначала сопрягите адаптер в настройках Bluetooth телефона (PIN обычно 1234 или 0000), зажигание — включено.", "Спочатку створіть пару з адаптером у налаштуваннях Bluetooth телефона (PIN зазвичай 1234 або 0000), запалювання — увімкнено."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state is ObdState.Connected) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { run(tr("Чтение ошибок…", "Зчитування помилок…")) { dtc = obd.readDtc() } }, modifier = Modifier.weight(1f)) { Text(tr("Ошибки", "Помилки")) }
                        OutlinedButton(onClick = { run(tr("Чтение пробега…", "Зчитування пробігу…")) { readOdo(false) } }, modifier = Modifier.weight(1f)) { Text(tr("Пробег", "Пробіг")) }
                        OutlinedButton(onClick = { liveOn = !liveOn }, modifier = Modifier.weight(1f)) { Text(if (liveOn) tr("Стоп", "Стоп") else tr("Датчики", "Датчики")) }
                    }
                }
            }
            busy?.let { b ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text(b)
                    }
                }
            }
            if (liveOn) live?.let { l -> item { LiveCard(l) } }
            dtc?.let { d ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d.all.isEmpty()) tr("✅ Ошибок нет", "✅ Помилок немає") else tr("Найдено кодов: ${d.all.size}", "Знайдено кодів: ${d.all.size}"),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        if (d.all.isNotEmpty()) TextButton(onClick = {
                            val text = d.all.joinToString("\n") { "${it}: ${Dtc.info(it).title}" }
                            ui.repo.saveIncident(Incident(time = System.currentTimeMillis(), km = ui.repo.s.currentKm, title = "OBD: " + d.all.joinToString(), note = text, severity = d.all.maxOf { Dtc.info(it).severity }))
                            ui.toast(tr("Записано в дневник", "Записано в щоденник"))
                        }) { Text(tr("В дневник", "У щоденник")) }
                    }
                }
                listOf(tr("Сохранённые", "Збережені") to d.stored, tr("Ожидающие (ещё не подтверждены)", "Очікувані (ще не підтверджені)") to d.pending, tr("Постоянные", "Постійні") to d.permanent).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                        list.forEach { code -> item(key = title + code) { DtcCard(code) } }
                    }
                }
                if (d.all.isNotEmpty()) item {
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Сбросить ошибки и Check Engine", "Скинути помилки й Check Engine")) }
                }
            }
        }
    }

    if (pickDevice) {
        val list = obd.bonded()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickDevice = false },
            title = { Text(tr("Сопряжённые устройства", "Спарені пристрої")) },
            text = {
                Column {
                    if (list.isEmpty()) Text(tr("Нет сопряжённых устройств. Сопрягите адаптер в настройках Bluetooth.", "Немає спарених пристроїв. Створіть пару з адаптером у налаштуваннях Bluetooth."))
                    list.forEach { d ->
                        TextButton(onClick = { pickDevice = false; scope.launch { obd.connect(d.address) } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(d.name); Text(d.address, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickDevice = false; ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(tr("Настройки Bluetooth", "Налаштування Bluetooth")) } },
            dismissButton = { TextButton(onClick = { pickDevice = false }) { Text(tr("Закрыть", "Закрити")) } },
        )
    }
    if (confirmClear) ConfirmDialog(
        tr("Сбросить ошибки?", "Скинути помилки?"),
        tr("Коды и лампа Check Engine погаснут, но причина не исчезнет — при неисправности ошибка вернётся. Сбросятся и тесты готовности (важно перед техосмотром). Делайте это после ремонта.", "Коди й лампа Check Engine згаснуть, але причина не зникне — за несправності помилка повернеться. Скинуться й тести готовності (важливо перед техоглядом). Робіть це після ремонту."),
        confirm = tr("Сбросить", "Скинути"), onDismiss = { confirmClear = false },
    ) {
        run(tr("Сброс…", "Скидання…")) {
            val ok = obd.clearDtc(ui.repo.s.currentKm)
            ui.toast(if (ok) tr("Ошибки сброшены", "Помилки скинуто") else tr("ЭБУ не принял команду (заглушите двигатель, зажигание оставьте)", "ЕБК не прийняв команду (заглушіть двигун, запалювання залиште)"))
            if (ok) dtc = obd.readDtc()
        }
    }
}

@Composable
private fun DtcCard(code: String) {
    val info = Dtc.info(code)
    val dc = LocalData.current
    val (icon, color) = when (info.severity) {
        2 -> Icons.Filled.Error to dc.critical
        1 -> Icons.Filled.Warning to dc.serious
        else -> Icons.Filled.Info to dc.good
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(info.title, style = MaterialTheme.typography.bodyMedium)
                Text(Dtc.severityLabel(info.severity), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(tr("Что проверить: ${info.hint}", "Що перевірити: ${info.hint}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveCard(l: LiveData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { LabeledValue(tr("Обороты", "Оберти"), l.rpm?.let { tr("$it об/мин", "$it об/хв") } ?: "—", Modifier.weight(1f)); LabeledValue(tr("Скорость", "Швидкість"), l.speed?.let { tr("$it км/ч", "$it км/год") } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue(tr("Охлаждающая ж.", "Охолоджувальна р."), l.coolant?.let { "$it °C" } ?: "—", Modifier.weight(1f)); LabeledValue(tr("Воздух на впуске", "Повітря на впуску"), l.intake?.let { "$it °C" } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue(tr("Нагрузка", "Навантаження"), l.load?.let { "$it%" } ?: "—", Modifier.weight(1f)); LabeledValue(tr("Дроссель", "Дросель"), l.throttle?.let { "$it%" } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue(tr("Напряжение", "Напруга"), l.voltage?.let { Fmt.dec(it) + tr(" В", " В") } ?: "—", Modifier.weight(1f)); LabeledValue(tr("Топливо", "Пальне"), l.fuelLevel?.let { "$it%" } ?: "—", Modifier.weight(1f)) }
            val hints = buildList {
                l.voltage?.let { v ->
                    if ((l.rpm ?: 0) > 400 && v < 13.2) add(tr("Напряжение при работающем двигателе ниже 13,2 В — проверьте генератор", "Напруга при працюючому двигуні нижча за 13,2 В — перевірте генератор"))
                    if ((l.rpm ?: 0) == 0 && v < 12.3) add(tr("АКБ разряжена (< 12,3 В без нагрузки) — зарядите или проверьте", "АКБ розряджена (< 12,3 В без навантаження) — зарядіть або перевірте"))
                    if (v > 14.8) add(tr("Перезаряд (> 14,8 В) — регулятор напряжения", "Перезаряд (> 14,8 В) — регулятор напруги"))
                }
                l.coolant?.let { if (it > 105) add(tr("Температура ОЖ высокая — следите за перегревом", "Температура ОР висока — стежте за перегрівом")) }
            }
            hints.forEach { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
