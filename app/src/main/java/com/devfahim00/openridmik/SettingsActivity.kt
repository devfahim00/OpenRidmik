package com.devfahim00.openridmik

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    /** Small preview card of a keyboard theme. */
    private class Swatch(ctx: Context, val t: KbTheme, val selected: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = ctx.resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.style = Paint.Style.FILL
            p.color = t.bg
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 12 * d, 12 * d, p)
            val pad = 8 * d
            val gap = 4 * d
            val kw = (w - 2 * pad - 3 * gap) / 4
            val kh = (h - 2 * pad - gap) / 2
            for (r in 0..1) {
                for (c in 0..3) {
                    p.color = when {
                        r == 1 && c == 3 -> t.accent
                        r == 1 && c == 0 -> t.special
                        else -> t.key
                    }
                    val l = pad + c * (kw + gap)
                    val tp = pad + r * (kh + gap)
                    canvas.drawRoundRect(RectF(l, tp, l + kw, tp + kh), 4 * d, 4 * d, p)
                }
            }
            if (selected) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3 * d
                p.color = t.accent
                canvas.drawRoundRect(RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d), 12 * d, 12 * d, p)
            }
        }
    }

    private lateinit var swatchRow: LinearLayout
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun parseColor(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return try {
            Color.parseColor(if (t.startsWith("#")) t else "#$t") or 0xFF000000.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = getColor(R.color.kb_bg)
        val surface = getColor(R.color.key_bg)
        val line = getColor(R.color.key_special_bg)
        val fg = getColor(R.color.key_text)
        val muted = getColor(R.color.key_hint)
        val accent = getColor(R.color.accent)
        val onAccent = getColor(R.color.accent_text)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!Themes.isNight(this)) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }

        fun lp(top: Int = 0) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }

        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        fun section(title: String) {
            root.addView(
                text(title.uppercase(), 12f, muted, bold = true).apply {
                    letterSpacing = 0.08f
                    setPadding(dp(8), 0, 0, 0)
                },
                lp(24).apply { bottomMargin = dp(8) },
            )
        }

        fun card(): LinearLayout {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(surface, 16)
            }
            root.addView(c, lp())
            return c
        }

        fun divider(c: LinearLayout) {
            c.addView(
                View(this).apply { setBackgroundColor(line) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    leftMargin = dp(16)
                },
            )
        }

        fun row(c: LinearLayout, first: Boolean, title: String, trailing: View? = null, onClick: (() -> Unit)? = null): TextView {
            if (!first) divider(c)
            val label = text(title, 16f, fg)
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(16), 0, dp(16), 0)
                addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (trailing != null) addView(trailing)
                if (onClick != null) {
                    isClickable = true
                    setOnClickListener { onClick() }
                }
            }
            c.addView(r, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return label
        }

        fun chevron() = text("›", 22f, muted)

        fun toggleRow(c: LinearLayout, first: Boolean, title: String, key: String) {
            val sw = Switch(this).apply {
                isChecked = Prefs.get(this@SettingsActivity, key)
                setOnCheckedChangeListener { _, on -> Prefs.set(this@SettingsActivity, key, on) }
            }
            row(c, first, title, sw) { sw.toggle() }
        }

        // ---- header ----
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                setPadding(dp(8), 0, dp(8), 0)
                addView(text("OpenRidmik", 30f, fg, bold = true))
                addView(
                    text("v${Updater.currentVersion(this@SettingsActivity)}", 13f, muted).apply { setPadding(dp(10), 0, 0, dp(5)) },
                )
            },
            lp(),
        )

        // ---- update banner (only when a newer release exists) ----
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(accent, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            visibility = View.GONE
        }
        val bannerTitle = text("", 16f, onAccent, bold = true)
        banner.addView(bannerTitle)
        banner.addView(text("Tap to download", 13f, onAccent).apply { alpha = 0.85f })
        root.addView(banner, lp(16))

        fun showUpdate(info: Updater.Info?) {
            if (info == null) {
                banner.visibility = View.GONE
                return
            }
            bannerTitle.text = "Update available · v${info.version}"
            banner.visibility = View.VISIBLE
            banner.setOnClickListener {
                val url = info.apkUrl.ifEmpty { info.pageUrl }
                if (url.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        showUpdate(Updater.available(this))
        // silent check whenever the app opens
        Updater.check(this, force = true) { r -> if (r is Updater.Result.Available) showUpdate(r.info) }

        // ---- keyboard ----
        section("Keyboard")
        val setup = card()
        row(setup, true, "Enable OpenRidmik", chevron()) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        row(setup, false, "Switch keyboard", chevron()) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        // ---- layout (segmented) ----
        section("Bangla layout")
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(surface, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root.addView(seg, lp())
        fun refreshSeg() {
            seg.removeAllViews()
            for ((id, name) in listOf("avro" to "Avro · phonetic", "probhat" to "Probhat · প্রভাত")) {
                val on = Prefs.layout(this) == id
                seg.addView(
                    text(name, 14f, if (on) onAccent else fg, bold = on).apply {
                        gravity = Gravity.CENTER
                        background = if (on) rounded(accent, 10) else null
                        setOnClickListener {
                            Prefs.setLayout(this@SettingsActivity, id)
                            refreshSeg()
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(42), 1f),
                )
            }
        }
        refreshSeg()

        // ---- theme ----
        section("Theme")
        swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(swatchRow)
            },
            lp(),
        )
        refreshSwatches(fg, muted)

        val customBox = card().apply { visibility = View.GONE }
        val cur = Themes.current(this)
        val fields = ArrayList<EditText>()
        fun field(name: String, initial: Int, first: Boolean) {
            val e = EditText(this).apply {
                setText(hex(initial))
                setTextColor(fg)
                textSize = 15f
                gravity = Gravity.END
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine()
                minWidth = dp(110)
            }
            fields.add(e)
            row(customBox, first, name, e)
        }
        field("Background", Prefs.customColor(this, "bg", cur.bg), true)
        field("Keys", Prefs.customColor(this, "key", cur.key), false)
        field("Text", Prefs.customColor(this, "text", cur.text), false)
        field("Accent", Prefs.customColor(this, "accent", cur.accent), false)
        row(customBox, false, "Apply custom theme") {
            val v = fields.map { parseColor(it.text.toString()) }
            if (v.any { it == null }) {
                Toast.makeText(this, "Use colours like #1A73E8", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setCustomColors(this, v[0]!!, v[1]!!, v[2]!!, v[3]!!)
                Prefs.setTheme(this, "custom")
                refreshSwatches(fg, muted)
                Toast.makeText(this, "Custom theme applied", Toast.LENGTH_SHORT).show()
            }
        }.setTextColor(accent)
        // toggle row sits above the (collapsed) custom colours card
        val customToggle = text("Custom colours", 14f, accent, bold = true).apply {
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener {
                customBox.visibility = if (customBox.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        root.addView(customToggle, root.indexOfChild(customBox), lp())

        // ---- layouts ----
        section("Keyboard layouts")
        val langs = card()
        fun langRow(first: Boolean, title: String, key: String) {
            val sw = Switch(this).apply {
                isChecked = Prefs.get(this@SettingsActivity, key)
                setOnCheckedChangeListener { b, on ->
                    Prefs.set(this@SettingsActivity, key, on)
                    // at least one layout has to stay on
                    if (listOf("lang_en", "lang_avro", "lang_probhat").none { Prefs.get(this@SettingsActivity, it) }) {
                        Prefs.set(this@SettingsActivity, key, true)
                        b.isChecked = true
                        Toast.makeText(this@SettingsActivity, "Keep at least one layout on", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row(langs, first, title, sw) { sw.toggle() }
        }
        langRow(true, "English", "lang_en")
        langRow(false, "Avro (phonetic)", "lang_avro")
        langRow(false, "Probhat", "lang_probhat")
        root.addView(
            text("Hold the space bar and slide left or right to switch between the layouts that are on.", 12f, muted),
            lp(6),
        )

        // ---- preferences ----
        section("Preferences")
        val prefs = card()
        toggleRow(prefs, true, "Vibrate on keypress", "haptic")
        toggleRow(prefs, false, "Sound on keypress", "sound")
        toggleRow(prefs, false, "Number row", "numberRow")
        toggleRow(prefs, false, "Bangla digits", "banglaDigits")
        toggleRow(prefs, false, "Word suggestions", "suggestions")
        toggleRow(prefs, false, "Clipboard history", "clipboard")
        row(prefs, false, "Clear clipboard history", chevron()) {
            ClipHistory.clearUnpinned(this)
            Toast.makeText(this, "Cleared (pinned items kept)", Toast.LENGTH_SHORT).show()
        }

        // ---- about ----
        section("About")
        val about = card()
        val status = text("", 14f, muted)
        row(about, true, "Check for updates", status) {
            status.text = "Checking…"
            Updater.check(this, force = true) { r ->
                when (r) {
                    is Updater.Result.Available -> {
                        status.text = "v${r.info.version}"
                        showUpdate(r.info)
                    }
                    is Updater.Result.UpToDate -> status.text = "Up to date"
                    is Updater.Result.Failed -> status.text = "Failed"
                }
            }
        }

        // ---- telegram ----
        root.addView(
            text("Join Telegram channel", 16f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.parseColor("#229ED9"), 16)
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/projectredfox")))
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(24) },
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(bg)
                isVerticalScrollBarEnabled = false
                addView(root)
            },
        )
    }

    private fun refreshSwatches(fg: Int, muted: Int) {
        swatchRow.removeAllViews()
        val selectedId = Prefs.theme(this)
        for (id in Themes.ids(this)) {
            val t = Themes.resolve(this, id)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, dp(10), 0)
            }
            cell.addView(Swatch(this, t, id == selectedId), LinearLayout.LayoutParams(dp(84), dp(56)))
            cell.addView(
                TextView(this).apply {
                    text = Themes.label(id)
                    textSize = 12f
                    setTextColor(if (id == selectedId) fg else muted)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, 0)
                },
            )
            cell.setOnClickListener {
                Prefs.setTheme(this, id)
                refreshSwatches(fg, muted)
            }
            swatchRow.addView(cell)
        }
    }
}
