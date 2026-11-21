package com.garasi.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class WeightPreset(val name: String, val grams: Int)

@Serializable
data class AppSettings(
    val defaultLang: ListingLang = ListingLang.ID,
    val city: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weightPresets: List<WeightPreset> = DEFAULT_PRESETS,
) {
    companion object {
        val DEFAULT_PRESETS = listOf(
            WeightPreset("Small (phone, book)", 500),
            WeightPreset("Medium (shoes, headphones)", 1500),
            WeightPreset("Large (appliance)", 5000),
        )
    }
}


object Presets {
    fun encode(list: List<WeightPreset>): String =
        list.joinToString("\n") { "${it.name.replace("\n", " ").replace("|", "/")}|${it.grams}" }

    fun decode(raw: String?): List<WeightPreset>? = raw?.lines()?.mapNotNull { line ->
        val i = line.lastIndexOf('|')
        if (i <= 0) null else line.substring(i + 1).toIntOrNull()?.let { WeightPreset(line.substring(0, i), it) }
    }
}
