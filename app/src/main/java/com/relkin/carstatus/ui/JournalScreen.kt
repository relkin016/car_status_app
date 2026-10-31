package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.LogType
import com.relkin.carstatus.data.Odo
import com.relkin.carstatus.data.OdoSource
import com.relkin.carstatus.data.Photo
import com.relkin.carstatus.data.PhotoOwner
import com.relkin.carstatus.data.Reports
import com.relkin.carstatus.data.Snapshot
import com.relkin.carstatus.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class JFilter(val label: String) { ALL("Все"), SERVICE("ТО"), FUEL("Заправки"), COST("Расходы"), DIARY("Дневник"), ODO("Пробег") }

private data class JRow(
    val key: String,
    val sort: Long,
    val date: LocalDate,
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val amount: Double?,
    val photos: List<Photo>,
    val filter: JFilter,
    val open: (Ui) -> Unit,
)

private fun rows(s: Snapshot): List<JRow> {
    val zone = ZoneId.systemDefault()
    val out = ArrayList<JRow>()
    for (e in s.log) {
        val (icon, f) = when (e.type) {
            LogType.FUEL -> Icons.Filled.LocalGasStation to JFilter.FUEL
            LogType.SERVICE -> Icons.Filled.Build to JFilter.SERVICE
            LogType.PART -> Icons.Filled.Inventory2 to JFilter.COST
            else -> Icons.Filled.ReceiptLong to JFilter.COST
        }
        val sub = listOfNotNull(e.km?.let { Fmt.km(it) }, e.note.takeIf { it.isNotBlank() }).joinToString(" · ")
        out += JRow("l${e.id}", e.date * 86_400_000L + 43_200_000L, LocalDate.ofEpochDay(e.date), icon, Reports.entryTitle(s, e), sub, e.total, s.photosOf(PhotoOwner.LOG, e.id), f) {
            it.edit = Edit.Log(e, e.type)
        }
    }
    for (i in s.incidents) {
        val d = Instant.ofEpochMilli(i.time).atZone(zone).toLocalDate()
        val sev = when (i.severity) { 2 -> "‼️ "; 1 -> "⚠️ "; else -> "" }
        val sub = listOfNotNull(Fmt.dateTime(i.time).substringAfter(' '), i.km?.let { Fmt.km(it) }, i.note.takeIf { it.isNotBlank() }).joinToString(" · ")
        out += JRow("i${i.id}", i.time, d, Icons.Filled.CarCrash, sev + i.title, sub, null, s.photosOf(PhotoOwner.INCIDENT, i.id), JFilter.DIARY) { it.edit = Edit.IncidentE(i) }
    }
    for (p in s.photos.filter { it.ownerType == PhotoOwner.NONE }) {
        val d = Instant.ofEpochMilli(p.time).atZone(zone).toLocalDate()
        out += JRow("p${p.id}", p.time, d, Icons.Filled.PhotoCamera, p.caption.ifBlank { "Фото" }, p.km?.let { Fmt.km(it) } ?: "", null, listOf(p), JFilter.DIARY) { it.edit = Edit.PhotoE(p) }
    }
    for (o in s.odo) {
        out += JRow("o${o.id}", o.date * 86_400_000L, LocalDate.ofEpochDay(o.date), Icons.Filled.Speed, "Пробег ${Fmt.km(o.km)}", OdoSource.label(o.source), null, emptyList(), JFilter.ODO) {
            it.push(Route.Odo)
        }
    }
    return out.sortedByDescending { it.sort }
}

@Composable
fun JournalScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var filter by rememberSaveable { mutableStateOf(JFilter.ALL) }
    val all = remember(s) { rows(s) }
    val list = all.filter { if (filter == JFilter.ALL) it.filter != JFilter.ODO else it.filter == filter }
    var view by remember { mutableStateOf<Photo?>(null) }
    TabScaffold(
        "Журнал",
        actions = { IconButton(onClick = { ui.push(Route.Photos) }) { Icon(Icons.Filled.PhotoLibrary, "Фото-журнал") } },
        fab = { FloatingActionButton(onClick = { ui.edit = Edit.AddMenu }) { Icon(Icons.Filled.Add, "Добавить") } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f.label) }) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                if (list.isEmpty()) item { Empty("Здесь появятся заправки, обслуживание, расходы, записи дневника и фото") }
                var month: LocalDate? = null
                for (r in list) {
                    val m = r.date.withDayOfMonth(1)
                    if (m != month) {
                        month = m
                        val total = list.filter { it.date.withDayOfMonth(1) == m }.sumOf { it.amount ?: 0.0 }
                        item(key = "m$m") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                                Text(Fmt.month(m), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                if (total > 0) Text(Fmt.money(total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item(key = r.key) {
                        Column(Modifier.clickable { r.open(ui) }) {
                            ListItem(
                                leadingContent = { Icon(r.icon, null) },
                                headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(Fmt.dayShort(r.date) + if (r.sub.isNotBlank()) " · ${r.sub}" else "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                trailingContent = { r.amount?.takeIf { it > 0 }?.let { Text("−" + Fmt.money(it)) } },
                            )
                            if (r.photos.isNotEmpty()) Row(
                                Modifier.padding(start = 56.dp, bottom = 8.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) { r.photos.forEach { p -> PhotoThumb(p.file, 64.dp, maxSide = 200) { view = p } } }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    view?.let { PhotoViewer(it.file, it.caption, { view = null }) }
}

/** Фото-журнал: все снимки сеткой; новые — с датой и текущим пробегом. */
@Composable
fun PhotosScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val adder = rememberPhotoAdder { file ->
        ui.repo.addPhoto(Photo(time = System.currentTimeMillis(), km = ui.repo.s.currentKm, file = file))
    }
    SubScreen(
        "Фото-журнал", ui::pop,
        actions = {
            IconButton(onClick = adder.gallery) { Icon(Icons.Filled.AddPhotoAlternate, "Из галереи") }
        },
        fab = { FloatingActionButton(onClick = adder.camera) { Icon(Icons.Filled.PhotoCamera, "Снять") } },
    ) { pad ->
        if (s.photos.isEmpty()) Column(Modifier.padding(pad)) { Empty("Снимайте одометр, повреждения, чеки, состояние шин — у каждого фото сохранятся дата и пробег") }
        else LazyVerticalGrid(
            GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(s.photos, key = { it.id }) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PhotoThumb(p.file, 110.dp) { ui.edit = Edit.PhotoE(p) }
                    Text(
                        Fmt.day(Instant.ofEpochMilli(p.time).atZone(ZoneId.systemDefault()).toLocalDate()) + (p.km?.let { "\n" + Fmt.km(it) } ?: ""),
                        style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    )
                    if (p.caption.isNotBlank()) Text(p.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** История показаний одометра. */
@Composable
fun OdoScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    var del by remember { mutableStateOf<Odo?>(null) }
    SubScreen("Показания одометра", ui::pop, fab = { FloatingActionButton(onClick = { ui.edit = Edit.Odo }) { Icon(Icons.Filled.Add, "Добавить") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(s.odo.reversed(), key = { it.id }) { o ->
                ListItem(
                    headlineContent = { Text(Fmt.km(o.km)) },
                    supportingContent = { Text(Fmt.day(o.date) + " · " + OdoSource.label(o.source)) },
                    trailingContent = { IconButton(onClick = { del = o }) { Icon(Icons.Filled.Delete, "Удалить") } },
                )
                HorizontalDivider()
            }
        }
    }
    del?.let { o -> ConfirmDialog("Удалить показание?", Fmt.km(o.km) + " от " + Fmt.day(o.date), onDismiss = { del = null }) { ui.repo.deleteOdo(o.id) } }
}
