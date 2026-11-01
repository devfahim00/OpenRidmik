package com.devfahim00.openridmik

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

class KbTheme(
    val id: String,
    val name: String,
    val bg: Int,
    val key: Int,
    val special: Int,
    val pressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
    val preview: Int,
)

object Themes {

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x * (1 - t) + y * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b)),
        )
    }

    private fun luminance(c: Int): Float =
        (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    /** Builds a full theme from the four base colours. */
    fun build(id: String, name: String, bg: Int, key: Int, text: Int, accent: Int): KbTheme = KbTheme(
        id = id,
        name = name,
        bg = bg,
        key = key,
        special = mix(bg, text, 0.10f),
        pressed = mix(key, text, 0.18f),
        text = text,
        hint = mix(text, bg, 0.45f),
        accent = accent,
        accentText = if (luminance(accent) > 0.6f) Color.rgb(0x20, 0x21, 0x24) else Color.WHITE,
        preview = mix(key, text, 0.08f),
    )

    private fun p(id: String, name: String, bg: String, key: String, text: String, accent: String) =
        build(id, name, Color.parseColor(bg), Color.parseColor(key), Color.parseColor(text), Color.parseColor(accent))

    val LIGHT = p("light", "Light", "#E9ECF0", "#FFFFFF", "#1F2328", "#1A73E8")
    val DARK = p("dark", "Dark", "#202124", "#3C4043", "#E8EAED", "#8AB4F8")

    val presets: List<KbTheme> = listOf(
        LIGHT,
        DARK,
        p("amoled", "AMOLED", "#000000", "#1C1C1E", "#FFFFFF", "#4FC3F7"),
        p("ocean", "Ocean", "#0B2540", "#13395F", "#E3F2FD", "#29B6F6"),
        p("forest", "Forest", "#12281C", "#1E4030", "#E8F5E9", "#66BB6A"),
        p("rose", "Rose", "#FDE8EE", "#FFFFFF", "#4A1F2E", "#E91E63"),
        p("lavender", "Lavender", "#2A1F3D", "#3D2E5C", "#F1E9FF", "#B388FF"),
        p("sunset", "Sunset", "#FFF1E0", "#FFFFFF", "#3E2723", "#FB8C00"),
    )

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun custom(c: Context): KbTheme {
        val base = if (isNight(c)) DARK else LIGHT
        return build(
            "custom", "Custom",
            Prefs.customColor(c, "bg", base.bg),
            Prefs.customColor(c, "key", base.key),
            Prefs.customColor(c, "text", base.text),
            Prefs.customColor(c, "accent", base.accent),
        )
    }

    fun resolve(c: Context, id: String): KbTheme = when (id) {
        "system" -> if (isNight(c)) DARK else LIGHT
        "custom" -> custom(c)
        else -> presets.firstOrNull { it.id == id } ?: if (isNight(c)) DARK else LIGHT
    }

    fun current(c: Context): KbTheme = resolve(c, Prefs.theme(c))

    /** Ids in the order they appear in the picker / quick-switch button. */
    fun ids(c: Context): List<String> {
        val list = ArrayList<String>()
        list.add("system")
        presets.forEach { list.add(it.id) }
        if (Prefs.hasCustomTheme(c)) list.add("custom")
        return list
    }

    fun label(id: String): String = if (id == "system") "System" else presets.firstOrNull { it.id == id }?.name ?: "Custom"

    fun next(c: Context): String {
        val all = ids(c)
        val i = all.indexOf(Prefs.theme(c))
        return all[(i + 1) % all.size]
    }
}
