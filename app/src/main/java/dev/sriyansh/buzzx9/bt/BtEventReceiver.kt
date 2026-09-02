package dev.sriyansh.buzzx9.bt

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.service.EqService

/**
 * Wakes the effect service back up after a reboot, and when any Bluetooth audio device
 * connects while the app is not running.
 *
 * Starting a foreground service from the background is normally forbidden on Android 12+.
 * Both triggers here are on the documented exemption list: BOOT_COMPLETED (for the
 * specialUse type) and Bluetooth broadcasts that require BLUETOOTH_CONNECT.
 */
class BtEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        EqRepo.init(context)
        if (!EqRepo.enabled) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                runCatching { EqService.start(context) }
                    .onFailure { Log.w("BtEventReceiver", "could not start EqService", it) }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Service stays up; BtMonitor will notice and drop the auto-armed profile.
                BtMonitor.refresh()
            }
        }
    }
}
