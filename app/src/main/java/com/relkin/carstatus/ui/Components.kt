package com.relkin.carstatus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.notify.Mode
import com.relkin.carstatus.util.Calc
import com.relkin.carstatus.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToLong

fun itemIcon(key: String): ImageVector = when (key) {
    "oil" -> Icons.Filled.OilBarrel
    "air" -> Icons.Filled.Air
    "fuel" -> Icons.Filled.LocalGasStation
    "brake" -> Icons.Filled.DiscFull
    "drop" -> Icons.Filled.Opacity
    "spark" -> Icons.Filled.ElectricBolt
    "belt" -> Icons.Filled.SettingsInputComponent
    "battery" -> Icons.Filled.BatteryChargingFull
    "wiper" -> Icons.Filled.Water
    "tire" -> Icons.Filled.TireRepair
    "ac" -> Icons.Filled.AcUnit
    "doc" -> Icons.Filled.Description
    "light" -> Icons.Filled.WbIncandescent
    "engine" -> Icons.Filled.Settings
    "chain" -> Icons.Filled.Link
    "bolt" -> Icons.Filled.Hardware
    "plug" -> Icons.Filled.Power
    "cable" -> Icons.Filled.Cable
    else -> Icons.Filled.Build
}

/**
 * Шкала «жизней»: [segments] делений, заполненных по доле [value] (0..1).
 * Цвет — по статусу; рядом всегда выводится число, цвет не несёт смысла один.
 */
@Composable
fun HpBar(value: Double?, modifier: Modifier = Modifier, segments: Int = 10, height: Dp = 10.dp) {
    val dc = LocalData.current
    val v = (value ?: 0.0).coerceIn(0.0, 1.0)
    val filled = v * segments
    val color = dc.hp(value)
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(segments) { i ->
            val part = (filled - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(dc.track)) {
                if (part > 0f) Box(Modifier.fillMaxWidth(part).fillMaxSize().background(color))
            }
        }
    }
}

@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = actions,
            )
        },
        floatingActionButton = fab,
        content = content,
    )
}

/** Экран вкладки: нижние отступы уже даёт панель вкладок; под заголовком — текущий транспорт (нажать — сменить). */
@Composable
fun TabScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        VehicleTitle()
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = fab,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Числовое поле: принимает выражение (45000+320), под полем — результат; справа — калькулятор. */
@Composable
fun NumField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    suffix: String = "",
    decimal: Boolean = true,
    hint: String? = null,
) {
    var calc by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.replace(',', '.')) },
        label = { Text(label) },
        suffix = suffix.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        trailingIcon = { IconButton(onClick = { calc = true }) { Icon(Icons.Filled.Calculate, "Калькулятор") } },
        supportingText = if (Calc.isExpression(value)) {
            { Text("= " + (Calc.eval(value)?.let { fmtNum(it, decimal) } ?: "ошибка в выражении")) }
        } else hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
    if (calc) CalculatorDialog(value, decimal, { calc = false }, onChange)
}

private fun fmtNum(v: Double, decimal: Boolean) = if (decimal) Fmt.dec(v) else Fmt.num(v.roundToLong())

fun parseNum(s: String): Double? = Calc.eval(s)
fun parseKm(s: String): Long? = Calc.eval(s)?.roundToLong()?.takeIf { it >= 0 }

@Composable
fun CalculatorDialog(initial: String, decimal: Boolean, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    var expr by remember { mutableStateOf(initial.replace('*', '×').replace('/', '÷').replace('-', '−')) }
    val value = Calc.eval(expr)
    fun result(v: Double) = if (decimal) Fmt.edit(v) else v.roundToLong().toString()
    fun press(k: String) {
        expr = when (k) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "=" -> value?.let(::result) ?: expr
            "+", "−", "×", "÷" -> if (expr.lastOrNull()?.toString() in listOf("+", "−", "×", "÷")) expr.dropLast(1) + k else expr + k
            "." -> {
                val tail = expr.takeLastWhile { it.isDigit() || it == '.' }
                if ('.' in tail) expr else expr + (if (tail.isEmpty()) "0." else ".")
            }
            else -> expr + k
        }
    }
    val rows = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf(".", "0", "⌫", "="),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Калькулятор") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    expr.ifEmpty { "0" }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (expr.isEmpty()) " " else value?.let { "= " + fmtNum(it, decimal) } ?: "…",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(),
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            FilledTonalButton(
                                onClick = { press(k) },
                                modifier = Modifier.weight(1f).aspectRatio(1.3f),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Стереть")
                                else Text(k, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { onResult(result(value!!)); onDismiss() }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Поле даты: по нажатию — выбор в календаре. */
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, label: String = "Дата", modifier: Modifier = Modifier.fillMaxWidth()) {
    var pick by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = Fmt.day(date), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        // поверх поля — чтобы нажатие не уходило в текстовое поле
        Box(Modifier.matchParentSize().clickable { pick = true })
    }
    if (pick) DatePickDialog(date, { pick = false }, onChange)
}

@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) { DatePicker(state) }
}

@Composable
fun TimePickDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("ОК") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Удалить", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = v == selected, onClick = { onSelect(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun ModeSelector(mode: Int, onChange: (Int) -> Unit) =
    Segmented(Mode.labels.mapIndexed { i, s -> i to s }, mode, onChange)

/** Диалог-форма с прокруткой. */
/** true, если хоть одно значение отличается от того, что было при первом показе формы. */
@Composable
fun changed(vararg values: Any?): Boolean {
    val initial = remember { values.toList() }
    return values.toList() != initial
}

/**
 * Диалог-форма. При [dirty] (есть несохранённые изменения) закрытие тапом мимо, «Назад» или «Отмена»
 * сначала спрашивает подтверждение — чтобы случайно не потерять введённое.
 */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "Сохранить",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    dirty: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var askExit by remember { mutableStateOf(false) }
    val tryDismiss = { if (dirty) askExit = true else onDismiss() }
    AlertDialog(
        onDismissRequest = tryDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
        confirmButton = { TextButton(enabled = confirmEnabled, onClick = onConfirm) { Text(confirmText) } },
        dismissButton = {
            Row {
                extra?.invoke()
                TextButton(onClick = tryDismiss) { Text("Отмена") }
            }
        },
    )
    if (askExit) AlertDialog(
        onDismissRequest = { askExit = false },
        title = { Text("Выйти без сохранения?") },
        text = { Text("Введённые данные будут потеряны.") },
        confirmButton = { TextButton(onClick = { askExit = false; onDismiss() }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { askExit = false }) { Text("Продолжить ввод") } },
    )
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Dot(color: androidx.compose.ui.graphics.Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(3.dp)).background(color))
}

@Composable
fun HSpace(w: Dp) = Box(Modifier.width(w))
