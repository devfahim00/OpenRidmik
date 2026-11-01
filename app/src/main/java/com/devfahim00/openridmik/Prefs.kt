package com.devfahim00.openridmik

import android.content.Context

object Prefs {
    private val DEFAULTS = mapOf(
        "haptic" to true,
        "sound" to false,
        "numberRow" to true,
        "suggestions" to true,
        "banglaDigits" to true,
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
}
