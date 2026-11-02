package com.devfahim00.openridmik

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class OpenRidmikService : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { ALPHA, SYM1, SYM2, NUM }

    private lateinit var avro: AvroEngine
    private val dictBn: Suggester by lazy {
        Suggester(
            assets.open("words_bn.txt").bufferedReader().use { it.readText() },
            assets.open("next_bn.txt").bufferedReader().use { it.readText() },
        )
    }
    private val dictEn: Suggester by lazy {
        Suggester(
            assets.open("words_en.txt").bufferedReader().use { it.readText() },
            assets.open("next_en.txt").bufferedReader().use { it.readText() },
        )
    }
    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var toggleBtn: TextView
    private var toolbarOpen = false
    /** Suggestions stay hidden (toolbar shows) until the user types something in the current field. */
    private var typedSinceStart = false
    private lateinit var kbView: KeyboardView
    private lateinit var theme: KbTheme
    private val suggestionViews = ArrayList<TextView>()
    private val toolbarButtons = ArrayList<View>()
    private var panel: View? = null
    private var clipList: LinearLayout? = null
    private var gearBtn: IconView? = null

    private var bangla = true
    private var probhat = false
    private var forceLatin = false
    private var passwordField = false
    private var mode = Mode.ALPHA
    private var shift = 0
    private var lastShiftTap = 0L
    private var enterLabel: String? = null
    private val raw = StringBuilder()
    private var barHasItems = false
    private var audio: AudioManager? = null
    private var clipboard: ClipboardManager? = null
    private val ui = Handler(Looper.getMainLooper())

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        avro = AvroEngine(assets.open("avro_rules.txt").bufferedReader().use { it.readText() })
        theme = Themes.current(this)
        // Warm up the emoji catalogue and the word lists off the main thread.
        Thread { EmojiData.groups(applicationContext) }.start()
        Thread {
            dictBn.top(1)
            dictEn.top(1)
        }.start()
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreateInputView(): View {
        theme = Themes.current(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }

        // toolbar: clipboard / theme / layout / settings
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbarButtons.clear()
        fun tool(kind: Int, onClick: () -> Unit): IconView {
            val v = IconView(this, kind).apply {
                color = theme.text
                setOnClickListener { onClick() }
            }
            toolbarButtons.add(v)
            toolbar.addView(v, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            return v
        }
        tool(Ico.CLIPBOARD) { showClipboard() }
        tool(Ico.SMILE) { showEmoji() }
        tool(Ico.NUMPAD) { showNumpad() }
        tool(Ico.KEYMAP) { showKeyMap() }
        tool(Ico.PALETTE) { cycleTheme() }
        gearBtn = tool(Ico.GEAR) { openSettings() }
        refreshUpdateBadge()

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
                setTextColor(theme.text)
                if (i == 1) setTypeface(typeface, Typeface.BOLD)
                setOnClickListener { pickSuggestion(text.toString()) }
            }
            suggestionViews.add(tv)
            suggestionBar.addView(tv, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        // small button on the left swaps between the suggestions and the toolbar icons
        toggleBtn = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(theme.hint)
            setOnClickListener {
                toolbarOpen = !toolbarOpen
                updateBarVisibility()
            }
        }
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        strip.addView(toggleBtn, lp(dp(40), LinearLayout.LayoutParams.MATCH_PARENT))
        strip.addView(toolbar, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        strip.addView(suggestionBar, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        kbView = KeyboardView(this).also { it.listener = this }
        root.addView(strip, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        root.addView(kbView, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        rebuild()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshUpdateBadge()
        Updater.check(this, force = false) { refreshUpdateBadge() }
        raw.clear()
        clearBar()
        toolbarOpen = false
        typedSinceStart = false
        hidePanel()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        forceLatin = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        bangla = if (forceLatin) false else Prefs.isBangla(this)
        probhat = Prefs.layout(this) == "probhat"
        if (!forceLatin) {
            // a layout the user switched off in settings is never left active
            val on = Prefs.enabledLangs(this)
            if (currentLang() !in on) applyLangFlags(on.first())
        }
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
        applyTheme()
        updateAutoCap()
        rebuild()
        currentInputConnection?.let { refreshBar(it) }
        scheduleRefresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.let { finishComposing(it) }
        UserDict.save(this)
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
        if (raw.isEmpty()) scheduleRefresh()
    }

    // ---- theme ----

    private fun applyTheme() {
        if (!::root.isInitialized) return
        theme = Themes.current(this)
        root.setBackgroundColor(theme.bg)
        for (tv in suggestionViews) tv.setTextColor(theme.text)
        for (b in toolbarButtons) tint(b)
        toggleBtn.setTextColor(theme.hint)
        kbView.refreshTheme()
        syncNavBar()
    }

    /** Paints the system navigation bar under the keyboard with the keyboard's own background colour. */
    private fun syncNavBar() {
        val w = window?.window ?: return
        try {
            w.navigationBarColor = theme.bg
            if (Build.VERSION.SDK_INT >= 28) w.navigationBarDividerColor = theme.bg
            val lum = (0.299f * Color.red(theme.bg) + 0.587f * Color.green(theme.bg) + 0.114f * Color.blue(theme.bg)) / 255f
            if (Build.VERSION.SDK_INT >= 26) {
                @Suppress("DEPRECATION")
                val flags = w.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility =
                    if (lum > 0.5f) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        } catch (e: Exception) {
            // some devices restrict the IME window; the keyboard still works without the tint
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (::root.isInitialized) syncNavBar()
    }

    private fun tint(v: View) {
        when (v) {
            is IconView -> v.color = theme.text
            is TextView -> v.setTextColor(theme.text)
        }
    }

    private fun cycleTheme() {
        val next = Themes.next(this)
        Prefs.setTheme(this, next)
        applyTheme()
        Toast.makeText(this, Themes.label(next), Toast.LENGTH_SHORT).show()
    }

    private fun refreshUpdateBadge() {
        gearBtn?.badge = Updater.available(this) != null
    }

    private fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ---- layout ----

    private fun rebuild() {
        if (!::kbView.isInitialized) return
        val rows = when (mode) {
            Mode.ALPHA -> Layouts.alpha(
                shift > 0, bangla,
                Prefs.get(this, "numberRow"), Prefs.get(this, "banglaDigits"),
                probhat,
            )
            Mode.SYM1 -> Layouts.symbols(1)
            Mode.SYM2 -> Layouts.symbols(2)
            Mode.NUM -> Layouts.numpad()
        }
        kbView.haptic = Prefs.get(this, "haptic")
        kbView.enterLabel = enterLabel
        kbView.shiftState = shift
        kbView.spaceArrows = Prefs.enabledLangs(this).size > 1
        kbView.spaceLabel = when {
            !bangla -> "English"
            probhat -> "Probhat"
            else -> "Avro"
        }
        kbView.rows = rows
        updateBarVisibility()
    }

    /** The strip above the keys shows the toolbar normally and word suggestions while composing (Avro). */
    private fun updateBarVisibility() {
        if (!::toolbar.isInitialized || !::toggleBtn.isInitialized) return
        val noPanel = panel == null
        // the toolbar is the default; suggestions only replace it once there is something to suggest
        val can = barHasItems && mode == Mode.ALPHA && !passwordField && !forceLatin && Prefs.get(this, "suggestions")
        val showSug = can && !toolbarOpen
        suggestionBar.visibility = if (noPanel && showSug) View.VISIBLE else View.GONE
        toolbar.visibility = if (noPanel && !showSug) View.VISIBLE else View.GONE
        toggleBtn.visibility = if (noPanel && can) View.VISIBLE else View.GONE
        toggleBtn.text = if (toolbarOpen) "‹" else "⋯"
    }

    private fun showNumpad() {
        currentInputConnection?.let { finishComposing(it) }
        if (panel != null) hidePanel()
        clearBar()
        mode = Mode.NUM
        shift = 0
        rebuild()
    }

    // ---- key handling ----

    override fun onKey(key: Key) {
        val ic = currentInputConnection ?: return
        if (toolbarOpen) {
            toolbarOpen = false
            updateBarVisibility()
        }
        if (key.code != Codes.SHIFT && key.code != Codes.SYMBOLS && key.code != Codes.SYM1 &&
            key.code != Codes.SYM2 && key.code != Codes.ALPHA && key.code != Codes.EMOJI
        ) typedSinceStart = true
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
                clearBar()
                updateAutoCap()
            }
            Codes.SPACE -> {
                finishComposing(ic)
                ic.commitText(" ", 1)
                learnLastWord(ic)
                afterChar()
                refreshBar(ic)
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
            else -> handleChar(ic, key)
        }
    }

    override fun onSpaceSwipe(dir: Int) {
        if (mode != Mode.ALPHA) return
        val on = Prefs.enabledLangs(this)
        if (on.size < 2) return
        val i = on.indexOf(currentLang())
        // either swipe direction moves forward: English -> Avro -> Probhat -> English
        val next = if (i < 0) on.first() else on[(i + 1) % on.size]
        setLang(next)
    }

    override fun onLongPress(key: Key): Boolean {
        val ic = currentInputConnection ?: return false
        return when {
            key.action != 0 -> {
                clipAction(ic, key.action)
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

    // ---- cut / copy / paste ----

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun clipAction(ic: InputConnection, action: Int) {
        finishComposing(ic)
        when (action) {
            Codes.PASTE -> pasteClip(ic)
            else -> {
                // With a selection act on it; otherwise act on the whole field (auto select-all).
                var text = ic.getSelectedText(0)?.toString()
                var before = ""
                var after = ""
                var whole = false
                if (text.isNullOrEmpty()) {
                    before = ic.getTextBeforeCursor(100000, 0)?.toString() ?: ""
                    after = ic.getTextAfterCursor(100000, 0)?.toString() ?: ""
                    text = before + after
                    whole = true
                }
                if (text.isEmpty()) {
                    toast("Nothing to ${if (action == Codes.CUT) "cut" else "copy"}")
                    return
                }
                clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
                ClipHistory.add(this, text)
                if (action == Codes.CUT) {
                    if (whole) ic.deleteSurroundingText(before.length, after.length) else ic.commitText("", 1)
                } else if (whole) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                }
                toast(if (action == Codes.CUT) "Cut" else "Copied")
            }
        }
        updateAutoCap()
    }

    private fun clipboardText(): String? {
        val clip = try {
            clipboard?.primaryClip
        } catch (e: Exception) {
            null
        } ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun pasteClip(ic: InputConnection) {
        val t = clipboardText()
        if (t.isNullOrEmpty()) {
            toast("Clipboard is empty")
            return
        }
        ic.commitText(t, 1)
    }

    private fun captureClip() {
        if (!Prefs.get(this, "clipboard") || passwordField) return
        val clip = try {
            clipboard?.primaryClip
        } catch (e: Exception) {
            null
        } ?: return
        if (clip.itemCount == 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        ) return
        val txt = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (txt.isBlank() || txt.length > 3000) return
        ClipHistory.add(this, txt)
        if (clipList != null) ui.post { refreshClipList() }
    }

    // ---- typing ----

    private fun isPhonetic(t: String): Boolean =
        t.length == 1 && (t[0] in 'a'..'z' || t[0] in 'A'..'Z' || t[0] == '`')

    private fun handleChar(ic: InputConnection, key: Key) {
        val t = key.out
        if (bangla && !probhat && mode == Mode.ALPHA && isPhonetic(t)) {
            raw.append(t)
            updateComposing(ic)
        } else {
            finishComposing(ic)
            ic.commitText(avroSymbol(ic, t), 1)
            refreshBar(ic)
        }
        afterChar()
    }

    /**
     * Avro symbols typed from the symbols page: `$` gives ৳, and `:` / `^` give visarga ঃ / chandrabindu ঁ
     * when they directly follow a Bangla letter. Hold the key to type the plain character instead.
     */
    private fun avroSymbol(ic: InputConnection, t: String): String {
        if (!bangla || probhat || t.length != 1) return t
        val prev = ic.getTextBeforeCursor(1, 0)?.firstOrNull()
        val afterBangla = prev != null && prev in '\u0980'..'\u09FF'
        return when {
            t == "$" -> "৳"
            t == ":" && afterBangla -> "ঃ"
            t == "^" && afterBangla -> "ঁ"
            else -> t
        }
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
            clearBar()
            scheduleRefresh()
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

    /** 0 = English, 1 = Avro (phonetic), 2 = Probhat. */
    private fun currentLang(): Int = if (!bangla) 0 else if (probhat) 2 else 1

    private fun applyLangFlags(idx: Int) {
        when (idx) {
            0 -> bangla = false
            1 -> {
                bangla = true
                probhat = false
            }
            else -> {
                bangla = true
                probhat = true
            }
        }
    }

    /** Switches layout (space-bar swipe); the space bar label and popup show which one is active. */
    private fun setLang(idx: Int) {
        currentInputConnection?.let { finishComposing(it) }
        applyLangFlags(idx)
        if (bangla) Prefs.setLayout(this, if (probhat) "probhat" else "avro")
        if (!forceLatin) Prefs.setBangla(this, bangla)
        shift = 0
        rebuild()
        updateAutoCap()
        currentInputConnection?.let { refreshBar(it) }
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
        val sug = if (Prefs.get(this, "suggestions") && !passwordField) {
            val learned = UserDict.suggest(this, bn, prevWord(ic, composing = true), 2)
            (learned + dictBn.suggest(bn, 6)).distinct().take(2)
        } else emptyList()
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
        if (word.isEmpty()) return
        if (raw.isNotEmpty()) {
            // Avro: the composing text is replaced by the chosen word
            ic.commitText("$word ", 1)
            raw.clear()
        } else {
            // English / Probhat: replace the partly typed word before the cursor
            val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
            val tw = trailingWord(before)
            if (tw.isNotEmpty()) ic.deleteSurroundingText(tw.length, 0)
            ic.commitText("$word ", 1)
        }
        clearBar()
        learnLastWord(ic)
        afterChar()
        refreshBar(ic)
    }

    private fun setBar(a: String?, b: String?, c: String?) {
        val items = listOf(a, b, c)
        for (i in 0 until suggestionViews.size) {
            val t = items[i] ?: ""
            suggestionViews[i].text = t
            suggestionViews[i].visibility = if (t.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
        barHasItems = items.any { !it.isNullOrEmpty() }
        updateBarVisibility()
    }

    // ---- learning + next-word prediction ----

    private val wordRe = Regex("[\\p{L}\\p{M}]+(?:'[\\p{L}]+)?")
    private val tailRe = Regex("[\\p{L}\\p{M}']+$")

    /** The partly typed word right before the cursor ("" when the cursor follows a space or symbol). */
    private fun trailingWord(before: String): String = tailRe.find(before)?.value?.trimStart('\'') ?: ""

    /** Word preceding the one being typed (composing) or the last finished word (after a space). */
    private fun prevWord(ic: InputConnection, composing: Boolean): String? {
        val t = ic.getTextBeforeCursor(80, 0)?.toString() ?: return null
        val ws = wordRe.findAll(t).map { it.value }.toList()
        if (!composing) return if (t.endsWith(" ")) ws.lastOrNull() else null
        // composing text sits before the cursor too, so skip it
        return if (ws.size >= 2) ws[ws.size - 2] else null
    }

    /** Called right after "word " has been committed: learns the word and its pair with the previous one. */
    private fun learnLastWord(ic: InputConnection) {
        if (passwordField || forceLatin) return
        val t = ic.getTextBeforeCursor(80, 0)?.toString() ?: return
        if (!t.endsWith(" ")) return
        val ws = wordRe.findAll(t).map { it.value }.toList()
        val w = ws.lastOrNull() ?: return
        UserDict.learn(this, ws.getOrNull(ws.size - 2), w)
    }

    private val refreshRun = Runnable { currentInputConnection?.let { refreshBar(it) } }

    private fun scheduleRefresh() {
        ui.removeCallbacks(refreshRun)
        ui.postDelayed(refreshRun, 60)
    }

    private fun isScriptBn(w: String) = w.isNotEmpty() && w[0].code in 0x0980..0x09FF

    private fun dictFor(w: String): Suggester = if (isScriptBn(w)) dictBn else dictEn

    /** Matches the typed word's capitalisation (Hel -> Hello, HEL -> HELLO). */
    private fun cased(sug: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> sug.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> sug.replaceFirstChar { it.uppercase() }
        else -> sug
    }

    /** "i" and "i'm" are always capitalised in English. */
    private fun display(w: String, sentenceStart: Boolean): String = when {
        w == "i" || w.startsWith("i'") -> w.replaceFirstChar { it.uppercase() }
        sentenceStart && w.isNotEmpty() && w[0] in 'a'..'z' -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    /**
     * One place that decides what the strip shows when nothing is being composed (English, Probhat, and Avro
     * between words): completions for the word before the cursor, or next-word predictions after a space.
     */
    private fun refreshBar(ic: InputConnection) {
        if (!Prefs.get(this, "suggestions") || passwordField || forceLatin || mode != Mode.ALPHA || panel != null) {
            clearBar()
            return
        }
        if (raw.isNotEmpty()) return
        if (!typedSinceStart) {
            clearBar()
            return
        }
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
        val tw = trailingWord(before)
        when {
            before.isEmpty() -> clearBar()
            tw.isNotEmpty() -> wordSuggestions(before, tw)
            before.last().isWhitespace() -> nextWordSuggestions(before)
            else -> clearBar()
        }
    }

    private fun wordSuggestions(before: String, tw: String) {
        val head = before.substring(0, before.length - tw.length)
        val prev = wordRe.findAll(head).map { it.value }.lastOrNull()
        val key = UserDict.norm(tw)
        val learned = UserDict.suggest(this, key, prev, 3)
        val seed = dictFor(tw).suggest(key, 6)
        val list = (learned + seed).distinct().filter { it != key }.take(2).map { cased(it, tw) }
        setBar(list.getOrNull(0), tw, list.getOrNull(1))
    }

    private fun nextWordSuggestions(before: String) {
        val trimmed = before.trimEnd()
        val sentenceStart = trimmed.isEmpty() || before.endsWith("\n") || trimmed.last() in ".!?\u0964"
        val prev = if (sentenceStart) null else wordRe.findAll(trimmed).map { it.value }.lastOrNull()
        val d = if (prev != null) dictFor(prev) else if (bangla) dictBn else dictEn
        val key = prev?.let { UserDict.norm(it) }
        val learned = if (key != null) UserDict.next(this, key, 3) else emptyList()
        val seed = when {
            key != null -> d.next(key, 6).ifEmpty { d.top(3) }
            else -> d.starters(6)
        }
        val latin = d === dictEn
        val list = (learned + seed).distinct().take(3).map { if (latin) display(it, sentenceStart) else it }
        setBar(list.getOrNull(0), list.getOrNull(1), list.getOrNull(2))
    }

    private fun clearBar() = setBar(null, null, null)

    // ---- panels (emoji / clipboard) ----

    private fun openPanel(v: View) {
        val h = maxOf(kbView.height + dp(38), dp(270))
        panel = v
        root.removeView(kbView)
        root.addView(v, lp(LinearLayout.LayoutParams.MATCH_PARENT, h))
        updateBarVisibility()
    }

    private fun hidePanel() {
        val p = panel ?: return
        root.removeView(p)
        root.addView(kbView, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel = null
        clipList = null
        updateBarVisibility()
    }

    private fun panelButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        background = rounded(theme.special, 10)
        setOnClickListener { onClick() }
    }

    private fun attachRepeat(v: View, act: () -> Unit) {
        val run = object : Runnable {
            override fun run() {
                act()
                ui.postDelayed(this, 50)
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    act()
                    ui.postDelayed(run, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ui.removeCallbacks(run)
            }
            true
        }
    }

    private fun panelBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        val abc = panelButton("ABC") { hidePanel() }
        val del = IconView(this, Ico.DELETE).apply {
            color = theme.text
            background = rounded(theme.special, 10)
            scale = 0.5f
        }
        attachRepeat(del) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        bar.addView(abc, lp(0, dp(40), 1.2f).apply { rightMargin = dp(4) })
        bar.addView(View(this), lp(0, dp(40), 3f))
        bar.addView(del, lp(0, dp(40), 1.2f).apply { leftMargin = dp(4) })
        return bar
    }

    // ---- emoji panel ----

    private fun showEmoji() {
        if (panel != null) return
        currentInputConnection?.let { finishComposing(it) }
        openPanel(buildEmojiPanel())
    }

    private fun commitEmoji(e: String) {
        currentInputConnection?.commitText(e, 1)
        EmojiData.addRecent(this, e)
    }

    private fun buildEmojiPanel(): View {
        val screenW = resources.displayMetrics.widthPixels
        val cols = (screenW / dp(44)).coerceIn(6, 16)
        val cellW = screenW / cols

        val groups = EmojiData.groups(this)
        var recents = EmojiData.recents(this).filter { EmojiData.supported(it) }
        var currentTab = -1
        var refreshTab: () -> Unit = {}
        fun commit(e: String) {
            commitEmoji(e)
            recents = EmojiData.recents(this).filter { EmojiData.supported(it) }
            if (currentTab == 0) refreshTab()
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }

        // skin-tone chooser (shown after long-pressing a person/hand emoji)
        val toneBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = rounded(theme.special, 0)
        }

        val grid = GridLayout(this).apply { columnCount = cols }
        val scroll = ScrollView(this).apply { addView(grid) }

        fun showTones(base: String) {
            val variants = EmojiData.TONES.map { EmojiData.withTone(base, it) }.filter { EmojiData.supported(it) }
            if (variants.isEmpty()) return
            toneBar.removeAllViews()
            for (v in listOf(base) + variants) {
                toneBar.addView(
                    TextView(this).apply {
                        text = v
                        textSize = 26f
                        gravity = Gravity.CENTER
                        setOnClickListener {
                            commit(v)
                            toneBar.visibility = View.GONE
                        }
                    },
                    lp(dp(46), dp(46)),
                )
            }
            toneBar.addView(
                TextView(this).apply {
                    text = "✕"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(theme.hint)
                    setOnClickListener { toneBar.visibility = View.GONE }
                },
                lp(dp(40), dp(46)),
            )
            toneBar.visibility = View.VISIBLE
        }

        fun fill(items: List<EmojiData.Emoji>, emptyHint: String) {
            toneBar.visibility = View.GONE
            grid.removeAllViews()
            scroll.scrollTo(0, 0)
            if (items.isEmpty()) {
                grid.addView(
                    TextView(this).apply {
                        text = emptyHint
                        textSize = 14f
                        setTextColor(theme.hint)
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                    },
                    GridLayout.LayoutParams().apply {
                        width = screenW
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                    },
                )
                return
            }
            for (e in items) {
                val tv = TextView(this).apply {
                    text = e.text
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        toneBar.visibility = View.GONE
                        commit(e.text)
                    }
                    if (e.skin) setOnLongClickListener {
                        showTones(e.text)
                        true
                    }
                }
                grid.addView(tv, GridLayout.LayoutParams().apply {
                    width = cellW
                    height = (cellW * 0.9f).toInt()
                })
            }
        }

        // category tabs: recent + every Unicode emoji group
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        val tabViews = ArrayList<IconView>()
        fun select(i: Int) {
            currentTab = i
            for ((j, t) in tabViews.withIndex()) {
                t.background = if (j == i) rounded(theme.accent, 10) else null
                t.color = if (j == i) theme.accentText else theme.text
            }
            if (i == 0) {
                fill(recents.map { EmojiData.Emoji(it, false) }, "Emoji you use will show up here.")
            } else {
                fill(groups[i - 1].items, "")
            }
        }
        val kinds = listOf(Ico.CLOCK) + groups.map { Ico.forGroup(it.name) }
        for ((i, kind) in kinds.withIndex()) {
            val t = IconView(this, kind).apply {
                color = theme.text
                scale = 0.6f
                setOnClickListener { select(i) }
            }
            tabViews.add(t)
            tabs.addView(t, lp(dp(46), dp(38)).apply { rightMargin = dp(2) })
        }
        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }

        box.addView(tabScroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(toneBar, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(scroll, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        box.addView(panelBottomBar(), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        refreshTab = { select(0) }
        select(if (recents.isNotEmpty() || groups.isEmpty()) 0 else 1)
        return box
    }

    // ---- Avro key map ----

    private fun showKeyMap() {
        if (panel != null) return
        currentInputConnection?.let { finishComposing(it) }
        openPanel(buildKeyMapPanel())
    }

    private fun buildKeyMapPanel(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }
        val screenW = resources.displayMetrics.widthPixels
        val cols = (screenW / dp(84)).coerceIn(3, 8)
        val cellW = (screenW - dp(16)) / cols

        box.addView(
            TextView(this).apply {
                text = "Avro key map"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.text)
                setPadding(dp(14), dp(10), dp(14), dp(2))
            },
        )
        box.addView(
            TextView(this).apply {
                text = "Type the letters in blue to get the Bangla on top."
                textSize = 12f
                setTextColor(theme.hint)
                setPadding(dp(14), 0, dp(14), dp(6))
            },
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }

        fun section(title: String, items: List<Pair<String, String>>) {
            if (items.isEmpty()) return
            list.addView(
                TextView(this).apply {
                    text = title.uppercase()
                    textSize = 11f
                    letterSpacing = 0.08f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(theme.hint)
                    setPadding(dp(6), dp(12), 0, dp(6))
                },
            )
            val grid = GridLayout(this).apply { columnCount = cols }
            for ((bn, latin) in items) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(theme.special, 10)
                    addView(
                        TextView(this@OpenRidmikService).apply {
                            text = bn
                            textSize = 22f
                            gravity = Gravity.CENTER
                            setTextColor(theme.text)
                        },
                    )
                    addView(
                        TextView(this@OpenRidmikService).apply {
                            text = latin
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                            setTextColor(theme.accent)
                        },
                    )
                }
                grid.addView(cell, GridLayout.LayoutParams().apply {
                    width = cellW - dp(6)
                    height = dp(64)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            list.addView(grid)
        }

        val hasanta = AvroEngine.HASANTA
        val vowels = ArrayList<Pair<String, String>>()
        val consonants = ArrayList<Pair<String, String>>()
        val conjuncts = ArrayList<Pair<String, String>>()
        val signs = ArrayList<Pair<String, String>>()
        for (e in avro.entries) {
            when (e.kind) {
                'V' -> vowels.add((if (e.kar.isEmpty()) e.value else "${e.value}  ${e.kar}") to e.key)
                'C' ->
                    if (e.value.contains(hasanta)) conjuncts.add(e.value to e.key)
                    else consonants.add(e.value to e.key)
                'J', 'W' -> signs.add("${e.value}  $hasanta${e.kar}" to e.key)
                else -> signs.add((if (e.value.isEmpty()) "∅ break" else e.value) to e.key)
            }
        }

        section("Vowels · independent  sign", vowels)
        section("Consonants", consonants)
        section("Conjuncts", conjuncts)
        section("Signs & special", signs)
        section(
            "Symbols page",
            listOf("৳" to "\$", "ঃ" to ":", "ঁ" to "^"),
        )
        list.addView(
            TextView(this).apply {
                text = "Capital letters give variants (T D N S R I U O Z). If a capital has no rule it works like the small letter. " +
                    "Hold a letter key to type its Bangla letter directly. On the symbols page \$ gives ৳, and : ^ give ঃ ঁ after a Bangla letter (hold for the plain character)."
                textSize = 12f
                setTextColor(theme.hint)
                setPadding(dp(6), dp(12), dp(6), dp(6))
            },
        )

        box.addView(ScrollView(this).apply { addView(list) }, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        box.addView(panelBottomBar(), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return box
    }

    // ---- clipboard panel ----

    private fun showClipboard() {
        if (panel != null) return
        currentInputConnection?.let { finishComposing(it) }
        captureClip()
        openPanel(buildClipPanel())
    }

    private fun buildClipPanel(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(8), dp(4))
        }
        header.addView(
            TextView(this).apply {
                text = "Clipboard"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(theme.text)
            },
            lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            panelButton("Clear") {
                ClipHistory.clearUnpinned(this)
                refreshClipList()
            }.apply { setPadding(dp(14), 0, dp(14), 0) },
            lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)),
        )
        box.addView(header, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }
        clipList = list
        box.addView(ScrollView(this).apply { addView(list) }, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        box.addView(panelBottomBar(), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshClipList()
        return box
    }

    private fun refreshClipList() {
        val list = clipList ?: return
        list.removeAllViews()
        val items = ClipHistory.ordered(this)
        if (items.isEmpty()) {
            list.addView(
                TextView(this).apply {
                    text = if (Prefs.get(this@OpenRidmikService, "clipboard")) {
                        "Nothing copied yet. Copy some text and it will show up here.\nTip: tap the pin to keep an item."
                    } else {
                        "Clipboard history is turned off. You can turn it on in OpenRidmik settings."
                    }
                    textSize = 14f
                    setTextColor(theme.hint)
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                },
                lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            return
        }
        for (item in items) list.addView(clipRow(item), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun clipRow(item: ClipHistory.Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(theme.key, 10)
            setPadding(dp(12), dp(2), dp(2), dp(2))
        }
        row.addView(
            TextView(this).apply {
                text = item.text
                textSize = 14f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(theme.text)
                setPadding(0, dp(8), dp(4), dp(8))
                setOnClickListener {
                    currentInputConnection?.commitText(item.text, 1)
                }
            },
            lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            IconView(this, Ico.PIN).apply {
                color = theme.text
                filled = item.pinned
                alpha = if (item.pinned) 1f else 0.4f
                scale = 0.5f
                setOnClickListener {
                    ClipHistory.togglePin(this@OpenRidmikService, item.text)
                    refreshClipList()
                }
            },
            lp(dp(40), dp(40)),
        )
        row.addView(
            IconView(this, Ico.CLOSE).apply {
                color = theme.hint
                scale = 0.45f
                setOnClickListener {
                    ClipHistory.remove(this@OpenRidmikService, item.text)
                    refreshClipList()
                }
            },
            lp(dp(40), dp(40)),
        )
        return row
    }
}
