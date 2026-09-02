package dev.sriyansh.buzzx9.bt

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

private const val TAG = "BtMonitor"

data class BtDeviceInfo(
    val name: String,
    val address: String,
    val batteryPercent: Int?, // null when the stack will not tell us
    val isLikelyBuzz: Boolean
)

/**
 * Watches which A2DP sink is actually connected. This is what "auto-arm" keys off, and it
 * is also how the app knows the buds it is meant to be tuning are the ones in your ears.
 */
object BtMonitor {

    private var appContext: Context? = null
    private var a2dp: BluetoothA2dp? = null
    private var registered = false

    var connected = mutableStateListOf<BtDeviceInfo>()
        private set

    var adapterOn by mutableStateOf(false)
        private set

    var hasPermission by mutableStateOf(false)
        private set

    /** Fired when the bound device connects or disconnects. */
    var onBoundDeviceChanged: ((connected: Boolean, device: BtDeviceInfo?) -> Unit)? = null

    private var boundWasConnected = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_STATE_CHANGED -> refresh()
            }
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        refreshPermission(ctx)

        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }

        val adapter = adapter(ctx)
        if (adapter != null && a2dp == null) {
            runCatching {
                adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (profile == BluetoothProfile.A2DP) {
                            a2dp = proxy as BluetoothA2dp
                            refresh()
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.A2DP) a2dp = null
                    }
                }, BluetoothProfile.A2DP)
            }
        }
        refresh()
    }

    fun refreshPermission(context: Context) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun refresh() {
        val ctx = appContext ?: return
        refreshPermission(ctx)
        val adapter = adapter(ctx)
        adapterOn = adapter?.isEnabled == true

        val list = mutableListOf<BtDeviceInfo>()
        if (hasPermission && adapterOn) {
            runCatching {
                a2dp?.connectedDevices?.forEach { d -> list.add(describe(d)) }
            }.onFailure { Log.w(TAG, "reading connected A2DP devices failed", it) }
        }

        connected.clear()
        connected.addAll(list)

        val boundAddr = dev.sriyansh.buzzx9.audio.EqRepo.boundAddress
        val match = list.firstOrNull { it.address == boundAddr }
        val nowConnected = match != null
        if (nowConnected != boundWasConnected) {
            boundWasConnected = nowConnected
            onBoundDeviceChanged?.invoke(nowConnected, match)
        }
    }

    private fun describe(d: BluetoothDevice): BtDeviceInfo {
        val name = runCatching { d.name }.getOrNull() ?: "(unnamed)"
        return BtDeviceInfo(
            name = name,
            address = d.address,
            batteryPercent = readBattery(d),
            isLikelyBuzz = name.contains("buzz", ignoreCase = true) ||
                name.contains("dubstep", ignoreCase = true)
        )
    }

    /**
     * BluetoothDevice.getBatteryLevel() is a hidden API. On some builds reflection still
     * works; on others the non-SDK-interface blocklist kills it. There is no public
     * replacement, so we ask nicely and report "unknown" when refused -- never a guess.
     */
    private fun readBattery(d: BluetoothDevice): Int? = runCatching {
        val m = BluetoothDevice::class.java.getMethod("getBatteryLevel")
        val level = m.invoke(d) as? Int ?: return@runCatching null
        if (level in 0..100) level else null
    }.getOrNull()

    fun stop() {
        val ctx = appContext ?: return
        if (registered) {
            runCatching { ctx.unregisterReceiver(receiver) }
            registered = false
        }
        a2dp?.let { proxy ->
            runCatching { adapter(ctx)?.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
        }
        a2dp = null
    }
}
