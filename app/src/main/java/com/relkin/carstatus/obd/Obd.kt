package com.relkin.carstatus.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.relkin.carstatus.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ObdState {
    data object Off : ObdState
    data object Connecting : ObdState
    data class Connected(val name: String, val protocol: String) : ObdState
    data class Failed(val message: String) : ObdState
}

data class LiveData(
    val rpm: Int? = null,
    val speed: Int? = null,
    val coolant: Int? = null,
    val intake: Int? = null,
    val load: Int? = null,
    val throttle: Int? = null,
    val fuelLevel: Int? = null,
    val voltage: Double? = null,
)

data class DtcResult(val stored: List<String>, val pending: List<String>, val permanent: List<String>) {
    val all: List<String> get() = (stored + pending + permanent).distinct()
}

/** Как получен пробег. */
sealed interface OdoRead {
    data class Odometer(val km: Long) : OdoRead
    data class Estimated(val km: Long) : OdoRead
    data object BaseSet : OdoRead
    data object Unsupported : OdoRead
}

data class BtDevice(val name: String, val address: String)

/** Одно подключение на всё приложение: живёт, пока открыта программа, а не вкладка. */
class Obd(private val ctx: Context, private val prefs: Prefs) {
    private val _state = MutableStateFlow<ObdState>(ObdState.Off)
    val state: StateFlow<ObdState> get() = _state
    private var elm: Elm327? = null
    private val mutex = Mutex()

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private val adapter get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    val bluetoothOn: Boolean get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun bonded(): List<BtDevice> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty().map { BtDevice(it.name ?: it.address, it.address) }
            // адаптеры обычно называются OBDII / ELM327 / V-LINK / Vgate — их наверх
            .sortedByDescending { d -> listOf("OBD", "ELM", "LINK", "VGATE", "KONNWEI", "VEEPEAK").any { d.name.uppercase().contains(it) } }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) = mutex.withLock {
        elm?.close(); elm = null
        _state.value = ObdState.Connecting
        try {
            val dev = adapter?.getRemoteDevice(address) ?: error("Bluetooth недоступен")
            adapter?.cancelDiscovery()
            val e = Elm327.connect(dev)
            elm = e
            prefs.obdAddress.set(address)
            _state.value = ObdState.Connected(dev.name ?: address, protocolName(e.protocol))
        } catch (t: Throwable) {
            _state.value = ObdState.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    fun disconnect() {
        elm?.close(); elm = null
        _state.value = ObdState.Off
    }

    private suspend fun <T> use(block: suspend (Elm327) -> T): T = mutex.withLock {
        val e = elm ?: error("Адаптер не подключён")
        try {
            block(e)
        } catch (t: java.io.IOException) {
            e.close(); elm = null
            _state.value = ObdState.Failed("Связь потеряна: ${t.message}")
            throw t
        }
    }

    suspend fun readDtc(): DtcResult = use { DtcResult(it.dtc("03"), it.dtc("07"), it.dtc("0A")) }

    suspend fun clearDtc(currentKm: Long): Boolean = use {
        val ok = it.clearDtc()
        // Сброс ошибок обнуляет PID 0x31 — база для оценки пробега начинается заново
        if (ok) prefs.obdBase.set("$currentKm:0")
        ok
    }

    suspend fun live(): LiveData = use { e ->
        LiveData(
            rpm = e.pid("0C")?.let { b -> if (b.size >= 2) (b[0] * 256 + b[1]) / 4 else null },
            speed = e.pid("0D")?.firstOrNull(),
            coolant = e.pid("05")?.firstOrNull()?.minus(40),
            intake = e.pid("0F")?.firstOrNull()?.minus(40),
            load = e.pid("04")?.firstOrNull()?.let { it * 100 / 255 },
            throttle = e.pid("11")?.firstOrNull()?.let { it * 100 / 255 },
            fuelLevel = e.pid("2F")?.firstOrNull()?.let { it * 100 / 255 },
            voltage = e.voltage(),
        )
    }

    /**
     * Пробег: PID 0xA6 (одометр) есть не у всех машин. Иначе — оценка по PID 0x31
     * (км после сброса ошибок) от базы «пробег в приложении на момент привязки».
     */
    suspend fun readOdometer(currentKm: Long): OdoRead = use { e ->
        e.pid("A6")?.takeIf { it.size >= 4 }?.let { b ->
            val km = ((b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()) / 10
            if (km > 0) return@use OdoRead.Odometer(km)
        }
        val dist = e.pid("31")?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]).toLong() } ?: return@use OdoRead.Unsupported
        val base = prefs.obdBase.value.split(':').mapNotNull { it.toLongOrNull() }
        if (base.size == 2 && dist >= base[1]) {
            val km = base[0] + (dist - base[1])
            if (km >= currentKm) return@use OdoRead.Estimated(km)
        }
        prefs.obdBase.set("$currentKm:$dist")
        OdoRead.BaseSet
    }

    private fun protocolName(p: String): String = when (p.trimStart('A')) {
        "1" -> "SAE J1850 PWM"
        "2" -> "SAE J1850 VPW"
        "3" -> "ISO 9141-2"
        "4" -> "ISO 14230-4 KWP (5 бод)"
        "5" -> "ISO 14230-4 KWP (быстрая)"
        "6" -> "CAN 11 бит 500 кбит/с"
        "7" -> "CAN 29 бит 500 кбит/с"
        "8" -> "CAN 11 бит 250 кбит/с"
        "9" -> "CAN 29 бит 250 кбит/с"
        "A" -> "SAE J1939 CAN"
        else -> p.ifEmpty { "неизвестен" }
    }
}
