package dev.sriyansh.buzzx9.audio

/**
 * Curves are dB offsets per band, ordered like [Bands.FREQS].
 *
 * Honest caveat: "Reference" is NOT a measured correction. Nobody has published a
 * frequency-response measurement of the Buzz X9, and this app cannot measure one. It is
 * a starting point built from the failure modes that budget TWS drivers share -- a
 * bloated upper-bass hump around 125 Hz that muddies everything above it, a scooped
 * lower midrange, and a sharp presence peak near 8 kHz. Trust your ears over the label.
 */
data class Preset(val name: String, val gains: FloatArray, val note: String) {
    override fun equals(other: Any?) = other is Preset && other.name == name
    override fun hashCode() = name.hashCode()
}

object Presets {
    val ALL = listOf(
        Preset(
            "Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "No processing. Use this to A/B everything else."
        ),
        Preset(
            "Reference", floatArrayOf(1f, 0f, -3f, -2f, 1f, 2.5f, 2f, 0f, -2.5f, -1f),
            "Trims the upper-bass mud, restores the scooped mids, tames the presence peak."
        ),
        Preset(
            "Bass Boost", floatArrayOf(6f, 5f, 3f, 0f, -1f, -1f, 0f, 0f, 0f, 0f),
            "Sub-bass lift with a slight mid cut so it stays clean rather than boomy."
        ),
        Preset(
            "Vocal Clarity", floatArrayOf(-2f, -3f, -3f, -1f, 2f, 4f, 3.5f, 1.5f, -1f, -2f),
            "Pulls speech and lead vocals forward. Good for calls and acoustic tracks."
        ),
        Preset(
            "Treble Lift", floatArrayOf(0f, 0f, -1f, -1f, 0f, 1f, 2f, 4f, 4.5f, 3f),
            "Adds air and detail. Fatiguing at volume -- back off if cymbals hiss."
        ),
        Preset(
            "Gaming", floatArrayOf(-1f, -2f, -3f, -2f, 0f, 2f, 5f, 5.5f, 3f, 0f),
            "Emphasises footsteps and reloads (2-4 kHz) and cuts explosion rumble."
        ),
        Preset(
            "Podcast", floatArrayOf(-6f, -5f, -3f, 0f, 3f, 4f, 3f, 1f, -2f, -4f),
            "Aggressive bandpass around the voice. Kills traffic and HVAC noise."
        ),
        Preset(
            "Loudness", floatArrayOf(5f, 4f, 1f, -1f, -2f, -1f, 0f, 2f, 4f, 4f),
            "Equal-loudness smile for very quiet listening. Muddy at high volume."
        )
    )

    fun byName(name: String?) = ALL.firstOrNull { it.name == name }
}
