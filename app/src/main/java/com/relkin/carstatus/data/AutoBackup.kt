package com.relkin.carstatus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.relkin.carstatus.CarApp
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.util.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Автокопия: та же резервная копия, что и «Экспорт базы», но по расписанию — в папку, выбранную пользователем
 * (доступ через SAF сохраняется между перезапусками). Старые автокопии сверх [com.relkin.carstatus.Prefs.backupKeep] удаляются;
 * ручные копии и чужие файлы в папке не трогаем — только имена с [PREFIX].
 */
object AutoBackup {
    const val PREFIX = "carstatus-auto-"
    /** Id уведомления об ошибке (общий диапазон, см. Reminders). */
    private const val N_BACKUP = 12
    private val nameFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    private val lock = Any()

    /** Пора делать копию: включено, папка выбрана, со дня последней копии прошло не меньше заданного числа дней. */
    fun due(ctx: Context): Boolean {
        val p = (ctx.applicationContext as CarApp).prefs
        val days = p.backupDays.value
        if (days <= 0 || p.backupDir.value.isEmpty()) return false
        val last = p.backupLast.value
        if (last == 0L) return true
        val lastDay = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
        return !LocalDate.now().isBefore(lastDay.plusDays(days.toLong()))
    }

    /** Сделать копию, если пора (из фонового потока). Ошибка — в настройки и уведомлением. */
    fun runIfDue(ctx: Context): Unit = synchronized(lock) {
        if (!due(ctx)) return
        run(ctx).onFailure { e ->
            val p = (ctx.applicationContext as CarApp).prefs
            Reminders.show(
                ctx, N_BACKUP, tr("Автокопия не сделана", "Автокопію не зроблено"),
                (e.message ?: e.javaClass.simpleName) + tr(". Проверьте папку: Ещё → Автокопия.", ". Перевірте теку: Ще → Автокопія."),
                p.backupMode.value,
            )
        }
    }

    /** Сделать копию сейчас. Возвращает имя файла. */
    fun run(ctx: Context): Result<String> = synchronized(lock) {
        val app = ctx.applicationContext as CarApp
        val p = app.prefs
        runCatching {
            val tree = Uri.parse(p.backupDir.value.ifEmpty { error(tr("Папка не выбрана", "Теку не вибрано")) })
            val cr = ctx.contentResolver
            if (cr.persistedUriPermissions.none { it.uri == tree && it.isWritePermission })
                error(tr("Нет доступа к папке — выберите её заново", "Немає доступу до теки — виберіть її знову"))
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = PREFIX + LocalDateTime.now().format(nameFmt) + ".zip"
            val file = DocumentsContract.createDocument(cr, dir, "application/zip", name)
                ?: error(tr("Не удалось создать файл в папке", "Не вдалося створити файл у теці"))
            try {
                Backup.export(ctx, app.garage, file)
            } catch (e: Throwable) {
                // недописанный архив не должен вытеснить при ротации целые копии
                runCatching { DocumentsContract.deleteDocument(cr, file) }
                throw e
            }
            rotate(ctx, tree, p.backupKeep.value.coerceAtLeast(1))
            p.backupLast.set(System.currentTimeMillis())
            p.backupError.set("")
            name
        }.onFailure { p.backupError.set(it.message ?: it.javaClass.simpleName) }
    }

    /** Удалить автокопии сверх [keep] самых новых (имена с датой — сортируются по времени). */
    private fun rotate(ctx: Context, tree: Uri, keep: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val files = ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            buildList<Pair<String, String>> { while (c.moveToNext()) add(c.getString(0) to (c.getString(1) ?: "")) }
        } ?: return
        files.filter { it.second.startsWith(PREFIX) && it.second.endsWith(".zip") }
            .sortedByDescending { it.second }
            .drop(keep)
            .forEach { runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) } }
    }

    /** Запомнить выбранную папку: постоянный доступ к новой, отпустить прежнюю. */
    fun setFolder(ctx: Context, tree: Uri) {
        val p = (ctx.applicationContext as CarApp).prefs
        val cr = ctx.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        cr.takePersistableUriPermission(tree, flags)
        val old = p.backupDir.value
        if (old.isNotEmpty() && old != tree.toString()) runCatching { cr.releasePersistableUriPermission(Uri.parse(old), flags) }
        p.backupDir.set(tree.toString())
        p.backupError.set("")
    }

    /** Имя папки для показа: последняя часть пути дерева («primary:Documents/Backups» → «Documents/Backups»). */
    fun folderName(uri: String): String =
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifEmpty { tr("корень памяти", "корінь пам'яті") } }.getOrDefault(uri)
}
