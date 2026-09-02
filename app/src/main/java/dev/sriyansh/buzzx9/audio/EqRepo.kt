package dev.sriyansh.buzzx9.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single source of truth for EQ settings, shared by the UI and the service (same process).
 * Every mutation persists immediately and pings [onChange] so the live effect follows.
 */
object EqRepo {

    private const val PREFS = "buzzx9_eq"
    private const val K_ENABLED = "enabled"
    private const val K_GAINS = "gains"
    private const val K_PREAMP = "preamp"
    private const val K_AUTO_PREAMP = "auto_preamp"
    private const val K_PRESET = "preset"
    private const val K_AUTO_ARM = "auto_arm"
    private const val K_BOUND_ADDR = "bound_addr"
    private const val K_BOUND_NAME = "bound_name"

    private lateinit var appContext: Context
    private var loaded = false

    var enabled by mutableStateOf(false)
        private set
    var preamp by mutableStateOf(0f)
        private set
    var autoPreamp by mutableStateOf(true)
        private set
    var presetName by mutableStateOf("Flat")
        private set
    var autoArm by mutableStateOf(true)
        private set
    var boundAddress by mutableStateOf<String?>(null)
        private set
    var boundName by mutableStateOf<String?>(null)
        private set

    val gains = mutableStateListOf<Float>().apply { repeat(Bands.COUNT) { add(0f) } }

    /** Set by the service so setting changes reach the live effect chain. */
    var onChange: (() -> Unit)? = null

    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean(K_ENABLED, false)
        preamp = p.getFloat(K_PREAMP, 0f)
        autoPreamp = p.getBoolean(K_AUTO_PREAMP, true)
        presetName = p.getString(K_PRESET, "Flat") ?: "Flat"
        autoArm = p.getBoolean(K_AUTO_ARM, true)
        boundAddress = p.getString(K_BOUND_ADDR, null)
        boundName = p.getString(K_BOUND_NAME, null)
        p.getString(K_GAINS, null)?.split(',')?.let { parts ->
            if (parts.size == Bands.COUNT) {
                parts.forEachIndexed { i, s -> gains[i] = s.toFloatOrNull() ?: 0f }
            }
        }
        loaded = true
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist() {
        if (!loaded) return
        prefs().edit()
            .putBoolean(K_ENABLED, enabled)
            .putString(K_GAINS, gains.joinToString(","))
            .putFloat(K_PREAMP, preamp)
            .putBoolean(K_AUTO_PREAMP, autoPreamp)
            .putString(K_PRESET, presetName)
            .putBoolean(K_AUTO_ARM, autoArm)
            .putString(K_BOUND_ADDR, boundAddress)
            .putString(K_BOUND_NAME, boundName)
            .apply()
    }

    /** The pre-amp actually handed to the effect, honouring the auto-headroom setting. */
    fun effectivePreamp(): Float =
        if (autoPreamp) EqEngine.autoPreamp(gains.toFloatArray()) else preamp

    fun gainsArray(): FloatArray = gains.toFloatArray()

    @JvmName("setEnabledState")
    fun setEnabled(on: Boolean) {
        enabled = on
        persist()
        onChange?.invoke()
    }

    fun setGain(index: Int, db: Float) {
        gains[index] = db.coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        presetName = "Custom"
        persist()
        onChange?.invoke()
    }

    fun applyPreset(preset: Preset) {
        preset.gains.forEachIndexed { i, g -> gains[i] = g }
        presetName = preset.name
        persist()
        onChange?.invoke()
    }

    @JvmName("setPreampValue")
    fun setPreamp(db: Float) {
        preamp = db.coerceIn(-12f, 0f)
        persist()
        onChange?.invoke()
    }

    @JvmName("setAutoPreampState")
    fun setAutoPreamp(on: Boolean) {
        autoPreamp = on
        persist()
        onChange?.invoke()
    }

    @JvmName("setAutoArmState")
    fun setAutoArm(on: Boolean) {
        autoArm = on
        persist()
    }

    fun bindDevice(address: String?, name: String?) {
        boundAddress = address
        boundName = name
        persist()
    }
}
