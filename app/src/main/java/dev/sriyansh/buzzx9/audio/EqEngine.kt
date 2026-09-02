package dev.sriyansh.buzzx9.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "EqEngine"
private const val PRIORITY = 1000

/** AudioManager.AUDIO_SESSION_ID_GENERATE is -1; 0 means "the global output mix". */
const val GLOBAL_SESSION = 0

/** Which effect implementation actually took hold on a given audio session. */
enum class Backend { NONE, DYNAMICS_PROCESSING, LEGACY_EQUALIZER }

/**
 * One attached effect chain. Android gives us two options and neither is guaranteed on
 * every device, so we try the good one first and degrade rather than fail.
 *
 * DynamicsProcessing (API 28+) gives arbitrary band counts at frequencies we choose, a
 * multiband compressor and a limiter. The legacy Equalizer gives whatever bands the HAL
 * felt like exposing -- usually five, at fixed centres -- so we resample our curve onto
 * them, and lean on LoudnessEnhancer for the compression Isolation mode needs.
 */
class SessionEffect(val sessionId: Int) {

    private var dp: DynamicsProcessing? = null
    private var legacy: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null

    var backend: Backend = Backend.NONE
        private set
    var lastError: String? = null
        private set

    /** Whether the DynamicsProcessing config we got actually carries an MBC stage. */
    private var mbcAvailable = false

    /**
     * Isolation mode needs some form of compression. The real one is the MBC stage; the
     * legacy path makes do with LoudnessEnhancer, which is cruder but better than nothing.
     */
    val isolationCapable: Boolean get() = mbcAvailable || loudness != null

    fun attach(): Boolean {
        // Ask for the compressor first, but never let wanting it cost us the equalizer:
        // some HALs reject a config with an MBC stage, and a ten-band EQ with no
        // Isolation mode beats falling all the way back to the HAL's five fixed bands.
        if (tryDynamics(withMbc = true)) {
            mbcAvailable = true
            return true
        }
        if (tryDynamics(withMbc = false)) {
            mbcAvailable = false
            Log.i(TAG, "MBC rejected on session $sessionId; EQ only")
            return true
        }

        try {
            legacy = Equalizer(PRIORITY, sessionId).also { it.setEnabled(true) }
            backend = Backend.LEGACY_EQUALIZER
            // Best-effort companion for Isolation mode; absence is not fatal.
            loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy Equalizer unavailable on session $sessionId", t)
            lastError = (lastError?.plus(" | ") ?: "") +
                "Equalizer: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
            legacy = null
        }

        backend = Backend.NONE
        return false
    }

    private fun tryDynamics(withMbc: Boolean): Boolean = try {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ true, /* preEqBandCount = */ Bands.COUNT,
            /* mbcInUse = */ withMbc,
            /* mbcBandCount = */ if (withMbc) MbcBands.BAND_COUNT else 0,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true
        ).build()
        dp = DynamicsProcessing(PRIORITY, sessionId, cfg).also { it.setEnabled(true) }
        backend = Backend.DYNAMICS_PROCESSING
        true
    } catch (t: Throwable) {
        Log.w(TAG, "DynamicsProcessing(mbc=$withMbc) failed on session $sessionId", t)
        lastError = "DynamicsProcessing: ${t.javaClass.simpleName} ${t.message ?: ""}".trim()
        dp = null
        false
    }

    fun setEnabled(on: Boolean) {
        runCatching { dp?.setEnabled(on) }
        runCatching { legacy?.setEnabled(on) }
        runCatching { loudness?.setEnabled(on && loudnessWanted) }
    }

    private var loudnessWanted = false

    fun apply(gainsDb: FloatArray, preampDb: Float, isolation: IsolationStrength?) {
        dp?.let { applyDynamics(it, gainsDb, preampDb, isolation) }
        legacy?.let { applyLegacy(it, gainsDb) }
        applyLoudness(isolation)
    }

    private fun applyDynamics(
        d: DynamicsProcessing,
        gainsDb: FloatArray,
        preampDb: Float,
        isolation: IsolationStrength?
    ) {
        runCatching {
            for (ch in 0 until d.channelCount) {
                val eq = d.getPreEqByChannelIndex(ch)
                eq.isEnabled = true
                for (b in 0 until Bands.COUNT) {
                    val band = eq.getBand(b)
                    band.isEnabled = true
                    band.cutoffFrequency = Bands.FREQS[b]
                    band.gain = gainsDb[b]
                    eq.setBand(b, band)
                }
                d.setPreEqByChannelIndex(ch, eq)

                // Isolation mode: compress each band so quiet detail rises above the
                // ambient noise floor without the loud parts getting louder.
                if (mbcAvailable) {
                    val mbc = d.getMbcByChannelIndex(ch)
                    mbc.isEnabled = isolation != null
                    if (isolation != null) {
                        for (b in 0 until MbcBands.BAND_COUNT) {
                            val band = mbc.getBand(b)
                            band.isEnabled = true
                            band.cutoffFrequency = MbcBands.FREQS[b]
                            band.attackTime = 8f
                            band.releaseTime = 120f
                            band.ratio = isolation.ratio
                            band.threshold = isolation.thresholdDb
                            band.kneeWidth = 6f
                            band.noiseGateThreshold = -90f
                            band.expanderRatio = 1f
                            band.preGain = 0f
                            band.postGain = isolation.makeupDb
                            mbc.setBand(b, band)
                        }
                    }
                    d.setMbcByChannelIndex(ch, mbc)
                }

                // Catches the transients that survive the pre-amp headroom.
                val lim = d.getLimiterByChannelIndex(ch)
                lim.isEnabled = true
                lim.threshold = -1f
                lim.ratio = 10f
                lim.attackTime = 1f
                lim.releaseTime = 60f
                lim.postGain = 0f
                d.setLimiterByChannelIndex(ch, lim)
            }
            d.setInputGainAllChannelsTo(preampDb)
        }.onFailure { Log.w(TAG, "applyDynamics failed", it) }
    }

    /**
     * The HAL equalizer exposes its own fixed centres. For each one, take the gain of our
     * nearest band -- crude, but it is a fallback path, not the intended one.
     */
    private fun applyLegacy(e: Equalizer, gainsDb: FloatArray) {
        runCatching {
            val range = e.bandLevelRange // millibels, [min, max]
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            for (b in 0 until e.numberOfBands.toInt()) {
                val centreHz = e.getCenterFreq(b.toShort()) / 1000f
                val nearest = Bands.FREQS.indices.minBy {
                    kotlin.math.abs(kotlin.math.ln(Bands.FREQS[it] / centreHz.coerceAtLeast(1f)))
                }
                val mb = (gainsDb[nearest] * 100f).roundToInt().coerceIn(lo, hi)
                e.setBandLevel(b.toShort(), mb.toShort())
            }
        }.onFailure { Log.w(TAG, "applyLegacy failed", it) }
    }

    /** Crude stand-in for MBC when only the legacy chain exists. */
    private fun applyLoudness(isolation: IsolationStrength?) {
        val le = loudness ?: return
        loudnessWanted = isolation != null
        runCatching {
            le.setTargetGain(((isolation?.makeupDb ?: 0f) * 100f).roundToInt())
            le.setEnabled(loudnessWanted)
        }.onFailure { Log.w(TAG, "applyLoudness failed", it) }
    }

    fun release() {
        runCatching { dp?.release() }
        runCatching { legacy?.release() }
        runCatching { loudness?.release() }
        dp = null
        legacy = null
        loudness = null
        backend = Backend.NONE
    }
}

/**
 * Owns every attached session. Session 0 is the global output mix and is the one that
 * matters -- it covers Spotify, YouTube, everything -- but some ROMs ignore it, so we also
 * accept per-app sessions announced via the standard audio-effect-control broadcast.
 */
class EqEngine {

    private val effects = LinkedHashMap<Int, SessionEffect>()

    @Synchronized
    fun openSession(sessionId: Int): SessionEffect? {
        effects[sessionId]?.let { return it }
        val fx = SessionEffect(sessionId)
        val ok = fx.attach()
        if (!ok) {
            Log.w(TAG, "Could not attach any effect to session $sessionId: ${fx.lastError}")
        }
        effects[sessionId] = fx
        return if (ok) fx else null
    }

    @Synchronized
    fun closeSession(sessionId: Int) {
        effects.remove(sessionId)?.release()
    }

    @Synchronized
    fun openGlobal(): SessionEffect? = openSession(GLOBAL_SESSION)

    @Synchronized
    fun applyAll(
        enabled: Boolean,
        gainsDb: FloatArray,
        preampDb: Float,
        isolation: IsolationStrength?
    ) {
        for (fx in effects.values) {
            fx.setEnabled(enabled)
            if (enabled) fx.apply(gainsDb, preampDb, isolation)
        }
    }

    @Synchronized
    fun releaseAll() {
        effects.values.forEach { it.release() }
        effects.clear()
    }

    @Synchronized
    fun status(): EngineStatus {
        val global = effects[GLOBAL_SESSION]
        return EngineStatus(
            globalBackend = global?.backend ?: Backend.NONE,
            globalError = global?.lastError,
            isolationSupported = global?.isolationCapable ?: false,
            sessionCount = effects.size,
            extraSessions = effects.keys.filter { it != GLOBAL_SESSION }
        )
    }

    companion object {
        /**
         * Headroom so that boosted bands do not clip the mixer. Negative dB equal to the
         * largest positive band gain, plus whatever make-up gain Isolation mode adds on
         * top, which is the standard conservative choice.
         */
        fun autoPreamp(gainsDb: FloatArray, isolation: IsolationStrength?): Float {
            var maxBoost = 0f
            for (g in gainsDb) maxBoost = max(maxBoost, g)
            return -(maxBoost + (isolation?.makeupDb ?: 0f))
        }
    }
}

data class EngineStatus(
    val globalBackend: Backend,
    val globalError: String?,
    /** False when the HAL refused a multiband-compressor stage, so Isolation cannot run. */
    val isolationSupported: Boolean,
    val sessionCount: Int,
    val extraSessions: List<Int>
)
