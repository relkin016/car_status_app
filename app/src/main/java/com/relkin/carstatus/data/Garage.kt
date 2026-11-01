package com.relkin.carstatus.data

import android.content.Context
import com.relkin.carstatus.util.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Гараж — группа транспорта. [notify] — получать ли уведомления по его транспорту. */
data class GarageInfo(val id: Long, val name: String, val notify: Boolean = true, val lastVehicle: Long? = null)

/** Транспорт в гараже; данные — в отдельном файле базы ([Garage.dbName]). */
data class VehicleRef(val id: Long, val garageId: Long, val position: Int = 0)

data class GarageState(
    val garages: List<GarageInfo> = emptyList(),
    val vehicles: List<VehicleRef> = emptyList(),
    val current: Long = 1,
) {
    val currentRef: VehicleRef? get() = vehicles.firstOrNull { it.id == current }
    val currentGarage: GarageInfo? get() = garages.firstOrNull { it.id == currentRef?.garageId } ?: garages.firstOrNull()
    fun inGarage(id: Long) = vehicles.filter { it.garageId == id }.sortedWith(compareBy({ it.position }, { it.id }))
    fun garage(id: Long) = garages.firstOrNull { it.id == id }
}

/**
 * Реестр гаражей и транспорта (garage.json). У каждого транспорта своя база: первый — car.db
 * (как в 0.1, поэтому старые данные и резервные копии подхватываются без миграции), остальные — vehicle_<id>.db.
 * Фото лежат в общей папке photos/ — имена файлов уникальны.
 */
class Garage(private val ctx: Context) {
    private val file get() = File(ctx.filesDir, FILE)
    private val repos = HashMap<Long, Repo>()

    /** Реестр создан впервые — для переноса настроек версии 0.1. Объявлен до [_state]: его выставляет [load]. */
    var created = false
        private set

    private val _state = MutableStateFlow(load())
    val state: StateFlow<GarageState> get() = _state
    val s: GarageState get() = _state.value

    @Synchronized
    fun repo(id: Long): Repo = repos.getOrPut(id) { Repo(ctx, dbName(id)) }

    val current: Repo get() = repo(s.current)

    /** Все транспорты по порядку гаражей. */
    val all: List<VehicleRef> get() = s.garages.flatMap { s.inGarage(it.id) }

    private fun load(): GarageState {
        val f = file
        if (f.exists()) runCatching { return fromJson(JSONObject(f.readText())) }
        created = true
        return GarageState(listOf(GarageInfo(1, tr("Мой гараж", "Мій гараж"), lastVehicle = 1)), listOf(VehicleRef(1, 1)), 1).also { save(it) }
    }

    private fun save(st: GarageState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson(st).toString())
        tmp.renameTo(file)
    }

    @Synchronized
    private fun update(f: (GarageState) -> GarageState) {
        val n = f(_state.value)
        save(n)
        _state.value = n
    }

    /** Закрыть все базы (перед заменой файлов при восстановлении). */
    @Synchronized
    fun closeAll() {
        repos.values.forEach { it.close() }
        repos.clear()
    }

    /** Растёт при полной перезагрузке: экраны должны взять новые [Repo]. */
    val epoch = MutableStateFlow(0)

    /** Перечитать реестр и базы (после восстановления из копии). */
    @Synchronized
    fun reloadAll() {
        closeAll()
        _state.value = load()
        epoch.value++
    }

    fun select(vehicleId: Long) {
        if (s.vehicles.none { it.id == vehicleId }) return
        update { st ->
            val g = st.vehicles.first { it.id == vehicleId }.garageId
            st.copy(current = vehicleId, garages = st.garages.map { if (it.id == g) it.copy(lastVehicle = vehicleId) else it })
        }
    }

    /** Перейти в гараж: последний выбранный в нём транспорт или первый. Пустой гараж — false. */
    fun selectGarage(garageId: Long): Boolean {
        val list = s.inGarage(garageId)
        if (list.isEmpty()) return false
        val last = s.garage(garageId)?.lastVehicle
        select(list.firstOrNull { it.id == last }?.id ?: list.first().id)
        return true
    }

    fun addGarage(name: String): Long {
        val id = (s.garages.maxOfOrNull { it.id } ?: 0) + 1
        update { it.copy(garages = it.garages + GarageInfo(id, name.ifBlank { "Гараж $id" })) }
        return id
    }

    fun saveGarage(g: GarageInfo) = update { st -> st.copy(garages = st.garages.map { if (it.id == g.id) g else it }) }

    /** Удалить гараж вместе с транспортом. Последний гараж не удаляется. */
    fun deleteGarage(id: Long) {
        if (s.garages.size <= 1) return
        s.inGarage(id).forEach { dropFiles(it.id) }
        update { st ->
            val vehicles = st.vehicles.filter { it.garageId != id }
            val garages = st.garages.filter { it.id != id }
            st.copy(garages = garages, vehicles = vehicles, current = if (vehicles.any { it.id == st.current }) st.current else vehicles.firstOrNull()?.id ?: st.current)
        }
        ensureVehicle()
    }

    /** Новый транспорт в гараже (пустая база — дальше экран первичной настройки); становится текущим. */
    fun addVehicle(garageId: Long): Long {
        val id = (s.vehicles.maxOfOrNull { it.id } ?: 0) + 1
        // от удалённого транспорта с тем же id мог остаться файл
        dbFile(id).delete()
        update { st ->
            val pos = (st.inGarage(garageId).maxOfOrNull { it.position } ?: -1) + 1
            st.copy(vehicles = st.vehicles + VehicleRef(id, garageId, pos))
        }
        select(id)
        return id
    }

    /** Удалить транспорт: база и фото. Если это последний — создаётся пустой. */
    fun deleteVehicle(id: Long) {
        dropFiles(id)
        update { st ->
            val vehicles = st.vehicles.filter { it.id != id }
            st.copy(vehicles = vehicles, current = if (st.current == id) vehicles.firstOrNull()?.id ?: st.current else st.current)
        }
        ensureVehicle()
    }

    fun moveVehicle(id: Long, garageId: Long) = update { st ->
        val pos = (st.inGarage(garageId).maxOfOrNull { it.position } ?: -1) + 1
        st.copy(vehicles = st.vehicles.map { if (it.id == id) it.copy(garageId = garageId, position = pos) else it })
    }

    /** Сдвинуть транспорт внутри гаража. */
    fun reorder(id: Long, delta: Int) = update { st ->
        val ref = st.vehicles.firstOrNull { it.id == id } ?: return@update st
        val list = st.inGarage(ref.garageId).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (j !in list.indices) return@update st
        list.add(j, list.removeAt(i))
        val pos = list.mapIndexed { n, v -> v.id to n }.toMap()
        st.copy(vehicles = st.vehicles.map { v -> pos[v.id]?.let { v.copy(position = it) } ?: v })
    }

    private fun ensureVehicle() {
        if (s.vehicles.isEmpty()) addVehicle(s.garages.first().id)
        else if (s.vehicles.none { it.id == s.current }) select(s.vehicles.first().id)
    }

    private fun dropFiles(id: Long) {
        val r = repo(id)
        val photos = r.s.photos.map { it.file }
        synchronized(this) { repos.remove(id) }
        r.close()
        photos.forEach { File(r.photoDir, it).delete() }
        dbFile(id).delete()
        File(dbFile(id).path + "-journal").delete()
    }

    fun dbFile(id: Long): File = ctx.getDatabasePath(dbName(id))

    companion object {
        const val FILE = "garage.json"
        fun dbName(id: Long) = if (id == 1L) Schema.NAME else "vehicle_$id.db"
        /** Обратное к [dbName]: id транспорта по имени файла базы, иначе null. */
        fun idOf(dbName: String): Long? = if (dbName == Schema.NAME) 1L else Regex("vehicle_(\\d+)\\.db").matchEntire(dbName)?.groupValues?.get(1)?.toLongOrNull()

        fun toJson(st: GarageState) = JSONObject().apply {
            put("current", st.current)
            put("garages", JSONArray().apply {
                st.garages.forEach { g ->
                    put(JSONObject().apply {
                        put("id", g.id); put("name", g.name); put("notify", g.notify); g.lastVehicle?.let { put("lastVehicle", it) }
                    })
                }
            })
            put("vehicles", JSONArray().apply {
                st.vehicles.forEach { v -> put(JSONObject().apply { put("id", v.id); put("garage", v.garageId); put("position", v.position) }) }
            })
        }

        fun fromJson(o: JSONObject): GarageState {
            val g = o.getJSONArray("garages")
            val v = o.getJSONArray("vehicles")
            val garages = (0 until g.length()).map { g.getJSONObject(it) }.map {
                GarageInfo(it.getLong("id"), it.optString("name"), it.optBoolean("notify", true), if (it.has("lastVehicle")) it.getLong("lastVehicle") else null)
            }
            require(garages.isNotEmpty())
            val ids = garages.map { it.id }.toSet()
            val vehicles = (0 until v.length()).map { v.getJSONObject(it) }.map {
                VehicleRef(it.getLong("id"), it.getLong("garage"), it.optInt("position"))
            }.map { if (it.garageId in ids) it else it.copy(garageId = garages.first().id) }
            return GarageState(garages, vehicles, o.optLong("current", vehicles.firstOrNull()?.id ?: 1))
        }
    }
}
