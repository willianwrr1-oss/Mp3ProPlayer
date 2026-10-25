package com.willian.mp3pro.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guarda os ajustes de áudio (persistidos) e os distribui ao processador de
 * áudio e à interface. É um singleton porque o serviço de reprodução e a tela
 * rodam no mesmo processo.
 */
object FxStore {
    private const val PREFS = "fx_settings"
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(FxSettings())
    val state: StateFlow<FxSettings> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _state.value = load(p)
    }

    fun update(transform: (FxSettings) -> FxSettings) {
        val novo = transform(_state.value)
        _state.value = novo
        prefs?.let { save(it, novo) }
    }

    fun applyPreset(preset: FxPreset) = update { atual ->
        preset.settings.copy(enabled = atual.enabled, preamp = atual.preamp)
    }

    private fun save(p: SharedPreferences, s: FxSettings) {
        p.edit()
            .putBoolean("enabled", s.enabled)
            .putString("bands", s.bands.joinToString(","))
            .putFloat("preamp", s.preamp)
            .putFloat("loudness", s.loudness)
            .putFloat("bass", s.bass)
            .putFloat("surround", s.surround)
            .putFloat("clarity", s.clarity)
            .putString("preset", s.presetName)
            .apply()
    }

    private fun load(p: SharedPreferences): FxSettings {
        val padrao = FxSettings()
        val bandas = p.getString("bands", null)
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == FxBands.COUNT }
            ?: padrao.bands
        return FxSettings(
            enabled = p.getBoolean("enabled", true),
            bands = bandas,
            preamp = p.getFloat("preamp", 0f),
            loudness = p.getFloat("loudness", 0f),
            bass = p.getFloat("bass", 0f),
            surround = p.getFloat("surround", 0f),
            clarity = p.getFloat("clarity", 0f),
            presetName = p.getString("preset", padrao.presetName) ?: padrao.presetName
        )
    }
}
