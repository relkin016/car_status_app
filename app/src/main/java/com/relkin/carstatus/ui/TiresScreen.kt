package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.TireMount
import com.relkin.carstatus.data.TireSeason
import com.relkin.carstatus.data.TireSet
import com.relkin.carstatus.data.Tires
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import java.time.LocalDate

/** Что открыто поверх экрана шин. */
private sealed interface TireEdit {
    data class SetE(val set: TireSet?) : TireEdit
    /** Поставить комплект ([set] = null — снять стоящий). */
    data class MountE(val set: TireSet?) : TireEdit
    data class MountEditE(val mount: TireMount) : TireEdit
}

@Composable
fun TiresScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var edit by remember { mutableStateOf<TireEdit?>(null) }
    val mounted = Tires.mounted(s)
    val active = s.tireSets.filter { !it.retired }
    val retired = s.tireSets.filter { it.retired }
    SubScreen(tr("Шины", "Шини"), ui::pop, fab = { FloatingActionButton(onClick = { edit = TireEdit.SetE(null) }) { Icon(Icons.Filled.Add, tr("Добавить комплект", "Додати комплект")) } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.tireSets.isEmpty()) item {
                Empty(tr(
                    "Добавьте комплекты — летний, зимний. Приложение посчитает пробег каждого, напомнит про износ протектора, возраст резины и переобувку по сезону.",
                    "Додайте комплекти — літній, зимовий. Застосунок порахує пробіг кожного, нагадає про знос протектора, вік гуми й перевзування за сезоном.",
                ))
            } else item { MountedCard(mounted, onSwap = { edit = TireEdit.MountE(active.firstOrNull { it.id != mounted?.id }) }, onRemove = { edit = TireEdit.MountE(null) }) }
            items(active, key = { it.id }) { set -> SetCard(set, set.id == mounted?.id, onMount = { edit = TireEdit.MountE(set) }) { edit = TireEdit.SetE(set) } }
            if (retired.isNotEmpty()) item { SectionTitle(tr("Списаны", "Списані"), Modifier.padding(start = 0.dp)) }
            items(retired, key = { it.id }) { set -> SetCard(set, false, onMount = null) { edit = TireEdit.SetE(set) } }
        }
    }
    when (val e = edit) {
        null -> {}
        is TireEdit.SetE -> TireSetDialog(e.set, { edit = null }) { edit = TireEdit.MountEditE(it) }
        is TireEdit.MountE -> MountDialog(e.set) { edit = null }
        is TireEdit.MountEditE -> MountEditDialog(e.mount) { edit = null }
    }
}

@Composable
private fun MountedCard(set: TireSet?, onSwap: () -> Unit, onRemove: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val dc = LocalData.current
    val m = Tires.openMount(s)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("Сейчас стоят", "Зараз стоять"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(set?.label ?: tr("не указано", "не вказано"), style = MaterialTheme.typography.titleLarge)
            if (m != null) Text(
                tr("с ${Fmt.day(m.dateOn)} · проехали ${Fmt.km((s.currentKm - m.kmOn).coerceAtLeast(0))}", "з ${Fmt.day(m.dateOn)} · проїхали ${Fmt.km((s.currentKm - m.kmOn).coerceAtLeast(0))}"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Tires.seasonWarn(s)?.let { Text(it.text, color = if (it.serious) dc.critical else dc.warning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSwap) { Text(if (set == null) tr("Поставить", "Поставити") else tr("Переобуть", "Перевзути")) }
                if (set != null) TextButton(onClick = onRemove) { Text(tr("Снять", "Зняти")) }
            }
        }
    }
}

@Composable
private fun SetCard(set: TireSet, mounted: Boolean, onMount: (() -> Unit)?, onClick: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val dc = LocalData.current
    val warns = Tires.warnings(set)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (mounted) CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(set.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (mounted) Text(tr("на колёсах", "на колесах"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            val info = listOfNotNull(
                TireSeason.label(set.season),
                set.size.ifBlank { null },
                listOf(set.brand, set.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }?.takeIf { it != set.title },
            ).joinToString(" · ")
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue(tr("Пробег", "Пробіг"), Fmt.km(Tires.km(s, set)))
                if (set.tread > 0) LabeledValue(tr("Протектор", "Протектор"), "${Fmt.dec(set.tread)} мм")
                Tires.ageYears(set)?.let { LabeledValue(tr("Возраст", "Вік"), "$it " + tr(Fmt.plural(it, "год", "года", "лет"), Fmt.plural(it, "рік", "роки", "років"))) }
            }
            if (set.storage.isNotBlank() && !mounted) Text(tr("Хранение: ", "Зберігання: ") + set.storage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            warns.forEach { Text("⚠ " + it.text, color = if (it.serious) dc.critical else dc.warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            if (onMount != null && !mounted) TextButton(onClick = onMount, contentPadding = PaddingValues(0.dp)) { Text(tr("Поставить на колёса", "Поставити на колеса")) }
        }
    }
}

@Composable
private fun TireSetDialog(t: TireSet?, close: () -> Unit, onEditMount: (TireMount) -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var set by remember { mutableStateOf(t ?: TireSet(name = "", season = if (LocalDate.now().monthValue in 4..9) TireSeason.SUMMER else TireSeason.WINTER)) }
    var price by remember { mutableStateOf(t?.price?.takeIf { it > 0 }?.let(Fmt::edit) ?: "") }
    var kmBefore by remember { mutableStateOf(t?.kmBefore?.takeIf { it > 0 }?.toString() ?: "") }
    var tread by remember { mutableStateOf(t?.tread?.takeIf { it > 0 }?.let(Fmt::edit) ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val priceV = if (price.isBlank()) 0.0 else parseNum(price)?.takeIf { it >= 0 }
    val kmV = if (kmBefore.isBlank()) 0L else parseKm(kmBefore)
    val treadV = if (tread.isBlank()) 0.0 else parseNum(tread)?.takeIf { it in 0.0..30.0 }
    val dotOk = set.dot.isBlank() || Tires.made(set.dot) != null
    FormDialog(
        title = if (t == null) tr("Новый комплект", "Новий комплект") else tr("Комплект шин", "Комплект шин"),
        onDismiss = close,
        dirty = changed(set, price, kmBefore, tread),
        confirmEnabled = priceV != null && kmV != null && treadV != null,
        onConfirm = {
            val treadDate = if (treadV != (t?.tread ?: 0.0)) LocalDate.now().toEpochDay() else t?.treadDate
            ui.repo.saveTireSet(set.copy(name = set.name.trim(), price = priceV!!, kmBefore = kmV!!, tread = treadV!!, treadDate = treadDate))
            close()
        },
        extra = if (t != null) { { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } } } else null,
    ) {
        @Composable
        fun F(label: String, v: String, hint: String? = null, error: Boolean = false, onChange: (String) -> Unit) = OutlinedTextField(
            v, onChange, label = { Text(label) }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth(),
            supportingText = hint?.let { { Text(it) } },
        )
        Segmented(TireSeason.all.map { it to TireSeason.emoji(it) + " " + TireSeason.label(it) }, set.season, { set = set.copy(season = it) })
        F(tr("Название (необязательно)", "Назва (необов'язково)"), set.name) { set = set.copy(name = it) }
        F(tr("Производитель", "Виробник"), set.brand) { set = set.copy(brand = it) }
        F(tr("Модель", "Модель"), set.model) { set = set.copy(model = it) }
        F(tr("Размер", "Розмір"), set.size, tr("Например, 205/55 R16 91H", "Наприклад, 205/55 R16 91H")) { set = set.copy(size = it.uppercase()) }
        F(
            "DOT", set.dot,
            Tires.made(set.dot)?.let { tr("Выпущены: ", "Випущені: ") + Fmt.month(it) }
                ?: tr("4 цифры на боковине: неделя и год, например 2321", "4 цифри на боковині: тиждень і рік, наприклад 2321"),
            error = !dotOk,
        ) { v -> set = set.copy(dot = v.filter { it.isDigit() }.take(4)) }
        NumField(tread, { tread = it }, tr("Протектор", "Протектор"), suffix = "мм", hint = tr("Последний замер; минимум ${Fmt.dec(Tires.minTread(set.season))} мм", "Останній замір; мінімум ${Fmt.dec(Tires.minTread(set.season))} мм") + (t?.treadDate?.let { " · " + Fmt.day(it) } ?: ""))
        NumField(kmBefore, { kmBefore = it }, tr("Пробег до учёта", "Пробіг до обліку"), suffix = "км", decimal = false, hint = tr("Сколько комплект проехал до того, как вы начали вести его здесь", "Скільки комплект проїхав до того, як ви почали вести його тут"))
        NumField(price, { price = it }, tr("Цена комплекта", "Ціна комплекту"), suffix = Fmt.currency)
        if (set.purchaseDate != null) DateField(LocalDate.ofEpochDay(set.purchaseDate!!), { set = set.copy(purchaseDate = it.toEpochDay()) }, tr("Дата покупки", "Дата купівлі"))
        else OutlinedButton(onClick = { set = set.copy(purchaseDate = LocalDate.now().toEpochDay()) }) { Text(tr("Указать дату покупки", "Вказати дату купівлі")) }
        F(tr("Где хранятся", "Де зберігаються"), set.storage, tr("Гараж, шинный отель, балкон…", "Гараж, шинний готель, балкон…")) { set = set.copy(storage = it) }
        OutlinedTextField(set.note, { set = set.copy(note = it) }, label = { Text(tr("Заметка", "Нотатка")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        if (t != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { set = set.copy(retired = !set.retired) }) {
            Checkbox(set.retired, { set = set.copy(retired = it) }); Text(tr("Списан (изношен, продан)", "Списаний (зношений, проданий)"))
        }
        val history = t?.let { Tires.mountsOf(s, it.id) }.orEmpty()
        if (history.isNotEmpty()) {
            Text(tr("Когда стояли", "Коли стояли"), style = MaterialTheme.typography.labelLarge)
            history.forEach { m ->
                ListItem(
                    headlineContent = { Text(Fmt.day(m.dateOn) + " — " + (m.dateOff?.let(Fmt::day) ?: tr("сейчас", "зараз"))) },
                    supportingContent = { Text(Fmt.km(m.kmOn) + " → " + (m.kmOff?.let(Fmt::km) ?: Fmt.km(s.currentKm)) + " · " + Fmt.km(((m.kmOff ?: s.currentKm) - m.kmOn).coerceAtLeast(0))) },
                    modifier = Modifier.clickable { onEditMount(m) },
                )
            }
        }
    }
    if (confirmDelete && t != null) ConfirmDialog(tr("Удалить комплект?", "Видалити комплект?"), tr("История установок тоже удалится. Если шины просто изношены — лучше отметить «Списан».", "Історія встановлень теж видалиться. Якщо шини просто зношені — краще позначити «Списаний»."), onDismiss = { confirmDelete = false }) {
        ui.repo.deleteTireSet(t.id); close()
    }
}

/** Переобувка: снять стоящий комплект и поставить [target] (null — только снять). */
@Composable
private fun MountDialog(target: TireSet?, close: () -> Unit) {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val current = Tires.mounted(s)
    val choices = s.tireSets.filter { !it.retired && it.id != current?.id }
    var pick by remember { mutableStateOf(target?.takeIf { it.id != current?.id }) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var km by remember { mutableStateOf(s.currentKm.toString()) }
    var cost by remember { mutableStateOf("") }
    val unmountOnly = target == null && current != null
    val kmV = parseKm(km)
    val costV = if (cost.isBlank()) 0.0 else parseNum(cost)?.takeIf { it >= 0 }
    val minKm = Tires.openMount(s)?.kmOn ?: 0
    FormDialog(
        title = if (unmountOnly) tr("Снять шины", "Зняти шини") else tr("Переобуть", "Перевзути"),
        onDismiss = close,
        dirty = changed(pick, date, km, cost),
        confirmEnabled = kmV != null && costV != null && (unmountOnly || pick != null),
        onConfirm = {
            val set = if (unmountOnly) null else pick
            val title = tr("Шиномонтаж", "Шиномонтаж") + (set?.let { ": ${it.title}" } ?: "")
            ui.repo.mountTires(set?.id, date.toEpochDay(), kmV!!, costV!!, title)
            ui.toast(if (set == null) tr("Шины сняты", "Шини знято") else tr("Стоят: ${set.title}", "Стоять: ${set.title}"))
            close()
        },
    ) {
        if (current != null) Text(tr("Снимаются: ", "Знімаються: ") + current.label)
        if (!unmountOnly) {
            if (choices.isEmpty()) Text(tr("Нет других комплектов — сначала добавьте его.", "Немає інших комплектів — спочатку додайте його."), color = MaterialTheme.colorScheme.error)
            else Picker(tr("Ставятся", "Ставляться"), pick?.label ?: tr("выберите", "виберіть"), choices.map { it to it.label }) { pick = it }
        }
        DateField(date, { date = it })
        NumField(km, { km = it }, tr("Пробег", "Пробіг"), suffix = "км", decimal = false, hint = if (kmV != null && kmV < minKm) tr("Меньше, чем при установке (${Fmt.km(minKm)})", "Менше, ніж під час встановлення (${Fmt.km(minKm)})") else null)
        NumField(cost, { cost = it }, tr("Стоимость шиномонтажа", "Вартість шиномонтажу"), suffix = Fmt.currency, hint = tr("Необязательно: попадёт в журнал как «Работы»", "Необов'язково: потрапить у журнал як «Роботи»"))
    }
}

/** Исправить период установки: даты и пробег. */
@Composable
private fun MountEditDialog(m: TireMount, close: () -> Unit) {
    val ui = LocalUi.current
    var dateOn by remember { mutableStateOf(LocalDate.ofEpochDay(m.dateOn)) }
    var kmOn by remember { mutableStateOf(m.kmOn.toString()) }
    var dateOff by remember { mutableStateOf(m.dateOff?.let(LocalDate::ofEpochDay)) }
    var kmOff by remember { mutableStateOf(m.kmOff?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val kmOnV = parseKm(kmOn)
    val kmOffV = if (m.dateOff == null) null else parseKm(kmOff)
    val ok = kmOnV != null && (m.dateOff == null || (kmOffV != null && kmOffV >= kmOnV && !dateOff!!.isBefore(dateOn)))
    FormDialog(
        title = tr("Период установки", "Період встановлення"),
        onDismiss = close,
        dirty = changed(dateOn, kmOn, dateOff, kmOff),
        confirmEnabled = ok,
        onConfirm = {
            ui.repo.saveTireMount(m.copy(dateOn = dateOn.toEpochDay(), kmOn = kmOnV!!, dateOff = dateOff?.toEpochDay(), kmOff = kmOffV))
            close()
        },
        extra = { TextButton(onClick = { confirmDelete = true }) { Text(tr("Удалить", "Видалити")) } },
    ) {
        DateField(dateOn, { dateOn = it }, tr("Поставлены", "Поставлені"))
        NumField(kmOn, { kmOn = it }, tr("Пробег при установке", "Пробіг під час встановлення"), suffix = "км", decimal = false)
        HorizontalDivider()
        if (dateOff != null) {
            DateField(dateOff!!, { dateOff = it }, tr("Сняты", "Зняті"))
            NumField(kmOff, { kmOff = it }, tr("Пробег при снятии", "Пробіг під час зняття"), suffix = "км", decimal = false)
        } else Text(tr("Стоят сейчас", "Стоять зараз"))
    }
    if (confirmDelete) ConfirmDialog(tr("Удалить период?", "Видалити період?"), tr("Пробег комплекта пересчитается.", "Пробіг комплекту перерахується."), onDismiss = { confirmDelete = false }) {
        ui.repo.deleteTireMount(m.id); close()
    }
}
