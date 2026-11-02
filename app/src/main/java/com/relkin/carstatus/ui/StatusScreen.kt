package com.relkin.carstatus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.Health
import com.relkin.carstatus.data.ItemHealth
import com.relkin.carstatus.data.Quests
import com.relkin.carstatus.data.Tips
import com.relkin.carstatus.data.Tires
import com.relkin.carstatus.data.Trigger
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr

@Composable
fun StatusScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val threshold by ui.prefs.hpThreshold.state.collectAsState()
    val h = remember(s) { Health.of(s, s.fallbackKmPerDay) }
    val dc = LocalData.current
    TabScaffold(
        tr("Статус", "Статус"),
        actions = { IconButton(onClick = { ui.push(Route.Items) }) { Icon(Icons.Filled.Tune, tr("Пункты ТО", "Пункти ТО")) } },
        fab = { FloatingActionButton(onClick = { ui.edit = Edit.AddMenu }) { Icon(Icons.Filled.Add, tr("Добавить", "Додати")) } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, null, tint = dc.hp(h.overall), modifier = Modifier.size(36.dp))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.kind.healthOf, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    h.overall?.let { "${(it * 100).toInt()} HP" } ?: tr("нет данных", "немає даних"),
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable { ui.edit = Edit.Odo }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Speed, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(Fmt.km(s.currentKm), style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "~${Fmt.num(h.kmPerDay.toLong().coerceAtLeast(0) * 30)} " + tr("км/мес", "км/міс") + if (h.kmPerDayMeasured) "" else tr(" (оценка)", " (оцінка)"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HpBar(h.overall, segments = 20, height = 14.dp)
                        val crit = h.items.filter { (it.percent ?: 101) <= threshold }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                crit.isNotEmpty() -> tr("Требует внимания: ", "Потребує уваги: ") + crit.joinToString { "${it.item.name} (${it.percent}%)" }
                                h.worst != null -> tr("Ближайшее: ${h.worst.item.name} — ${dueText(h.worst)}", "Найближче: ${h.worst.item.name} — ${dueText(h.worst)}")
                                else -> tr("Укажите, когда обслуживались узлы — и появится прогноз", "Вкажіть, коли обслуговувалися вузли — і з'явиться прогноз")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Quests.active(s.kind).forEach { q ->
                val season = q.season()
                val done = q.steps.count { q.stepKey(season, it) in s.questDone }
                item(key = "quest_${q.key}") {
                    Card(Modifier.fillMaxWidth().clickable { ui.push(Route.Quests) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("${q.emoji} Сезонный квест: ${q.title}", "${q.emoji} Сезонний квест: ${q.title}"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { done.toFloat() / q.steps.size }, modifier = Modifier.fillMaxWidth().height(8.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(if (done == q.steps.size) tr("Выполнено! Всё готово к сезону", "Виконано! Усе готово до сезону") else "$done " + tr("из", "з") + " ${q.steps.size} · " + tr("дальше: ", "далі: ") + q.steps.first { q.stepKey(season, it) !in s.questDone }.title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Tips.active(s.kind).firstOrNull()?.let { t ->
                item(key = "tip") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("💡 ${t.title}", style = MaterialTheme.typography.titleSmall)
                            Text(t.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // шины: не по сезону или стоящий комплект изношен/стар
            val tireWarns = if (!Tires.supported(s.kind)) emptyList() else
                listOfNotNull(Tires.seasonWarn(s)) + (Tires.mounted(s)?.let { Tires.warnings(it) }.orEmpty())
            if (tireWarns.isNotEmpty()) item(key = "tires") {
                Card(Modifier.fillMaxWidth().clickable { ui.push(Route.Tires) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TireRepair, null)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(Tires.mounted(s)?.let { tr("Шины: ", "Шини: ") + it.label } ?: tr("Шины", "Шини"), style = MaterialTheme.typography.titleSmall)
                            tireWarns.forEach { Text(it.text.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall, color = if (it.serious) dc.critical else MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    }
                }
            }
            val open = s.breakdowns.filter { !it.done }
            if (open.isNotEmpty()) item(key = "br") {
                Card(Modifier.fillMaxWidth().clickable { ui.push(Route.Breakdowns) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ReportProblem, null)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(tr("Поломки: ${open.size}", "Поломки: ${open.size}"), style = MaterialTheme.typography.titleSmall)
                            Text(open.take(3).joinToString { it.title }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SectionTitle(tr("Узлы и расходники", "Вузли й витратні матеріали"), Modifier.padding(start = 0.dp)) }
            items(h.items, key = { it.item.id }) { ih -> ItemCard(ih) { ui.push(Route.ItemDetail(ih.item.id)) } }
        }
    }
}

fun dueText(ih: ItemHealth): String {
    val parts = buildList {
        ih.kmLeft?.let { add(if (it >= 0) Fmt.km(it) else tr("перепробег ${Fmt.km(-it)}", "перепробіг ${Fmt.km(-it)}")) }
        ih.daysLeft?.let { add(if (it >= 0) Fmt.days(it) else tr("просрочено на ${Fmt.days(-it)}", "прострочено на ${Fmt.days(-it)}")) }
    }
    val due = ih.due?.let { " → ~${Fmt.day(it)} (" + (if (ih.trigger == Trigger.KM) tr("по пробегу", "за пробігом") else tr("по времени", "за часом")) + ")" } ?: ""
    return (if (parts.isEmpty()) tr("нет данных", "немає даних") else tr("осталось ", "залишилось ") + parts.joinToString(tr(" или ", " або "))) + due
}

@Composable
fun ItemCard(ih: ItemHealth, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(itemIcon(ih.item.icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(ih.item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    ih.percent?.let { if (ih.overdue) "0% !" else "$it%" } ?: "—",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            HpBar(ih.hp)
            Spacer(Modifier.height(6.dp))
            Text(dueText(ih), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
