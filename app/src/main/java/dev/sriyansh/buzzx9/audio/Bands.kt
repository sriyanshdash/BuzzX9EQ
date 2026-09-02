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

    fun flat() = FloatArray(COUNT)
}
