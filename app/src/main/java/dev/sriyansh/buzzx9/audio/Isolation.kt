package dev.sriyansh.buzzx9.audio

/**
 * Multiband compression settings for Isolation mode.
 *
 * What this is NOT: noise cancellation. The Buzz X9 advertises ENC, which cleans up your
 * voice on the microphone for whoever you are calling. It does nothing for what you hear,
 * it runs only during calls, and it lives in the earbud firmware where no app can reach
 * it. These buds have no ANC at all.
 *
 * What this IS: the honest alternative. In a noisy place the quiet parts of a track fall
 * below the ambient noise floor and vanish, so you reach for the volume and then get
 * blasted by the loud parts. Compressing each frequency band separately lifts those quiet
 * passages while leaving the loud ones alone, so the music stays audible without the
 * volume war. The tonal balance -- your EQ curve -- is untouched.
 */
enum class IsolationStrength(
    val label: String,
    val ratio: Float,
    val thresholdDb: Float,
    val makeupDb: Float,
    val blurb: String
) {
    LIGHT(
        "Light", 2f, -24f, 2f,
        "Barely there. Evens out the biggest dips without changing the character."
    ),
    MEDIUM(
        "Medium", 3f, -30f, 4f,
        "The commute setting. Quiet detail survives traffic and cabin noise."
    ),
    STRONG(
        "Strong", 4f, -36f, 6f,
        "Everything pushed forward. Effective on an aeroplane, flattening on a good track."
    );

    companion object {
        fun byName(name: String?) =
            IsolationStrength.entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Crossover points for the compressor. Four bands is plenty and costs little CPU. */
object MbcBands {
    const val BAND_COUNT = 4
    val FREQS = floatArrayOf(200f, 1000f, 4000f, 20000f)
}
