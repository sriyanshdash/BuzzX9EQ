package dev.sriyansh.buzzx9.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.sriyansh.buzzx9.MainActivity
import dev.sriyansh.buzzx9.R
import dev.sriyansh.buzzx9.audio.EngineStatus
import dev.sriyansh.buzzx9.audio.EqEngine
import dev.sriyansh.buzzx9.audio.EqRepo
import dev.sriyansh.buzzx9.audio.Presets
import dev.sriyansh.buzzx9.bt.BtMonitor

private const val TAG = "EqService"
private const val CHANNEL_ID = "buzzx9_eq"
private const val NOTIF_ID = 42

/**
 * Holds the effect chain open. Android releases an AudioEffect as soon as the object that
 * created it is collected, so something long-lived has to own it. A foreground service is
 * the only thing that survives the app being swiped away.
 */
class EqService : Service() {

    private val engine = EqEngine()

    /**
     * Media apps announce their audio session with these broadcasts so a system equalizer
     * can hook in. Registering at runtime rather than in the manifest sidesteps the
     * implicit-broadcast restrictions, at the cost of only catching sessions that open
     * while we are already running -- which is exactly when we care.
     */
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?"
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.i(TAG, "session $session opened by $pkg")
                    engine.openSession(session)
                    reapply()
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    Log.i(TAG, "session $session closed by $pkg")
                    engine.closeSession(session)
                    publishStatus()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        EqRepo.init(this)
        createChannel()
        // API 34+ insists the type be named at start time; it must match the manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        ContextCompat.registerReceiver(
            this,
            sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED
        )

        engine.openGlobal()

        EqRepo.onChange = { reapply() }

        BtMonitor.onBoundDeviceChanged = { connected, device ->
            if (EqRepo.autoArm) {
                Log.i(TAG, "bound device ${device?.name} connected=$connected")
                reapply()
                updateNotification()
            }
        }
        BtMonitor.start(this)

        reapply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE -> {
                EqRepo.setEnabled(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> reapply()
        }
        return START_STICKY
    }

    /**
     * Auto-arm semantics: when it is on and a device is bound, the curve is only live while
     * that device is actually connected. Speaker output stays untouched.
     */
    private fun shouldBeActive(): Boolean {
        if (!EqRepo.enabled) return false
        if (!EqRepo.autoArm) return true
        val bound = EqRepo.boundAddress ?: return true
        return BtMonitor.connected.any { it.address == bound }
    }

    private fun reapply() {
        val live = shouldBeActive()
        engine.applyAll(
            live, EqRepo.gainsArray(), EqRepo.effectivePreamp(), EqRepo.activeIsolation()
        )
        publishStatus()
        updateNotification()
    }

    private fun publishStatus() {
        status = engine.status()
        active = shouldBeActive()
    }

    // ------------------------------------------------------------------ notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Equalizer status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that the equalizer is holding the audio effect open."
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val off = PendingIntent.getService(
            this, 1,
            Intent(this, EqService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_IMMUTABLE
        )

        val bound = EqRepo.boundName
        val live = shouldBeActive()
        val text = when {
            !live && bound != null -> "Waiting for $bound"
            !live -> "Idle"
            else -> {
                val preset = Presets.byName(EqRepo.presetName)?.name ?: EqRepo.presetName
                val iso = EqRepo.activeIsolation()
                if (iso == null) preset else "$preset  +  Isolation ${iso.label}"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (live) "Equalizer active" else "Equalizer standby")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Turn off", off)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionReceiver) }
        EqRepo.onChange = null
        BtMonitor.onBoundDeviceChanged = null
        engine.releaseAll()
        status = null
        active = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_DISABLE = "dev.sriyansh.buzzx9.DISABLE"
        const val ACTION_REFRESH = "dev.sriyansh.buzzx9.REFRESH"

        /** Live engine diagnostics, observed by the UI. Null when the service is down. */
        var status by mutableStateOf<EngineStatus?>(null)

        /** Whether the curve is currently being applied (vs. armed but waiting). */
        var active by mutableStateOf(false)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EqService::class.java))
        }

        /**
         * Nudges a running service to re-read settings. Deliberately a no-op when the
         * equalizer is off, so dragging a slider never resurrects the service.
         */
        fun refresh(context: Context) {
            if (!EqRepo.enabled) return
            runCatching {
                context.startService(
                    Intent(context, EqService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EqService::class.java))
        }
    }
}
