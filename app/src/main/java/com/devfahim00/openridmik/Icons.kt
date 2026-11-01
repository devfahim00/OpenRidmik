package com.devfahim00.openridmik

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

object Ico {
    const val CLIPBOARD = 1
    const val PALETTE = 2
    const val GEAR = 3
    const val PIN = 4
    const val CLOSE = 5
    const val CLOCK = 6
    const val SMILE = 7
    const val PERSON = 8
    const val PAW = 9
    const val FOOD = 10
    const val CAR = 11
    const val BALL = 12
    const val BULB = 13
    const val HEART = 14
    const val FLAG = 15
    const val DELETE = 16
    const val NUMPAD = 17

    fun forGroup(name: String): Int = when (name) {
        "Smileys & Emotion" -> SMILE
        "People & Body" -> PERSON
        "Animals & Nature" -> PAW
        "Food & Drink" -> FOOD
        "Travel & Places" -> CAR
        "Activities" -> BALL
        "Objects" -> BULB
        "Symbols" -> HEART
        "Flags" -> FLAG
        else -> SMILE
    }
}

/** Simple line icons drawn on a 24x24 grid (centred), tinted with [color]. */
class IconView(ctx: Context, var kind: Int) : View(ctx) {
    var color: Int = Color.BLACK
        set(v) {
            field = v
            invalidate()
        }
    var filled: Boolean = false
        set(v) {
            field = v
            invalidate()
        }
    /** Fraction of the view's smaller side used by the 24-unit grid. */
    var scale: Float = 0.55f

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(c: Canvas) {
        val k = minOf(width, height) * scale / 24f
        stroke.color = color
        fill.color = color
        c.save()
        c.translate(width / 2f, height / 2f)
        c.scale(k, k)
        drawIcon(c)
        c.restore()
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float) = c.drawCircle(x, y, r, fill)

    private fun drawIcon(c: Canvas) {
        when (kind) {
            Ico.CLIPBOARD -> {
                c.drawRoundRect(RectF(-7f, -8f, 7f, 10f), 2.5f, 2.5f, stroke)
                c.drawRoundRect(RectF(-3.5f, -10.5f, 3.5f, -6f), 1.8f, 1.8f, stroke)
                c.drawLine(-3.5f, 0f, 3.5f, 0f, stroke)
                c.drawLine(-3.5f, 4f, 2f, 4f, stroke)
            }
            Ico.PALETTE -> {
                c.drawCircle(0f, 0f, 10f, stroke)
                dot(c, -4.5f, -3.5f, 1.6f)
                dot(c, 0.5f, -6f, 1.6f)
                dot(c, 5.5f, -2f, 1.6f)
                dot(c, -6f, 2.5f, 1.6f)
                c.drawCircle(3.5f, 5f, 2.6f, stroke)
            }
            Ico.GEAR -> {
                c.drawCircle(0f, 0f, 6.5f, stroke)
                c.drawCircle(0f, 0f, 2.6f, stroke)
                val w = stroke.strokeWidth
                stroke.strokeWidth = 3.4f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    c.drawLine(cs * 7.6f, sn * 7.6f, cs * 10f, sn * 10f, stroke)
                }
                stroke.strokeWidth = w
            }
            Ico.PIN -> {
                c.save()
                c.rotate(35f)
                val p = if (filled) fill else stroke
                c.drawCircle(0f, -4.5f, 4.5f, p)
                c.drawLine(0f, 0f, 0f, 11f, stroke)
                c.restore()
            }
            Ico.CLOSE -> {
                c.drawLine(-6f, -6f, 6f, 6f, stroke)
                c.drawLine(-6f, 6f, 6f, -6f, stroke)
            }
            Ico.CLOCK -> {
                c.drawCircle(0f, 0f, 9f, stroke)
                c.drawLine(0f, 0f, 0f, -5f, stroke)
                c.drawLine(0f, 0f, 4f, 2f, stroke)
            }
            Ico.SMILE -> {
                c.drawCircle(0f, 0f, 9f, stroke)
                dot(c, -3.4f, -2.6f, 1.3f)
                dot(c, 3.4f, -2.6f, 1.3f)
                c.drawArc(RectF(-5.5f, -4f, 5.5f, 6f), 25f, 130f, false, stroke)
            }
            Ico.PERSON -> {
                c.drawCircle(0f, -4.5f, 4f, stroke)
                c.drawArc(RectF(-8f, 3f, 8f, 19f), 180f, 180f, false, stroke)
            }
            Ico.PAW -> {
                dot(c, 0f, 4f, 4.8f)
                dot(c, -6.8f, -1f, 2.3f)
                dot(c, -2.4f, -6f, 2.3f)
                dot(c, 2.4f, -6f, 2.3f)
                dot(c, 6.8f, -1f, 2.3f)
            }
            Ico.FOOD -> {
                c.drawLine(-4f, -9f, -4f, 10f, stroke)
                c.drawLine(-7.5f, -9f, -7.5f, -3f, stroke)
                c.drawLine(-0.5f, -9f, -0.5f, -3f, stroke)
                val p = Path().apply {
                    moveTo(-7.5f, -3f)
                    quadTo(-4f, 1.5f, -0.5f, -3f)
                }
                c.drawPath(p, stroke)
                c.drawLine(6.5f, 10f, 6.5f, -9f, stroke)
                val blade = Path().apply {
                    moveTo(6.5f, -9f)
                    quadTo(10.5f, -5f, 6.5f, 0f)
                }
                c.drawPath(blade, stroke)
            }
            Ico.CAR -> {
                c.drawRoundRect(RectF(-10f, 0f, 10f, 7f), 2.5f, 2.5f, stroke)
                val roof = Path().apply {
                    moveTo(-6.5f, 0f)
                    lineTo(-4f, -6f)
                    lineTo(4f, -6f)
                    lineTo(6.5f, 0f)
                }
                c.drawPath(roof, stroke)
                dot(c, -5.5f, 8f, 2.2f)
                dot(c, 5.5f, 8f, 2.2f)
            }
            Ico.BALL -> {
                c.drawCircle(0f, 0f, 9f, stroke)
                c.drawLine(-9f, 0f, 9f, 0f, stroke)
                c.drawLine(0f, -9f, 0f, 9f, stroke)
                c.drawArc(RectF(-15f, -9f, -3f, 9f), -50f, 100f, false, stroke)
                c.drawArc(RectF(3f, -9f, 15f, 9f), 130f, 100f, false, stroke)
            }
            Ico.BULB -> {
                c.drawCircle(0f, -3f, 7f, stroke)
                c.drawLine(-3f, 6f, 3f, 6f, stroke)
                c.drawLine(-2.4f, 9.5f, 2.4f, 9.5f, stroke)
                c.drawLine(-2f, 3f, -2f, 6f, stroke)
                c.drawLine(2f, 3f, 2f, 6f, stroke)
            }
            Ico.HEART -> {
                val p = Path().apply {
                    moveTo(0f, 9f)
                    cubicTo(-14f, -1f, -8f, -10f, 0f, -3f)
                    cubicTo(8f, -10f, 14f, -1f, 0f, 9f)
                }
                c.drawPath(p, stroke)
            }
            Ico.FLAG -> {
                c.drawLine(-6f, -10f, -6f, 11f, stroke)
                val p = Path().apply {
                    moveTo(-6f, -9f)
                    cubicTo(-1f, -12f, 3f, -6f, 8f, -9f)
                    lineTo(8f, 1f)
                    cubicTo(3f, 4f, -1f, -2f, -6f, 1f)
                }
                c.drawPath(p, stroke)
            }
            Ico.NUMPAD -> {
                c.drawRoundRect(RectF(-10f, -8f, 10f, 8f), 3f, 3f, stroke)
                for (i in -1..1) for (j in -1..1) dot(c, i * 5f, j * 4f, 1.2f)
            }
            Ico.DELETE -> {
                val p = Path().apply {
                    moveTo(-10f, 0f)
                    lineTo(-5f, -7f)
                    lineTo(9f, -7f)
                    lineTo(9f, 7f)
                    lineTo(-5f, 7f)
                    close()
                }
                c.drawPath(p, stroke)
                c.drawLine(-0.5f, -3f, 5f, 3f, stroke)
                c.drawLine(5f, -3f, -0.5f, 3f, stroke)
            }
        }
    }
}
