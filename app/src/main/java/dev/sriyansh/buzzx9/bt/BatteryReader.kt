package dev.sriyansh.buzzx9.bt

import android.bluetooth.BluetoothDevice
import android.util.Log

private const val TAG = "BatteryReader"

/**
 * Metadata keys from BluetoothDevice. They are @SystemApi, so the constants are not in the
 * public SDK and we hardcode the values the platform has used since Android 10.
 */
private const val META_UNTETHERED_LEFT_BATTERY = 10
private const val META_UNTETHERED_RIGHT_BATTERY = 11
private const val META_UNTETHERED_CASE_BATTERY = 12
private const val META_UNTETHERED_LEFT_CHARGING = 13
private const val META_UNTETHERED_RIGHT_CHARGING = 14
private const val META_UNTETHERED_CASE_CHARGING = 15
private const val META_MAIN_BATTERY = 16
private const val META_MAIN_CHARGING = 17

/** One earpiece or the case. [percent] is null when nothing reported a figure. */
data class CellState(val percent: Int?, val charging: Boolean?) {
    val known: Boolean get() = percent != null
}

/**
 * Everything we managed to learn about the buds' charge, plus how we learned it, because
 * "n/a" is only useful if you can see which doors were tried and which were shut.
 */
data class BatteryState(
    val left: CellState = CellState(null, null),
    val right: CellState = CellState(null, null),
    val case: CellState = CellState(null, null),
    val combined: CellState = CellState(null, null),
    val sourceLog: List<String> = emptyList()
) {
    val anyKnown: Boolean get() = left.known || right.known || case.known || combined.known
    val perBudKnown: Boolean get() = left.known || right.known
}

/**
 * Android has no public API for headset battery. There are four routes to it, all of them
 * partial, so we try every one and take whatever answers.
 *
 *  1. getMetadata(), which is where per-earbud figures live. Normally gated behind
 *     BLUETOOTH_PRIVILEGED, so it usually refuses -- but it costs nothing to ask.
 *  2. getBatteryLevel(), the single combined figure the stack derives from the vendor
 *     HFP indicators most cheap TWS send. Hidden API, and on newer builds the non-SDK
 *     blocklist can kill it.
 *  3. The BATTERY_LEVEL_CHANGED broadcast, handled in BtMonitor and fed back in here.
 *  4. The BLE Battery Service, if the buds run a GATT server exposing one. Handled by
 *     the Probe tab, since it needs a connection.
 *
 * Nothing here ever invents a number. If every route refuses, the UI says so plainly.
 */
object BatteryReader {

    /** Populated by BtMonitor from the hidden battery broadcast, keyed by MAC. */
    private val broadcastLevels = mutableMapOf<String, Int>()

    /** Populated by the Probe tab after a successful BLE Battery Service read. */
    private val gattLevels = mutableMapOf<String, Int>()

    fun recordBroadcastLevel(address: String, percent: Int) {
        if (percent in 0..100) broadcastLevels[address] = percent
    }

    fun recordGattLevel(address: String, percent: Int) {
        if (percent in 0..100) gattLevels[address] = percent
    }

    fun read(device: BluetoothDevice): BatteryState {
        val log = mutableListOf<String>()

        val left = CellState(
            metadataInt(device, META_UNTETHERED_LEFT_BATTERY, log, "left"),
            metadataBool(device, META_UNTETHERED_LEFT_CHARGING)
        )
        val right = CellState(
            metadataInt(device, META_UNTETHERED_RIGHT_BATTERY, log, "right"),
            metadataBool(device, META_UNTETHERED_RIGHT_CHARGING)
        )
        val case = CellState(
            metadataInt(device, META_UNTETHERED_CASE_BATTERY, log, "case"),
            metadataBool(device, META_UNTETHERED_CASE_CHARGING)
        )

        var combinedPct = metadataInt(device, META_MAIN_BATTERY, log, "main")
        val combinedCharging = metadataBool(device, META_MAIN_CHARGING)

        if (combinedPct == null) {
            combinedPct = batteryLevelReflection(device, log)
        }
        if (combinedPct == null) {
            combinedPct = broadcastLevels[device.address]?.also {
                log.add("BATTERY_LEVEL_CHANGED broadcast: $it%")
            }
        }
        if (combinedPct == null) {
            combinedPct = gattLevels[device.address]?.also {
                log.add("BLE Battery Service: $it%")
            }
        }

        return BatteryState(
            left = left,
            right = right,
            case = case,
            combined = CellState(combinedPct, combinedCharging),
            sourceLog = log
        )
    }

    /** Metadata values come back as an ASCII decimal string in a byte array. */
    private fun rawMetadata(device: BluetoothDevice, key: Int): String? = runCatching {
        val m = BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType)
        val bytes = m.invoke(device, key) as? ByteArray ?: return@runCatching null
        String(bytes).trim().ifEmpty { null }
    }.getOrElse {
        Log.d(TAG, "getMetadata($key) refused: ${it.javaClass.simpleName}")
        null
    }

    private fun metadataInt(
        device: BluetoothDevice,
        key: Int,
        log: MutableList<String>,
        label: String
    ): Int? {
        val v = rawMetadata(device, key)?.toIntOrNull() ?: return null
        if (v !in 0..100) return null
        log.add("getMetadata $label: $v%")
        return v
    }

    private fun metadataBool(device: BluetoothDevice, key: Int): Boolean? =
        rawMetadata(device, key)?.lowercase()?.let { it == "true" || it == "1" }

    private fun batteryLevelReflection(
        device: BluetoothDevice,
        log: MutableList<String>
    ): Int? = runCatching {
        val m = BluetoothDevice::class.java.getMethod("getBatteryLevel")
        val level = m.invoke(device) as? Int ?: return@runCatching null
        if (level in 0..100) {
            log.add("getBatteryLevel(): $level%")
            level
        } else {
            null
        }
    }.getOrElse {
        log.add("getBatteryLevel() refused: ${it.javaClass.simpleName}")
        null
    }
}
