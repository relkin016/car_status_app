package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.Car
import com.relkin.carstatus.data.Defaults
import com.relkin.carstatus.data.VehicleKind
import com.relkin.carstatus.data.Health
import com.relkin.carstatus.data.Item
import com.relkin.carstatus.data.LogType
import com.relkin.carstatus.data.Reports
import com.relkin.carstatus.util.Fmt
import java.time.LocalDate

@Composable
fun ItemsScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var edit by remember { mutableStateOf<Item?>(null) }
    var restore by remember { mutableStateOf(false) }
    SubScreen(
        "Пункты ТО", ui::pop,
        actions = { IconButton(onClick = { restore = true }) { Icon(Icons.Filled.RestartAlt, "Базовый список") } },
        fab = { FloatingActionButton(onClick = { edit = Item(name = "", kmInterval = 10_000, dayInterval = 365) }) { Icon(Icons.Filled.Add, "Добавить") } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    "У каждого пункта два лимита — по пробегу и по времени; срабатывает тот, что наступит раньше. 0 — лимит не используется.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(s.items, key = { it.id }) { it ->
                ListItem(
                    headlineContent = { Text(it.name) },
                    supportingContent = { Text(limits(it)) },
                    leadingContent = { Icon(itemIcon(it.icon), null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { ui.repo.moveItem(it.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Выше") }
                            IconButton(onClick = { ui.repo.moveItem(it.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Ниже") }
                            Switch(it.enabled, { v -> ui.repo.saveItem(it.copy(enabled = v)) })
                        }
                    },
                    modifier = Modifier.clickable { edit = it },
                )
                HorizontalDivider()
            }
        }
    }
    edit?.let { ItemEditDialog(it) { edit = null } }
    if (restore) ConfirmDialog(
        "Базовый список", "Добавить недостающие пункты из стандартного набора (масло, фильтры, тормоза…)? Ваши пункты останутся.",
        confirm = "Добавить", onDismiss = { restore = false },
    ) { ui.repo.restoreDefaultItems() }
}

fun limits(it: Item): String = listOfNotNull(
    it.kmInterval.takeIf { v -> v > 0 }?.let { v -> "каждые ${Fmt.km(v)}" },
    it.dayInterval.takeIf { v -> v > 0 }?.let { v -> if (v % 365 == 0L) "${v / 365} г." else Fmt.days(v) },
).joinToString(" или ").ifEmpty { "лимиты не заданы" }

@Composable
fun ItemEditDialog(item: Item, close: () -> Unit) {
    val ui = LocalUi.current
    var name by remember { mutableStateOf(item.name) }
    var icon by remember { mutableStateOf(item.icon) }
    var km by remember { mutableStateOf(item.kmInterval.toString()) }
    var days by remember { mutableStateOf(item.dayInterval.toString()) }
    var lastKm by remember { mutableStateOf(item.lastKm?.toString() ?: "") }
    var lastDate by remember { mutableStateOf(item.lastDate?.let(LocalDate::ofEpochDay)) }
    var note by remember { mutableStateOf(item.note) }
    var del by remember { mutableStateOf(false) }
    val kmV = parseKm(km.ifBlank { "0" })
    val daysV = parseKm(days.ifBlank { "0" })
    val lastKmV = if (lastKm.isBlank()) null else parseKm(lastKm)
    FormDialog(
        title = if (item.id == 0L) "Новый пункт ТО" else "Пункт ТО",
        onDismiss = close,
        dirty = changed(name, icon, km, days, lastKm, lastDate, note),
        confirmEnabled = name.isNotBlank() && kmV != null && daysV != null && (lastKm.isBlank() || lastKmV != null),
        onConfirm = {
            ui.repo.saveItem(item.copy(name = name.trim(), icon = icon, kmInterval = kmV!!, dayInterval = daysV!!, lastKm = lastKmV, lastDate = lastDate?.toEpochDay(), note = note.trim()))
            close()
        },
        extra = if (item.id != 0L) { { TextButton(onClick = { del = true }) { Text("Удалить") } } } else null,
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Defaults.icons.forEach { k ->
                FilterChip(selected = icon == k, onClick = { icon = k }, label = { Icon(itemIcon(k), null, Modifier.size(18.dp)) })
            }
        }
        NumField(km, { km = it }, "Лимит по пробегу", suffix = "км", decimal = false, hint = "0 — не учитывать")
        NumField(days, { days = it }, "Лимит по времени", suffix = "дн.", decimal = false, hint = "365 — год, 730 — 2 года")
        Text("Последнее обслуживание", style = MaterialTheme.typography.labelLarge)
        NumField(lastKm, { lastKm = it }, "Пробег", suffix = "км", decimal = false)
        if (lastDate != null) DateField(lastDate!!, { lastDate = it })
        else OutlinedButton(onClick = { lastDate = LocalDate.now() }) { Text("Указать дату") }
        OutlinedTextField(note, { note = it }, label = { Text("Заметка (артикул, допуск, объём)") }, modifier = Modifier.fillMaxWidth())
    }
    if (del) ConfirmDialog("Удалить пункт?", "История записей сохранится.", onDismiss = { del = false }) { ui.repo.deleteItem(item.id); close() }
}

@Composable
fun ItemDetailScreen(id: Long) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val item = s.item(id) ?: run { androidx.compose.runtime.LaunchedEffect(Unit) { ui.pop() }; return }
    val (rate, _) = remember(s) { Health.kmPerDay(s, s.fallbackKmPerDay) }
    val ih = Health.item(item, s.currentKm, rate)
    var edit by remember { mutableStateOf(false) }
    val history = s.log.filter { it.itemId == id }
    SubScreen(item.name, ui::pop, actions = { IconButton(onClick = { edit = true }) { Icon(Icons.Filled.Edit, "Изменить") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ih.percent?.let { "$it HP" } ?: "нет данных", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        HpBar(ih.hp, height = 14.dp)
                        Text(dueText(ih))
                        HorizontalDivider()
                        Row {
                            LabeledValue("Лимиты", limits(item), Modifier.weight(1f))
                        }
                        Row {
                            LabeledValue("Последний раз", item.lastDate?.let { Fmt.day(it) } ?: "—", Modifier.weight(1f))
                            LabeledValue("На пробеге", item.lastKm?.let { Fmt.km(it) } ?: "—", Modifier.weight(1f))
                        }
                        ih.dueKm?.let { LabeledValue("Следующее по пробегу", Fmt.km(it)) }
                        if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Button(onClick = { ui.edit = Edit.Log(null, LogType.SERVICE, item) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Обслужено — записать")
                }
            }
            item { Text("История", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            if (history.isEmpty()) item { Empty("Записей пока нет") }
            items(history, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(Reports.entryTitle(s, e)) },
                    supportingContent = { Text(Fmt.day(e.date) + (e.km?.let { " · " + Fmt.km(it) } ?: "") + if (e.note.isNotBlank()) " · ${e.note}" else "") },
                    trailingContent = { if (e.total > 0) Text(Fmt.money(e.total)) },
                    modifier = Modifier.clickable { ui.edit = Edit.Log(e, e.type) },
                )
            }
        }
    }
    if (edit) ItemEditDialog(item) { edit = false }
}

/** Выбор вида транспорта: плитки с эмодзи. */
@Composable
fun KindPicker(kind: VehicleKind, onPick: (VehicleKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VehicleKind.entries.forEach { k ->
            FilterChip(kind == k, { onPick(k) }, label = { Text("${k.emoji} ${k.label}") })
        }
    }
}

/** Первичная настройка транспорта: вид, профиль, текущий пробег, базовый список расходников. */
@Composable
fun SetupScreen() {
    val ui = LocalUi.current
    val gs by ui.garage.state.collectAsState()
    var kind by remember { mutableStateOf(VehicleKind.PETROL) }
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var serviced by remember { mutableStateOf(true) }
    val kmV = parseKm(km)
    // настроенный транспорт уже есть — можно отменить добавление
    val others = gs.vehicles.filter { it.id != gs.current && ui.garage.repo(it.id).s.setupDone }
    val first = others.isEmpty()
    val defaults = kind.items
    androidx.compose.material3.Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (first) "🚗 Авто·Статус" else "Новый транспорт", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (first) "Всё хранится только на телефоне: без аккаунтов и интернета. Расскажите о транспорте — и приложение начнёт следить за его «здоровьем». Позже можно добавить ещё транспорт и гаражи."
                else "Гараж «${gs.currentGarage?.name.orEmpty()}». Выберите вид — от него зависят расходники, единицы и квесты.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Вид транспорта", style = MaterialTheme.typography.labelLarge)
            KindPicker(kind) { kind = it }
            OutlinedTextField(name, { name = it }, label = { Text("Название (необязательно)") }, placeholder = { Text(kind.defaultTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(make, { make = it }, label = { Text("Марка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Модель") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(year, { year = it }, label = { Text("Год выпуска") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumField(km, { km = it }, "Текущий пробег", suffix = "км", decimal = false, hint = if (kind.isCar || kind == VehicleKind.MOTO) null else "Если нет одометра — по велокомпьютеру или приложению; можно 0")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { serviced = !serviced }) {
                Checkbox(serviced, { serviced = it })
                Text("Считать, что всё обслужено сейчас (поправите потом по каждому пункту)")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { ui.repo.setup(Car(name = name.trim(), make = make.trim(), model = model.trim(), year = year.trim(), kind = kind), kmV!!, serviced) },
                enabled = kmV != null, modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать") }
            if (!first) OutlinedButton(
                onClick = { ui.garage.deleteVehicle(gs.current) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Отмена") }
            Text(
                "В список сразу попадут ${defaults.size} расходников и узлов: " + defaults.take(4).joinToString { it.name.lowercase() } + "… Его можно редактировать.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
