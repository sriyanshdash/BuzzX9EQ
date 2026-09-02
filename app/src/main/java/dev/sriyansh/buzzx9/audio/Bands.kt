package dev.sriyansh.buzzx9.audio

/**
 * Ten ISO-standard octave centres. These are the crossover points handed to
 * DynamicsProcessing's pre-EQ, which treats each entry as the upper edge of its band,
 * so the array must stay sorted ascending.
 */
object Bands {
    const val COUNT = 10
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    val LABELS = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    /** Two or three words naming what lives in the band. Shown beside the slider. */
    val NAMES = arrayOf(
        "Sub-bass",
        "Bass",
        "Upper bass",
        "Low mids",
        "Mids",
        "Core mids",
        "Upper mids",
        "Presence",
        "Brilliance",
        "Air"
    )

    /**
     * Plain-language guide, written for someone who has never touched an equalizer.
     * Each one says what you hear if you raise it and what goes wrong if you overdo it,
     * because "boost 250 Hz" means nothing without knowing it turns things boxy.
     */
    val DESCRIPTIONS = arrayOf(
        "The rumble you feel more than hear. Cinema explosions, deep synth bass. " +
            "Small earbuds can barely produce it, so big boosts here mostly waste headroom.",
        "The punch of a kick drum and the low notes of a bass guitar. " +
            "Raise for weight; too much and everything turns boomy.",
        "Warmth and fullness. This is the band budget earbuds usually overdo, " +
            "so cutting it is often the single biggest cleanup you can make.",
        "The body of male vocals, guitars and snare. " +
            "Too much sounds boxy, like music playing inside a cupboard.",
        "Fullness of the midrange. Cutting a little here opens up a crowded mix; " +
            "too much boost sounds honky or nasal.",
        "Where most instruments and voices actually live. " +
            "Changes here affect nearly everything, so move this one gently.",
        "Clarity and attack. Raise it to make vocals cut through on a noisy bus. " +
            "Overdone, it gets shouty and tiring.",
        "Consonants and the crack of a snare. This is what makes speech intelligible. " +
            "Too much turns harsh and edgy.",
        "Cymbals and the hiss of S sounds. Cut it if singers sound spitty; " +
            "raise it for detail.",
        "The last octave, felt as openness and sparkle. " +
            "Most earbuds roll off up here anyway, so the effect is subtle."
    )

    fun flat() = FloatArray(COUNT)
}
