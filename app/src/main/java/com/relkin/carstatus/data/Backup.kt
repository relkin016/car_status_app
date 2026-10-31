package com.relkin.carstatus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Резервная копия — zip: car.db (файл базы) + photos/. */
object Backup {
    private const val DB = "car.db"
    private const val PHOTOS = "photos/"

    fun export(ctx: Context, repo: Repo, to: Uri): Int {
        var n = 0
        ctx.contentResolver.openOutputStream(to)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DB))
                repo.dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                repo.photoDir.listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry(PHOTOS + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    n++
                }
            }
        } ?: error("Не удалось открыть файл")
        return n
    }

    /** Восстановить из копии; текущие данные заменяются. Сначала распаковка во временную папку и проверка базы. */
    fun import(ctx: Context, repo: Repo, from: Uri) {
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val tmpPhotos = File(tmp, "photos").apply { mkdirs() }
        var db: File? = null
        ctx.contentResolver.openInputStream(from)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // защита от путей вида ../
                    if (name.contains("..") || e.isDirectory) continue
                    val out = when {
                        name == DB -> File(tmp, DB).also { db = it }
                        name.startsWith(PHOTOS) && !name.substring(PHOTOS.length).contains('/') -> File(tmpPhotos, name.substring(PHOTOS.length))
                        else -> null
                    } ?: continue
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        } ?: error("Не удалось открыть файл")
        val dbFile = db ?: error("В архиве нет базы $DB — это не резервная копия «Авто·Статус»")
        val tables = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.list("SELECT name FROM sqlite_master WHERE type='table'") { it.str("name") }.toSet()
        }
        require(tables.containsAll(Schema.REQUIRED)) { "Файл базы повреждён или от другого приложения" }

        repo.reopen {
            val target = repo.dbFile
            File(target.path + "-journal").delete()
            dbFile.copyTo(target, overwrite = true)
            val dir = repo.photoDir
            dir.listFiles()?.forEach { it.delete() }
            tmpPhotos.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        }
        tmp.deleteRecursively()
    }
}
