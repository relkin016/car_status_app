package com.relkin.carstatus.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Schema {
    const val VERSION = 1
    const val NAME = "car.db"

    val CREATE = listOf(
        "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE odo (_id INTEGER PRIMARY KEY, date INTEGER NOT NULL, km INTEGER NOT NULL, source INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE items (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, icon TEXT, km_interval INTEGER, day_interval INTEGER, " +
            "last_km INTEGER, last_date INTEGER, enabled INTEGER NOT NULL DEFAULT 1, position INTEGER, note TEXT, cost REAL)",
        "CREATE TABLE log (_id INTEGER PRIMARY KEY, type INTEGER NOT NULL, date INTEGER NOT NULL, km INTEGER, item_id INTEGER, " +
            "title TEXT, amount REAL, parts REAL, liters REAL, full INTEGER, note TEXT)",
        "CREATE TABLE incidents (_id INTEGER PRIMARY KEY, time INTEGER NOT NULL, km INTEGER, title TEXT, note TEXT, severity INTEGER)",
        "CREATE TABLE photos (_id INTEGER PRIMARY KEY, time INTEGER NOT NULL, km INTEGER, file TEXT NOT NULL, caption TEXT, owner_type INTEGER, owner_id INTEGER)",
        "CREATE TABLE breakdowns (_id INTEGER PRIMARY KEY, title TEXT NOT NULL, note TEXT, priority INTEGER, done INTEGER, " +
            "created_date INTEGER, created_km INTEGER, done_date INTEGER, position INTEGER)",
        "CREATE TABLE quest_done (key TEXT PRIMARY KEY)",
        "CREATE INDEX log_date ON log(date)",
    )

    /** Без этих таблиц файл не считаем резервной копией. */
    val REQUIRED = setOf("meta", "odo", "items", "log")
}

class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, Schema.NAME, null, Schema.VERSION) {
    init {
        // Без WAL: файл базы всегда целостен и его можно копировать как резервную копию.
        setWriteAheadLoggingEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.CREATE.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
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
