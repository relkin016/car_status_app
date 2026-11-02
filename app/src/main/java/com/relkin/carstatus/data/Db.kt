package com.relkin.carstatus.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Schema {
    const val VERSION = 4
    /** Файл базы первого транспорта (с версии 0.1); у остальных — vehicle_<id>.db. */
    const val NAME = "car.db"

    val CREATE = listOf(
        "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE odo (_id INTEGER PRIMARY KEY, date INTEGER NOT NULL, km INTEGER NOT NULL, source INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE items (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, icon TEXT, km_interval INTEGER, day_interval INTEGER, " +
            "last_km INTEGER, last_date INTEGER, enabled INTEGER NOT NULL DEFAULT 1, position INTEGER, note TEXT, cost REAL)",
        "CREATE TABLE log (_id INTEGER PRIMARY KEY, type INTEGER NOT NULL, date INTEGER NOT NULL, km INTEGER, item_id INTEGER, " +
            "title TEXT, amount REAL, parts REAL, liters REAL, full INTEGER, note TEXT, brand TEXT, part_no TEXT, km_manual INTEGER NOT NULL DEFAULT 1)",
        "CREATE TABLE incidents (_id INTEGER PRIMARY KEY, time INTEGER NOT NULL, km INTEGER, title TEXT, note TEXT, severity INTEGER)",
        "CREATE TABLE photos (_id INTEGER PRIMARY KEY, time INTEGER NOT NULL, km INTEGER, file TEXT NOT NULL, caption TEXT, owner_type INTEGER, owner_id INTEGER)",
        "CREATE TABLE breakdowns (_id INTEGER PRIMARY KEY, title TEXT NOT NULL, note TEXT, priority INTEGER, done INTEGER, " +
            "created_date INTEGER, created_km INTEGER, done_date INTEGER, position INTEGER)",
        "CREATE TABLE quest_done (key TEXT PRIMARY KEY)",
        "CREATE INDEX log_date ON log(date)",
    ) + listOf(
        // с версии 4 — комплекты шин и периоды, когда они стояли на транспорте
        "CREATE TABLE tire_sets (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, season INTEGER, brand TEXT, model TEXT, size TEXT, dot TEXT, " +
            "purchase_date INTEGER, price REAL, km_before INTEGER, tread REAL, tread_date INTEGER, storage TEXT, note TEXT, retired INTEGER, position INTEGER)",
        "CREATE TABLE tire_mounts (_id INTEGER PRIMARY KEY, set_id INTEGER NOT NULL, date_on INTEGER NOT NULL, km_on INTEGER NOT NULL, date_off INTEGER, km_off INTEGER)",
    )

    /** Таблицы, добавленные в версии 4 (последние в [CREATE]). */
    val V4: List<String> get() = CREATE.takeLast(2)

    /** Без этих таблиц файл не считаем резервной копией. */
    val REQUIRED = setOf("meta", "odo", "items", "log")
}

class DbHelper(ctx: Context, name: String) : SQLiteOpenHelper(ctx, name, null, Schema.VERSION) {
    init {
        // Без WAL: файл базы всегда целостен и его можно копировать как резервную копию.
        setWriteAheadLoggingEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.CREATE.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE log ADD COLUMN brand TEXT")
            db.execSQL("ALTER TABLE log ADD COLUMN part_no TEXT")
        }
        // Старые записи считаем введёнными вручную — их пробег не пересчитываем
        if (oldVersion < 3) db.execSQL("ALTER TABLE log ADD COLUMN km_manual INTEGER NOT NULL DEFAULT 1")
        if (oldVersion < 4) Schema.V4.forEach(db::execSQL)
    }
}

fun Cursor.str(c: String): String = getString(getColumnIndexOrThrow(c)) ?: ""
fun Cursor.long(c: String): Long = getLong(getColumnIndexOrThrow(c))
fun Cursor.longOrNull(c: String): Long? = getColumnIndexOrThrow(c).let { if (isNull(it)) null else getLong(it) }
fun Cursor.dbl(c: String): Double = getColumnIndexOrThrow(c).let { if (isNull(it)) 0.0 else getDouble(it) }
fun Cursor.int(c: String): Int = getColumnIndexOrThrow(c).let { if (isNull(it)) 0 else getInt(it) }

inline fun <T> SQLiteDatabase.list(sql: String, vararg args: String, map: (Cursor) -> T): List<T> =
    rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }

fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
    for ((k, v) in pairs) when (v) {
        null -> putNull(k)
        is Long -> put(k, v)
        is Int -> put(k, v)
        is Double -> put(k, v)
        is Boolean -> put(k, if (v) 1 else 0)
        is String -> put(k, v)
        else -> put(k, v.toString())
    }
}
