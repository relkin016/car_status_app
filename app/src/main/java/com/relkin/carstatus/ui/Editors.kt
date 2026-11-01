package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.EvStation
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
import androidx.compose.material3.IconButton
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
import com.relkin.carstatus.data.PartRef
import com.relkin.carstatus.data.LogType
import com.relkin.carstatus.data.OdoSource
import com.relkin.carstatus.data.Photo
import com.relkin.carstatus.data.PhotoOwner
import com.relkin.carstatus.data.Energy
import com.relkin.carstatus.data.VehicleKind
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
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
        Edit.Switch -> SwitchDialog(close)
        Edit.Odo -> OdoDialog(close)
        is Edit.Log -> LogDialog(e, close)
        is Edit.IncidentE -> IncidentDialog(e.incident, close)
        is Edit.BreakdownE -> BreakdownDialog(e.b, close)
        is Edit.PhotoE -> PhotoDialog(e.photo, close)
    }
}

/** Название типа записи с учётом вида транспорта: «Топливо» или «Зарядка». */
fun logLabel(type: Int, kind: VehicleKind) = if (type == LogType.FUEL) kind.refuel else LogType.label(type)

fun energyIcon(kind: VehicleKind): ImageVector =
    if (kind.energy == Energy.ELECTRIC) Icons.Filled.EvStation else Icons.Filled.LocalGasStation

@Composable
private fun AddMenu(close: () -> Unit) {
    val ui = LocalUi.current
    fun go(e: Edit) { ui.edit = e }
    val kind = ui.repo.s.kind
    val rows = listOfNotNull<Triple<ImageVector, String, () -> Unit>>(
        Triple(Icons.Filled.Speed, tr("Пробег", "Пробіг")) { go(Edit.Odo) },
        if (kind.energy == Energy.NONE) null else Triple(energyIcon(kind), kind.refuel) { go(Edit.Log(null, LogType.FUEL)) },
        Triple(Icons.Filled.Build, tr("Обслуживание (ТО)", "Обслуговування (ТО)")) { go(Edit.Log(null, LogType.SERVICE)) },
        Triple(Icons.Filled.Handyman, tr("Работы (ремонт)", "Роботи (ремонт)")) { go(Edit.Log(null, LogType.WORK)) },
        Triple(Icons.Filled.Inventory2, tr("Запчасти", "Запчастини")) { go(Edit.Log(null, LogType.PART)) },
        Triple(Icons.Filled.ReceiptLong, tr("Прочий расход", "Інша витрата")) { go(Edit.Log(null, LogType.OTHER)) },
        Triple(Icons.Filled.CarCrash, tr("Запись в дневник", "Запис у щоденник")) { go(Edit.IncidentE(null)) },
        Triple(Icons.Filled.ReportProblem, tr("Поломка в список", "Поломка до списку")) { go(Edit.BreakdownE(null)) },
        Triple(Icons.Filled.PhotoCamera, tr("Фото в журнал", "Фото в журнал")) { close(); ui.push(Route.Photos) },
    )
    AlertDialog(
        onDismissRequest = close,
        title = { Text(tr("Добавить", "Додати")) },
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
        dismissButton = { TextButton(onClick = close) { Text(tr("Закрыть", "Закрити")) } },
    )
}

/** Ввод пробега: в поле — текущее значение, можно дописать выражение (+320) или открыть калькулятор. */
@Composable
fun OdoDialog(close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var text by remember { mutableStateOf(s.currentKm.toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var auto by remember { mutableStateOf(true) }
    val km = parseKm(text)
    val less = km != null && km < s.currentKm
    FormDialog(
        title = tr("Текущий пробег", "Поточний пробіг"),
        onDismiss = close,
        dirty = changed(text, date),
        confirmEnabled = km != null,
        onConfirm = {
            ui.repo.addOdo(km!!, date.toEpochDay(), OdoSource.MANUAL)
            ui.toast(tr("Пробег ${Fmt.km(km)} сохранён", "Пробіг ${Fmt.km(km)} збережено"))
            close()
        },
    ) {
        NumField(
            text, { text = it; auto = false }, tr("Пробег", "Пробіг"), suffix = "км", decimal = false,
            hint = if (less) tr("Меньше последнего (${Fmt.km(s.currentKm)}) — проверьте", "Менше за останній (${Fmt.km(s.currentKm)}) — перевірте") else tr("Было: ${Fmt.km(s.currentKm)}", "Було: ${Fmt.km(s.currentKm)}") +
                (s.lastOdoDate?.let { tr(" от ${Fmt.day(it)}", " від ${Fmt.day(it)}") } ?: ""),
        )
        DateField(date, { date = it; if (auto) text = s.kmAt(it.toEpochDay()).toString() })
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
    // Пробег подставляется по дате, пока его не ввели вручную
    var kmAuto by remember { mutableStateOf(e?.km == null) }
    var itemId by remember { mutableStateOf(e?.itemId ?: req.item?.id) }
    var title by remember { mutableStateOf(e?.title ?: "") }
    var amount by remember { mutableStateOf(e?.amount?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var parts by remember { mutableStateOf(e?.parts?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var liters by remember { mutableStateOf(e?.liters?.takeIf { it != 0.0 }?.let(Fmt::edit) ?: "") }
    var full by remember { mutableStateOf(e?.full ?: true) }
    var note by remember { mutableStateOf(e?.note ?: "") }
    val partRefs = remember { mutableStateListOf<PartRef>().apply { addAll(e?.partRefs.orEmpty().ifEmpty { listOf(PartRef()) }) } }
    var confirmDelete by remember { mutableStateOf(false) }
    val drafts = rememberDraftPhotos()
    val dismiss = { dropDrafts(ui, drafts); close() }
    val hasParts = type == LogType.SERVICE || type == LogType.WORK || type == LogType.PART

    val kmVal = if (km.isBlank()) null else parseKm(km)
    val amountVal = if (amount.isBlank()) 0.0 else parseNum(amount)
    val partsVal = if (parts.isBlank()) 0.0 else parseNum(parts)
    val litersVal = if (liters.isBlank()) 0.0 else parseNum(liters)
    val valid = (km.isBlank() || kmVal != null) && amountVal != null && partsVal != null && litersVal != null &&
        (type != LogType.SERVICE || itemId != null || title.isNotBlank()) && (type != LogType.WORK || title.isNotBlank())

    val kind = s.kind
    FormDialog(
        title = (if (e == null) tr("Новая запись: ", "Новий запис: ") else "") + logLabel(type, kind),
        onDismiss = dismiss,
        dirty = changed(type, date, km, itemId, title, amount, parts, liters, full, note, PartRef.encode(partRefs)) || drafts.isNotEmpty(),
        confirmEnabled = valid,
        onConfirm = {
            val (brand, partNo) = if (hasParts) PartRef.encode(partRefs) else "" to ""
            val entry = LogEntry(
                id = e?.id ?: 0, type = type, date = date.toEpochDay(), km = kmVal,
                itemId = if (type == LogType.SERVICE || type == LogType.PART) itemId else null,
                title = title.trim(), amount = amountVal!!, parts = if (type == LogType.SERVICE || type == LogType.WORK) partsVal!! else 0.0,
                liters = if (type == LogType.FUEL) litersVal!! else 0.0, full = full, note = note.trim(),
                brand = brand, partNo = partNo,
            )
            val files = drafts.toList()
            ui.repo.saveLog(entry) { id -> saveDrafts(ui, files, PhotoOwner.LOG, id, kmVal) }
            close()
        },
        extra = if (e != null) { { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } } } else null,
    ) {
        if (e == null) Segmented(
            listOfNotNull(
                if (kind.energy == Energy.NONE) null else LogType.FUEL to (if (kind.energy == Energy.ELECTRIC) tr("Заряд", "Заряд") else tr("Топл.", "Пальне")),
                LogType.SERVICE to tr("ТО", "ТО"), LogType.WORK to tr("Раб.", "Роб."), LogType.PART to tr("Зап.", "Зап."), LogType.OTHER to tr("Проч.", "Ін."),
            ),
            type, { type = it },
        )
        DateField(date, { date = it; if (kmAuto) km = s.kmAt(it.toEpochDay()).toString() })
        NumField(
            km, { km = it; kmAuto = false }, tr("Пробег", "Пробіг"), suffix = "км", decimal = false,
            hint = if (kmAuto && date != LocalDate.now()) tr("Подставлен по показаниям на ${Fmt.day(date)}", "Підставлено за показниками на ${Fmt.day(date)}") else null,
        )
        if (type == LogType.SERVICE || type == LogType.PART) {
            Picker(
                if (type == LogType.SERVICE) tr("Пункт ТО", "Пункт ТО") else tr("Для пункта ТО (необязательно)", "Для пункту ТО (необов'язково)"),
                s.item(itemId)?.name ?: tr("— другое —", "— інше —"),
                listOf<Pair<Long?, String>>(null to tr("— другое —", "— інше —")) + s.items.map { it.id to it.name },
            ) { id ->
                itemId = id
                // Подсказка из прошлой замены этого пункта
                if (partRefs.all { it.isBlank }) s.log.firstOrNull { it.itemId == id && it.id != e?.id && it.partInfo.isNotBlank() }?.let {
                    partRefs.clear(); partRefs.addAll(it.partRefs)
                }
            }
            if (type == LogType.SERVICE && itemId != null) Text(
                tr("Запись обновит «последнее обслуживание» пункта и восстановит его HP.", "Запис оновить «останнє обслуговування» пункту й відновить його HP."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(when (type) { LogType.FUEL -> if (kind.energy == Energy.ELECTRIC) tr("Где заряжали (дом, станция)", "Де заряджали (дім, станція)") else tr("АЗС / марка топлива", "АЗС / марка пального"); LogType.OTHER -> tr("Что (мойка, страховка, штраф…)", "Що (мийка, страховка, штраф…)"); LogType.WORK -> tr("Что делали (проводка, кузов, подвеска…)", "Що робили (проводка, кузов, підвіска…)"); else -> tr("Описание работ / деталей", "Опис робіт / деталей") }) },
        )
        when (type) {
            LogType.FUEL -> {
                val ev = kind.energy == Energy.ELECTRIC
                NumField(liters, { liters = it }, if (ev) tr("Энергия", "Енергія") else tr("Литры", "Літри"), suffix = kind.unit)
                NumField(
                    amount, { amount = it }, tr("Сумма", "Сума"), suffix = Fmt.currency,
                    hint = if ((litersVal ?: 0.0) > 0 && (amountVal ?: 0.0) > 0) "${Fmt.money2(amountVal!! / litersVal!!)}/${kind.unit}" else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { full = !full }) {
                    Checkbox(full, { full = it }); Text(if (ev) tr("До 100% (для расчёта расхода)", "До 100% (для розрахунку витрати)") else tr("Полный бак (для расчёта расхода)", "Повний бак (для розрахунку витрати)"))
                }
            }
            LogType.SERVICE, LogType.WORK -> {
                NumField(amount, { amount = it }, tr("Работа", "Робота"), suffix = Fmt.currency)
                NumField(parts, { parts = it }, tr("Запчасти и материалы", "Запчастини й матеріали"), suffix = Fmt.currency)
            }
            else -> NumField(amount, { amount = it }, tr("Сумма", "Сума"), suffix = Fmt.currency)
        }
        if (hasParts) {
            partRefs.forEachIndexed { i, ref ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ref.brand, { partRefs[i] = ref.copy(brand = it) }, label = { Text(tr("Бренд", "Бренд")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(ref.partNo, { partRefs[i] = ref.copy(partNo = it.uppercase()) }, label = { Text(tr("Артикул", "Артикул")) }, singleLine = true, modifier = Modifier.weight(1f))
                    if (partRefs.size > 1) IconButton(onClick = { partRefs.removeAt(i) }) { Icon(Icons.Filled.Close, tr("Убрать деталь", "Прибрати деталь")) }
                }
            }
            TextButton(onClick = { partRefs += PartRef() }) {
                Icon(Icons.Filled.Add, null); Text(tr("Добавить деталь", "Додати деталь"))
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text(tr("Заметка", "Нотатка")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        PhotoStrip(
            existing = e?.let { s.photosOf(PhotoOwner.LOG, it.id).map { p -> p.file } }.orEmpty(),
            added = drafts, onAdd = { drafts += it }, onRemoveAdded = { drafts -= it; File(ui.repo.photoDir, it).delete() },
        )
    }
    if (confirmDelete && e != null) ConfirmDialog(tr("Удалить запись?", "Видалити запис?"), tr("Запись и её фото будут удалены.", "Запис і його фото буде видалено."), onDismiss = { confirmDelete = false }) {
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
    var kmAuto by remember { mutableStateOf(i?.km == null) }
    var title by remember { mutableStateOf(i?.title ?: "") }
    var note by remember { mutableStateOf(i?.note ?: "") }
    var severity by remember { mutableStateOf(i?.severity ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val drafts = rememberDraftPhotos()
    val dismiss = { dropDrafts(ui, drafts); close() }
    val kmVal = if (km.isBlank()) null else parseKm(km)

    FormDialog(
        title = if (i == null) tr("Запись в дневник", "Запис у щоденник") else tr("Дневник", "Щоденник"),
        onDismiss = dismiss,
        dirty = changed(date, minutes, km, title, note, severity) || drafts.isNotEmpty(),
        confirmEnabled = title.isNotBlank() && (km.isBlank() || kmVal != null),
        onConfirm = {
            val t = date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
            val files = drafts.toList()
            ui.repo.saveIncident(Incident(i?.id ?: 0, t, kmVal, title.trim(), note.trim(), severity)) { id ->
                saveDrafts(ui, files, PhotoOwner.INCIDENT, id, kmVal)
            }
            close()
        },
        extra = if (i != null) { { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } } } else null,
    ) {
        OutlinedTextField(title, { title = it }, label = { Text(tr("Что случилось", "Що сталося")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(date, { date = it; if (kmAuto) km = s.kmAt(it.toEpochDay()).toString() }, modifier = Modifier.weight(1.4f))
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).padding(top = 8.dp)) { Text(Fmt.hm(minutes)) }
        }
        NumField(km, { km = it; kmAuto = false }, tr("Пробег", "Пробіг"), suffix = "км", decimal = false)
        Segmented(listOf(0 to tr("Заметка", "Нотатка"), 1 to tr("Внимание", "Увага"), 2 to tr("Серьёзно", "Серйозно")), severity, { severity = it })
        OutlinedTextField(note, { note = it }, label = { Text(tr("Подробности: звук, условия, что делали", "Подробиці: звук, умови, що робили")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        PhotoStrip(
            existing = i?.let { s.photosOf(PhotoOwner.INCIDENT, it.id).map { p -> p.file } }.orEmpty(),
            added = drafts, onAdd = { drafts += it }, onRemoveAdded = { drafts -= it; File(ui.repo.photoDir, it).delete() },
        )
        if (i == null) TextButton(onClick = {
            ui.edit = null
            dropDrafts(ui, drafts)
            ui.edit = Edit.BreakdownE(Breakdown(title = title.trim(), note = note.trim(), priority = severity, createdDate = date.toEpochDay(), createdKm = kmVal))
        }, enabled = title.isNotBlank()) { Text(tr("Это поломка → в список поломок", "Це поломка → до списку поломок")) }
    }
    if (pickTime) TimePickDialog(minutes, { pickTime = false }) { minutes = it }
    if (confirmDelete && i != null) ConfirmDialog(tr("Удалить запись дневника?", "Видалити запис щоденника?"), i.title, onDismiss = { confirmDelete = false }) {
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
        title = if (existing == null) tr("Новая поломка", "Нова поломка") else tr("Поломка", "Поломка"),
        onDismiss = close,
        dirty = changed(title, note, priority),
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            ui.repo.saveBreakdown(
                (b ?: Breakdown(title = "", createdDate = LocalDate.now().toEpochDay(), createdKm = s.currentKm))
                    .copy(title = title.trim(), note = note.trim(), priority = priority),
            )
            close()
        },
        extra = if (existing != null) { { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } } } else null,
    ) {
        OutlinedTextField(title, { title = it }, label = { Text(tr("Что не так", "Що не так")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Segmented(listOf(0 to tr("Терпит", "Терпить"), 1 to tr("Скоро", "Скоро"), 2 to tr("Срочно", "Терміново")), priority, { priority = it })
        OutlinedTextField(note, { note = it }, label = { Text(tr("Заметка (запчасть, цена, СТО)", "Нотатка (запчастина, ціна, СТО)")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
    if (confirmDelete && existing != null) ConfirmDialog(tr("Удалить поломку?", "Видалити поломку?"), existing.title, onDismiss = { confirmDelete = false }) {
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
        title = tr("Фото", "Фото"),
        onDismiss = close,
        dirty = changed(caption, km),
        confirmEnabled = km.isBlank() || kmVal != null,
        onConfirm = { ui.repo.updatePhoto(p.copy(caption = caption.trim(), km = kmVal)); close() },
        extra = { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } },
    ) {
        PhotoThumb(p.file, 240.dp, Modifier.fillMaxWidth(), maxSide = 800) { view = true }
        Text(Fmt.dateTime(p.time), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(caption, { caption = it }, label = { Text(tr("Подпись", "Підпис")) }, modifier = Modifier.fillMaxWidth())
        NumField(km, { km = it }, tr("Пробег", "Пробіг"), suffix = "км", decimal = false)
    }
    if (view) PhotoViewer(p.file, caption, { view = false })
    if (confirmDelete) ConfirmDialog(tr("Удалить фото?", "Видалити фото?"), tr("Файл будет удалён.", "Файл буде видалено."), onDismiss = { confirmDelete = false }) {
        ui.repo.deletePhoto(p); close()
    }
}
