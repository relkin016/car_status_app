package com.relkin.carstatus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.CostCat
import com.relkin.carstatus.data.CostReport
import com.relkin.carstatus.data.Costs
import com.relkin.carstatus.util.Fmt
import java.time.LocalDate

enum class Period(val label: String, val days: Long?) { M1("Месяц", 30), M3("3 мес", 91), Y1("Год", 365), ALL("Всё время", null) }

fun Period.from(): Long? = days?.let { LocalDate.now().toEpochDay() - it + 1 }

/** Общий для экрана расходов и выписки выбранный период. */
object PeriodState { var period by mutableStateOf(Period.Y1) }

@Composable
fun CostScreen() {
    val ui = LocalUi.current
    val s by ui.repo.snap.collectAsState()
    val period = PeriodState.period
    val r = remember(s, period) { Costs.report(s, period.from()) }
    TabScaffold(
        "Цена километра",
        actions = { IconButton(onClick = { ui.push(Route.Report(ReportKind.STATEMENT)) }) { Icon(Icons.Filled.Description, "Выписка") } },
        fab = { FloatingActionButton(onClick = { ui.edit = Edit.AddMenu }) { Icon(Icons.Filled.Add, "Добавить") } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Period.entries.forEach { p -> FilterChip(period == p, { PeriodState.period = p }, label = { Text(p.label) }) }
                }
            }
            item { Hero(r) }
            item { Breakdown(r) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Пройдено", Fmt.km(r.km), Modifier.weight(1f))
                    Tile("Потрачено", Fmt.money(r.total), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Расход", r.consumption?.let { Fmt.dec(it) + " л/100" } ?: "—", Modifier.weight(1f))
                    Tile("Цена литра", r.pricePerLiter?.let { Fmt.money2(it) } ?: "—", Modifier.weight(1f))
                }
            }
            if (r.byMonth.size > 1) {
                item { Text("По месяцам", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                val max = r.byMonth.maxOf { it.second.values.sum() }.coerceAtLeast(1.0)
                r.byMonth.reversed().forEach { (m, v) ->
                    item(key = m.toString()) {
                        val total = v.values.sum()
                        Column {
                            Row {
                                Text(Fmt.month(m.atDay(1)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(Fmt.money(total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth((total / max).toFloat().coerceAtLeast(0.02f))) { Stacked(v, total, 10) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Цена километра = (топливо + ТО + запчасти + прочее) ÷ пройденные км за период. Пробег берётся из показаний одометра и записей.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Hero(r: CostReport) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Реальная цена 1 км", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                r.perKm?.let { Fmt.money2(it) } ?: "—",
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            )
            Text(
                if (r.km == 0L) "Нужно минимум два показания пробега за период" else "за ${Fmt.km(r.km)} · ${r.entries.size} операций",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            r.perKm?.let { Text("≈ ${Fmt.money(it * 100)} на 100 км", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Сегменты с зазором 2 dp, скруглённые края; категория всегда подписана в легенде. */
@Composable
private fun Stacked(v: Map<CostCat, Double>, total: Double, height: Int) {
    val dc = LocalData.current
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total <= 0) Box(Modifier.fillMaxSize().background(dc.track))
        else CostCat.entries.forEach { c ->
            val x = v[c] ?: 0.0
            if (x > 0) Box(Modifier.weight((x / total).toFloat()).fillMaxHeight().background(dc.cat(c)))
        }
    }
}

@Composable
private fun Breakdown(r: CostReport) {
    val dc = LocalData.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Stacked(r.byCat, r.total, 14)
            CostCat.entries.forEach { c ->
                val v = r.byCat[c] ?: 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(dc.cat(c), 12.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(c.label, Modifier.weight(1f))
                    Text(
                        (r.perKm(c)?.let { Fmt.money2(it) + "/км  " } ?: "") ,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Fmt.money(v), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (r.total > 0) " ${(v / r.total * 100).toInt()}%" else "",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
