package dev.sriyansh.buzzx9.audio

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/**
 * Curves are dB offsets per band, ordered like [Bands.FREQS].
 *
 * Honest caveat: "Reference" is NOT a measured correction. Nobody has published a
 * frequency-response measurement of the Buzz X9, and this app cannot measure one. It is
 * a starting point built from the failure modes that budget TWS drivers share -- a
 * bloated upper-bass hump around 125 Hz that muddies everything above it, a scooped
 * lower midrange, and a sharp presence peak near 8 kHz. Trust your ears over the label.
 */
data class Preset(
    val name: String,
    val gains: FloatArray,
    val note: String,
    val userDefined: Boolean = false
) {
    override fun equals(other: Any?) = other is Preset && other.name == name
    override fun hashCode() = name.hashCode()
}

object Presets {

    val BUILT_IN = listOf(
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

    /** User-saved curves, loaded from prefs at startup. Observable so the chip row updates. */
    val custom = mutableStateListOf<Preset>()

    val all: List<Preset> get() = BUILT_IN + custom

    fun byName(name: String?): Preset? = all.firstOrNull { it.name == name }

    fun isNameTaken(name: String) = all.any { it.name.equals(name, ignoreCase = true) }

    // ------------------------------------------------------------------ persistence

    private const val PREFS = "buzzx9_presets"
    private const val KEY = "custom"

    // name and gains are joined with characters a user cannot type into the name field.
    private const val REC_SEP = "\u001E"
    private const val FIELD_SEP = "\u001F"

    private lateinit var appContext: Context

    fun load(context: Context) {
        appContext = context.applicationContext
        custom.clear()
        val raw = prefs().getString(KEY, null) ?: return
        for (record in raw.split(REC_SEP)) {
            if (record.isBlank()) continue
            val parts = record.split(FIELD_SEP)
            if (parts.size < 2) continue
            val gains = parts[1].split(',').mapNotNull { it.toFloatOrNull() }
            if (gains.size != Bands.COUNT) continue
            custom.add(
                Preset(
                    name = parts[0],
                    gains = gains.toFloatArray(),
                    note = parts.getOrNull(2).orEmpty().ifBlank { "Your own curve." },
                    userDefined = true
                )
            )
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist() {
        if (!::appContext.isInitialized) return
        val raw = custom.joinToString(REC_SEP) { p ->
            p.name + FIELD_SEP + p.gains.joinToString(",") + FIELD_SEP + p.note
        }
        prefs().edit().putString(KEY, raw).apply()
    }

    /** Saves (or overwrites) a user preset. Returns the stored preset. */
    fun save(name: String, gains: FloatArray, note: String = ""): Preset {
        val clean = sanitize(name)
        val preset = Preset(
            name = clean,
            gains = gains.copyOf(),
            note = note.ifBlank { "Your own curve." },
            userDefined = true
        )
        val existing = custom.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
        if (existing >= 0) custom[existing] = preset else custom.add(preset)
        persist()
        return preset
    }

    fun delete(preset: Preset) {
        custom.removeAll { it.name == preset.name }
        persist()
    }

    /** Strips the separators and trims, so a saved name can never corrupt the store. */
    fun sanitize(name: String): String =
        name.replace(REC_SEP, "").replace(FIELD_SEP, "").trim().take(24)
}
