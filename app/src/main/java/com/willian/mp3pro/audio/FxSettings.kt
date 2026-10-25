package com.willian.mp3pro.audio

/** Frequências centrais das 6 faixas do equalizador (Hz). */
object FxBands {
    val freqs = floatArrayOf(60f, 250f, 1000f, 3000f, 8000f, 14000f)
    val labels = listOf("60 Hz", "250 Hz", "1 kHz", "3 kHz", "8 kHz", "14 kHz")
    const val COUNT = 6
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

/**
 * Todos os ajustes de áudio. Os efeitos (loudness, graves, espacial, clareza)
 * vão de 0.0 (desligado) a 1.0 (máximo).
 */
data class FxSettings(
    val enabled: Boolean = true,
    val bands: List<Float> = List(FxBands.COUNT) { 0f },
    val preamp: Float = 0f,
    val loudness: Float = 0f,
    val bass: Float = 0f,
    val surround: Float = 0f,
    val clarity: Float = 0f,
    val presetName: String = "Plano"
) {
    fun withBand(index: Int, db: Float): FxSettings {
        val novo = bands.toMutableList()
        novo[index] = db.coerceIn(FxBands.MIN_DB, FxBands.MAX_DB)
        return copy(bands = novo, presetName = "Personalizado")
    }
}

data class FxPreset(val name: String, val settings: FxSettings)

object FxPresets {
    private fun p(
        name: String,
        bands: List<Float>,
        loudness: Float = 0f,
        bass: Float = 0f,
        surround: Float = 0f,
        clarity: Float = 0f
    ) = FxPreset(
        name,
        FxSettings(
            bands = bands, loudness = loudness, bass = bass,
            surround = surround, clarity = clarity, presetName = name
        )
    )

    val all: List<FxPreset> = listOf(
        p("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f)),
        p("Pop", listOf(-1f, 2f, 4f, 3f, 1f, -1f)),
        p("Rock", listOf(5f, 3f, -1f, -2f, 3f, 5f)),
        p("Eletrônica", listOf(6f, 4f, 0f, 1f, 3f, 5f)),
        p("Vocal", listOf(-2f, -1f, 3f, 5f, 3f, 0f)),
        p("Graves++", listOf(8f, 5f, 1f, 0f, 0f, 0f)),
        p("Agudos+", listOf(0f, 0f, 0f, 2f, 5f, 7f)),
        p(
            "Turbo (Loud + Surround)",
            listOf(4f, 2f, 0f, 1f, 3f, 4f),
            loudness = 0.6f, bass = 0.4f, surround = 0.5f, clarity = 0.3f
        )
    )
}
