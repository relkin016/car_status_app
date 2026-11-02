package com.relkin.carstatus.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.relkin.carstatus.data.AutoBackup
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Автокопия по расписанию в выбранную папку. */
@Composable
fun AutoBackupScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val p = ui.prefs
    val days by p.backupDays.state.collectAsState()
    val dir by p.backupDir.state.collectAsState()
    val last by p.backupLast.state.collectAsState()
    val err by p.backupError.state.collectAsState()
    val mode by p.backupMode.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    // включить сразу после выбора папки, если её выбирали ради включения
    var enableAfterPick by remember { mutableStateOf(0) }

    fun backupNow() {
        busy = true
        ui.scope.launch {
            val r = withContext(Dispatchers.IO) { AutoBackup.run(ctx) }
            busy = false
            ui.toast(r.fold({ tr("Копия сохранена: $it", "Копію збережено: $it") }, { tr("Ошибка: ${it.message}", "Помилка: ${it.message}") }))
        }
    }

    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) { enableAfterPick = 0; return@rememberLauncherForActivityResult }
        runCatching { AutoBackup.setFolder(ctx, uri) }
            .onSuccess {
                if (enableAfterPick > 0) p.backupDays.set(enableAfterPick)
                enableAfterPick = 0
                // первая копия — сразу, чтобы проверить, что папка подходит
                if (p.backupDays.value > 0) backupNow()
            }
            .onFailure { ui.toast(tr("Не удалось получить доступ к папке: ${it.message}", "Не вдалося отримати доступ до теки: ${it.message}")) }
    }

    SubScreen(tr("Автокопия", "Автокопія"), ui::pop) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                tr(
                    "Резервная копия всех гаражей, записей и фото сама сохраняется в выбранную папку — во время ежедневной проверки (${Fmt.hm(p.checkTime.value)}) или при запуске приложения. Выберите папку, которая переживёт переустановку: «Документы», «Загрузки» или папку облака, синхронизируемую с телефоном.",
                    "Резервна копія всіх гаражів, записів і фото сама зберігається у вибрану теку — під час щоденної перевірки (${Fmt.hm(p.checkTime.value)}) або під час запуску застосунку. Виберіть теку, яка переживе перевстановлення: «Документи», «Завантаження» або теку хмари, що синхронізується з телефоном.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(tr("Как часто", "Як часто"))
            Segmented(
                listOf(0 to tr("Выкл", "Вимк"), 1 to tr("День", "День"), 7 to tr("Неделя", "Тиждень"), 30 to tr("Месяц", "Місяць")), days,
                { v ->
                    if (v > 0 && dir.isEmpty()) { enableAfterPick = v; pickDir.launch(null) }
                    else p.backupDays.set(v)
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Папка", "Тека"))
                    Text(
                        if (dir.isEmpty()) tr("не выбрана", "не вибрано") else AutoBackup.folderName(dir),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { pickDir.launch(null) }) { Text(if (dir.isEmpty()) tr("Выбрать", "Вибрати") else tr("Сменить", "Змінити")) }
            }
            PrefNum(p.backupKeep, tr("Хранить копий", "Зберігати копій"), "", 1..50, tr("Более старые автокопии удаляются. Ручные копии не трогаются.", "Старіші автокопії видаляються. Ручні копії не зачіпаються."))
            Text(
                tr("Последняя: ", "Остання: ") + (if (last > 0) Fmt.dateTime(last) else tr("ещё не было", "ще не було")),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (err.isNotEmpty()) Text(tr("Ошибка: ", "Помилка: ") + err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = ::backupNow, enabled = dir.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) tr("Сохраняю…", "Зберігаю…") else tr("Сделать копию сейчас", "Зробити копію зараз"))
            }
            Text(tr("Если копия не удалась", "Якщо копія не вдалася"))
            ModeSelector(mode, p.backupMode::set)
            if (days > 0 && !Reminders.canNotify(ctx)) Text(
                tr("Уведомления запрещены — об ошибке копии узнаете только здесь.", "Сповіщення заборонено — про помилку копії дізнаєтеся лише тут."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tr("Восстановить: Ещё → «Восстановить из копии» и выберите файл carstatus-auto-….zip.", "Відновити: Ще → «Відновити з копії» і виберіть файл carstatus-auto-….zip."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
