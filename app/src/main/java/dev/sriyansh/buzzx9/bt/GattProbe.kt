package dev.sriyansh.buzzx9.bt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanEntry(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturerIds: List<Int>
)

/**
 * The research half of the app.
 *
 * The Buzz X9 has no published control protocol, but the chipsets these budget TWS are
 * built on usually do. JieLi's RCSP, for instance, runs over a BLE service in the 0xAE00
 * range or over classic SPP. If the buds expose one of those, direct firmware control
 * (real on-device EQ, gesture remapping) becomes possible later. This screen finds out.
 *
 * It only ever reads. Nothing here writes to the earbuds.
 */
object GattProbe {

    private val handler = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    var scanning by mutableStateOf(false)
        private set
    var connectingTo by mutableStateOf<String?>(null)
        private set

    val results = mutableStateListOf<ScanEntry>()
    val log = mutableStateListOf<String>()

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

    private fun line(s: String) {
        handler.post {
            log.add(fmt.format(Date()) + "  " + s)
            if (log.size > 800) repeat(200) { log.removeAt(0) }
        }
    }

    fun clear() {
        results.clear()
        log.clear()
    }

    private fun canScan(ctx: Context) = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.BLUETOOTH_SCAN
    ) == PackageManager.PERMISSION_GRANTED

    private fun canConnect(ctx: Context) = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

    private fun adapterOf(ctx: Context) =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ---------------------------------------------------------------- classic (SDP)

    /**
     * Bonded-device SDP records. This is where classic-Bluetooth control channels show up:
     * an SPP record (0x1101) on a pair of earbuds is a strong hint that a vendor protocol
     * sits behind it, because music playback does not need SPP.
     */
    fun dumpBondedDevices(context: Context) {
        if (!canConnect(context)) {
            line("! BLUETOOTH_CONNECT not granted, cannot read bonded devices")
            return
        }
        val adapter = adapterOf(context)
        if (adapter == null || !adapter.isEnabled) {
            line("! Bluetooth is off")
            return
        }
        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        line("=== " + bonded.size + " bonded device(s) ===")
        for (d in bonded) {
            val name = runCatching { d.name }.getOrNull() ?: "(unnamed)"
            line("")
            line("* " + name + "  [" + d.address + "]  type=" + typeName(d.type))
            val uuids = runCatching { d.uuids }.getOrNull()
            if (uuids == null || uuids.isEmpty()) {
                line("    no cached SDP records (connect the device, then retry)")
            } else {
                for (u in uuids) {
                    val s = u.uuid.toString()
                    line("    " + s + "   " + describeUuid(s))
                }
            }
        }
        line("")
        line("=== end of bonded dump ===")
    }

    private fun typeName(t: Int) = when (t) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "le"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
        else -> "unknown"
    }

    // ---------------------------------------------------------------- BLE scan

    fun startScan(context: Context, durationMs: Long = 12_000L) {
        if (scanning) return
        if (!canScan(context)) {
            line("! BLUETOOTH_SCAN not granted")
            return
        }
        val adapter = adapterOf(context)
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            line("! Bluetooth is off, or this device has no BLE scanner")
            return
        }

        results.clear()
        line("=== BLE scan started (" + (durationMs / 1000) + "s) ===")
        scanning = true

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val uuids = rec?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                val mfg = mutableListOf<Int>()
                rec?.manufacturerSpecificData?.let { sparse ->
                    for (i in 0 until sparse.size()) mfg.add(sparse.keyAt(i))
                }
                val entry = ScanEntry(
                    address = result.device.address,
                    name = rec?.deviceName ?: runCatching { result.device.name }.getOrNull(),
                    rssi = result.rssi,
                    serviceUuids = uuids,
                    manufacturerIds = mfg
                )
                handler.post {
                    val idx = results.indexOfFirst { it.address == entry.address }
                    if (idx >= 0) {
                        results[idx] = entry
                    } else {
                        results.add(entry)
                        val tags = uuids.joinToString(" ") { describeUuid(it) }.trim()
                        line(
                            "found " + (entry.name ?: "(no name)") + " [" + entry.address +
                                "] rssi=" + entry.rssi + (if (tags.isEmpty()) "" else "  " + tags)
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                line("! scan failed, error " + errorCode)
                handler.post { scanning = false }
            }
        }
        scanCallback = cb

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val started = runCatching { scanner.startScan(null, settings, cb) }
        if (started.isFailure) {
            val t = started.exceptionOrNull()
            line("! startScan threw " + (t?.javaClass?.simpleName ?: "?") + ": " + (t?.message ?: ""))
            scanning = false
            return
        }

        handler.postDelayed({ stopScan(context) }, durationMs)
    }

    fun stopScan(context: Context) {
        if (!scanning) return
        val scanner = adapterOf(context)?.bluetoothLeScanner
        scanCallback?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        scanCallback = null
        scanning = false
        line("=== scan stopped, " + results.size + " device(s) ===")
    }

    // ---------------------------------------------------------------- GATT dump

    fun dumpGatt(context: Context, address: String) {
        if (!canConnect(context)) {
            line("! BLUETOOTH_CONNECT not granted")
            return
        }
        disconnect()
        val adapter = adapterOf(context) ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            line("! bad address " + address)
            return
        }
        connectingTo = address
        line("")
        line("=== connecting GATT to " + address + " ===")

        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {

            private val readQueue = ArrayDeque<BluetoothGattCharacteristic>()

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    line("connected (status=" + status + "), discovering services...")
                    runCatching { g.discoverServices() }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    line("disconnected (status=" + status + ")")
                    handler.post { connectingTo = null }
                    runCatching { g.close() }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    line("! service discovery failed, status=" + status)
                    return
                }
                line("--- " + g.services.size + " service(s) ---")
                for (svc in g.services) {
                    val su = svc.uuid.toString()
                    line("SERVICE " + su + "   " + describeUuid(su))
                    for (ch in svc.characteristics) {
                        val cu = ch.uuid.toString()
                        line("   CHAR " + cu + "  [" + props(ch.properties) + "]  " + describeUuid(cu))
                        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                            readQueue.addLast(ch)
                        }
                    }
                }
                line("--- reading " + readQueue.size + " readable characteristic(s) ---")
                next(g)
            }

            private fun next(g: BluetoothGatt) {
                val ch = readQueue.removeFirstOrNull()
                if (ch == null) {
                    line("=== dump complete ===")
                    handler.postDelayed({ disconnect() }, 500L)
                    return
                }
                val ok = runCatching { g.readCharacteristic(ch) }.getOrDefault(false)
                if (!ok) next(g)
            }

            // Delivered instead of the ByteArray overload below API 33.
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                status: Int
            ) {
                report(ch, ch.value, status)
                next(g)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                report(ch, value, status)
                next(g)
            }

            private fun report(ch: BluetoothGattCharacteristic, v: ByteArray?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || v == null) {
                    line("   READ " + ch.uuid + " -> status=" + status)
                    return
                }
                line("   READ " + ch.uuid + " -> " + hex(v) + "  " + ascii(v))
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.let { g ->
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
        handler.post { connectingTo = null }
    }

    // ---------------------------------------------------------------- helpers

    private fun props(p: Int): String {
        val out = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) out.add("R")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out.add("W")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out.add("Wnr")
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out.add("N")
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out.add("I")
        return if (out.isEmpty()) "-" else out.joinToString(",")
    }

    private fun hex(v: ByteArray): String {
        val head = v.take(32).joinToString(" ") { String.format("%02X", it) }
        return if (v.size > 32) head + " ...(" + v.size + "B)" else head
    }

    private fun ascii(v: ByteArray): String {
        val s = v.take(32).map { b ->
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) c.toChar() else '.'
        }.joinToString("")
        return "\"" + s + "\""
    }

    /**
     * Heuristic vendor fingerprinting. A hit is a lead worth chasing, not proof: these
     * 16-bit ranges get reused freely across the far-east audio SoC ecosystem.
     */
    fun describeUuid(uuid: String): String {
        val u = uuid.lowercase()
        val short = if (u.length == 36 && u.endsWith("-0000-1000-8000-00805f9b34fb")) {
            u.substring(4, 8)
        } else {
            null
        }

        if (short != null) {
            val known = KNOWN_SHORT[short]
            if (known != null) return "<" + known + ">"
            if (short.startsWith("ae")) return "<likely JieLi RCSP/OTA, worth investigating>"
            if (short.startsWith("fd")) return "<assigned member service " + short + ">"
        }
        val long = KNOWN_LONG[u]
        if (long != null) return "<" + long + ">"
        return ""
    }

    private val KNOWN_SHORT = mapOf(
        "1101" to "Serial Port Profile (SPP), vendor control often rides here",
        "110a" to "A2DP source",
        "110b" to "A2DP sink",
        "110c" to "AVRCP target",
        "110e" to "AVRCP remote control",
        "1108" to "Headset",
        "111e" to "Hands-Free",
        "1200" to "PnP Information, holds the vendor and product ID",
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "180a" to "Device Information",
        "180f" to "Battery Service",
        "1812" to "HID over GATT",
        "2a19" to "Battery Level",
        "2a24" to "Model Number",
        "2a25" to "Serial Number",
        "2a26" to "Firmware Revision",
        "2a27" to "Hardware Revision",
        "2a29" to "Manufacturer Name",
        "ae00" to "JieLi RCSP / OTA service, direct control may be possible",
        "ae01" to "JieLi RCSP write",
        "ae02" to "JieLi RCSP notify",
        "ae30" to "JieLi dual-mode RCSP",
        "fee7" to "Tencent / Chinese vendor service",
        "fff0" to "generic vendor service, inspect its characteristics",
        "ffe0" to "generic serial-over-BLE",
        "fe59" to "Nordic Secure DFU (firmware update)",
        "18f0" to "Realtek / Bluetrum serial-over-BLE"
    )

    private val KNOWN_LONG = mapOf(
        "00006287-3c17-d293-8e48-14fe2e4da212" to "Airoha RACE command channel",
        "0000fd5a-0000-1000-8000-00805f9b34fb" to "member-assigned audio service"
    )

    /** True when anything advertised smells like a controllable vendor channel. */
    fun looksControllable(entry: ScanEntry): Boolean = entry.serviceUuids.any {
        val d = describeUuid(it)
        d.contains("JieLi", true) || d.contains("vendor", true) || d.contains("RACE", true)
    }

    fun logAsText(): String = log.joinToString("\n")
}
