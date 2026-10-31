package com.relkin.carstatus.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Адаптер ELM327 по Bluetooth SPP. Все вызовы — из корутины, работа идёт в Dispatchers.IO. */
class Elm327 private constructor(private val socket: BluetoothSocket) {
    private val input: InputStream = socket.inputStream
    private val output: OutputStream = socket.outputStream
    /** Номер протокола (ATDPN): 6–C — CAN. */
    var protocol: String = ""
        private set
    val isCan: Boolean get() = protocol.trimStart('A').firstOrNull()?.let { it in '6'..'9' || it in 'A'..'C' } == true

    companion object {
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission")
        suspend fun connect(device: BluetoothDevice): Elm327 = withContext(Dispatchers.IO) {
            val socket = try {
                device.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
            } catch (e: IOException) {
                // Часть дешёвых адаптеров не отвечает на SDP — запасной путь через канал 1
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    (m.invoke(device, 1) as BluetoothSocket).also { it.connect() }
                }.getOrElse { throw e }
            }
            Elm327(socket).apply { init() }
        }
    }

    private suspend fun init() {
        cmd("ATZ", 3000)
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) cmd(c)
        // Первый запрос запускает автоопределение протокола
        cmd("0100", 8000)
        protocol = cmd("ATDPN").trim()
    }

    /** Отправить команду и прочитать ответ до приглашения '>'. */
    suspend fun cmd(c: String, timeoutMs: Long = 4000): String = withContext(Dispatchers.IO) {
        while (input.available() > 0) input.read()
        output.write((c + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val b = input.read()
                if (b < 0 || b == '>'.code) break
                sb.append(b.toChar())
            } else Thread.sleep(10)
        }
        sb.toString().replace("\u0000", "").trim()
    }

    /** Строки ответа без служебных сообщений. */
    private fun lines(raw: String): List<String> = raw.split('\r', '\n').map { it.trim().replace(" ", "") }
        .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") && it != "OK" && !it.startsWith("BUSINIT") }

    fun isNoData(raw: String) = raw.contains("NODATA", true) || raw.contains("NO DATA", true) ||
        raw.contains("UNABLE", true) || raw.contains("ERROR", true) || raw.contains("STOPPED", true) || raw.contains("?")

    /** Байты ответа на PID режима 01: всё после "41 XX". */
    suspend fun pid(p: String): List<Int>? {
        val raw = cmd("01$p")
        if (isNoData(raw)) return null
        val hex = lines(raw).joinToString("")
        val i = hex.indexOf("41" + p.uppercase())
        if (i < 0) return null
        return hex.substring(i + 4).chunked(2).mapNotNull { it.takeIf { s -> s.length == 2 }?.toIntOrNull(16) }
    }

    /** Коды ошибок: 03 — сохранённые, 07 — ожидающие, 0A — постоянные. */
    suspend fun dtc(mode: String = "03"): List<String> {
        val raw = cmd(mode, 6000)
        if (isNoData(raw)) return emptyList()
        val resp = (mode.toInt(16) + 0x40).toString(16).uppercase()
        val ls = lines(raw)
            .map { it.replace(Regex("^[0-9A-F]:"), "") } // номер кадра в многокадровом CAN-ответе
            .filter { !Regex("^[0-9A-F]{3}$").matches(it) } // длина ответа перед кадрами
        val codes = ArrayList<String>()
        if (isCan) {
            val hex = ls.joinToString("")
            val i = hex.indexOf(resp)
            if (i < 0) return emptyList()
            // после "43" — число кодов, затем по 2 байта на код
            val body = hex.substring(i + 4)
            body.chunked(4).filter { it.length == 4 }.forEach { codes += it }
        } else {
            // ISO/KWP: каждая строка "43" + 3 кода
            for (l in ls) if (l.startsWith(resp)) l.substring(2).chunked(4).filter { it.length == 4 }.forEach { codes += it }
        }
        return codes.filter { it != "0000" }.map(Dtc::decode).distinct()
    }

    suspend fun clearDtc(): Boolean = !isNoData(cmd("04", 6000))

    suspend fun voltage(): Double? = cmd("ATRV").replace("V", "").trim().toDoubleOrNull()

    fun close() {
        runCatching { socket.close() }
    }
}
