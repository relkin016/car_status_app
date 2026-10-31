package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.GarageInfo
import com.relkin.carstatus.data.Health
import com.relkin.carstatus.data.VehicleRef

/** Строка под заголовком вкладки: текущий транспорт и гараж; по нажатию — переключение. */
@Composable
fun VehicleTitle() {
    val ui = LocalUi.current
    val gs by ui.garage.state.collectAsState()
    val s by ui.repo.snap.collectAsState()
    val garageName = gs.currentGarage?.name.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { ui.edit = Edit.Switch },
    ) {
        Text(
            s.car.label + if (gs.garages.size > 1) " · $garageName" else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.Filled.ArrowDropDown, "Сменить транспорт", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Краткая сводка транспорта для списков: эмодзи, название, HP, пробег. */
@Composable
private fun VehicleRow(v: VehicleRef, selected: Boolean, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.garage.repo(v.id).snap.collectAsState()
    val hp = remember(s) { if (s.setupDone) Health.of(s, s.fallbackKmPerDay).overall else null }
    ListItem(
        leadingContent = { Text(s.kind.emoji, style = MaterialTheme.typography.headlineSmall) },
        headlineContent = { Text(s.car.title, fontWeight = if (selected) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (!s.setupDone) "${s.kind.label} · не настроен"
                else listOfNotNull(s.kind.label, com.relkin.carstatus.util.Fmt.km(s.currentKm), hp?.let { "${(it * 100).toInt()} HP" }).joinToString(" · "),
            )
        },
        trailingContent = trailing,
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Быстрое переключение: гаражи вкладками сверху, под ними — транспорт выбранного гаража. */
@Composable
fun SwitchDialog(close: () -> Unit) {
    val ui = LocalUi.current
    val gs by ui.garage.state.collectAsState()
    var garageId by remember { mutableStateOf(gs.currentGarage?.id ?: gs.garages.first().id) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Гараж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gs.garages.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    gs.garages.forEach { g ->
                        FilterChip(garageId == g.id, { garageId = g.id }, label = { Text(g.name) }, leadingIcon = { Icon(Icons.Filled.Warehouse, null, Modifier.size(18.dp)) })
                    }
                }
                val list = gs.inGarage(garageId)
                if (list.isEmpty()) Empty("В этом гараже пока пусто")
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEach { v ->
                        VehicleRow(v, v.id == gs.current) { close(); ui.switchTo(v.id) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { close(); ui.addVehicle(garageId) }, modifier = Modifier.weight(1f)) { Text("+ Транспорт") }
                    OutlinedButton(onClick = { close(); ui.push(Route.Garage) }, modifier = Modifier.weight(1f)) { Text("Гаражи…") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Закрыть") } },
    )
}

/** Управление гаражами: названия, уведомления, транспорт в каждом, перенос между гаражами. */
@Composable
fun GarageScreen() {
    val ui = LocalUi.current
    val gs by ui.garage.state.collectAsState()
    var editGarage by remember { mutableStateOf<GarageInfo?>(null) }
    var newGarage by remember { mutableStateOf(false) }
    var delGarage by remember { mutableStateOf<GarageInfo?>(null) }
    var delVehicle by remember { mutableStateOf<VehicleRef?>(null) }
    SubScreen("Гаражи", ui::pop, fab = {
        FloatingActionButton(onClick = { newGarage = true }) { Icon(Icons.Filled.Add, "Новый гараж") }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Переключайтесь между гаражами и транспортом в заголовке любой вкладки. Уведомления приходят сразу по всем гаражам, где они включены — не только по открытому.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            gs.garages.forEach { g ->
                item(key = "g${g.id}") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warehouse, null, tint = MaterialTheme.colorScheme.primary)
                                Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                IconButton(onClick = { editGarage = g }) { Icon(Icons.Filled.Edit, "Переименовать") }
                                if (gs.garages.size > 1) IconButton(onClick = { delGarage = g }) { Icon(Icons.Filled.Delete, "Удалить гараж") }
                            }
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Уведомления из этого гаража", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(g.notify, { ui.garage.saveGarage(g.copy(notify = it)) })
                            }
                            HorizontalDivider(Modifier.padding(top = 4.dp))
                            val list = gs.inGarage(g.id)
                            if (list.isEmpty()) Empty("Пусто")
                            list.forEachIndexed { i, v ->
                                VehicleRow(v, v.id == gs.current, trailing = {
                                    VehicleMenu(v, g, first = i == 0, last = i == list.lastIndex, others = gs.garages - g) { delVehicle = v }
                                }) { ui.switchTo(v.id) }
                            }
                            TextButton(onClick = { ui.addVehicle(g.id) }, modifier = Modifier.padding(start = 8.dp)) {
                                Icon(Icons.Filled.Add, null); Text("Добавить транспорт")
                            }
                        }
                    }
                }
            }
        }
    }
    if (newGarage || editGarage != null) {
        var name by remember { mutableStateOf(editGarage?.name ?: "") }
        FormDialog(
            title = if (editGarage == null) "Новый гараж" else "Гараж",
            onDismiss = { newGarage = false; editGarage = null },
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                editGarage?.let { ui.garage.saveGarage(it.copy(name = name.trim())) } ?: ui.garage.addGarage(name.trim())
                newGarage = false; editGarage = null
            },
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Название (дом, дача, работа…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
    delGarage?.let { g ->
        val n = gs.inGarage(g.id).size
        ConfirmDialog(
            "Удалить гараж «${g.name}»?",
            if (n == 0) "Гараж пуст." else "Вместе с ним будут удалены $n транспорт(а) со всеми записями и фото. Перенесите нужный в другой гараж заранее.",
            onDismiss = { delGarage = null },
        ) { ui.garage.deleteGarage(g.id) }
    }
    delVehicle?.let { v ->
        val title = ui.garage.repo(v.id).s.car.label
        ConfirmDialog("Удалить «$title»?", "Все записи, пункты ТО и фото этого транспорта будут удалены без возможности восстановления.", onDismiss = { delVehicle = null }) {
            ui.garage.deleteVehicle(v.id)
        }
    }
}

@Composable
private fun VehicleMenu(v: VehicleRef, g: GarageInfo, first: Boolean, last: Boolean, others: List<GarageInfo>, onDelete: () -> Unit) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Edit, "Действия") }
        DropdownMenu(open, { open = false }) {
            if (!first) DropdownMenuItem(text = { Text("Выше") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) }, onClick = { ui.garage.reorder(v.id, -1); open = false })
            if (!last) DropdownMenuItem(text = { Text("Ниже") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) }, onClick = { ui.garage.reorder(v.id, 1); open = false })
            others.forEach { o ->
                DropdownMenuItem(
                    text = { Text("В гараж «${o.name}»") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                    onClick = { ui.garage.moveVehicle(v.id, o.id); open = false },
                )
            }
            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { open = false; onDelete() })
        }
    }
}
