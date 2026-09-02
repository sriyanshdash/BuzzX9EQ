package dev.sriyansh.buzzx9.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * A steady sine you can leave running while you drag a band. If the tone's loudness does
 * not change when the matching band moves, the effect is not reaching the output -- which
 * on Bluetooth almost always means A2DP hardware offload is bypassing the effects chain.
 */
object TestTone {

    private const val SAMPLE_RATE = 48000

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    var playingFreq: Float? = null
        private set

    fun isPlaying() = playingFreq != null

    fun toggle(freqHz: Float) {
        if (playingFreq == freqHz) stop() else start(freqHz)
    }

    @Synchronized
    fun start(freqHz: Float) {
        stop()
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            // A generated session, exactly like a music app would use. The global
            // session-0 effect still applies on top, which is what we are testing.
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()

        track = t
        playingFreq = freqHz
        t.play()

        thread(name = "buzzx9-tone", isDaemon = true) {
            val frames = 1024
            val buf = FloatArray(frames * 2)
            var phase = 0.0
            val step = 2.0 * PI * freqHz / SAMPLE_RATE
            // -12 dBFS: loud enough to judge, quiet enough to leave headroom for boosts.
            val amp = 0.25f
            while (track === t && playingFreq != null) {
                for (i in 0 until frames) {
                    val s = (sin(phase) * amp).toFloat()
                    buf[i * 2] = s
                    buf[i * 2 + 1] = s
                    phase += step
                    if (phase > 2 * PI) phase -= 2 * PI
                }
                val written = runCatching {
                    t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                }.getOrDefault(-1)
                if (written < 0) break
            }
            runCatching { t.stop() }
            runCatching { t.release() }
        }
    }

    @Synchronized
    fun stop() {
        playingFreq = null
        track = null
    }
}
