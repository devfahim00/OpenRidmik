package com.devfahim00.openridmik

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

object Codes {
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val ALPHA = -3
    const val ENTER = -4
    const val DELETE = -5
    const val EMOJI = -6
    const val SYM2 = -8
    const val SYM1 = -9
    const val CUT = -10
    const val COPY = -11
    const val PASTE = -12
    const val SPACE = 32
}

class Key(
    val label: String,
    val code: Int,
    val out: String = label,
    val weight: Float = 1f,
    val hint: String? = null,
    val alt: String? = null,
    val special: Boolean = false,
    val spacer: Boolean = false,
    val repeat: Boolean = false,
    /** Long-press action (Codes.CUT / COPY / PASTE), 0 for none. */
    val action: Int = 0,
)

/** Canvas-drawn, GBoard-style keyboard with rounded keys, key previews, key repeat and long press. */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: Key)
        fun onLongPress(key: Key): Boolean

        /** Space bar dragged sideways: +1 for right, -1 for left, once per step of travel. */
        fun onSpaceSwipe(dir: Int) {}
    }

    var listener: Listener? = null
    var haptic = true
    var enterLabel: String? = null
        set(v) {
            field = v
            invalidate()
        }
    var shiftState = 0
        set(v) {
            field = v
            invalidate()
        }
    var spaceLabel = ""
        set(v) {
            field = v
            invalidate()
        }
    /** Show the small ◂ ▸ arrows on the space bar (only useful when there are 2+ layouts to switch between). */
    var spaceArrows = true
        set(v) {
            field = v
            invalidate()
        }
    /** User-chosen keyboard height, in percent of the default row height. */
    var heightPercent = 100
        set(v) {
            if (field == v) return
            field = v
            requestLayout()
            invalidate()
        }
    var rows: List<List<Key>> = emptyList()
        set(v) {
            field = v
            layoutKeys()
            requestLayout()
            invalidate()
        }

    private var rects: List<List<RectF>> = emptyList()
    private var rowH = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val presses = HashMap<Int, Press>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var cBg = 0
    private var cKey = 0
    private var cSpecial = 0
    private var cPressed = 0
    private var cText = 0
    private var cHint = 0
    private var cAccent = 0
    private var cAccentText = 0
    private var cPreview = 0

    init {
        refreshTheme()
    }

    fun refreshTheme() {
        val t = Themes.current(context)
        cBg = t.bg
        cKey = t.key
        cSpecial = t.special
        cPressed = t.pressed
        cText = t.text
        cHint = t.hint
        cAccent = t.accent
        cAccentText = t.accentText
        cPreview = t.preview
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private inner class Press(var key: Key, var rect: RectF, val startX: Float = 0f) {
        var longDone = false
        var fired = false
        /** True once a space-bar press has turned into a language-switching swipe. */
        var swiping = false
        var swipeFired = false
        val longRun = Runnable {
            if (listener?.onLongPress(key) == true) {
                longDone = true
                invalidate()
            }
        }
        val repeatRun = object : Runnable {
            override fun run() {
                listener?.onKey(key)
                handler.postDelayed(this, 45)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tall = rows.size >= 6
        val rh = dp(if (landscape) (if (tall) 34f else 40f) else (if (tall) 42f else 48f)) * heightPercent / 100f
        val h = (rh * maxOf(rows.size, 1) + dp(8f)).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0 || height == 0 || rows.isEmpty()) {
            rects = emptyList()
            return
        }
        val padTop = dp(4f)
        rowH = (height - dp(8f)) / rows.size
        rects = rows.mapIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            row.map { k ->
                val w = width * k.weight / total
                val rc = RectF(x, padTop + r * rowH, x + w, padTop + (r + 1) * rowH)
                x += w
                rc
            }
        }
        // the layout can change under a finger (language swipe): keep an active space press on the new space key
        for (p in presses.values) {
            if (p.key.code != Codes.SPACE) continue
            for (r in rows.indices) for (i in rows[r].indices) {
                if (rows[r][i].code == Codes.SPACE) {
                    p.key = rows[r][i]
                    p.rect = rects[r][i]
                }
            }
        }
    }

    private fun hit(x: Float, y: Float): Pair<Key, RectF>? {
        if (rects.isEmpty() || rects.size != rows.size) return null
        val r = ((y - dp(4f)) / rowH).toInt().coerceIn(0, rows.size - 1)
        var best: Pair<Key, RectF>? = null
        var bestD = Float.MAX_VALUE
        for (i in rows[r].indices) {
            val k = rows[r][i]
            if (k.spacer) continue
            val rc = rects[r][i]
            val d = when {
                x < rc.left -> rc.left - x
                x > rc.right -> x - rc.right
                else -> 0f
            }
            if (d < bestD) {
                bestD = d
                best = k to rc
            }
        }
        return best
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        if (rects.size != rows.size) return
        val gx = dp(3f)
        val gy = dp(4f)
        val rad = dp(9f)
        for (r in rows.indices) {
            for (i in rows[r].indices) {
                val k = rows[r][i]
                if (k.spacer) continue
                val rc = rects[r][i]
                val pressed = presses.values.any { it.key === k }
                val box = RectF(rc.left + gx, rc.top + gy, rc.right - gx, rc.bottom - gy)
                val isEnter = k.code == Codes.ENTER
                fill.color = when {
                    pressed -> cPressed
                    isEnter -> cAccent
                    k.special -> cSpecial
                    else -> cKey
                }
                canvas.drawRoundRect(box, rad, rad, fill)
                val fg = if (isEnter && !pressed) cAccentText else cText
                drawKeyContent(canvas, k, box, fg)
            }
        }
        for (p in presses.values) {
            if (p.swiping) drawSwipePreview(canvas, p) else drawPreview(canvas, p)
        }
    }

    /** While swiping the space bar, show the language that is now active in a big popup above it. */
    private fun drawSwipePreview(canvas: Canvas, p: Press) {
        val rc = p.rect
        val w = minOf(width - dp(16f), maxOf(rc.width() * 0.8f, dp(170f)))
        val h = rowH * 1.1f
        val left = (rc.centerX() - w / 2f).coerceIn(dp(8f), width - w - dp(8f))
        val bottom = rc.top - dp(2f)
        val top = maxOf(bottom - h, dp(1f))
        val box = RectF(left, top, left + w, top + h)
        fill.color = cPreview
        canvas.drawRoundRect(box, dp(12f), dp(12f), fill)
        stroke.color = cHint
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(box, dp(12f), dp(12f), stroke)
        drawText(canvas, spaceLabel, box.centerX(), box.centerY(), sp(20f), cText)
    }

    private fun drawKeyContent(canvas: Canvas, k: Key, box: RectF, fg: Int) {
        val cx = box.centerX()
        val cy = box.centerY()
        val s = minOf(box.width(), box.height()) * 0.27f
        stroke.color = fg
        stroke.strokeWidth = dp(2f)
        fill.color = fg
        when (k.code) {
            Codes.SHIFT -> {
                val p = Path().apply {
                    moveTo(cx, cy - s)
                    lineTo(cx + s, cy)
                    lineTo(cx + s * 0.45f, cy)
                    lineTo(cx + s * 0.45f, cy + s)
                    lineTo(cx - s * 0.45f, cy + s)
                    lineTo(cx - s * 0.45f, cy)
                    lineTo(cx - s, cy)
                    close()
                }
                if (shiftState > 0) canvas.drawPath(p, fill) else canvas.drawPath(p, stroke)
                if (shiftState == 2) {
                    canvas.drawRect(cx - s * 0.45f, cy + s * 1.2f, cx + s * 0.45f, cy + s * 1.45f, fill)
                }
            }
            Codes.DELETE -> {
                val p = Path().apply {
                    moveTo(cx - s * 1.25f, cy)
                    lineTo(cx - s * 0.5f, cy - s * 0.9f)
                    lineTo(cx + s * 1.2f, cy - s * 0.9f)
                    lineTo(cx + s * 1.2f, cy + s * 0.9f)
                    lineTo(cx - s * 0.5f, cy + s * 0.9f)
                    close()
                }
                canvas.drawPath(p, stroke)
                canvas.drawLine(cx - s * 0.1f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, stroke)
                canvas.drawLine(cx + s * 0.7f, cy - s * 0.4f, cx - s * 0.1f, cy + s * 0.4f, stroke)
            }
            Codes.ENTER -> {
                val label = enterLabel
                if (label != null) {
                    drawText(canvas, label, cx, cy, sp(14f), fg)
                } else {
                    val p = Path().apply {
                        moveTo(cx + s, cy - s * 0.8f)
                        lineTo(cx + s, cy + s * 0.2f)
                        lineTo(cx - s, cy + s * 0.2f)
                    }
                    canvas.drawPath(p, stroke)
                    canvas.drawLine(cx - s, cy + s * 0.2f, cx - s * 0.35f, cy - s * 0.45f, stroke)
                    canvas.drawLine(cx - s, cy + s * 0.2f, cx - s * 0.35f, cy + s * 0.85f, stroke)
                }
            }
            Codes.EMOJI -> {
                canvas.drawCircle(cx, cy, s, stroke)
                canvas.drawCircle(cx - s * 0.35f, cy - s * 0.25f, s * 0.12f, fill)
                canvas.drawCircle(cx + s * 0.35f, cy - s * 0.25f, s * 0.12f, fill)
                canvas.drawArc(
                    RectF(cx - s * 0.55f, cy - s * 0.35f, cx + s * 0.55f, cy + s * 0.55f),
                    20f, 140f, false, stroke,
                )
            }
            Codes.SPACE -> {
                drawText(canvas, spaceLabel, cx, cy, sp(13f), cHint)
                // small arrows hint that the space bar can be dragged left / right to switch language
                text.textSize = sp(13f)
                val half = text.measureText(spaceLabel) / 2f + dp(12f)
                val ah = dp(4f)
                fill.color = cHint
                if (spaceArrows && half + dp(14f) < box.width() / 2f) {
                    val lx = cx - half
                    canvas.drawPath(Path().apply {
                        moveTo(lx, cy); lineTo(lx + ah, cy - ah); lineTo(lx + ah, cy + ah); close()
                    }, fill)
                    val rx = cx + half
                    canvas.drawPath(Path().apply {
                        moveTo(rx, cy); lineTo(rx - ah, cy - ah); lineTo(rx - ah, cy + ah); close()
                    }, fill)
                }
            }
            else -> {
                val big = k.label.length <= 2
                // keys with a corner hint: shrink the main label a little and drop it so the two never touch
                val hinted = k.hint != null
                val size = if (big) (if (hinted) 20f else 22f) else 15f
                val dy = if (hinted) dp(4f) else 0f
                drawText(canvas, k.label, cx, cy + dy, sp(size), fg)
                k.hint?.let {
                    text.textSize = sp(9f)
                    val w = text.measureText(it)
                    val hx = if (w > dp(14f)) box.right - dp(3f) - w / 2f else box.right - dp(8f)
                    drawText(canvas, it, hx, box.top + dp(10f), sp(9f), cHint)
                }
            }
        }
    }

    private fun drawText(canvas: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int) {
        text.textSize = size
        text.color = color
        val y = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(s, x, y, text)
    }

    private fun drawPreview(canvas: Canvas, p: Press) {
        val k = p.key
        // while a long-press alternate is being typed, show that alternate in the popup
        val shown = if (p.longDone && k.alt != null && k.action == 0) k.alt else k.label
        if (k.code <= Codes.SPACE || shown.isEmpty() || shown.length > 3) return
        val rc = p.rect
        val w = rc.width() * 1.3f
        val h = rc.height() * 1.4f
        val left = (rc.centerX() - w / 2f).coerceIn(dp(2f), width - w - dp(2f))
        val bottom = rc.top - dp(2f)
        val top = maxOf(bottom - h, dp(1f))
        val box = RectF(left, top, left + w, top + h)
        fill.color = cPreview
        canvas.drawRoundRect(box, dp(12f), dp(12f), fill)
        stroke.color = cHint
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(box, dp(12f), dp(12f), stroke)
        drawText(canvas, shown, box.centerX(), box.centerY(), sp(30f), cText)
    }

    // ---- touch ----

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        val h = hit(x, y) ?: return
        val p = Press(h.first, h.second, x)
        presses[id] = p
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (p.key.repeat) {
            p.fired = true
            listener?.onKey(p.key)
            handler.postDelayed(p.repeatRun, 400)
        } else {
            handler.postDelayed(p.longRun, 420)
        }
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = presses[id] ?: return
        if (p.key.code == Codes.SPACE && !p.longDone) {
            if (!p.swiping && Math.abs(x - p.startX) > dp(22f)) {
                p.swiping = true
                handler.removeCallbacks(p.longRun)
                if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate()
            }
            // one swipe = one layout change, however far the finger travels
            if (p.swiping && !p.swipeFired && Math.abs(x - p.startX) > dp(40f)) {
                p.swipeFired = true
                if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                listener?.onSpaceSwipe(if (x > p.startX) 1 else -1)
                invalidate()
            }
            return
        }
        if (p.longDone || p.key.repeat) return
        if (!p.rect.contains(x, y)) {
            val h = hit(x, y)
            if (h != null && h.first !== p.key) {
                handler.removeCallbacks(p.longRun)
                p.key = h.first
                p.rect = h.second
                handler.postDelayed(p.longRun, 420)
                invalidate()
            }
        }
    }

    private fun up(id: Int) {
        val p = presses.remove(id) ?: return
        handler.removeCallbacks(p.longRun)
        handler.removeCallbacks(p.repeatRun)
        if (!p.longDone && !p.fired && !p.swiping) listener?.onKey(p.key)
        invalidate()
    }

    private fun cancelAll() {
        for (p in presses.values) {
            handler.removeCallbacks(p.longRun)
            handler.removeCallbacks(p.repeatRun)
        }
        presses.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }
}
