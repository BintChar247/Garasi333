package com.garasi.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny SharedPreferences-backed settings store exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("garasi_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        defaultLang = runCatching { ListingLang.valueOf(prefs.getString("lang", "ID")!!) }.getOrDefault(ListingLang.ID),
        city = prefs.getString("city", "") ?: "",
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        weightPresets = decodePresets(prefs.getString("presets", null)) ?: AppSettings.DEFAULT_PRESETS,
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("lang", s.defaultLang.name)
            .putString("city", s.city)
            .putString("theme", s.theme.name)
            .putString("presets", encodePresets(s.weightPresets))
            .apply()
        _state.value = s
    }

    companion object {
        fun encodePresets(list: List<WeightPreset>): String = Presets.encode(list)
        fun decodePresets(raw: String?): List<WeightPreset>? = Presets.decode(raw)
    }
}
