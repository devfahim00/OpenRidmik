package com.devfahim00.openridmik

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

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

        fun label(text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
            root.addView(
                TextView(this).apply {
                    this.text = text
                    textSize = size
                    setTextColor(color)
                    if (bold) setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = top },
            )
        }

        fun button(text: String, onClick: () -> Unit) {
            root.addView(
                Button(this).apply {
                    this.text = text
                    isAllCaps = false
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (8 * d).toInt() },
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
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (14 * d).toInt() },
            )
        }

        label("OpenRidmik", 30f, fg, bold = true)
        label("Bangla phonetic keyboard with a modern, Gboard-style layout.", 14f, muted, top = (4 * d).toInt())

        label("Setup", 18f, fg, bold = true, top = (24 * d).toInt())
        button("1. Enable OpenRidmik") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button("2. Switch to OpenRidmik") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        label("Preferences", 18f, fg, bold = true, top = (24 * d).toInt())
        toggle("Vibrate on keypress", "haptic")
        toggle("Sound on keypress", "sound")
        toggle("Number row", "numberRow")
        toggle("Bangla digits in Bangla mode", "banglaDigits")
        toggle("Word suggestions", "suggestions")

        label("Try it", 18f, fg, bold = true, top = (24 * d).toInt())
        root.addView(
            EditText(this).apply {
                hint = "Type here, e.g. ami bangla likhte pari"
                setTextColor(fg)
                setHintTextColor(muted)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        label(
            "Tips: tap the globe key to switch Bangla/English (long-press for the keyboard picker). " +
                "Long-press the comma key for emoji. Long-press a letter to type its Bangla letter directly. " +
                "Use capital letters for T, D, N, S, R, I, U, O (Shift key).",
            13f, muted, top = (20 * d).toInt(),
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.kb_bg))
                addView(root)
            },
        )
    }
}
