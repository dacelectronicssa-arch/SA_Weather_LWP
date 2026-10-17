package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.sin
import kotlin.random.Random

/**
 * The MSC Armonia sailing across Table Bay: a Lirica-class cruise ship with a white hull,
 * the MSC compass logo near the bow, tiers of balconies, orange lifeboats and a raked funnel.
 * Lit up at night. The ship is painted once into a bitmap per direction and day/night.
 */
class CruiseShip(private val dp: Float) {

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f
    private var len = 0f       // ship length on screen
    private var waterY = 0f

    var active = false
        private set
    private var t = 0f
    private var x = 0f         // stern position
    private var dir = 1f       // +1 sailing right (bow on the right), -1 sailing left
    private var speed = 0f

    private val cache = HashMap<Int, Bitmap>() // key: facingRight * 2 + night

    fun setSize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat()
        h = height.toFloat()
        len = w * 0.42f
        // A little way out from the far shore (which is at 72% of the height; the beach at 93.5%).
        waterY = h * (0.72f + 0.215f * 0.2f)
        cache.clear()
        active = false
    }

    fun launch() {
        if (w == 0f) return
        dir = if (rnd.nextBoolean()) 1f else -1f
        speed = (w + len) / (100f + rnd.nextFloat() * 40f) // a stately 2 minutes or so
        x = if (dir > 0) -len else w + len               // stern position, ship just off-screen
        t = 0f
        active = true
    }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        x += dir * speed * dt
        if ((dir > 0 && x > w + len * 0.3f) || (dir < 0 && x < -len * 0.3f)) active = false
    }

    /** Ship bow-to-stern span on screen, for the current direction. */
    private fun left() = if (dir > 0) x else x - len

    fun draw(canvas: Canvas, night: Boolean, haze: Float) {
        if (!active) return
        val bmp = bitmap(dir > 0, night)
        val bob = sin(t * 0.8f) * 0.6f * dp
        val alpha = (255 * haze).toInt().coerceIn(0, 255)

        drawWake(canvas, night, alpha)
        // Reflection: a soft, faint, squashed copy of the hull colour (and lit windows at night).
        drawReflection(canvas, night, alpha)

        shipPaint.alpha = alpha
        val top = waterY + bob - (bmp.height - bottomPad)
        canvas.drawBitmap(bmp, left() - sidePad, top, shipPaint)
    }

    // ---- Water effects (screen space) -----------------------------------------

    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val reflectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private fun drawWake(c: Canvas, night: Boolean, alpha: Int) {
        val stern = x
        val bow = x + dir * len * 0.985f
        foamPaint.color = if (night) 0xFF9AA3B5.toInt() else 0xFFFFFFFF.toInt()
        // Trailing wake: widening, fading lines behind the stern.
        for (k in 0 until 7) {
            val d = len * (0.02f + k * 0.07f)
            val spread = (1.5f + k * 1.6f) * dp
            val a = (alpha * (0.75f - k * 0.1f)).toInt().coerceAtLeast(0)
            foamPaint.alpha = a
            foamPaint.strokeWidth = (1.6f - k * 0.15f) * dp
            val x0 = stern - dir * d
            val x1 = stern - dir * (d + len * 0.06f)
            val wob = sin(t * 2f + k) * 0.6f * dp
            c.drawLine(x0, waterY + spread * 0.5f + wob, x1, waterY + spread + wob, foamPaint)
        }
        // Bow wave.
        foamPaint.alpha = (alpha * 0.85f).toInt()
        foamPaint.strokeWidth = 1.6f * dp
        val pulse = 0.8f + 0.2f * sin(t * 3f)
        c.drawLine(bow - dir * len * 0.08f * pulse, waterY + 1.2f * dp, bow + dir * 2f * dp, waterY + 0.3f * dp, foamPaint)
        // Waterline foam along the hull.
        foamPaint.alpha = (alpha * 0.4f).toInt()
        foamPaint.strokeWidth = 1f * dp
        c.drawLine(left() + len * 0.03f, waterY + 0.8f * dp, left() + len * 0.95f, waterY + 0.8f * dp, foamPaint)
    }

    private fun drawReflection(c: Canvas, night: Boolean, alpha: Int) {
        val l = left()
        if (night) {
            reflectPaint.color = 0xFFFFDDA0.toInt()
            reflectPaint.strokeWidth = 1.4f * dp
            for (row in 1..5) {
                val y = waterY + row * 3.2f * dp
                var px = l + len * 0.06f
                var i = 0
                while (px < l + len * 0.9f) {
                    val flicker = 0.5f + 0.5f * sin(t * 2.5f + i * 1.3f + row)
                    reflectPaint.alpha = (alpha * 0.5f * flicker * (1f - row * 0.15f)).toInt()
                    c.drawLine(px, y, px + len * 0.03f, y, reflectPaint)
                    px += len * 0.05f
                    i++
                }
            }
        } else {
            reflectPaint.color = 0xFFFFFFFF.toInt()
            reflectPaint.strokeWidth = 1.2f * dp
            for (row in 1..4) {
                val y = waterY + row * 3f * dp
                reflectPaint.alpha = (alpha * 0.22f * (1f - row * 0.2f)).toInt()
                val wob = sin(t * 1.5f + row) * 3f * dp
                c.drawLine(l + len * 0.04f + wob, y, l + len * 0.92f + wob, y, reflectPaint)
            }
        }
    }

    // ---- Painting the ship ---------------------------------------------------------

    private val shipPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sidePad get() = 4f * dp
    private val bottomPad get() = 2f * dp

    private fun bitmap(facingRight: Boolean, night: Boolean): Bitmap {
        val key = (if (facingRight) 2 else 0) + (if (night) 1 else 0)
        return cache.getOrPut(key) { paint(facingRight, night) }
    }

    private fun paint(facingRight: Boolean, night: Boolean): Bitmap {
        val l = len
        val bw = (l + 2 * sidePad).toInt()
        val bh = (l * 0.29f + bottomPad).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Origin: stern at the waterline; y is negative upwards.
        val saved = c.save()
        c.translate(sidePad, bh - bottomPad)
        if (!facingRight) {
            c.translate(l, 0f)
            c.scale(-1f, 1f)
        }

        val white = if (night) 0xFF8C93A3.toInt() else 0xFFF5F6F8.toInt()
        val shade = if (night) 0xFF6F7686.toInt() else 0xFFCDD3DA.toInt()
        val navy = 0xFF1C2A4C.toInt()
        val glass = if (night) 0xFFFFE2A6.toInt() else 0xFF3E5672.toInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun poly(vararg p: Float) = Path().apply {
            moveTo(p[0] * l, p[1] * l)
            for (i in 2 until p.size step 2) lineTo(p[i] * l, p[i + 1] * l)
            close()
        }

        // Funnel (behind the upper decks): raked aft, dark cap, gold compass.
        fill.color = white
        c.drawPath(poly(0.205f, -0.19f, 0.295f, -0.19f, 0.275f, -0.262f, 0.19f, -0.262f), fill)
        fill.color = 0xFF16181C.toInt()
        c.drawPath(poly(0.192f, -0.252f, 0.278f, -0.252f, 0.275f, -0.262f, 0.19f, -0.262f), fill)
        fill.color = 0xFFC9A24A.toInt()
        c.drawCircle(0.238f * l, -0.222f * l, 0.011f * l, fill)

        // Mast with radar.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (night) 0xFF5A6070.toInt() else 0xFF7B828C.toInt()
            strokeWidth = 0.004f * l
        }
        c.drawLine(0.7f * l, -0.2f * l, 0.7f * l, -0.25f * l, line)
        c.drawLine(0.685f * l, -0.236f * l, 0.715f * l, -0.236f * l, line)

        // Top decks: sun deck and the deck carrying the bridge.
        fill.color = white
        c.drawPath(poly(0.2f, -0.175f, 0.81f, -0.175f, 0.79f, -0.195f, 0.22f, -0.195f), fill)
        fill.color = shade
        c.drawRect(0.3f * l, -0.203f * l, 0.68f * l, -0.195f * l, fill)

        // Main superstructure: stepped terraces at the stern, rounded front.
        fill.color = white
        c.drawPath(Path().apply {
            moveTo(0.09f * l, -0.105f * l)
            lineTo(0.86f * l, -0.105f * l)
            quadTo(0.865f * l, -0.16f * l, 0.82f * l, -0.175f * l)
            lineTo(0.12f * l, -0.175f * l)
            lineTo(0.12f * l, -0.155f * l)
            lineTo(0.1f * l, -0.155f * l)
            lineTo(0.1f * l, -0.13f * l)
            lineTo(0.09f * l, -0.13f * l)
            close()
        }, fill)

        // Lifeboat deck.
        fill.color = white
        c.drawPath(poly(0.06f, -0.058f, 0.9f, -0.058f, 0.88f, -0.106f, 0.07f, -0.106f), fill)
        fill.color = shade
        c.drawRect(0.12f * l, -0.099f * l, 0.84f * l, -0.068f * l, fill)
        fill.color = 0xFFF26A1B.toInt()
        var bx = 0.15f
        while (bx < 0.8f) {
            c.drawRoundRect(RectF(bx * l, -0.094f * l, (bx + 0.048f) * l, -0.076f * l), 0.008f * l, 0.008f * l, fill)
            bx += 0.062f
        }

        // Hull: transom stern, raked bow, navy stripe at the waterline.
        fill.color = white
        c.drawPath(Path().apply {
            moveTo(0.012f * l, 0f)
            lineTo(0.955f * l, 0f)
            quadTo(0.985f * l, -0.03f * l, 1f * l, -0.066f * l)
            lineTo(0.004f * l, -0.06f * l)
            lineTo(0f, -0.044f * l)
            close()
        }, fill)
        fill.color = navy
        c.drawPath(poly(0.012f, 0f, 0.955f, 0f, 0.962f, -0.007f, 0.008f, -0.007f), fill)

        // Rows of windows and balconies.
        fun row(y: Float, x0: Float, x1: Float, on: Float, off: Float, thick: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glass
                strokeWidth = thick * l
                pathEffect = DashPathEffect(floatArrayOf(on * l, off * l), 0f)
            }
            c.drawLine(x0 * l, y * l, x1 * l, y * l, p)
        }
        row(-0.021f, 0.05f, 0.93f, 0.006f, 0.012f, 0.006f)    // portholes
        row(-0.036f, 0.05f, 0.93f, 0.006f, 0.012f, 0.006f)
        row(-0.05f, 0.06f, 0.92f, 0.012f, 0.007f, 0.007f)     // larger windows
        for ((i, y) in listOf(-0.115f, -0.128f, -0.141f, -0.154f, -0.167f).withIndex()) {
            row(y, 0.125f + i * 0.005f, 0.82f - i * 0.004f, 0.013f, 0.004f, 0.0085f) // balconies
        }
        row(-0.185f, 0.24f, 0.7f, 0.02f, 0.008f, 0.007f)     // upper deck windows
        // Bridge windows across the front.
        fill.color = if (night) 0xFFFFE9C0.toInt() else 0xFF26364A.toInt()
        c.drawRect(0.72f * l, -0.191f * l, 0.79f * l, -0.183f * l, fill)

        if (night) {
            // Deck lights along the top.
            val lights = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFF3D6.toInt()
                strokeWidth = 0.004f * l
                strokeCap = Paint.Cap.ROUND
                pathEffect = DashPathEffect(floatArrayOf(0.001f * l, 0.018f * l), 0f)
            }
            c.drawLine(0.22f * l, -0.198f * l, 0.79f * l, -0.198f * l, lights)
            c.drawLine(0.31f * l, -0.205f * l, 0.67f * l, -0.205f * l, lights)
        }

        // Undo the mirror for lettering, so it reads correctly on both sides.
        c.restoreToCount(saved)
        c.translate(sidePad, bh - bottomPad)
        val logoCx = (if (facingRight) 0.84f else 0.16f) * l
        val logoY = -0.022f * l
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (night) 0xFF22304F.toInt() else navy
            typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
            textSize = 0.032f * l
            textAlign = Paint.Align.LEFT
        }
        val word = "MSC"
        val tw = text.measureText(word)
        val compassR = 0.013f * l
        val total = compassR * 2 + 0.006f * l + tw
        val startX = logoCx - total / 2f
        // The compass mark: a navy disc with a white four-point star.
        fill.color = text.color
        c.drawCircle(startX + compassR, logoY - compassR * 0.9f, compassR, fill)
        fill.color = white
        val sx = startX + compassR
        val sy = logoY - compassR * 0.9f
        val r = compassR * 0.8f
        val ri = r * 0.25f
        c.drawPath(Path().apply {
            moveTo(sx, sy - r); lineTo(sx + ri, sy - ri); lineTo(sx + r, sy); lineTo(sx + ri, sy + ri)
            lineTo(sx, sy + r); lineTo(sx - ri, sy + ri); lineTo(sx - r, sy); lineTo(sx - ri, sy - ri); close()
        }, fill)
        c.drawText(word, startX + compassR * 2 + 0.006f * l, logoY, text)
        return bmp
    }
}
