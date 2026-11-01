package com.relkin.carstatus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.relkin.carstatus.util.tr
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Резервная копия — zip: garage.json (гаражи), базы транспорта (car.db, vehicle_<id>.db) + photos/.
 * Копия версии 0.1 (только car.db и photos/) восстанавливается как один гараж с одним транспортом.
 */
object Backup {
    private const val PHOTOS = "photos/"

    fun export(ctx: Context, garage: Garage, to: Uri): Int {
        var n = 0
        ctx.contentResolver.openOutputStream(to)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(Garage.FILE))
                zip.write(Garage.toJson(garage.s).toString(2).toByteArray())
                zip.closeEntry()
                for (v in garage.s.vehicles) {
                    val f = garage.dbFile(v.id)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry(Garage.dbName(v.id)))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                garage.current.photoDir.listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry(PHOTOS + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    n++
                }
            }
        } ?: error(tr("Не удалось открыть файл", "Не вдалося відкрити файл"))
        return n
    }

    /** Восстановить из копии; текущие данные заменяются. Сначала распаковка во временную папку и проверка баз. */
    fun import(ctx: Context, garage: Garage, from: Uri) {
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val tmpPhotos = File(tmp, "photos").apply { mkdirs() }
        val dbs = HashMap<Long, File>()
        var registry: File? = null
        ctx.contentResolver.openInputStream(from)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // защита от путей вида ../
                    if (name.contains("..") || e.isDirectory) continue
                    val id = Garage.idOf(name)
                    val out = when {
                        id != null -> File(tmp, name).also { dbs[id] = it }
                        name == Garage.FILE -> File(tmp, name).also { registry = it }
                        name.startsWith(PHOTOS) && !name.substring(PHOTOS.length).contains('/') -> File(tmpPhotos, name.substring(PHOTOS.length))
                        else -> null
                    } ?: continue
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        } ?: error(tr("Не удалось открыть файл", "Не вдалося відкрити файл"))
        if (dbs.isEmpty()) error(tr("В архиве нет базы ${Schema.NAME} — это не резервная копия «Авто·Статус»", "В архіві немає бази ${Schema.NAME} — це не резервна копія «Авто·Статус»"))
        for (f in dbs.values) {
            val tables = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                d.list("SELECT name FROM sqlite_master WHERE type='table'") { it.str("name") }.toSet()
            }
            require(tables.containsAll(Schema.REQUIRED)) { tr("Файл базы ${f.name} повреждён или от другого приложения", "Файл бази ${f.name} пошкоджений або з іншого застосунку") }
        }
        // Реестр из копии проверяем заранее; без него — один гараж со всеми базами из архива
        val state = registry?.let { runCatching { Garage.fromJson(org.json.JSONObject(it.readText())) }.getOrNull() }
            ?.let { st -> st.copy(vehicles = st.vehicles.filter { it.id in dbs }) }
            ?.takeIf { it.vehicles.isNotEmpty() }
            ?: GarageState(listOf(GarageInfo(1, tr("Мой гараж", "Мій гараж"))), dbs.keys.sorted().mapIndexed { i, id -> VehicleRef(id, 1, i) }, dbs.keys.min())

        val photoDir = garage.current.photoDir
        synchronized(garage) {
            // закрыть все базы, заменить файлы, открыть заново
            val old = garage.s.vehicles.map { it.id }
            garage.closeAll()
            for (id in old) {
                garage.dbFile(id).delete()
                File(garage.dbFile(id).path + "-journal").delete()
            }
            for ((id, f) in dbs) if (state.vehicles.any { it.id == id }) f.copyTo(garage.dbFile(id).apply { parentFile?.mkdirs() }, overwrite = true)
            File(ctx.filesDir, Garage.FILE).writeText(Garage.toJson(state).toString())
            photoDir.listFiles()?.forEach { it.delete() }
            tmpPhotos.listFiles()?.forEach { it.copyTo(File(photoDir, it.name), overwrite = true) }
            garage.reloadAll()
        }
        tmp.deleteRecursively()
    }
}
