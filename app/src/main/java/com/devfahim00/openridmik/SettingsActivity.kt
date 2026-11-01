package com.devfahim00.openridmik

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val fg = getColor(R.color.key_text)
        val muted = getColor(R.color.key_hint)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        fun lp(top: Int = 0) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = top }

        fun label(text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
            root.addView(
                TextView(this).apply {
                    this.text = text
                    textSize = size
                    setTextColor(color)
                    if (bold) setTypeface(typeface, Typeface.BOLD)
                },
                lp(top),
            )
        }

        fun button(text: String, onClick: () -> Unit) {
            root.addView(
                Button(this).apply {
                    this.text = text
                    isAllCaps = false
                    setOnClickListener { onClick() }
                },
                lp((8 * d).toInt()),
            )
        }

        fun toggle(text: String, key: String) {
            root.addView(
                Switch(this).apply {
                    this.text = text
                    textSize = 16f
                    setTextColor(fg)
                    isChecked = Prefs.get(this@SettingsActivity, key)
                    setOnCheckedChangeListener { _, on -> Prefs.set(this@SettingsActivity, key, on) }
                },
                lp((14 * d).toInt()),
            )
        }

        label("OpenRidmik", 30f, fg, bold = true)
        label("Bangla keyboard with Avro phonetic and Probhat layouts.", 14f, muted, top = (4 * d).toInt())

        label("Setup", 18f, fg, bold = true, top = (24 * d).toInt())
        button("1. Enable OpenRidmik") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button("2. Switch to OpenRidmik") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        // ---- Bangla layout ----
        label("Bangla layout", 18f, fg, bold = true, top = (24 * d).toInt())
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val avroBtn = RadioButton(this).apply {
            text = "Avro (phonetic) – type ami → আমি"
            setTextColor(fg)
            id = View.generateViewId()
        }
        val probhatBtn = RadioButton(this).apply {
            text = "Probhat (প্রভাত) – fixed layout"
            setTextColor(fg)
            id = View.generateViewId()
        }
        group.addView(avroBtn)
        group.addView(probhatBtn)
        group.check(if (Prefs.layout(this) == "probhat") probhatBtn.id else avroBtn.id)
        group.setOnCheckedChangeListener { _, id ->
            Prefs.setLayout(this, if (id == probhatBtn.id) "probhat" else "avro")
        }
        root.addView(group, lp((8 * d).toInt()))

        // ---- Theme ----
        label("Keyboard theme", 18f, fg, bold = true, top = (24 * d).toInt())
        swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(swatchRow)
            },
            lp((8 * d).toInt()),
        )
        refreshSwatches(fg, muted)

        label("Custom colours (hex, e.g. #1A73E8)", 14f, muted, top = (16 * d).toInt())
        val cur = Themes.current(this)
        fun field(name: String, initial: Int): EditText {
            label(name, 13f, fg, top = (8 * d).toInt())
            val e = EditText(this).apply {
                setText(hex(initial))
                setTextColor(fg)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine()
            }
            root.addView(e, lp())
            return e
        }
        val fBg = field("Background", Prefs.customColor(this, "bg", cur.bg))
        val fKey = field("Keys", Prefs.customColor(this, "key", cur.key))
        val fText = field("Text", Prefs.customColor(this, "text", cur.text))
        val fAccent = field("Accent (Enter key)", Prefs.customColor(this, "accent", cur.accent))
        button("Apply custom theme") {
            val bg = parseColor(fBg.text.toString())
            val key = parseColor(fKey.text.toString())
            val text = parseColor(fText.text.toString())
            val accent = parseColor(fAccent.text.toString())
            if (bg == null || key == null || text == null || accent == null) {
                Toast.makeText(this, "Enter colours like #1A73E8", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setCustomColors(this, bg, key, text, accent)
                Prefs.setTheme(this, "custom")
                refreshSwatches(fg, muted)
                Toast.makeText(this, "Custom theme applied", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Clipboard ----
        label("Clipboard", 18f, fg, bold = true, top = (24 * d).toInt())
        toggle("Clipboard history", "clipboard")
        button("Clear clipboard history") {
            ClipHistory.clearUnpinned(this)
            Toast.makeText(this, "Cleared (pinned items kept)", Toast.LENGTH_SHORT).show()
        }

        label("Preferences", 18f, fg, bold = true, top = (24 * d).toInt())
        toggle("Vibrate on keypress", "haptic")
        toggle("Sound on keypress", "sound")
        toggle("Number row", "numberRow")
        toggle("Bangla digits in Bangla mode", "banglaDigits")
        toggle("Word suggestions (Avro)", "suggestions")

        label("Try it", 18f, fg, bold = true, top = (24 * d).toInt())
        root.addView(
            EditText(this).apply {
                hint = "Type here, e.g. ami bangla likhte pari"
                setTextColor(fg)
                setHintTextColor(muted)
            },
            lp(),
        )

        label(
            "Tips: select text, then press and hold X to cut, C to copy, V to paste. " +
                "The 📋 button opens the clipboard history (tap 📌 to pin), 🎨 switches theme, " +
                "and the layout button switches Avro / Probhat. The smiley key next to the globe opens emoji " +
                "by category (hold a hand or person emoji for skin tones). " +
                "Tap the globe to switch Bangla/English (hold for the keyboard picker). " +
                "In Avro use capital letters for T, D, N, S, R, I, U, O.",
            13f, muted, top = (20 * d).toInt(),
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.kb_bg))
                addView(root)
            },
        )
    }

    private fun refreshSwatches(fg: Int, muted: Int) {
        val d = resources.displayMetrics.density
        swatchRow.removeAllViews()
        val selectedId = Prefs.theme(this)
        for (id in Themes.ids(this)) {
            val t = Themes.resolve(this, id)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
            }
            cell.addView(
                Swatch(this, t, id == selectedId),
                LinearLayout.LayoutParams((84 * d).toInt(), (56 * d).toInt()),
            )
            cell.addView(
                TextView(this).apply {
                    text = Themes.label(id)
                    textSize = 12f
                    setTextColor(if (id == selectedId) fg else muted)
                    gravity = android.view.Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            cell.setOnClickListener {
                Prefs.setTheme(this, id)
                refreshSwatches(fg, muted)
            }
            swatchRow.addView(cell)
        }
    }
}
