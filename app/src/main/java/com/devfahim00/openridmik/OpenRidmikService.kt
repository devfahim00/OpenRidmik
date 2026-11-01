package com.devfahim00.openridmik

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OpenRidmikService : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { ALPHA, SYM1, SYM2 }

    private lateinit var avro: AvroEngine
    private lateinit var dict: Suggester
    private lateinit var root: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var kbView: KeyboardView
    private val suggestionViews = ArrayList<TextView>()
    private var emojiPanel: View? = null

    private var bangla = true
    private var forceLatin = false
    private var mode = Mode.ALPHA
    private var shift = 0
    private var lastShiftTap = 0L
    private var enterLabel: String? = null
    private val raw = StringBuilder()
    private var audio: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        avro = AvroEngine(assets.open("avro_rules.txt").bufferedReader().use { it.readText() })
        dict = Suggester(assets.open("words_bn.txt").bufferedReader().use { it.readText() })
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val d = resources.displayMetrics.density
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.kb_bg))
        }
        suggestionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        suggestionViews.clear()
        for (i in 0 until 3) {
            val tv = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 17f
                maxLines = 1
                setTextColor(getColor(R.color.key_text))
                if (i == 1) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setOnClickListener { pickSuggestion(text.toString()) }
            }
            suggestionViews.add(tv)
            suggestionBar.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        kbView = KeyboardView(this).also { it.listener = this }
        root.addView(suggestionBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * d).toInt()))
        root.addView(kbView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        rebuild()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        raw.clear()
        clearBar()
        hideEmoji()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        forceLatin = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        bangla = if (forceLatin) false else Prefs.isBangla(this)
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) Mode.SYM1 else Mode.ALPHA
        val multiLine = (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        enterLabel = if (multiLine || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) null
        else when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }
        shift = 0
        kbView.refreshTheme()
        updateAutoCap()
        rebuild()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.let { finishComposing(it) }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (raw.isNotEmpty() && candidatesStart == -1 && candidatesEnd == -1) {
            raw.clear()
            clearBar()
        }
    }

    // ---- layout ----

    private fun rebuild() {
        if (!::kbView.isInitialized) return
        val rows = when (mode) {
            Mode.ALPHA -> Layouts.alpha(
                shift > 0, bangla,
                Prefs.get(this, "numberRow"), Prefs.get(this, "banglaDigits"),
            )
            Mode.SYM1 -> Layouts.symbols(1)
            Mode.SYM2 -> Layouts.symbols(2)
        }
        kbView.haptic = Prefs.get(this, "haptic")
        kbView.enterLabel = enterLabel
        kbView.shiftState = shift
        kbView.spaceLabel = if (bangla) "বাংলা · ফোনেটিক" else "English"
        kbView.rows = rows
        updateBarVisibility()
    }

    private fun updateBarVisibility() {
        val show = mode == Mode.ALPHA && bangla && Prefs.get(this, "suggestions") && emojiPanel == null
        suggestionBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---- key handling ----

    override fun onKey(key: Key) {
        val ic = currentInputConnection ?: return
        if (Prefs.get(this, "sound")) {
            audio?.playSoundEffect(
                if (key.code == Codes.DELETE) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD,
                -1f,
            )
        }
        when (key.code) {
            Codes.SHIFT -> handleShift()
            Codes.DELETE -> handleDelete(ic)
            Codes.ENTER -> {
                finishComposing(ic)
                performEnter(ic)
                updateAutoCap()
            }
            Codes.SPACE -> {
                finishComposing(ic)
                ic.commitText(" ", 1)
                afterChar()
            }
            Codes.SYMBOLS, Codes.SYM1 -> {
                finishComposing(ic)
                mode = Mode.SYM1
                rebuild()
            }
            Codes.SYM2 -> {
                mode = Mode.SYM2
                rebuild()
            }
            Codes.ALPHA -> {
                mode = Mode.ALPHA
                rebuild()
            }
            Codes.EMOJI -> showEmoji()
            Codes.LANG -> toggleLang()
            else -> handleChar(ic, key)
        }
    }

    override fun onLongPress(key: Key): Boolean {
        val ic = currentInputConnection ?: return false
        return when {
            key.code == Codes.LANG -> {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                true
            }
            key.code == ','.code && mode == Mode.ALPHA -> {
                showEmoji()
                true
            }
            key.code > Codes.SPACE && key.alt != null -> {
                finishComposing(ic)
                ic.commitText(key.alt, 1)
                afterChar()
                true
            }
            else -> false
        }
    }

    private fun isPhonetic(t: String): Boolean =
        t.length == 1 && (t[0] in 'a'..'z' || t[0] in 'A'..'Z' || t[0] == '`')

    private fun handleChar(ic: InputConnection, key: Key) {
        val t = key.out
        if (bangla && mode == Mode.ALPHA && isPhonetic(t)) {
            raw.append(t)
            updateComposing(ic)
        } else {
            finishComposing(ic)
            ic.commitText(t, 1)
        }
        afterChar()
    }

    private fun afterChar() {
        if (shift == 1) {
            shift = 0
            rebuild()
        }
        updateAutoCap()
    }

    private fun handleDelete(ic: InputConnection) {
        if (raw.isNotEmpty()) {
            raw.deleteCharAt(raw.length - 1)
            updateComposing(ic)
        } else {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateAutoCap()
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == 2 -> 0
            now - lastShiftTap < 350 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        rebuild()
    }

    private fun updateAutoCap() {
        if (bangla || mode != Mode.ALPHA || shift != 0) return
        val ei = currentInputEditorInfo ?: return
        if (ei.inputType == InputType.TYPE_NULL) return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        if (caps != 0) {
            shift = 1
            rebuild()
        }
    }

    private fun toggleLang() {
        currentInputConnection?.let { finishComposing(it) }
        bangla = !bangla
        if (!forceLatin) Prefs.setBangla(this, bangla)
        shift = 0
        rebuild()
        updateAutoCap()
    }

    private fun performEnter(ic: InputConnection) {
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val multiLine = ei != null && (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (multiLine || noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(action)
        }
    }

    // ---- composing / suggestions ----

    private fun updateComposing(ic: InputConnection) {
        if (raw.isEmpty()) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            clearBar()
            return
        }
        val bn = avro.convert(raw.toString())
        ic.setComposingText(bn, 1)
        val sug = if (Prefs.get(this, "suggestions")) dict.suggest(bn, 2) else emptyList()
        setBar(sug.getOrNull(0), bn, sug.getOrNull(1))
    }

    private fun finishComposing(ic: InputConnection) {
        if (raw.isEmpty()) return
        ic.commitText(avro.convert(raw.toString()), 1)
        raw.clear()
        clearBar()
    }

    private fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (word.isEmpty() || raw.isEmpty()) return
        ic.commitText("$word ", 1)
        raw.clear()
        clearBar()
    }

    private fun setBar(a: String?, b: String?, c: String?) {
        val items = listOf(a, b, c)
        for (i in 0 until suggestionViews.size) {
            val t = items[i] ?: ""
            suggestionViews[i].text = t
            suggestionViews[i].visibility = if (t.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun clearBar() = setBar(null, null, null)

    // ---- emoji panel ----

    private fun showEmoji() {
        if (emojiPanel != null) return
        currentInputConnection?.let { finishComposing(it) }
        val d = resources.displayMetrics.density
        val h = maxOf(kbView.height, (240 * d).toInt())
        val panel = buildEmojiPanel()
        emojiPanel = panel
        root.removeView(kbView)
        root.addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h))
        updateBarVisibility()
    }

    private fun hideEmoji() {
        val p = emojiPanel ?: return
        root.removeView(p)
        root.addView(kbView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        emojiPanel = null
        updateBarVisibility()
    }

    private fun buildEmojiPanel(): View {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val cols = (screenW / (44 * d)).toInt().coerceIn(6, 16)
        val cellW = screenW / cols
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.kb_bg))
        }
        val grid = GridLayout(this).apply { columnCount = cols }
        for (e in EMOJIS) {
            val tv = TextView(this).apply {
                text = e
                textSize = 26f
                gravity = Gravity.CENTER
                setOnClickListener { currentInputConnection?.commitText(e, 1) }
            }
            grid.addView(tv, GridLayout.LayoutParams().apply {
                width = cellW
                height = (cellW * 0.9f).toInt()
            })
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun barButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.key_text))
            setBackgroundColor(getColor(R.color.key_special_bg))
            setOnClickListener { onClick() }
        }
        bar.addView(barButton("ABC") { hideEmoji() }, LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f))
        bar.addView(View(this), LinearLayout.LayoutParams(0, (44 * d).toInt(), 3f))
        bar.addView(
            barButton("⌫") { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) },
            LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f),
        )
        panel.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return panel
    }

    companion object {
        private val EMOJIS = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "😉", "😊", "😇",
            "🥰", "😍", "🤩", "😘", "😗", "😋", "😛", "😜", "🤪", "😎", "🤓", "🥳",
            "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺",
            "😢", "😭", "😤", "😠", "😡", "🤬", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮",
            "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷",
            "👍", "👎", "👌", "✌", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
            "👋", "🤚", "✋", "🖐", "👏", "🙌", "🤝", "🙏", "💪", "👀", "👅", "👄",
            "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖", "💯",
            "🔥", "✨", "⭐", "🌟", "💥", "🎉", "🎊", "🎁", "🏆", "⚽", "🏏", "🎮",
            "🌹", "🌸", "🌼", "🌞", "🌙", "☁", "🌧", "⚡", "🌈", "🍕", "🍔", "🍟",
            "☕", "🍵", "🍰", "🍎", "🍌", "🍇", "🥭", "🍚", "🚗", "✈", "🏠", "📱",
        )
    }
}
