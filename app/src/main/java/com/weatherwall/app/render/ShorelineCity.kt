package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** Shoreline settings, as exported from the in-chat shoreline designer. */
object ShorelineDesign {
    const val CITY_PATTERN = 42
    const val TOWER_HEIGHT = 0.9
    const val DENSITY = 1.15
    const val BACK_ROW = true
    const val CRANES = true
}

/** Same generator as the designer, in doubles like JavaScript, so the layout matches exactly. */
private class Mulberry32d(seed: Int) {
    private var s = seed
    fun next(): Double {
        s += 0x6D2B79F5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/**
 * The city along the far shore, from Woodstock to Sea Point: low industrial buildings, the city
 * centre towers (with the Portside Tower and the Civic Centre), harbour cranes and sheds, the
 * V&A Waterfront with its clock tower, and Sea Point's apartment blocks. Two rows for depth.
 * Painted once into a bitmap for each side the sun can light it from.
 */
class ShorelineCity(
    private val wf: Float,
    private val shoreY: Float,
    private val unit: Float,
    private val night: Boolean,
    private val haze: Int,
) {
    class Building(
        val x: Double, val w: Double, var h: Double, val type: String, val grid: Boolean,
        val tone: Double, val lit: Double, val warm: Double, val zone: String, val seed: Int,
    )

    val backRow = ArrayList<Building>()
    val frontRow = ArrayList<Building>()

    private val px2 = wf / 720f // the designer's "2 phone pixels" unit
    private fun mx(f: Double) = (f * wf).toFloat()

    /** Bitmap covers from here down to just below the shoreline. */
    val top: Float = shoreY - unit * 0.12f
    private val bmpH = (shoreY - top + 4f).toInt()

    /** Red aviation lights on the tallest towers: (x, y) pairs in screen coordinates. */
    val beacons: FloatArray

    init {
        generate()
        val list = ArrayList<Float>()
        for (b in frontRow) {
            val hh = (b.h * unit).toFloat()
            val antTop = when {
                b.type == "portside" -> shoreY - hh - hh * 0.3f
                b.type == "antenna" && b.h > 0.045 -> shoreY - hh - hh * 0.25f
                else -> continue
            }
            list += mx(b.x + b.w / 2); list += antTop
        }
        beacons = list.toFloatArray()
    }

    private fun generate() {
        val r = Mulberry32d(ShorelineDesign.CITY_PATTERN)
        val th = ShorelineDesign.TOWER_HEIGHT
        val dn = ShorelineDesign.DENSITY
        val zones = listOf(
            Triple(0.13, 0.34, "low"), Triple(0.34, 0.56, "cbd"), Triple(0.56, 0.66, "port"),
            Triple(0.66, 0.76, "wf"), Triple(0.76, 1.0, "sp"),
        )
        for (row in 0..1) {
            val out = if (row == 0) backRow else frontRow
            for ((z0, z1, k) in zones) {
                var x = z0 + r.next() * 0.004
                while (x < z1) {
                    var grid = r.next() < 0.5
                    val w: Double
                    var h: Double
                    var type = "slab"
                    when (k) {
                        "low" -> {
                            w = 0.008 + r.next() * 0.014; h = 0.004 + r.next() * 0.009
                            type = if (r.next() < 0.35) "saw" else "slab"
                        }
                        "cbd" -> {
                            val bell = exp(-((x - 0.455) / 0.06).pow(2))
                            w = 0.006 + r.next() * 0.011
                            h = (0.01 + 0.045 * bell * (0.35 + 0.65 * r.next())) * th
                            type = if (h > 0.03) {
                                if (r.next() < 0.4) "crown" else if (r.next() < 0.5) "antenna" else "slab"
                            } else "slab"
                        }
                        "port" -> { w = 0.012 + r.next() * 0.016; h = 0.004 + r.next() * 0.006; type = "shed" }
                        "wf" -> {
                            w = 0.008 + r.next() * 0.014; h = 0.006 + r.next() * 0.011
                            type = if (r.next() < 0.3) "pitched" else "slab"
                        }
                        else -> { w = 0.007 + r.next() * 0.011; h = 0.008 + r.next() * 0.017; grid = true }
                    }
                    if (row == 0) h *= 1.25 + r.next() * 0.5
                    val tone = 0.86 + r.next() * 0.2
                    val lit = r.next()
                    val warm = r.next()
                    val seed = floor(r.next() * 1e9).toInt()
                    out += Building(x, w, h, type, grid, tone, lit, warm, k, seed)
                    x += w + (if (row == 0) 0.003 + r.next() * 0.012 else 0.0006 + r.next() * 0.003) / dn
                }
            }
        }
        // Landmarks.
        frontRow += Building(0.463, 0.0105, 0.072 * th, "portside", true, 1.02, 0.5, 0.2, "cbd", 77)
        frontRow += Building(0.392, 0.034, 0.022 * th, "slab", false, 0.95, 0.6, 0.7, "cbd", 78)
        frontRow += Building(0.695, 0.0032, 0.014, "clock", false, 1.0, 0.0, 1.0, "wf", 79)
        frontRow.sortBy { it.h } // shorter first, so taller buildings stand in front
    }

    private var cachedRight: Bitmap? = null
    private var cachedLeft: Bitmap? = null

    /** The painted city for light coming from the right (afternoon) or the left (morning). */
    fun bitmap(lightFromRight: Boolean): Bitmap {
        val cached = if (lightFromRight) cachedRight else cachedLeft
        if (cached != null) return cached
        val bmp = Bitmap.createBitmap(wf.toInt().coerceAtLeast(1), bmpH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(0f, -top)
        if (ShorelineDesign.BACK_ROW) for (b in backRow) drawBuilding(c, b, lightFromRight, back = true)
        if (ShorelineDesign.CRANES) for (x in doubleArrayOf(0.572, 0.597, 0.622, 0.646)) drawCrane(c, x)
        for (b in frontRow) drawBuilding(c, b, lightFromRight, back = false)
        if (lightFromRight) cachedRight = bmp else cachedLeft = bmp
        return bmp
    }

    // ---- Painting (mirrors the designer's drawing) ----------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    private fun drawBuilding(c: Canvas, b: Building, lightFromRight: Boolean, back: Boolean) {
        val x = mx(b.x)
        val wd = mx(b.w)
        val hh = (b.h * unit).toFloat()
        val top = shoreY - hh

        var face = when (b.zone) {
            "sp", "wf" -> rgb(236, 232, 222)
            "low", "port" -> rgb(176, 178, 176)
            else -> rgb(214, 220, 226)
        }
        if (b.type == "portside") face = rgb(200, 212, 224)
        face = scale(face, b.tone.toFloat())
        if (night) face = rgb(22, 26, 36)
        face = mix(face, haze, if (back) 0.55f else if (night) 0.2f else 0.18f)

        val sideW = max(1.2f * px2, wd * 0.24f)
        val dirAbs = 0.6f // the side face's shade; a fixed mid value keeps the bitmap valid all morning/afternoon
        val sideCol = scale(face, if (night) 0.8f else 0.72f + 0.22f * (1f - dirAbs))
        val sx = if (lightFromRight) x + wd - sideW else x

        fill.color = face
        c.drawRect(x, top, x + wd, shoreY + 1f, fill)
        fill.color = sideCol
        c.drawRect(sx, top, sx + sideW, shoreY + 1f, fill)

        when (b.type) {
            "saw" -> {
                fill.color = scale(face, 0.8f)
                val n = max(2, Math.round(wd / (6f * px2 / 2f)))
                for (i in 0 until n) {
                    val a = x + i * wd / n
                    c.drawPath(Path().apply {
                        moveTo(a, top); lineTo(a + wd / n, top); lineTo(a + wd / n, top - hh * 0.35f); close()
                    }, fill)
                }
            }
            "pitched" -> {
                fill.color = if (night) rgb(30, 30, 36) else mix(rgb(150, 80, 60), haze, 0.3f)
                c.drawPath(Path().apply {
                    moveTo(x - 1f, top); lineTo(x + wd / 2f, top - hh * 0.3f); lineTo(x + wd + 1f, top); close()
                }, fill)
            }
            "crown", "portside" -> {
                fill.color = scale(face, 0.95f)
                c.drawRect(x + wd * 0.18f, top - hh * 0.07f, x + wd * 0.82f, top, fill)
                c.drawRect(x + wd * 0.34f, top - hh * 0.12f, x + wd * 0.66f, top - hh * 0.07f, fill)
            }
        }
        val antTop = when (b.type) {
            "antenna" -> top - hh * 0.25f
            "portside" -> top - hh * 0.3f
            else -> null
        }
        if (antTop != null) {
            line.color = if (night) rgb(60, 60, 70) else rgb(120, 124, 130)
            line.strokeWidth = max(0.6f, 1.4f * px2)
            c.drawLine(x + wd / 2f, top - (if (b.type == "portside") hh * 0.12f else 0f), x + wd / 2f, antTop, line)
        }
        if (b.type == "clock") {
            fill.color = if (night) rgb(60, 30, 30) else rgb(168, 62, 52)
            c.drawRect(x, top, x + wd, top + hh, fill)
            fill.color = if (night) rgb(255, 230, 170) else rgb(240, 236, 226)
            c.drawCircle(x + wd / 2f, top + hh * 0.25f, wd * 0.32f, fill)
            return
        }
        if (b.type == "shed") return

        // Windows: ribbon bands or a grid by day; a scatter of warm and cool lights at night.
        val r = Mulberry32d(b.seed)
        val rowH = 4.2f * px2
        val winH = rowH * 0.45f
        if (hh < rowH * 1.5f) return
        val cols = if (b.grid) max(1, floor((wd - sideW) / (3.4f * px2)).toInt()) else 1
        val dayWin = if (b.zone == "sp" || b.zone == "wf") rgb(120, 140, 160) else rgb(90, 118, 146)
        var y = top + rowH * 0.6f
        while (y < shoreY - rowH * 0.5f) {
            for (col in 0 until cols) {
                val cx = (if (lightFromRight) x else x + sideW) + col * ((wd - sideW) / cols) +
                    (if (b.grid) 0.6f else 0.8f) * px2
                val cw = if (b.grid) (wd - sideW) / cols - 1.2f * px2 else wd - sideW - 1.6f * px2
                if (night) {
                    val on = r.next() < 0.25 + 0.35 * b.lit
                    if (!on) continue
                    fill.color = if (r.next() < 0.35 + 0.5 * b.warm) withAlpha(rgb(255, 214, 140), 230) else withAlpha(rgb(220, 235, 255), 217)
                } else {
                    fill.color = withAlpha(dayWin, if (back) 64 else 140)
                }
                c.drawRect(cx, y, cx + max(0.8f, cw), y + winH, fill)
            }
            y += rowH
        }
    }

    private fun drawCrane(c: Canvas, at: Double) {
        val x = mx(at)
        val h = 0.03f * unit
        val top = shoreY - h
        val w = mx(0.012)
        line.color = if (night) rgb(70, 76, 90) else mix(rgb(226, 232, 238), haze, 0.15f)
        line.strokeWidth = max(0.8f, 1.6f * px2)
        c.drawLine(x, shoreY, x, top, line)
        c.drawLine(x + w, shoreY, x + w, top, line)
        c.drawLine(x, top + h * 0.35f, x + w, top + h * 0.35f, line)
        c.drawLine(x - w * 0.9f, top, x + w * 2.2f, top, line)
        c.drawLine(x + w * 0.5f, top - h * 0.25f, x - w * 0.9f, top, line)
        c.drawLine(x + w * 0.5f, top - h * 0.25f, x + w * 2.2f, top, line)
        fill.color = if (night) rgb(90, 96, 110) else mix(rgb(62, 104, 150), haze, 0.2f)
        c.drawRect(x + w * 0.1f, top - h * 0.08f, x + w * 0.9f, top + h * 0.04f, fill)
        if (night) {
            fill.color = rgb(255, 236, 190)
            c.drawCircle(x + w * 2.2f, top, 1.5f * px2, fill)
            c.drawCircle(x - w * 0.9f, top, 1.5f * px2, fill)
        }
    }
}
