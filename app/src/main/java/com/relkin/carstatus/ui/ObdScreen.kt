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
            try { block() } catch (t: Throwable) { ui.toast("Ошибка: ${t.message ?: t.javaClass.simpleName}") }
            busy = null
        }
    }

    suspend fun readOdo(silent: Boolean) {
        when (val r = obd.readOdometer(ui.repo.s.currentKm)) {
            is OdoRead.Odometer -> { ui.repo.addOdo(r.km, source = OdoSource.OBD); ui.toast("Пробег с одометра: ${Fmt.km(r.km)}") }
            is OdoRead.Estimated -> if (r.km > ui.repo.s.currentKm) {
                ui.repo.addOdo(r.km, source = OdoSource.OBD); ui.toast("Пробег по OBD: ${Fmt.km(r.km)}")
            } else if (!silent) ui.toast("Пробег не изменился")
            OdoRead.BaseSet -> if (!silent) ui.toast("Машина не отдаёт одометр. Точка отсчёта сохранена — дальше пробег будет считаться по OBD")
            OdoRead.Unsupported -> if (!silent) ui.toast("ЭБУ не сообщает пробег — вводите вручную")
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

    TabScaffold("OBD2-диагностика") { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Адаптер ELM327 (Bluetooth)", style = MaterialTheme.typography.titleMedium)
                        when {
                            !granted -> {
                                Text("Нужно разрешение на подключение к Bluetooth-устройствам.")
                                Button(onClick = { if (Build.VERSION.SDK_INT >= 31) perm.launch(Manifest.permission.BLUETOOTH_CONNECT) else granted = true }) { Text("Разрешить") }
                            }
                            !obd.bluetoothOn -> {
                                Text("Bluetooth выключен.")
                                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Настройки Bluetooth") }
                            }
                            else -> {
                                val dev = obd.bonded().firstOrNull { it.address == address }
                                Text(
                                    when (val st = state) {
                                        ObdState.Off -> "Не подключено" + (dev?.let { " · ${it.name}" } ?: "")
                                        ObdState.Connecting -> "Подключение…"
                                        is ObdState.Connected -> "Подключено: ${st.name}\nПротокол: ${st.protocol}"
                                        is ObdState.Failed -> "Ошибка: ${st.message}"
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state is ObdState.Connected) OutlinedButton(onClick = { obd.disconnect(); liveOn = false }) { Text("Отключить") }
                                    else Button(
                                        enabled = state != ObdState.Connecting,
                                        onClick = { if (address.isEmpty()) pickDevice = true else scope.launch { obd.connect(address) } },
                                    ) { Text("Подключить") }
                                    OutlinedButton(onClick = { pickDevice = true }) { Text("Адаптер…") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Читать пробег при подключении", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(autoOdo == 1, { ui.prefs.obdAutoOdo.set(if (it) 1 else 0) })
                                }
                            }
                        }
                        Text(
                            "Сначала сопрягите адаптер в настройках Bluetooth телефона (PIN обычно 1234 или 0000), зажигание — включено.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state is ObdState.Connected) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { run("Чтение ошибок…") { dtc = obd.readDtc() } }, modifier = Modifier.weight(1f)) { Text("Ошибки") }
                        OutlinedButton(onClick = { run("Чтение пробега…") { readOdo(false) } }, modifier = Modifier.weight(1f)) { Text("Пробег") }
                        OutlinedButton(onClick = { liveOn = !liveOn }, modifier = Modifier.weight(1f)) { Text(if (liveOn) "Стоп" else "Датчики") }
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
                            if (d.all.isEmpty()) "✅ Ошибок нет" else "Найдено кодов: ${d.all.size}",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        if (d.all.isNotEmpty()) TextButton(onClick = {
                            val text = d.all.joinToString("\n") { "${it}: ${Dtc.info(it).title}" }
                            ui.repo.saveIncident(Incident(time = System.currentTimeMillis(), km = ui.repo.s.currentKm, title = "OBD: " + d.all.joinToString(), note = text, severity = d.all.maxOf { Dtc.info(it).severity }))
                            ui.toast("Записано в дневник")
                        }) { Text("В дневник") }
                    }
                }
                listOf("Сохранённые" to d.stored, "Ожидающие (ещё не подтверждены)" to d.pending, "Постоянные" to d.permanent).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                        list.forEach { code -> item(key = title + code) { DtcCard(code) } }
                    }
                }
                if (d.all.isNotEmpty()) item {
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить ошибки и Check Engine") }
                }
            }
        }
    }

    if (pickDevice) {
        val list = obd.bonded()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickDevice = false },
            title = { Text("Сопряжённые устройства") },
            text = {
                Column {
                    if (list.isEmpty()) Text("Нет сопряжённых устройств. Сопрягите адаптер в настройках Bluetooth.")
                    list.forEach { d ->
                        TextButton(onClick = { pickDevice = false; scope.launch { obd.connect(d.address) } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(d.name); Text(d.address, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickDevice = false; ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Настройки Bluetooth") } },
            dismissButton = { TextButton(onClick = { pickDevice = false }) { Text("Закрыть") } },
        )
    }
    if (confirmClear) ConfirmDialog(
        "Сбросить ошибки?",
        "Коды и лампа Check Engine погаснут, но причина не исчезнет — при неисправности ошибка вернётся. Сбросятся и тесты готовности (важно перед техосмотром). Делайте это после ремонта.",
        confirm = "Сбросить", onDismiss = { confirmClear = false },
    ) {
        run("Сброс…") {
            val ok = obd.clearDtc(ui.repo.s.currentKm)
            ui.toast(if (ok) "Ошибки сброшены" else "ЭБУ не принял команду (заглушите двигатель, зажигание оставьте)")
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
                Text("Что проверить: ${info.hint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveCard(l: LiveData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { LabeledValue("Обороты", l.rpm?.let { "$it об/мин" } ?: "—", Modifier.weight(1f)); LabeledValue("Скорость", l.speed?.let { "$it км/ч" } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue("Охлаждающая ж.", l.coolant?.let { "$it °C" } ?: "—", Modifier.weight(1f)); LabeledValue("Воздух на впуске", l.intake?.let { "$it °C" } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue("Нагрузка", l.load?.let { "$it%" } ?: "—", Modifier.weight(1f)); LabeledValue("Дроссель", l.throttle?.let { "$it%" } ?: "—", Modifier.weight(1f)) }
            Row { LabeledValue("Напряжение", l.voltage?.let { Fmt.dec(it) + " В" } ?: "—", Modifier.weight(1f)); LabeledValue("Топливо", l.fuelLevel?.let { "$it%" } ?: "—", Modifier.weight(1f)) }
            val hints = buildList {
                l.voltage?.let { v ->
                    if ((l.rpm ?: 0) > 400 && v < 13.2) add("Напряжение при работающем двигателе ниже 13,2 В — проверьте генератор")
                    if ((l.rpm ?: 0) == 0 && v < 12.3) add("АКБ разряжена (< 12,3 В без нагрузки) — зарядите или проверьте")
                    if (v > 14.8) add("Перезаряд (> 14,8 В) — регулятор напряжения")
                }
                l.coolant?.let { if (it > 105) add("Температура ОЖ высокая — следите за перегревом") }
            }
            hints.forEach { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
