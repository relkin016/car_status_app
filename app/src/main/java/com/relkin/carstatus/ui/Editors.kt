package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.Breakdown
import com.relkin.carstatus.data.Incident
import com.relkin.carstatus.data.LogEntry
import com.relkin.carstatus.data.LogType
import com.relkin.carstatus.data.OdoSource
import com.relkin.carstatus.data.Photo
import com.relkin.carstatus.data.PhotoOwner
import com.relkin.carstatus.util.Fmt
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun EditDialogs() {
    val ui = LocalUi.current
    val close = { ui.edit = null }
    when (val e = ui.edit) {
        null -> {}
        Edit.AddMenu -> AddMenu(close)
        Edit.Odo -> OdoDialog(close)
        is Edit.Log -> LogDialog(e, close)
        is Edit.IncidentE -> IncidentDialog(e.incident, close)
        is Edit.BreakdownE -> BreakdownDialog(e.b, close)
        is Edit.PhotoE -> PhotoDialog(e.photo, close)
    }
}

@Composable
private fun AddMenu(close: () -> Unit) {
    val ui = LocalUi.current
    fun go(e: Edit) { ui.edit = e }
    val rows = listOf<Triple<ImageVector, String, () -> Unit>>(
        Triple(Icons.Filled.Speed, "Пробег") { go(Edit.Odo) },
        Triple(Icons.Filled.LocalGasStation, "Заправка") { go(Edit.Log(null, LogType.FUEL)) },
        Triple(Icons.Filled.Build, "Обслуживание (ТО)") { go(Edit.Log(null, LogType.SERVICE)) },
        Triple(Icons.Filled.Inventory2, "Запчасти") { go(Edit.Log(null, LogType.PART)) },
        Triple(Icons.Filled.ReceiptLong, "Прочий расход") { go(Edit.Log(null, LogType.OTHER)) },
        Triple(Icons.Filled.CarCrash, "Запись в дневник") { go(Edit.IncidentE(null)) },
        Triple(Icons.Filled.ReportProblem, "Поломка в список") { go(Edit.BreakdownE(null)) },
        Triple(Icons.Filled.PhotoCamera, "Фото в журнал") { close(); ui.push(Route.Photos) },
    )
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Добавить") },
        text = {
            Column {
                rows.forEach { (icon, label, action) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(icon, null) },
                        modifier = Modifier.clickable(onClick = action),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Закрыть") } },
    )
}

/** Ввод пробега: в поле — текущее значение, можно дописать выражение (+320) или открыть калькулятор. */
@Composable
fun OdoDialog(close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var text by remember { mutableStateOf(s.currentKm.toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val km = parseKm(text)
    val less = km != null && km < s.currentKm
    FormDialog(
        title = "Текущий пробег",
        onDismiss = close,
        confirmEnabled = km != null,
        onConfirm = {
            ui.repo.addOdo(km!!, date.toEpochDay(), OdoSource.MANUAL)
            ui.toast("Пробег ${Fmt.km(km)} сохранён")
            close()
        },
    ) {
        NumField(
            text, { text = it }, "Пробег", suffix = "км", decimal = false,
            hint = if (less) "Меньше последнего (${Fmt.km(s.currentKm)}) — проверьте" else "Было: ${Fmt.km(s.currentKm)}" +
                (s.lastOdoDate?.let { " от ${Fmt.day(it)}" } ?: ""),
        )
        DateField(date, { date = it })
    }
}

/** Выпадающий выбор из списка (без ExposedDropdown — проще и стабильнее). */
@Composable
fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}

/** Новые фото формы: хранятся файлами до сохранения записи, при отмене удаляются. */
@Composable
private fun rememberDraftPhotos() = remember { mutableStateListOf<String>() }

private fun dropDrafts(ui: Ui, files: List<String>) = files.forEach { File(ui.repo.photoDir, it).delete() }

private fun saveDrafts(ui: Ui, files: List<String>, type: Int, owner: Long, km: Long?) {
    val now = System.currentTimeMillis()
    files.forEach { ui.repo.addPhoto(Photo(time = now, km = km, file = it, ownerType = type, ownerId = owner)) }
}

@Composable
private fun LogDialog(req: Edit.Log, close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val e = req.entry
    var type by remember { mutableStateOf(e?.type ?: req.type) }
    var date by remember { mutableStateOf(e?.let { LocalDate.ofEpochDay(it.date) } ?: LocalDate.now()) }
    var km by remember { mutableStateOf(e?.km?.toString() ?: s.currentKm.toString()) }
    var itemId by remember { mutableStateOf(e?.itemId ?: req.item?.id) }
    var title by remember { mutableStateOf(e?.title ?: "") }
    var amount by remember { mutableStateOf(e?.amount?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var parts by remember { mutableStateOf(e?.parts?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var liters by remember { mutableStateOf(e?.liters?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var full by remember { mutableStateOf(e?.full ?: true) }
    var note by remember { mutableStateOf(e?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val drafts = rememberDraftPhotos()
    val dismiss = { dropDrafts(ui, drafts); close() }

    val kmVal = if (km.isBlank()) null else parseKm(km)
    val amountVal = if (amount.isBlank()) 0.0 else parseNum(amount)
    val partsVal = if (parts.isBlank()) 0.0 else parseNum(parts)
    val litersVal = if (liters.isBlank()) 0.0 else parseNum(liters)
    val valid = (km.isBlank() || kmVal != null) && amountVal != null && partsVal != null && litersVal != null &&
        (type != LogType.SERVICE || itemId != null || title.isNotBlank())

    FormDialog(
        title = (if (e == null) "Новая запись: " else "") + LogType.label(type),
        onDismiss = dismiss,
        confirmEnabled = valid,
        onConfirm = {
            val entry = LogEntry(
                id = e?.id ?: 0, type = type, date = date.toEpochDay(), km = kmVal,
                itemId = if (type == LogType.SERVICE || type == LogType.PART) itemId else null,
                title = title.trim(), amount = amountVal!!, parts = if (type == LogType.SERVICE) partsVal!! else 0.0,
                liters = if (type == LogType.FUEL) litersVal!! else 0.0, full = full, note = note.trim(),
            )
            val files = drafts.toList()
            ui.repo.saveLog(entry) { id -> saveDrafts(ui, files, PhotoOwner.LOG, id, kmVal) }
            close()
        },
        extra = if (e != null) { { TextButton(onClick = { confirmDelete = true }) { Text("Удалить") } } } else null,
    ) {
        if (e == null) Segmented(
            listOf(LogType.FUEL to "Топл.", LogType.SERVICE to "ТО", LogType.PART to "Зап.", LogType.OTHER to "Проч."),
            type, { type = it },
        )
        DateField(date, { date = it })
        NumField(km, { km = it }, "Пробег", suffix = "км", decimal = false)
        if (type == LogType.SERVICE || type == LogType.PART) {
            Picker(
                if (type == LogType.SERVICE) "Пункт ТО" else "Для пункта ТО (необязательно)",
                s.item(itemId)?.name ?: "— другое —",
                listOf<Pair<Long?, String>>(null to "— другое —") + s.items.map { it.id to it.name },
            ) { itemId = it }
            if (type == LogType.SERVICE && itemId != null) Text(
                "Запись обновит «последнее обслуживание» пункта и восстановит его HP.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(when (type) { LogType.FUEL -> "АЗС / марка топлива"; LogType.OTHER -> "Что (мойка, страховка, штраф…)"; else -> "Описание работ / деталей" }) },
        )
        when (type) {
            LogType.FUEL -> {
                NumField(liters, { liters = it }, "Литры", suffix = "л")
                NumField(
                    amount, { amount = it }, "Сумма", suffix = Fmt.currency,
                    hint = if ((litersVal ?: 0.0) > 0 && (amountVal ?: 0.0) > 0) "${Fmt.money2(amountVal!! / litersVal!!)}/л" else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { full = !full }) {
                    Checkbox(full, { full = it }); Text("Полный бак (для расчёта расхода)")
                }
            }
            LogType.SERVICE -> {
                NumField(amount, { amount = it }, "Работа", suffix = Fmt.currency)
                NumField(parts, { parts = it }, "Запчасти и материалы", suffix = Fmt.currency)
            }
            else -> NumField(amount, { amount = it }, "Сумма", suffix = Fmt.currency)
        }
        OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        PhotoStrip(
            existing = e?.let { s.photosOf(PhotoOwner.LOG, it.id).map { p -> p.file } }.orEmpty(),
            added = drafts, onAdd = { drafts += it }, onRemoveAdded = { drafts -= it; File(ui.repo.photoDir, it).delete() },
        )
    }
    if (confirmDelete && e != null) ConfirmDialog("Удалить запись?", "Запись и её фото будут удалены.", onDismiss = { confirmDelete = false }) {
        ui.repo.deleteLog(e.id); dismiss()
    }
}

@Composable
private fun IncidentDialog(i: Incident?, close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val zone = ZoneId.systemDefault()
    val initial = i?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.time), zone) } ?: LocalDateTime.now()
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var minutes by remember { mutableStateOf(initial.hour * 60 + initial.minute) }
    var pickTime by remember { mutableStateOf(false) }
    var km by remember { mutableStateOf(i?.km?.toString() ?: s.currentKm.toString()) }
    var title by remember { mutableStateOf(i?.title ?: "") }
    var note by remember { mutableStateOf(i?.note ?: "") }
    var severity by remember { mutableStateOf(i?.severity ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val drafts = rememberDraftPhotos()
    val dismiss = { dropDrafts(ui, drafts); close() }
    val kmVal = if (km.isBlank()) null else parseKm(km)

    FormDialog(
        title = if (i == null) "Запись в дневник" else "Дневник",
        onDismiss = dismiss,
        confirmEnabled = title.isNotBlank() && (km.isBlank() || kmVal != null),
        onConfirm = {
            val t = date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
            val files = drafts.toList()
            ui.repo.saveIncident(Incident(i?.id ?: 0, t, kmVal, title.trim(), note.trim(), severity)) { id ->
                saveDrafts(ui, files, PhotoOwner.INCIDENT, id, kmVal)
            }
            close()
        },
        extra = if (i != null) { { TextButton(onClick = { confirmDelete = true }) { Text("Удалить") } } } else null,
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("Что случилось") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(date, { date = it }, modifier = Modifier.weight(1.4f))
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).padding(top = 8.dp)) { Text(Fmt.hm(minutes)) }
        }
        NumField(km, { km = it }, "Пробег", suffix = "км", decimal = false)
        Segmented(listOf(0 to "Заметка", 1 to "Внимание", 2 to "Серьёзно"), severity, { severity = it })
        OutlinedTextField(note, { note = it }, label = { Text("Подробности: звук, условия, что делали") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        PhotoStrip(
            existing = i?.let { s.photosOf(PhotoOwner.INCIDENT, it.id).map { p -> p.file } }.orEmpty(),
            added = drafts, onAdd = { drafts += it }, onRemoveAdded = { drafts -= it; File(ui.repo.photoDir, it).delete() },
        )
        if (i == null) TextButton(onClick = {
            ui.edit = null
            dropDrafts(ui, drafts)
            ui.edit = Edit.BreakdownE(Breakdown(title = title.trim(), note = note.trim(), priority = severity, createdDate = date.toEpochDay(), createdKm = kmVal))
        }, enabled = title.isNotBlank()) { Text("Это поломка → в список поломок") }
    }
    if (pickTime) TimePickDialog(minutes, { pickTime = false }) { minutes = it }
    if (confirmDelete && i != null) ConfirmDialog("Удалить запись дневника?", i.title, onDismiss = { confirmDelete = false }) {
        ui.repo.deleteIncident(i.id); dismiss()
    }
}

@Composable
private fun BreakdownDialog(b: Breakdown?, close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var title by remember { mutableStateOf(b?.title ?: "") }
    var note by remember { mutableStateOf(b?.note ?: "") }
    var priority by remember { mutableStateOf(b?.priority ?: 1) }
    var confirmDelete by remember { mutableStateOf(false) }
    val existing = b?.takeIf { it.id != 0L }
    FormDialog(
        title = if (existing == null) "Новая поломка" else "Поломка",
        onDismiss = close,
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            ui.repo.saveBreakdown(
                (b ?: Breakdown(title = "", createdDate = LocalDate.now().toEpochDay(), createdKm = s.currentKm))
                    .copy(title = title.trim(), note = note.trim(), priority = priority),
            )
            close()
        },
        extra = if (existing != null) { { TextButton(onClick = { confirmDelete = true }) { Text("Удалить") } } } else null,
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("Что не так") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Segmented(listOf(0 to "Терпит", 1 to "Скоро", 2 to "Срочно"), priority, { priority = it })
        OutlinedTextField(note, { note = it }, label = { Text("Заметка (запчасть, цена, СТО)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
    if (confirmDelete && existing != null) ConfirmDialog("Удалить поломку?", existing.title, onDismiss = { confirmDelete = false }) {
        ui.repo.deleteBreakdown(existing.id); close()
    }
}

@Composable
private fun PhotoDialog(p: Photo, close: () -> Unit) {
    val ui = LocalUi.current
    var caption by remember { mutableStateOf(p.caption) }
    var km by remember { mutableStateOf(p.km?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(false) }
    val kmVal = if (km.isBlank()) null else parseKm(km)
    FormDialog(
        title = "Фото",
        onDismiss = close,
        confirmEnabled = km.isBlank() || kmVal != null,
        onConfirm = { ui.repo.updatePhoto(p.copy(caption = caption.trim(), km = kmVal)); close() },
        extra = { TextButton(onClick = { confirmDelete = true }) { Text("Удалить") } },
    ) {
        PhotoThumb(p.file, 240.dp, Modifier.fillMaxWidth(), maxSide = 800) { view = true }
        Text(Fmt.dateTime(p.time), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(caption, { caption = it }, label = { Text("Подпись") }, modifier = Modifier.fillMaxWidth())
        NumField(km, { km = it }, "Пробег", suffix = "км", decimal = false)
    }
    if (view) PhotoViewer(p.file, caption, { view = false })
    if (confirmDelete) ConfirmDialog("Удалить фото?", "Файл будет удалён.", onDismiss = { confirmDelete = false }) {
        ui.repo.deletePhoto(p); close()
    }
}
