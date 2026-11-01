package com.devfahim00.openridmik

import android.content.Context

object Prefs {
    private val DEFAULTS = mapOf(
        "haptic" to true,
        "sound" to false,
        "numberRow" to true,
        "suggestions" to true,
        "banglaDigits" to true,
        "clipboard" to true,
    )

    private fun sp(c: Context) = c.getSharedPreferences("openridmik", Context.MODE_PRIVATE)

    fun get(c: Context, key: String): Boolean = sp(c).getBoolean(key, DEFAULTS[key] ?: false)

    fun set(c: Context, key: String, value: Boolean) {
        sp(c).edit().putBoolean(key, value).apply()
    }

    fun isBangla(c: Context): Boolean = sp(c).getString("lang", "bn") == "bn"

    fun setBangla(c: Context, bangla: Boolean) {
        sp(c).edit().putString("lang", if (bangla) "bn" else "en").apply()
    }

    /** Bangla typing layout: "avro" (phonetic) or "probhat" (fixed). */
    fun layout(c: Context): String = sp(c).getString("layout", "avro") ?: "avro"

    fun setLayout(c: Context, layout: String) {
        sp(c).edit().putString("layout", layout).apply()
    }

    /** Theme id: "system", one of the presets in [Themes], or "custom". */
    fun theme(c: Context): String = sp(c).getString("theme", "system") ?: "system"

    fun setTheme(c: Context, id: String) {
        sp(c).edit().putString("theme", id).apply()
    }

    fun hasCustomTheme(c: Context): Boolean = sp(c).contains("custom_bg")

    fun customColor(c: Context, name: String, def: Int): Int = sp(c).getInt("custom_$name", def)

    fun setCustomColors(c: Context, bg: Int, key: Int, text: Int, accent: Int) {
        sp(c).edit()
            .putInt("custom_bg", bg)
            .putInt("custom_key", key)
            .putInt("custom_text", text)
            .putInt("custom_accent", accent)
            .apply()
    }

    fun getString(c: Context, key: String, def: String = ""): String = sp(c).getString(key, def) ?: def

    fun setString(c: Context, key: String, value: String) {
        sp(c).edit().putString(key, value).apply()
    }
}
