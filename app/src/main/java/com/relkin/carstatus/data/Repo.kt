package com.relkin.carstatus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * Доступ к базе одного транспорта (у каждого — свой файл). Данных немного (годы записей — тысячи строк),
 * поэтому после каждой записи снимок перечитывается целиком.
 */
class Repo(private val ctx: Context, val dbName: String) {
    private val helper = DbHelper(ctx, dbName)
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _snap = MutableStateFlow(load())
    val snap: StateFlow<Snapshot> get() = _snap
    val s: Snapshot get() = _snap.value

    /** Вызывается после каждого изменения (перепланировать уведомления). */
    var onChanged: (() -> Unit)? = null

    val photoDir: File get() = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun write(block: SQLiteDatabase.() -> Unit) {
        scope.launch { lock.withLock { writeNow(block) } }
    }

    fun writeNow(block: SQLiteDatabase.() -> Unit) {
        val d = db
        d.beginTransaction()
        try {
            d.block()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        reload()
    }

    fun reload() {
        _snap.value = load()
        onChanged?.invoke()
    }

    /** Закрыть базу перед удалением транспорта. */
    fun close() = helper.close()

    val dbFile: File get() = ctx.getDatabasePath(dbName)

    private fun load(): Snapshot {
        val d = db
        val meta = d.list("SELECT key, value FROM meta") { it.str("key") to it.str("value") }.toMap()
        return Snapshot(
            car = meta["car"]?.let(::carFromJson) ?: Car(),
            setupDone = meta["setup"] == "1",
            odo = d.list("SELECT * FROM odo ORDER BY date, km") { Odo(it.long("_id"), it.long("date"), it.long("km"), it.int("source")) },
            items = d.list("SELECT * FROM items ORDER BY position, _id") {
                Item(
                    id = it.long("_id"), name = it.str("name"), icon = it.str("icon").ifEmpty { "build" },
                    kmInterval = it.longOrNull("km_interval") ?: 0, dayInterval = it.longOrNull("day_interval") ?: 0,
                    lastKm = it.longOrNull("last_km"), lastDate = it.longOrNull("last_date"),
                    enabled = it.int("enabled") == 1, position = it.int("position"), note = it.str("note"), cost = it.dbl("cost"),
                )
            },
            log = d.list("SELECT * FROM log ORDER BY date DESC, km DESC, _id DESC") {
                LogEntry(
                    id = it.long("_id"), type = it.int("type"), date = it.long("date"), km = it.longOrNull("km"),
                    itemId = it.longOrNull("item_id"), title = it.str("title"), amount = it.dbl("amount"), parts = it.dbl("parts"),
                    liters = it.dbl("liters"), full = it.int("full") == 1, note = it.str("note"),
                    brand = it.str("brand"), partNo = it.str("part_no"),
                )
            },
            incidents = d.list("SELECT * FROM incidents ORDER BY time DESC") {
                Incident(it.long("_id"), it.long("time"), it.longOrNull("km"), it.str("title"), it.str("note"), it.int("severity"))
            },
            photos = d.list("SELECT * FROM photos ORDER BY time DESC") {
                Photo(it.long("_id"), it.long("time"), it.longOrNull("km"), it.str("file"), it.str("caption"), it.int("owner_type"), it.long("owner_id"))
            },
            breakdowns = d.list("SELECT * FROM breakdowns ORDER BY done, priority DESC, position, _id") {
                Breakdown(
                    it.long("_id"), it.str("title"), it.str("note"), it.int("priority"), it.int("done") == 1,
                    it.long("created_date"), it.longOrNull("created_km"), it.longOrNull("done_date"), it.int("position"),
                )
            },
            questDone = d.list("SELECT key FROM quest_done") { it.str("key") }.toSet(),
        )
    }

    // ---------- Авто и первичная настройка ----------

    fun saveCar(car: Car) = write { putMeta("car", carToJson(car).toString()) }

    /** Первый запуск: профиль, текущий пробег и базовый список расходников вида (по желанию — «всё обслужено сейчас»). */
    fun setup(car: Car, km: Long, allServicedNow: Boolean) = write {
        putMeta("car", carToJson(car).toString())
        insert("odo", null, values("date" to today(), "km" to km, "source" to OdoSource.MANUAL))
        delete("items", null, null)
        for (it in car.kind.items) {
            insertItem(if (allServicedNow) it.copy(lastKm = km, lastDate = today()) else it)
        }
        putMeta("setup", "1")
    }

    private fun SQLiteDatabase.putMeta(k: String, v: String) {
        insertWithOnConflict("meta", null, values("key" to k, "value" to v), SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- Пробег ----------

    fun addOdo(km: Long, date: Long = today(), source: Int = OdoSource.MANUAL) = write {
        insert("odo", null, values("date" to date, "km" to km, "source" to source))
    }

    fun deleteOdo(id: Long) = write { delete("odo", "_id=?", arrayOf(id.toString())) }

    // ---------- Пункты ТО ----------

    private fun SQLiteDatabase.insertItem(it: Item): Long = insert("items", null, itemValues(it))

    private fun itemValues(it: Item) = values(
        "name" to it.name, "icon" to it.icon, "km_interval" to it.kmInterval, "day_interval" to it.dayInterval,
        "last_km" to it.lastKm, "last_date" to it.lastDate, "enabled" to it.enabled, "position" to it.position,
        "note" to it.note, "cost" to it.cost,
    )

    fun saveItem(it: Item) = write {
        if (it.id == 0L) insertItem(it.copy(position = (s.items.maxOfOrNull { i -> i.position } ?: -1) + 1))
        else update("items", itemValues(it), "_id=?", arrayOf(it.id.toString()))
    }

    fun deleteItem(id: Long) = write {
        delete("items", "_id=?", arrayOf(id.toString()))
        update("log", values("item_id" to null), "item_id=?", arrayOf(id.toString()))
    }

    fun moveItem(id: Long, delta: Int) = write {
        val list = s.items.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@write
        list.add(j, list.removeAt(i))
        list.forEachIndexed { pos, it -> update("items", values("position" to pos), "_id=?", arrayOf(it.id.toString())) }
    }

    /** Добавить к списку недостающие пункты из базового набора вида транспорта. */
    fun restoreDefaultItems() = write {
        val names = s.items.map { it.name }.toSet()
        var pos = (s.items.maxOfOrNull { it.position } ?: -1) + 1
        // пункт мог быть создан на другом языке — сверяем оба названия
        val ru = Consumables.of(s.kind, uk = false)
        val uk = Consumables.of(s.kind, uk = true)
        for (n in ru.indices) if (ru[n].name !in names && uk[n].name !in names) insertItem(s.kind.items[n].copy(position = pos++))
    }

    // ---------- Журнал ----------

    /**
     * Сохранить запись. Запись ТО с пунктом сдвигает «последнее обслуживание» пункта, если она новее.
     * Возвращает id через [onSaved] (нужно для прикрепления фото).
     */
    fun saveLog(e: LogEntry, onSaved: (Long) -> Unit = {}) = write {
        val v = values(
            "type" to e.type, "date" to e.date, "km" to e.km, "item_id" to e.itemId, "title" to e.title,
            "amount" to e.amount, "parts" to e.parts, "liters" to e.liters, "full" to e.full, "note" to e.note,
            "brand" to e.brand, "part_no" to e.partNo,
        )
        val id = if (e.id == 0L) insert("log", null, v) else { update("log", v, "_id=?", arrayOf(e.id.toString())); e.id }
        if (e.itemId != null) recomputeItem(e.itemId, e)
        val old = s.log.firstOrNull { it.id == e.id }?.itemId
        if (old != null && old != e.itemId) recomputeItem(old, null)
        onSaved(id)
    }

    fun deleteLog(id: Long) = write {
        val e = s.log.firstOrNull { it.id == id }
        delete("log", "_id=?", arrayOf(id.toString()))
        deletePhotosOf(PhotoOwner.LOG, id)
        e?.itemId?.let { recomputeItem(it, null) }
    }

    /** Последнее обслуживание пункта = самая поздняя запись ТО по нему (или прежнее значение, если записей нет). */
    private fun SQLiteDatabase.recomputeItem(itemId: Long, added: LogEntry?) {
        val item = s.item(itemId) ?: return
        val rows = list("SELECT date, km FROM log WHERE item_id=? ORDER BY date DESC, km DESC LIMIT 1", itemId.toString()) {
            it.long("date") to it.longOrNull("km")
        }
        val last = rows.firstOrNull() ?: return
        val km = last.second ?: added?.km ?: item.lastKm
        update("items", values("last_date" to last.first, "last_km" to km), "_id=?", arrayOf(itemId.toString()))
    }

    // ---------- Дневник ----------

    fun saveIncident(i: Incident, onSaved: (Long) -> Unit = {}) = write {
        val v = values("time" to i.time, "km" to i.km, "title" to i.title, "note" to i.note, "severity" to i.severity)
        val id = if (i.id == 0L) insert("incidents", null, v) else { update("incidents", v, "_id=?", arrayOf(i.id.toString())); i.id }
        onSaved(id)
    }

    fun deleteIncident(id: Long) = write {
        delete("incidents", "_id=?", arrayOf(id.toString()))
        deletePhotosOf(PhotoOwner.INCIDENT, id)
    }

    // ---------- Фото ----------

    fun addPhoto(p: Photo) = write {
        insert("photos", null, values(
            "time" to p.time, "km" to p.km, "file" to p.file, "caption" to p.caption,
            "owner_type" to p.ownerType, "owner_id" to p.ownerId,
        ))
    }

    fun updatePhoto(p: Photo) = write {
        update("photos", values("caption" to p.caption, "km" to p.km, "time" to p.time), "_id=?", arrayOf(p.id.toString()))
    }

    fun deletePhoto(p: Photo) = write {
        delete("photos", "_id=?", arrayOf(p.id.toString()))
        File(photoDir, p.file).delete()
    }

    /** Фото, снятые до сохранения записи (владелец 0), привязать к ней. */
    fun attachPhotos(ids: List<Long>, ownerType: Int, ownerId: Long) = write {
        for (id in ids) update("photos", values("owner_type" to ownerType, "owner_id" to ownerId), "_id=?", arrayOf(id.toString()))
    }

    private fun SQLiteDatabase.deletePhotosOf(type: Int, id: Long) {
        val files = list("SELECT file FROM photos WHERE owner_type=? AND owner_id=?", type.toString(), id.toString()) { it.str("file") }
        delete("photos", "owner_type=? AND owner_id=?", arrayOf(type.toString(), id.toString()))
        files.forEach { File(photoDir, it).delete() }
    }

    // ---------- Поломки ----------

    fun saveBreakdown(b: Breakdown) = write {
        val v = values(
            "title" to b.title, "note" to b.note, "priority" to b.priority, "done" to b.done, "created_date" to b.createdDate,
            "created_km" to b.createdKm, "done_date" to b.doneDate, "position" to b.position,
        )
        if (b.id == 0L) insert("breakdowns", null, v) else update("breakdowns", v, "_id=?", arrayOf(b.id.toString()))
    }

    fun setBreakdownDone(b: Breakdown, done: Boolean) =
        saveBreakdown(b.copy(done = done, doneDate = if (done) today() else null))

    fun deleteBreakdown(id: Long) = write {
        delete("breakdowns", "_id=?", arrayOf(id.toString()))
        deletePhotosOf(PhotoOwner.BREAKDOWN, id)
    }

    // ---------- Квесты ----------

    fun setQuestStep(key: String, done: Boolean) = write {
        if (done) insertWithOnConflict("quest_done", null, values("key" to key), SQLiteDatabase.CONFLICT_IGNORE)
        else delete("quest_done", "key=?", arrayOf(key))
    }

    // ---------- Профиль авто в JSON ----------

    private fun carToJson(c: Car) = JSONObject().apply {
        put("name", c.name); put("make", c.make); put("model", c.model); put("year", c.year); put("vin", c.vin)
        put("plate", c.plate); put("engine", c.engine); put("fuel", c.fuel)
        c.purchaseDate?.let { put("purchaseDate", it) }; c.purchaseKm?.let { put("purchaseKm", it) }
        put("kind", c.kind.name); put("kmPerMonth", c.kmPerMonth)
    }

    private fun carFromJson(s: String): Car = runCatching {
        val o = JSONObject(s)
        Car(
            o.optString("name"), o.optString("make"), o.optString("model"), o.optString("year"), o.optString("vin"),
            o.optString("plate"), o.optString("engine"), o.optString("fuel"),
            if (o.has("purchaseDate")) o.getLong("purchaseDate") else null,
            if (o.has("purchaseKm")) o.getLong("purchaseKm") else null,
            VehicleKind.of(o.optString("kind")) ?: VehicleKind.guess(o.optString("fuel")),
            o.optInt("kmPerMonth", 0),
        )
    }.getOrDefault(Car())

    private fun today() = java.time.LocalDate.now().toEpochDay()
}
