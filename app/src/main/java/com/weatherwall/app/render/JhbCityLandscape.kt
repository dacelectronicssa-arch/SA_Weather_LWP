package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Jhb City settings, from the in-chat skyline designer. */
object JhbDesign {
    const val SKYLINE_HEIGHT = 1.7f // vertical stretch of the reference photo
    const val SKYLINE_TOP = 0.4f    // where the tip of Hillbrow Tower sits (fraction of height)
    const val CITY_WIDTH = 1.4f     // horizontal zoom, centred on the tower
    const val TOWER_WIDTH = 1.3f
    const val TREE_BAND = 0.06f     // height of the tree line along the bottom
    const val JACARANDA = 1f        // bloom strength when in season
    const val HORIZON = 0.7f

    // Ponte City (photo px for position; 1 = its real size relative to Hillbrow Tower)
    const val PONTE_X = 625f
    const val PONTE_HEIGHT = 1f
    const val PONTE_WIDTH = 1f

    const val PREVIEW_W = 300f
}

/**
 * Johannesburg from the west: Hillbrow Tower over the brick and cream high-rises of Hillbrow
 * and Berea, laid out from the reference photo (coordinates are that photo's pixels), with a
 * line of trees and jacarandas along the bottom. Lights come on at night.
 */
class JhbCityLandscape(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
) : Landscape {
    private val wf = width.toFloat()
    private val hf = height.toFloat()
    private val k = wf / JhbDesign.PREVIEW_W
    private val sx = wf / (PHOTO_W / JhbDesign.CITY_WIDTH)

    private fun x(px: Float) = wf / 2f + (px - TOWER_PX + 14f) * sx
    private fun y(py: Float) = hf * JhbDesign.SKYLINE_TOP + (py - 68f) * sx * JhbDesign.SKYLINE_HEIGHT

    override val tableTopY get() = hf * 0.5f
    override val mountainUnit get() = min(wf, hf * 0.8f) * 1.35f

    private val sb = cfg.skyBottom
    private var twilight = 0f
    private val night = cfg.night
    private var bakedKey = -1
    private var city: Bitmap? = null
    private var time = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Jacarandas flower in October and November.
    private val bloom = JhbDesign.JACARANDA * jacarandaSeason()

    override fun setTwilight(amount: Float) { twilight = amount }
    override fun update(dt: Float) { time += dt }

    // ---- Light -------------------------------------------------------------------------------

    private val dark get() = if (night) 1f else twilight * 0.35f
    private val warm get() = if (night) 0f else twilight

    private fun shade(col: Int, haze: Float): Int {
        var c = mix(col, sb, haze)
        if (warm > 0f) c = mix(c, rgb(255, 170, 120), warm * 0.18f)
        return mix(c, rgb(22, 20, 34), dark * 0.88f)
    }

    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    // ---- Layout (generated exactly as in the designer) -----------------------------------

    private class B(val x0: Float, val x1: Float, val top: Float, val col: Int, val side: Boolean = false, val strip: Boolean = false)

    private val back = ArrayList<B>()
    private val main = ArrayList<B>()
    private val mid = ArrayList<B>()
    private val near = ArrayList<B>()
    private val low = ArrayList<B>()
    private val win = FloatArray(40000)

    private class Tree(val u: Float, val v: Float, val s: Float, val j: Boolean, val ph: Float, val k: Float)
    private val band = ArrayList<Tree>()

    init {
        var r = Mulberry32(11)
        var px = 40f
        while (px < 960f) {
            val w = 18 + r.next() * 30
            val top = 252 + r.next() * 36
            back += B(px, px + w, top, COLS[intArrayOf(0, 3, 4, 7, 6)[(r.next() * 5).toInt()]])
            px += w * (0.8f + r.next() * 0.2f)
        }
        for (m in MAIN) main += B(m[0], m[1], m[2], COLS[m[3].toInt()], side = m[4] > 0f)
        r = Mulberry32(23)
        px = 40f
        while (px < 960f) {
            val w = 24 + r.next() * 36
            val gap = px > 425 && px < 540
            if (!gap) { val top = 290 + r.next() * 36; mid += B(px, px + w, top, COLS[intArrayOf(0, 1, 3, 4, 7, 6)[(r.next() * 6).toInt()]]) }
            px += w * (0.9f + r.next() * 0.12f)
        }
        for (m in NEAR) near += B(m[0], m[1], m[2], COLS[m[3].toInt()], strip = m[4] > 0f)
        r = Mulberry32(31)
        px = 30f
        while (px < 970f) {
            val w = 40 + r.next() * 70
            val top = 400 + r.next() * 40
            low += B(px, px + w, top, COLS[intArrayOf(1, 3, 0, 6, 4, 2)[(r.next() * 6).toInt()]])
            px += w * (0.92f + r.next() * 0.1f)
        }
        r = Mulberry32(41)
        repeat(70) { band += Tree(r.next(), r.next(), 0.75f + r.next() * 0.6f, r.next() < 0.32f, r.next() * 6f, r.next()) }
        band.sortBy { it.v }
        r = Mulberry32(5)
        for (i in win.indices) win[i] = r.next()
    }

    private var wi = 0
    private fun rnd(): Float = win[wi++ % win.size]

    private fun hash(n: Float): Float {
        val v = sin(n * 12.9898) * 43758.5453
        return (v - floor(v)).toFloat()
    }

    // ---- Buildings ---------------------------------------------------------------------

    private fun building(c: Canvas, b: B, bot: Float, haze: Float, plain: Boolean = false, noRoof: Boolean = false) {
        val a = x(b.x0); val bx = x(b.x1); val top = y(b.top); val yb = min(hf, y(bot))
        if (bx < 0 || a > wf || top > hf) return
        val base = shade(b.col, haze)
        val face = if (b.side) mix(base, Color.BLACK, 0.3f) else base
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, top, 0f, yb, mix(face, Color.WHITE, 0.06f), mix(face, Color.BLACK, 0.1f), Shader.TileMode.CLAMP)
        c.drawRect(a, top, bx, yb, p)
        p.shader = null
        val fw0 = bx - a
        val sideW = if (b.side) 0f else min(fw0 * 0.22f, max(2f * k, fw0 * 0.2f))
        val fw = fw0 - sideW
        if (plain) {
            if (sideW > k) { p.color = mix(base, Color.BLACK, 0.18f); c.drawRect(bx - sideW, top, bx, yb, p) }
            return
        }
        val h = hash(b.x0 * 0.37f + b.top)
        val style = when {
            b.strip -> RIBBON
            h < 0.35f -> BALCONY
            h < 0.6f -> GRID
            h < 0.8f -> PAIRS
            else -> RIBBON
        }
        val rowH = max(3.4f * k, 3.6f * k * (sx / k) / 0.59f * JhbDesign.SKYLINE_HEIGHT / 1.7f)
        val glass = mix(face, rgb(38, 44, 64), 0.55f)
        val frame = mix(face, Color.WHITE, 0.25f)
        val slab = mix(face, Color.BLACK, 0.14f)
        val floors = ((yb - top - 4 * k) / rowH).toInt()
        for (f in 0 until floors) {
            val yy = top + 3 * k + f * rowH
            if (style != RIBBON) { p.color = slab; c.drawRect(a, yy + rowH - 0.7f * k, a + fw, yy + rowH, p) }
            if (style == RIBBON) {
                p.color = if (night) (if (rnd() < 0.5f) 0xD9FFCD82.toInt() else 0xBF1E1E30.toInt()) else glass
                c.drawRect(a + 1.2f * k, yy + 0.6f * k, a + fw - 1.2f * k, yy + 0.6f * k + rowH * 0.42f, p)
                p.color = frame
                var xx = a + 4 * k
                while (xx < a + fw - 2 * k) { c.drawRect(xx, yy + 0.6f * k, xx + 0.5f * k, yy + 0.6f * k + rowH * 0.42f, p); xx += 4 * k }
                continue
            }
            val cols = max(1, ((fw - 2 * k) / ((if (style == PAIRS) 5f else 3.4f) * k)).toInt())
            val cw = (fw - 2 * k) / cols
            for (kk in 0 until cols) {
                val xx = a + k + kk * cw
                val r = rnd()
                val ww = if (style == PAIRS) cw * 0.62f else cw * 0.55f
                val wh = rowH * 0.5f
                val wx = xx + (cw - ww) / 2
                val wy = yy + rowH * 0.18f
                if (night) {
                    p.color = when {
                        r >= 0.42f -> 0x8C141422.toInt()
                        r < 0.06f -> 0xF2BEE1FF.toInt()
                        r < 0.25f -> 0xF2FFD691.toInt()
                        else -> 0xD9FFECBE.toInt()
                    }
                    c.drawRect(wx, wy, wx + ww, wy + wh, p)
                } else {
                    p.color = frame; c.drawRect(wx - 0.3f * k, wy - 0.3f * k, wx + ww + 0.3f * k, wy + wh + 0.3f * k, p)
                    p.color = if (r < 0.15f) mix(glass, Color.WHITE, 0.25f) else glass
                    c.drawRect(wx, wy, wx + ww, wy + wh, p)
                    if (style == PAIRS) { p.color = frame; c.drawRect(wx + ww / 2 - 0.25f * k, wy, wx + ww / 2 + 0.25f * k, wy + wh, p) }
                }
            }
            if (style == BALCONY) {
                p.color = mix(face, Color.WHITE, if (night) 0.05f else 0.32f)
                c.drawRect(a + 0.5f * k, yy + rowH * 0.72f, a + fw - 0.5f * k, yy + rowH * 0.88f, p)
                p.color = mix(face, Color.BLACK, 0.25f)
                c.drawRect(a + 0.5f * k, yy + rowH * 0.88f, a + fw - 0.5f * k, yy + rowH * 0.98f, p)
            }
        }
        if (sideW > k) {
            p.color = mix(base, Color.BLACK, 0.26f)
            c.drawRect(bx - sideW, top, bx, yb, p)
            val sc = max(1, (sideW / (3 * k)).toInt())
            val cwS = sideW / sc
            for (f in 0 until floors) {
                val yy = top + 3 * k + f * rowH
                for (kk in 0 until sc) {
                    val r = rnd()
                    p.color = if (night) (if (r < 0.35f) 0xBFFFCD82.toInt() else 0x800F0F19.toInt()) else mix(base, rgb(20, 22, 34), 0.55f)
                    val wx = bx - sideW + kk * cwS + cwS * 0.25f
                    c.drawRect(wx, yy + rowH * 0.2f, wx + cwS * 0.5f, yy + rowH * 0.65f, p)
                }
            }
        }
        p.color = mix(base, Color.BLACK, 0.35f); c.drawRect(a, top, bx, top + 1.3f * k, p)
        p.color = mix(base, Color.WHITE, 0.2f); c.drawRect(a, top + 1.3f * k, a + fw, top + 1.9f * k, p)
        if (!noRoof && fw0 > 8 * k) {
            val rh = rowH * 0.9f
            if (h > 0.3f) {
                val rx = a + fw0 * (0.2f + h * 0.3f)
                p.color = mix(base, Color.BLACK, 0.15f); c.drawRect(rx, top - rh, rx + fw0 * 0.22f, top, p)
                p.color = mix(base, Color.BLACK, 0.35f); c.drawRect(rx + fw0 * 0.132f, top - rh, rx + fw0 * 0.22f, top, p)
            }
            if (h > 0.55f) { p.color = shade(rgb(200, 200, 205), 0f); c.drawRect(a + fw0 * 0.65f, top - rh * 0.7f, a + fw0 * 0.77f, top, p) }
            if (h < 0.25f) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.5f * k; p.color = mix(base, rgb(40, 40, 50), 0.5f)
                c.drawLine(a + fw0 * 0.75f, top, a + fw0 * 0.75f, top - rh * 2.2f, p)
                p.style = Paint.Style.FILL
            }
        }
    }

    // ---- Hillbrow Tower --------------------------------------------------------------------

    private val towerX get() = x(TOWER_PX)
    private fun hw(v: Float) = v * sx * JhbDesign.TOWER_WIDTH * 0.5f

    private fun cylinder(c: Canvas, p0: Float, p1: Float, w: Float, conc: Int) {
        val cx = towerX
        val lt = mix(conc, Color.WHITE, 0.25f)
        p.color = Color.BLACK
        p.shader = LinearGradient(cx - hw(w), 0f, cx + hw(w), 0f, intArrayOf(mix(conc, Color.BLACK, 0.08f), lt, mix(conc, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - hw(w), y(p0), cx + hw(w), y(p0) + max(1.2f * k, y(p1) - y(p0)), p)
        p.shader = null
    }

    private fun tower(c: Canvas) {
        val cx = towerX
        val conc = shade(rgb(214, 212, 210), 0f)
        val dk = mix(conc, Color.BLACK, 0.3f)
        val lt = mix(conc, Color.WHITE, 0.25f)
        p.style = Paint.Style.FILL
        // Shaft
        p.color = Color.BLACK
        p.shader = LinearGradient(cx - hw(15.5f), 0f, cx + hw(15.5f), 0f, intArrayOf(mix(conc, Color.BLACK, 0.08f), lt, conc, dk), floatArrayOf(0f, 0.3f, 0.55f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(cx - hw(13f), y(152f)); path.lineTo(cx - hw(15.5f), y(300f)); path.lineTo(cx + hw(15.5f), y(300f)); path.lineTo(cx + hw(13f), y(152f)); path.close()
        c.drawPath(path, p)
        p.shader = null
        p.color = mix(conc, Color.BLACK, 0.12f)
        var pp = 170f
        while (pp < 300f) { c.drawRect(cx - hw(14f), y(pp), cx + hw(14f), y(pp) + 0.6f * k, p); pp += 26f }
        if (night) { p.color = 0xBFFFFFFF.toInt(); pp = 165f; while (pp < 280f) { c.drawRect(cx - 0.5f * k, y(pp), cx + 0.5f * k, y(pp) + k, p); pp += 18f } }
        // The blue Telkom box
        val blue = if (night) rgb(60, 110, 255) else shade(rgb(46, 140, 214), 0f)
        if (night) {
            glow(c, cx, (y(98f) + y(152f)) / 2f, hw(46f), (y(158f) - y(92f)) * 0.75f, 0x4678FF, 0.32f)
        }
        p.color = Color.BLACK
        p.shader = LinearGradient(cx - hw(22f), 0f, cx + hw(22f), 0f, intArrayOf(mix(blue, Color.BLACK, 0.1f), mix(blue, Color.WHITE, 0.15f), mix(blue, Color.BLACK, 0.3f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - hw(22f), y(130f), cx + hw(22f), y(152f), p)
        p.shader = null
        p.color = if (night) 0xF2EBF5FF.toInt() else 0xF2FFFFFF.toInt()
        p.textSize = max(4.5f * k, hw(10f))
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText("Telkom", cx, (y(130f) + y(152f)) / 2f + p.textSize * 0.36f, p)
        // Head: platforms with glazed bands, cap, mast
        cylinder(c, 120f, 130f, 14f, conc)
        p.color = dk; c.drawRect(cx - hw(16f), y(129f), cx + hw(16f), y(129f) + k, p)
        cylinder(c, 117f, 120f, 26f, conc); glazed(c, 111f, 22f, 6f, conc)
        cylinder(c, 108f, 111f, 28f, conc); glazed(c, 102f, 22f, 6f, conc)
        cylinder(c, 98f, 102f, 26f, conc); cylinder(c, 91f, 98f, 8f, conc)
        p.style = Paint.Style.STROKE
        p.color = mix(conc, rgb(90, 90, 100), 0.3f)
        p.strokeWidth = 1.3f * k; c.drawLine(cx, y(91f), cx, y(68f), p)
        p.strokeWidth = 0.8f * k
        c.drawLine(cx - hw(6f), y(84f), cx + hw(6f), y(84f), p)
        c.drawLine(cx - hw(4f), y(77f), cx + hw(4f), y(77f), p)
        p.style = Paint.Style.FILL
    }

    private fun glazed(c: Canvas, p0: Float, w: Float, h: Float, conc: Int) {
        val cx = towerX
        p.shader = if (night) LinearGradient(cx - hw(w), 0f, cx + hw(w), 0f, intArrayOf(0xE6AAC8FF.toInt(), 0xFFE6F0FF.toInt(), 0xE67896E6.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        else LinearGradient(cx - hw(w), 0f, cx + hw(w), 0f, intArrayOf(mix(conc, rgb(40, 46, 64), 0.55f), mix(conc, rgb(90, 100, 125), 0.5f), mix(conc, rgb(25, 28, 40), 0.7f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - hw(w), y(p0), cx + hw(w), y(p0 + h), p)
        p.shader = null
        p.color = if (night) 0x80FFFFFF.toInt() else mix(conc, Color.BLACK, 0.2f)
        var kk = -w + 3
        while (kk < w) { c.drawRect(cx + hw(kk), y(p0), cx + hw(kk) + 0.4f * k, y(p0 + h), p); kk += 4 }
    }

    /** A soft light halo: fades smoothly from [alpha] at the centre to nothing at the rim. */
    private fun glow(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rgb: Int, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        c.save()
        c.translate(cx, cy)
        c.scale(1f, ry / rx)
        p.color = Color.BLACK
        p.shader = android.graphics.RadialGradient(0f, 0f, rx,
            intArrayOf((a shl 24) or rgb, ((a * 0.45f).toInt() shl 24) or rgb, rgb and 0xFFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx, p)
        p.shader = null
        c.restore()
    }

    // ---- Ponte City ----------------------------------------------------------------------
    // The 54-storey cylinder in Berea with the Vodacom sign wrapped round its crown. It really
    // stands almost behind Hillbrow Tower from this side, so it's set just to the right of it,
    // behind the Hillbrow blocks, with its upper floors and crown showing above them.

    private val ponteX get() = x(JhbDesign.PONTE_X)
    private val ponteR get() = 32f * JhbDesign.PONTE_WIDTH * sx
    private val ponteTopPx get() = 330f - 173f * JhbDesign.PONTE_HEIGHT
    private val ponteTop get() = y(ponteTopPx)
    private val ponteSign get() = y(ponteTopPx + 14f) - y(ponteTopPx)

    private fun ponte(c: Canvas) {
        val cx = ponteX
        val r = ponteR
        val top = ponteTop
        val signH = ponteSign
        val bot = y(345f)
        val conc = shade(rgb(196, 188, 176), 0.08f)
        val dark = mix(conc, Color.BLACK, 0.45f)
        val pi = Math.PI.toFloat()
        p.style = Paint.Style.FILL

        // Body: a shaded cylinder, lit from the west (left).
        p.color = Color.BLACK
        p.shader = LinearGradient(cx - r, 0f, cx + r, 0f,
            intArrayOf(mix(conc, Color.BLACK, 0.12f), mix(conc, rgb(255, 250, 240), 0.12f), conc, mix(conc, Color.BLACK, 0.3f), mix(conc, Color.BLACK, 0.45f)),
            floatArrayOf(0f, 0.22f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - r, top + signH, cx + r, bot, p)
        p.shader = null

        // Floors and windows wrapping round the curve: widest facing us, thinning to the edges.
        val rowH = max(1.6f * k, (y(330f) - y(ponteTopPx + 14f)) / 50f)
        val nc = 28
        var wk = 0
        var yy = top + signH + rowH * 0.6f
        while (yy < bot) {
            p.color = withAlpha(mix(conc, Color.BLACK, 0.2f), 128)
            c.drawRect(cx - r, yy + rowH - 0.5f * k, cx + r, yy + rowH, p)
            for (kk in 0 until nc) {
                val a0 = -pi / 2 + (kk + 0.18f) / nc * pi
                val a1 = -pi / 2 + (kk + 0.82f) / nc * pi
                val x0 = cx + sin(a0) * r
                val x1 = cx + sin(a1) * r
                if (x1 - x0 < 0.35f * k) continue
                val ang = (a0 + a1) / 2
                val rr = win[(wk++ * 7) % win.size]
                if (night) {
                    if (rr < 0.55f) {
                        p.color = if (rr < 0.07f) 0xF2C8E6FF.toInt() else 0xEBFFD48C.toInt()
                        c.drawRect(x0, yy + rowH * 0.15f, x1, yy + rowH * 0.7f, p)
                    }
                } else {
                    val lightK = max(0f, cos(ang + 0.5f))
                    p.color = mix(mix(conc, rgb(44, 52, 72), 0.62f), rgb(200, 215, 235), 0.25f * lightK * (if (rr < 0.2f) 1.6f else 1f))
                    c.drawRect(x0, yy + rowH * 0.15f, x1, yy + rowH * 0.7f, p)
                }
            }
            yy += rowH
        }
        // Vertical concrete ribs.
        p.color = withAlpha(mix(conc, Color.WHITE, 0.08f), 217)
        var kk = 0
        while (kk <= nc) {
            val a = -pi / 2 + kk.toFloat() / nc * pi
            val xr = cx + sin(a) * r
            val wr = max(0.5f * k, 1.4f * k * cos(a))
            c.drawRect(xr - wr / 2, top + signH, xr + wr / 2, bot, p)
            kk += 4
        }
        p.color = 0x40000000
        c.drawRect(cx - r, top + signH, cx + r, top + signH + 1.5f * k, p)

        // The Vodacom sign round the crown.
        val red = if (night) rgb(255, 24, 30) else shade(rgb(230, 0, 0), 0f)
        if (night) {
            glow(c, cx, top + signH / 2, r * 1.35f, signH * 1.4f, 0xFF2828, 0.28f)
        }
        p.color = Color.BLACK
        p.shader = LinearGradient(cx - r, 0f, cx + r, 0f,
            intArrayOf(mix(red, Color.BLACK, 0.25f), mix(red, Color.WHITE, if (night) 0.08f else 0.12f), red, mix(red, Color.BLACK, 0.45f)),
            floatArrayOf(0f, 0.25f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - r * 1.01f, top, cx + r * 1.01f, top + signH, p)
        p.shader = null
        p.color = mix(conc, Color.BLACK, 0.3f)
        c.drawRect(cx - r * 1.02f, top - 1.2f * k, cx + r * 1.02f, top + 0.4f * k, p)
        c.drawRect(cx - r * 1.01f, top + signH - k, cx + r * 1.01f, top + signH + 0.2f * k, p)

        val white = if (night) Color.WHITE else shade(Color.WHITE, 0f)
        // The wording spans most of the visible half of the drum; the logo sits in its own slot first.
        val span = 2.3f
        val widths = floatArrayOf(0.62f, 0.56f, 0.58f, 0.56f, 0.52f, 0.58f, 0.86f)
        val logoSlot = 1.15f
        val total = widths.sum() + logoSlot
        c.save()
        c.clipRect(cx - r, top, cx + r, top + signH)
        // Speech-mark logo
        val logoA = -span / 2 + logoSlot * 0.45f / total * span
        c.save()
        c.translate(cx + sin(logoA) * r, top + signH / 2)
        c.scale(cos(logoA), 1f)
        p.color = white; c.drawCircle(0f, 0f, signH * 0.3f, p)
        p.color = red; c.drawCircle(signH * 0.05f, -signH * 0.06f, signH * 0.17f, p)
        path.reset()
        path.moveTo(signH * 0.08f, signH * 0.06f); path.quadTo(-signH * 0.02f, signH * 0.24f, -signH * 0.16f, signH * 0.2f)
        path.quadTo(-signH * 0.02f, signH * 0.12f, 0f, signH * 0.04f); path.close()
        c.drawPath(path, p)
        c.restore()
        // Letters, each squeezed as it curves away round the drum.
        p.color = white
        p.textSize = signH * 0.56f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        var a = -span / 2 + logoSlot / total * span
        for ((i, ch) in "Vodacom".withIndex()) {
            val da = widths[i] / total * span
            val ang = a + da / 2
            c.save()
            c.translate(cx + sin(ang) * r, top + signH * 0.56f)
            c.scale(cos(ang), 1f)
            c.drawText(ch.toString(), 0f, p.textSize * 0.36f, p)
            c.restore()
            a += da
        }
        c.restore()

        // Roof: lift housing and antenna.
        p.color = mix(conc, Color.BLACK, 0.15f)
        c.drawRect(cx - r * 0.25f, top - signH * 0.45f, cx + r * 0.07f, top, p)
        p.color = dark
        c.drawRect(cx - r * 0.05f, top - signH * 0.45f, cx + r * 0.07f, top, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f * k
        c.drawLine(cx + r * 0.3f, top, cx + r * 0.3f, top - signH * 0.9f, p)
        p.style = Paint.Style.FILL
    }

    // ---- Bake ------------------------------------------------------------------------------

    private fun bake() {
        val bmp = city ?: Bitmap.createBitmap(wf.toInt(), hf.toInt(), Bitmap.Config.ARGB_8888).also { city = it }
        bmp.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bmp)
        wi = 0
        if (night) {
            p.color = Color.BLACK
        p.shader = LinearGradient(0f, y(230f), 0f, y(310f), 0x00FF8C3C, 0x73FF8C3C, Shader.TileMode.CLAMP)
            c.drawRect(0f, y(230f), wf, y(310f), p)
            p.shader = null
        }
        for (b in back) building(c, b, 330f, 0.45f, plain = true)
        p.color = shade(mix(COLS[0], sb, 0.3f), 0f)
        c.drawRect(0f, y(300f), wf, hf, p)
        ponte(c)
        tower(c)
        for (b in main) building(c, b, 345f, 0.1f, noRoof = b.side)
        for (b in mid) building(c, b, 395f, 0.03f)
        for (b in near) building(c, b, 460f, 0f)
        for (b in low) building(c, b, 700f, 0f)
        bakedKey = key()
    }

    private fun key() = (dark * 40).toInt() * 100 + (warm * 40).toInt()

    // ---- Trees ----------------------------------------------------------------------------

    private fun blobTree(c: Canvas, x: Float, y: Float, s: Float, col: Int, ph: Float) {
        val sw = sin(time * 0.7f + ph) * s * 0.03f
        p.color = mix(col, Color.BLACK, 0.22f)
        c.drawOval(x + sw - s * 0.62f, y + s * 0.08f - s * 0.42f, x + sw + s * 0.62f, y + s * 0.08f + s * 0.42f, p)
        p.color = col
        for (kk in 0 until 3) {
            val a = kk * 2.1f + ph
            val cx = x + cos(a) * s * 0.28f + sw
            val cy = y - s * 0.06f + sin(a) * s * 0.12f
            c.drawOval(cx - s * 0.4f, cy - s * 0.32f, cx + s * 0.4f, cy + s * 0.32f, p)
        }
        p.color = mix(col, rgb(255, 255, 220), 0.2f)
        c.drawOval(x - s * 0.15f + sw - s * 0.26f, y - s * 0.22f - s * 0.16f, x - s * 0.15f + sw + s * 0.26f, y - s * 0.22f + s * 0.16f, p)
    }

    private fun treeBand(c: Canvas) {
        val tb = JhbDesign.TREE_BAND
        val top = hf * (1f - tb)
        p.style = Paint.Style.FILL
        p.color = shade(rgb(58, 86, 46), 0f)
        c.drawRect(0f, top + hf * tb * 0.45f, wf, hf, p)
        val sizeK = k * kotlin.math.sqrt(tb / 0.12f)
        for (t in band) {
            val yy = top + t.v * hf * tb * 0.85f + hf * 0.01f
            val s = (22f + t.v * 16f) * t.s * sizeK
            val green = mix(rgb(58, 104, 46), rgb(100, 142, 58), t.k)
            val col = if (t.j) mix(green, rgb(166, 118, 210), bloom) else green
            val xx = t.u * (wf + 30 * k) - 15 * k
            blobTree(c, xx, yy, s, shade(col, 0f), t.ph)
            if (t.j && bloom > 0.4f && !night) {
                p.color = withAlpha(shade(rgb(206, 176, 240), 0f), 178)
                for (kk in 0 until 4) {
                    val a = kk * 1.7f + t.ph
                    c.drawCircle(xx + cos(a) * s * 0.3f, yy - s * 0.15f + sin(a) * s * 0.12f, s * 0.05f, p)
                }
            }
        }
        if (bloom > 0.4f) { // fallen petals
            p.color = withAlpha(shade(rgb(170, 130, 215), 0f), 140)
            val r = Mulberry32(9)
            repeat(80) { val px = r.next() * wf; val py = hf - r.next() * hf * tb * 0.35f; c.drawRect(px, py, px + 1.6f * k, py + k, p) }
        }
    }

    // ---- Rooftops for the pigeons ----------------------------------------------------------

    /**
     * Free spots along every visible rooftop, [gap] apart: one array of (x, y) pairs per roof.
     * A spot counts only if no building drawn later covers it and it's above the tree line.
     */
    fun roofSpots(gap: Float): List<FloatArray> {
        val order = ArrayList<B>().apply { addAll(main); addAll(mid); addAll(near); addAll(low) }
        val treeTop = hf * (1f - JhbDesign.TREE_BAND) - gap
        val out = ArrayList<FloatArray>()
        for ((i, b) in order.withIndex()) {
            if (b.side) continue
            val top = y(b.top)
            if (top < 0f || top > treeTop) continue
            val a = x(b.x0); val bx = x(b.x1)
            val pts = ArrayList<Float>()
            var px = a + gap
            while (px < bx - gap) {
                val hidden = (i + 1 until order.size).any { j -> val o = order[j]; px >= x(o.x0) && px <= x(o.x1) && top >= y(o.top) - gap }
                if (px > gap && px < wf - gap && !hidden) { pts += px; pts += top }
                px += gap
            }
            if (pts.size >= 6) out += pts.toFloatArray()
        }
        return out
    }

    // ---- Frame ----------------------------------------------------------------------------

    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        if (city == null || key() != bakedKey) bake()
        canvas.drawBitmap(city!!, 0f, 0f, null)
        // Aircraft warning light on the mast.
        if (dark > 0.35f && sin(time * 2.4f) > -0.2f) {
            p.color = 0xFFFF3A30.toInt()
            canvas.drawCircle(towerX, y(68f), 1.6f * k, p)
            canvas.drawCircle(ponteX + ponteR * 0.3f, ponteTop - ponteSign * 0.9f, 1.1f * k, p) // Ponte's mast
        }
        treeBand(canvas)
    }

    private fun jacarandaSeason(): Float {
        val cal = Calendar.getInstance().apply { timeInMillis = SkyTime.now() }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        // Peak about 25 October in the south (25 April in the north), fading over a few weeks.
        val peak = if (cfg.latitude < 0f) 298 else 115
        var d = abs(day - peak).toFloat()
        d = min(d, 365f - d)
        return (1f - ((d - 15f) / 20f).coerceIn(0f, 1f))
    }

    private companion object {
        const val PHOTO_W = 640f
        const val TOWER_PX = 494f
        const val RIBBON = 0
        const val BALCONY = 1
        const val GRID = 2
        const val PAIRS = 3

        // salmon, brick, deep, cream, grey, blue, beige, pink
        val COLS = intArrayOf(
            Color.rgb(206, 142, 112), Color.rgb(174, 96, 76), Color.rgb(146, 72, 58), Color.rgb(222, 204, 180),
            Color.rgb(176, 176, 182), Color.rgb(140, 150, 172), Color.rgb(200, 184, 160), Color.rgb(200, 150, 140),
        )

        private const val SA = 0f; private const val BR = 1f; private const val DE = 2f; private const val CR = 3f
        private const val GR = 4f; private const val BL = 5f; private const val BE = 6f; private const val PI = 7f

        // x0, x1, top (photo px), colour, side face
        val MAIN = arrayOf(
            floatArrayOf(110f, 150f, 250f, SA, 0f), floatArrayOf(150f, 185f, 270f, CR, 0f), floatArrayOf(185f, 205f, 262f, BR, 0f),
            floatArrayOf(205f, 240f, 283f, PI, 0f), floatArrayOf(240f, 258f, 236f, SA, 0f), floatArrayOf(258f, 272f, 243f, BR, 0f),
            floatArrayOf(272f, 300f, 222f, GR, 0f), floatArrayOf(285f, 300f, 210f, CR, 0f), floatArrayOf(300f, 325f, 224f, GR, 0f),
            floatArrayOf(325f, 350f, 258f, GR, 0f), floatArrayOf(350f, 392f, 215f, SA, 0f), floatArrayOf(392f, 402f, 215f, DE, 1f),
            floatArrayOf(400f, 425f, 243f, BE, 0f), floatArrayOf(425f, 440f, 270f, CR, 0f), floatArrayOf(440f, 470f, 255f, GR, 0f),
            floatArrayOf(470f, 483f, 262f, CR, 0f), floatArrayOf(477f, 512f, 285f, SA, 0f), floatArrayOf(500f, 542f, 262f, BR, 0f),
            floatArrayOf(542f, 592f, 233f, GR, 0f), floatArrayOf(592f, 675f, 233f, BL, 0f), floatArrayOf(548f, 618f, 268f, BR, 0f),
            floatArrayOf(675f, 698f, 262f, CR, 0f), floatArrayOf(695f, 735f, 252f, PI, 0f), floatArrayOf(735f, 745f, 252f, DE, 1f),
            floatArrayOf(745f, 775f, 262f, CR, 0f), floatArrayOf(775f, 812f, 255f, BR, 0f), floatArrayOf(812f, 830f, 240f, GR, 0f),
            floatArrayOf(830f, 870f, 236f, SA, 0f), floatArrayOf(870f, 905f, 250f, CR, 0f),
        )

        // x0, x1, top, colour, ribbon windows
        val NEAR = arrayOf(
            floatArrayOf(630f, 665f, 290f, BR, 0f), floatArrayOf(525f, 580f, 322f, DE, 0f), floatArrayOf(607f, 740f, 360f, BR, 1f),
            floatArrayOf(740f, 920f, 366f, BR, 1f), floatArrayOf(60f, 180f, 350f, CR, 0f), floatArrayOf(180f, 260f, 345f, SA, 0f),
            floatArrayOf(250f, 330f, 360f, CR, 0f), floatArrayOf(330f, 380f, 350f, BR, 0f), floatArrayOf(440f, 500f, 365f, CR, 0f),
            floatArrayOf(380f, 440f, 372f, SA, 0f), floatArrayOf(500f, 610f, 380f, BE, 0f), floatArrayOf(40f, 60f, 360f, BR, 0f),
        )
    }
}
