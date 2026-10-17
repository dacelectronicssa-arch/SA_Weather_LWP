package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Altron head office settings. Everything is laid out in the reference photo's pixels
 * (747 × 1042, a low two-point view of the corner with vertical walls kept vertical).
 */
object AltronDesign {
    /** Eye level, as a fraction of the phone's height (the sun and moon set here). */
    const val HORIZON = 0.93f

    /** 1 shows the photo's full width; above 1 zooms in on [FOCUS_X], trimming the sides. */
    const val ZOOM = 1.6f
    /** Photo x kept in the middle of the screen when zoomed (the logo and the corner). */
    const val FOCUS_X = 400f

    const val PHOTO_W = 747f
    const val PHOTO_H = 1042f

    // The lens: the near corner of the building and the two vanishing points on eye level.
    const val EYE_Y = 945f
    const val CORNER_X = 456.5f
    const val LEFT_VP = -60f       // left facade (the big ALTRON wall) runs to here
    const val RIGHT_VP = 917f      // right facade (the entrance block)
    const val BLOCK_VP = 787f      // the taller silver block behind, at the far right

    // Heights at the near corner (photo y).
    const val ROOF = 550f
    const val BAND = 612f          // bottom of the translucent band under the roof
    const val CLAD = 750f          // bottom of the navy / dark grey cladding
    const val GROUND = 955f

    /** Inverse-distance step between the structural bays of the long left wall. */
    const val BAY = 0.00094f
}

/**
 * Altron's head office: the corner of the building seen from the car park at dusk, with the
 * lit ALTRON sign on the navy wall, the entrance canopy and the long carport. Laid out from the
 * reference photo with its perspective; lights come on from dusk.
 */
class AltronLandscape(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
) : Landscape {
    private val d = AltronDesign
    private val wf = width.toFloat()
    private val hf = height.toFloat()
    /** Photo px -> screen px (the photo fills the width, or more when zoomed; extra phone height is sky). */
    private val s = wf / d.PHOTO_W * d.ZOOM
    /** Screen x of the photo's left edge (≤ 0 when zoomed, never leaving a gap at either side). */
    private val ox = (wf / 2f - d.FOCUS_X * s).coerceIn(wf - d.PHOTO_W * s, 0f)
    private fun sy(py: Float) = hf - (d.PHOTO_H - py) * s

    override val tableTopY get() = sy(d.ROOF)
    override val mountainUnit get() = (d.GROUND - d.ROOF) * s

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val m = Matrix()
    private var bmp: Bitmap? = null
    private var bakedKey = -1

    // ---- Light ------------------------------------------------------------------------------

    private val night = cfg.night
    private var twilight = 0f
    private var sunDir = 0f
    private var sunAmt = 0f

    override fun setTwilight(amount: Float) { twilight = amount }
    override fun setLight(direction: Float, intensity: Float) { sunDir = direction; sunAmt = intensity }
    override fun update(dt: Float) {}

    /** 0 = daylight .. 1 = the photo's dusk. */
    private val dusk get() = if (night) 1f else twilight
    /** 0 .. 1 from dusk into deep night. */
    private val deep get() = if (night) 1f - twilight else 0f
    /** Building and carport lights. */
    private val lights get() = night || twilight > 0.55f

    private val grey = cfg.overcast || cfg.fog || cfg.rain > 0
    /** Direct sun on each wall by day: the left wall faces left of camera, the right wall right. */
    private val sunLeft get() = if (grey) 0f else max(0f, -sunDir) * sunAmt * (1f - dusk)
    private val sunRight get() = if (grey) 0f else max(0f, sunDir) * sunAmt * (1f - dusk)

    /** A material: its daylight colour and its colour in the reference photo at dusk. */
    private fun col(day: Int, duskCol: Int, sun: Float = 0f): Int {
        var c = mix(if (grey) mix(day, 0xFFA9AFB6.toInt(), 0.12f) else day, duskCol, dusk)
        if (sun > 0f) c = mix(c, Color.WHITE, sun * 0.18f)
        if (deep > 0f) c = mix(c, NIGHT, deep * 0.62f)
        return c
    }

    /** A surface lit by the building's own lights once they are on. */
    private fun litCol(day: Int, lit: Int, sun: Float = 0f): Int =
        if (lights) mix(lit, scale(lit, 0.8f), deep * 0.5f) else col(day, mix(lit, NIGHT, 0.45f), sun)

    private fun key(): Int = (dusk * 24).toInt() * 10000 + (deep * 24).toInt() * 100 +
        (if (lights) 50 else 0) + ((sunDir + 1f) * 4f).toInt() * 5 + (sunAmt * 4f).toInt()

    // ---- Perspective -----------------------------------------------------------------------

    /** Screen y on a facade at photo x, for the line that is at [cornerY] on the near corner. */
    private fun onLeft(x: Float, cornerY: Float) = d.EYE_Y + (cornerY - d.EYE_Y) * (x - d.LEFT_VP) / (d.CORNER_X - d.LEFT_VP)
    private fun onRight(x: Float, cornerY: Float) = d.EYE_Y + (cornerY - d.EYE_Y) * (d.RIGHT_VP - x) / (d.RIGHT_VP - d.CORNER_X)
    private fun onBlock(x: Float, y658: Float) = d.EYE_Y + (y658 - d.EYE_Y) * (d.BLOCK_VP - x) / (d.BLOCK_VP - BLOCK_X)

    /** Photo x of a point [bays] structural bays along the left wall from the corner. */
    private fun leftBayX(bays: Float) = d.LEFT_VP + 1f / (1f / (d.CORNER_X - d.LEFT_VP) + bays * d.BAY)
    private fun rightX(t: Float) = d.RIGHT_VP - 1f / (1f / (d.RIGHT_VP - d.CORNER_X) + t)

    /** A band of the left wall between two corner heights, from x0 to x1. */
    private fun leftBand(x0: Float, x1: Float, y0: Float, y1: Float): Path = Path().apply {
        moveTo(x0, onLeft(x0, y0)); lineTo(x1, onLeft(x1, y0)); lineTo(x1, onLeft(x1, y1)); lineTo(x0, onLeft(x0, y1)); close()
    }

    private fun rightBand(x0: Float, x1: Float, y0: Float, y1: Float): Path = Path().apply {
        moveTo(x0, onRight(x0, y0)); lineTo(x1, onRight(x1, y0)); lineTo(x1, onRight(x1, y1)); lineTo(x0, onRight(x0, y1)); close()
    }

    private fun blockBand(x0: Float, x1: Float, y0: Float, y1: Float): Path = Path().apply {
        moveTo(x0, onBlock(x0, y0)); lineTo(x1, onBlock(x1, y0)); lineTo(x1, onBlock(x1, y1)); lineTo(x0, onBlock(x0, y1)); close()
    }

    private fun fill(c: Canvas, shape: Path, color: Int) { p.shader = null; p.style = Paint.Style.FILL; p.color = color; c.drawPath(shape, p) }

    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float) {
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = w; p.color = color
        c.drawLine(x0, y0, x1, y1, p)
        p.style = Paint.Style.FILL
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int, sx: Float = 1f, sy: Float = 1f) {
        if (alpha <= 0) return
        c.save()
        c.translate(x, y); c.scale(sx, sy)
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = RadialGradient(0f, 0f, r, withAlpha(color, alpha), withAlpha(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, r, p)
        p.shader = null
        c.restore()
    }

    // ---- Bake ------------------------------------------------------------------------------

    private fun bake() {
        val bh = ((d.PHOTO_H - TOP) * s).toInt().coerceAtLeast(1)
        val b = bmp ?: Bitmap.createBitmap(wf.toInt().coerceAtLeast(1), bh, Bitmap.Config.ARGB_8888).also { bmp = it }
        b.eraseColor(Color.TRANSPARENT)
        val c = Canvas(b)
        c.translate(ox, 0f)
        c.scale(s, s)
        c.translate(0f, -TOP)
        distance(c)
        ground(c)
        leftWall(c)
        rightWall(c)
        silverBlock(c)
        carport(c)
        entranceCanopy(c)
        foreground(c)
        if (lights) lightGlows(c)
        bakedKey = key()
    }

    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        if (bmp == null || key() != bakedKey) bake()
        canvas.drawBitmap(bmp!!, 0f, sy(TOP), null)
    }

    // ---- The far distance ------------------------------------------------------------------

    private fun distance(c: Canvas) {
        val tree = col(0xFF4C5A44.toInt(), 0xFF2A2C3A.toInt())
        // Far left: low trees and a light industrial unit beyond the carport.
        fill(c, Path().apply {
            moveTo(0f, 948f); lineTo(0f, 918f); quadTo(6f, 910f, 12f, 916f); quadTo(18f, 906f, 26f, 918f); lineTo(30f, 948f); close()
        }, tree)
        fill(c, Path().apply { addRect(0f, 922f, 22f, 946f, Path.Direction.CW) }, col(0xFFC9CCD0.toInt(), 0xFF8E8A94.toInt()))
        line(c, 9f, 946f, 9f, 900f, col(0xFF5A626C.toInt(), 0xFF2C3040.toInt()), 0.8f)
        // Far right: a neighbouring warehouse and a tree.
        fill(c, Path().apply { addRect(730f, 902f, 747f, 952f, Path.Direction.CW) }, col(0xFF6F7884.toInt(), 0xFF3E4152.toInt()))
        fill(c, Path().apply { addRect(730f, 902f, 747f, 906f, Path.Direction.CW) }, col(0xFF39414D.toInt(), 0xFF1F2230.toInt()))
        fill(c, Path().apply { addCircle(742f, 918f, 9f, Path.Direction.CW) }, tree)
    }

    // ---- Paving -----------------------------------------------------------------------------

    private fun ground(c: Canvas) {
        val wet = cfg.rain > 0
        val farCol = col(0xFF9C807A.toInt(), 0xFF7C6064.toInt())
        val nearCol = col(0xFF8A665F.toInt(), 0xFF56404E.toInt()).let { if (wet) scale(it, 0.82f) else it }
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, 940f, 0f, d.PHOTO_H, farCol, nearCol, Shader.TileMode.CLAMP)
        c.drawRect(0f, 935f, d.PHOTO_W, d.PHOTO_H, p)
        p.shader = null

        // Brick paving: short dashes laid in a herringbone, each pointing at one of the two
        // vanishing points, shrinking with distance.
        c.save()
        c.clipRect(0f, d.EYE_Y + 4f, d.PHOTO_W, d.PHOTO_H)
        val lightBrick = withAlpha(scale(nearCol, 1.28f), 120)
        val darkBrick = withAlpha(scale(nearCol, 0.62f), 120)
        var y = d.EYE_Y + 6f
        var row = 0
        while (y < d.PHOTO_H) {
            val depth = (y - d.EYE_Y) / (d.PHOTO_H - d.EYE_Y) // 0 far .. 1 near
            val step = 1.6f + 7.5f * depth
            var x = (row % 2) * step * 0.5f
            var i = row
            while (x < d.PHOTO_W) {
                val vp = if (i % 2 == 0) d.LEFT_VP else d.RIGHT_VP
                val dx = x - vp
                val dy = y - d.EYE_Y
                val len = step * 0.75f / kotlin.math.sqrt(dx * dx + dy * dy)
                line(c, x, y, x - dx * len, y - dy * len, if ((i * 7 + row * 3) % 5 < 2) lightBrick else darkBrick, 0.35f + 0.9f * depth)
                x += step
                i++
            }
            y += 0.8f + 3.2f * depth
            row++
        }
        c.restore()

        // The lighter concrete apron along the foot of the long wall, lit by the carport.
        val apron = litCol(0xFFB8B2A8.toInt(), 0xFF9A7A50.toInt())
        fill(c, Path().apply {
            moveTo(0f, 944f); lineTo(330f, onLeft(330f, d.GROUND) - 1f)
            lineTo(330f, 966f); lineTo(0f, 952f); close()
        }, apron)
        // Paving lines at the edge of the apron.
        line(c, 0f, 952f, 330f, 966f, withAlpha(col(0xFFDCD6CC.toInt(), 0xFFB79A78.toInt()), 150), 0.8f)
        // The entrance forecourt under the canopy.
        fill(c, Path().apply {
            moveTo(330f, 962f); lineTo(456f, onRight(456f, d.GROUND) + 1f); lineTo(747f, 952f); lineTo(747f, 962f)
            lineTo(600f, 968f); lineTo(470f, 972f); lineTo(330f, 972f); close()
        }, litCol(0xFF9E8A80.toInt(), 0xFF6E5452.toInt()))
        // Tonal patches in the brick (wear, oil, cleaner bricks), squashed by perspective.
        val rnd = java.util.Random(11)
        for (k in 0 until 46) {
            val gy = d.EYE_Y + 10f + rnd.nextFloat() * (d.PHOTO_H - d.EYE_Y)
            val depth = (gy - d.EYE_Y) / (d.PHOTO_H - d.EYE_Y)
            val gx = rnd.nextFloat() * d.PHOTO_W
            val r = 20f + 70f * depth * (0.5f + rnd.nextFloat())
            glow(c, gx, gy, r, if (rnd.nextBoolean()) Color.BLACK else 0xFFE8C8D0.toInt(), 16 + rnd.nextInt(14), sy = 0.16f + 0.1f * depth)
        }
        // Faint bay lines in front of the loading doors, square to the wall.
        val bayLine = withAlpha(col(0xFFEDEDED.toInt(), 0xFFC6B4B0.toInt()), 110)
        for (k in 0..18) {
            val bx = leftBayX(1.2f + k * 0.5f)
            if (bx < 4f) break
            val by = onLeft(bx, d.GROUND) + 9f
            val dx = bx - d.RIGHT_VP
            val dy = by - d.EYE_Y
            val len = 0.32f
            line(c, bx, by, bx + dx * len * 0.12f, by + dy * len, bayLine, 0.35f + 0.5f * ((by - d.EYE_Y) / 60f).coerceIn(0f, 1f))
        }
        // Walkway pavers under the entrance canopy: joints running to both vanishing points.
        c.save()
        c.clipPath(Path().apply {
            moveTo(330f, 962f); lineTo(456f, onRight(456f, d.GROUND) + 1f); lineTo(747f, 952f); lineTo(747f, 962f)
            lineTo(600f, 968f); lineTo(470f, 972f); lineTo(330f, 972f); close()
        })
        val joint = withAlpha(col(0xFF6E625E.toInt(), 0xFF3A2C30.toInt()), 150)
        var jx = 300f
        while (jx < 760f) { line(c, d.RIGHT_VP, d.EYE_Y, jx - 260f, 1042f, joint, 0.35f); jx += 7f }
        var jy = 954f
        while (jy < 975f) { line(c, 320f, jy + 4f, 747f, jy - 6f, joint, 0.35f); jy += 2.6f }
        c.restore()
        // Soft shadows from the canopy posts, thrown away from the lit foyer.
        val shadow = withAlpha(Color.BLACK, if (lights) 70 else 40)
        for (x in floatArrayOf(397f, 438f, 512f, 562f)) {
            fill(c, Path().apply { moveTo(x - 1.2f, 962f); lineTo(x + 1.2f, 962f); lineTo(x - 12f, 978f); lineTo(x - 17f, 978f); close() }, shadow)
        }
        fill(c, Path().apply { moveTo(318f, 968f); lineTo(322f, 968f); lineTo(306f, 992f); lineTo(298f, 992f); close() }, shadow)
        if (wet) {
            // Wet paving holds a soft reflection of the building's lights.
            p.color = Color.BLACK
            p.shader = LinearGradient(0f, 955f, 0f, 1042f, withAlpha(Color.WHITE, 40), withAlpha(Color.WHITE, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 955f, d.PHOTO_W, d.PHOTO_H, p)
            p.shader = null
        }
    }

    // ---- The long left wall with the ALTRON sign -------------------------------------------------

    private val endX get() = leftBayX(9.85f)

    private fun leftWall(c: Canvas) {
        val x0 = endX
        val xc = d.CORNER_X
        val sun = sunLeft
        // Translucent band under the roof, with its panel grid.
        val poly = col(0xFFCBD2D9.toInt(), 0xFF5D7EAC.toInt(), sun)
        fill(c, leftBand(x0, xc, d.ROOF, d.BAND), poly)
        // The panels glow faintly from inside at dusk, brighter towards the top.
        c.save(); c.clipPath(leftBand(x0, xc, d.ROOF, d.BAND))
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, 560f, 0f, 760f, withAlpha(col(0xFFFFFFFF.toInt(), 0xFFD8CCD0.toInt()), 90), withAlpha(Color.WHITE, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 540f, d.PHOTO_W, 900f, p); p.shader = null
        c.restore()
        val grid = withAlpha(col(0xFF98A3AF.toInt(), 0xFF3C5478.toInt()), 200)
        line(c, x0, onLeft(x0, 571f), xc, onLeft(xc, 571f), grid, 0.5f)
        line(c, x0, onLeft(x0, 591f), xc, onLeft(xc, 591f), grid, 0.5f)
        run {
            var k = 0f
            while (k < 9.85f) {
                val x = leftBayX(k)
                line(c, x, onLeft(x, d.ROOF), x, onLeft(x, d.BAND), grid, 0.5f)
                k += 0.25f
            }
        }
        // Navy cladding with fine vertical ribs.
        val navy = col(0xFF233F63.toInt(), 0xFF1F3A63.toInt(), sun)
        fill(c, leftBand(x0, xc, d.BAND, d.CLAD), navy)
        // Corrugation: dark troughs with a faint sheen on each crest, lighter towards the foot.
        c.save(); c.clipPath(leftBand(x0, xc, d.BAND, d.CLAD))
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, 600f, 0f, 860f, withAlpha(Color.BLACK, 40), withAlpha(Color.WHITE, 22), Shader.TileMode.CLAMP)
        c.drawRect(0f, 540f, d.PHOTO_W, 900f, p); p.shader = null
        c.restore()
        val rib = withAlpha(scale(navy, 0.7f), 190)
        val crest = withAlpha(scale(navy, 1.45f), 110)
        run {
            var k = 0f
            var odd = false
            while (k < 9.85f) {
                val x = leftBayX(k)
                line(c, x, onLeft(x, d.BAND), x, onLeft(x, d.CLAD), if (odd) crest else rib, 0.35f)
                odd = !odd
                k += 0.035f
            }
        }
        // The sign bay: a deeper navy panel standing slightly proud of the wall.
        val signX = leftBayX(1.14f)
        val panel = col(0xFF16365A.toInt(), 0xFF133558.toInt(), sun)
        fill(c, leftBand(signX, xc, d.BAND + 2f, d.CLAD), panel)
        line(c, signX, onLeft(signX, d.BAND + 2f), signX, onLeft(signX, d.CLAD), withAlpha(col(0xFF5E7A9C.toInt(), 0xFF3E5E86.toInt()), 220), 1.1f)
        // Structural columns between the bays.
        val post = col(0xFF1A2F4C.toInt(), 0xFF142844.toInt())
        for (i in 1..9) {
            val x = leftBayX(1.14f + (i - 1))
            if (i > 1) line(c, x, onLeft(x, d.BAND), x, onLeft(x, d.CLAD), post, 1.1f)
        }
        // Roof edge trim and the line under the translucent band.
        val trim = col(0xFF2D3E52.toInt(), 0xFF1B2A44.toInt())
        line(c, x0, onLeft(x0, d.ROOF), xc, d.ROOF, trim, 1.6f)
        line(c, x0, onLeft(x0, d.BAND), xc, d.BAND, col(0xFF4A6888.toInt(), 0xFF2E4C78.toInt()), 1.4f)

        // Lower wall: a light ribbed band, then pale horizontal cladding in every bay, lit warm
        // under the carport; roller doors in the far bays.
        val ribbed = col(0xFF8FA4BA.toInt(), 0xFF3E6086.toInt(), sun)
        fill(c, leftBand(x0, xc, d.CLAD, 775f), ribbed)
        for (k in 0..2) {
            val yy = 757f + k * 6f
            line(c, x0, onLeft(x0, yy), xc, onLeft(xc, yy), withAlpha(scale(ribbed, 1.3f), 160), 0.5f)
        }
        val lowX = signX
        val glassX = 340f
        val upper = col(0xFFD4D9DE.toInt(), 0xFF7E879E.toInt(), sun)
        val lower = litCol(0xFFDCDFE2.toInt(), 0xFFB89C72.toInt(), sun)
        fill(c, leftBand(x0, glassX, 775f, 852f), upper)
        fill(c, leftBand(x0, glassX, 852f, d.GROUND), lower)
        // Horizontal ribs on the cladding.
        var ry = 778f
        while (ry < d.GROUND) {
            val base = if (ry < 852f) upper else lower
            line(c, x0, onLeft(x0, ry), glassX, onLeft(glassX, ry), withAlpha(scale(base, 0.8f), 150), 0.35f)
            ry += 3.2f
        }
        // A wall vent beside the sign bay.
        fill(c, leftBand(leftBayX(1.3f), leftBayX(1.55f), 780f, 795f), col(0xFF6A7280.toInt(), 0xFF3C4256.toInt()))
        run {
            var k = 1.14f
            while (k < 9.85f) {
                val x = leftBayX(k)
                line(c, x, onLeft(x, 775f), x, onLeft(x, d.GROUND), withAlpha(scale(lower, 0.82f), 170), 0.45f)
                k += 1f
            }
        }
        // Roller doors and yellow bollards in each far bay.
        val door = litCol(0xFFEEEFF0.toInt(), 0xFFD8C8A4.toInt())
        val bollard = litCol(0xFFE2B21C.toInt(), 0xFFE0A21E.toInt())
        for (i in 2..9) {
            val a = leftBayX(1.14f + (i - 1) - 0.82f)
            val bx = leftBayX(1.14f + (i - 1) - 0.25f)
            fill(c, leftBand(bx, a, 872f, d.GROUND), door)
            var yy = 875f
            while (yy < d.GROUND) { line(c, bx, onLeft(bx, yy), a, onLeft(a, yy), withAlpha(scale(door, 0.8f), 150), 0.35f); yy += 3.5f }
            line(c, bx, onLeft(bx, 872f), bx, onLeft(bx, d.GROUND), withAlpha(scale(door, 0.6f), 200), 0.5f)
            val pb = leftBayX(1.14f + (i - 1) - 0.86f)
            val pyb = onLeft(pb, d.GROUND) + 2f
            val ph = (onLeft(pb, d.GROUND) - onLeft(pb, 922f))
            fill(c, Path().apply { addRect(pb - 0.9f, pyb - ph, pb + 0.9f, pyb, Path.Direction.CW) }, bollard)
            fill(c, Path().apply { addRect(pb - 0.9f, pyb - ph * 0.55f, pb + 0.9f, pyb - ph * 0.42f, Path.Direction.CW) }, col(0xFF222222.toInt(), 0xFF1A1414.toInt()))
        }
        // The roller shutter nearest the entrance (under the carport).
        val shutter = litCol(0xFFC9CCD0.toInt(), 0xFF9C8A70.toInt())
        fill(c, leftBand(272f, glassX - 4f, 880f, d.GROUND), shutter)
        var sy = 882f
        while (sy < d.GROUND) { line(c, 272f, onLeft(272f, sy), glassX - 4f, onLeft(glassX - 4f, sy), withAlpha(scale(shutter, 0.72f), 170), 0.4f); sy += 2.2f }

        // The entrance under the sign: a blue fascia box, louvred glazing and the glass foyer.
        val fasciaX = 305f
        val fascia = col(0xFF4A7AA8.toInt(), 0xFF2C5A88.toInt(), sun)
        fill(c, leftBand(fasciaX, xc + 3f, 775f, 808f), fascia)
        // Its underside, in shadow.
        fill(c, leftBand(fasciaX, xc + 3f, 808f, 812f), col(0xFF233E5A.toInt(), 0xFF0E1E34.toInt()))
        line(c, fasciaX, onLeft(fasciaX, 775f), xc + 3f, onLeft(xc + 3f, 775f), withAlpha(scale(fascia, 1.45f), 230), 1f)
        line(c, fasciaX, onLeft(fasciaX, 775f), fasciaX, onLeft(fasciaX, 812f), withAlpha(scale(fascia, 1.25f), 230), 0.8f)
        val louvre = litCol(0xFF3E4752.toInt(), 0xFF4A4038.toInt())
        fill(c, leftBand(glassX, xc, 812f, 865f), louvre)
        run {
            var k = 0f
            val kEnd = (1f / (glassX - d.LEFT_VP) - 1f / (xc - d.LEFT_VP)) / d.BAY
            while (k < kEnd) {
                val x = leftBayX(k)
                line(c, x, onLeft(x, 812f), x, onLeft(x, 865f), withAlpha(if ((k / 0.04f).toInt() % 2 == 0) scale(louvre, 0.55f) else mix(louvre, 0xFFE8D2A4.toInt(), if (lights) 0.35f else 0.1f), 210), 0.6f)
                k += 0.04f
            }
        }
        foyerGlass(c, glassX, xc, 865f, d.GROUND, left = true)
        // The near corner trim.
        line(c, xc, d.ROOF, xc, d.CLAD, col(0xFFB9C3CC.toInt(), 0xFF7E90AE.toInt()), 1.6f)
        // A security camera on the corner.
        line(c, xc + 0.5f, 645f, xc + 3f, 648f, col(0xFF3A3E46.toInt(), 0xFF1C1E26.toInt()), 0.9f)
        fill(c, Path().apply { addOval(RectF(xc + 1.6f, 647f, xc + 5f, 652.5f), Path.Direction.CW) }, col(0xFFE6E8EA.toInt(), 0xFF8C93A6.toInt()))
        fill(c, Path().apply { addCircle(xc + 3.3f, 651f, 0.9f, Path.Direction.CW) }, col(0xFF1A1C22.toInt(), 0xFF0C0C12.toInt()))

        logo(c)
    }

    /** Glass with mullions: warm and bright when the lights are on, dark reflections by day. */
    private fun foyerGlass(c: Canvas, x0: Float, x1: Float, y0: Float, y1: Float, left: Boolean) {
        val shape = if (left) leftBand(x0, x1, y0, y1) else rightBand(x0, x1, y0, y1)
        if (lights) {
            fill(c, shape, mix(0xFF2A2522.toInt(), 0xFF1C1816.toInt(), deep))
            // Some rooms brighter than others.
            val n = 9
            for (i in 0 until n) {
                val lit = PANE_LIGHT[(i + (if (left) 3 else 0) + (y0 / 7f).toInt()) % PANE_LIGHT.size]
                if (lit < 0.2f) continue
                val xa = x0 + (x1 - x0) * i / n
                val xb = x0 + (x1 - x0) * (i + 1) / n
                val pane = if (left) leftBand(xa, xb, y0, y1) else rightBand(xa, xb, y0, y1)
                fill(c, pane, withAlpha(0xFFC49A5C.toInt(), (10 + 80 * lit).toInt()))
            }
        } else {
            fill(c, shape, col(0xFF3A4A5C.toInt(), 0xFF2A3446.toInt()))
            // Sky reflected in the glass.
            c.save(); c.clipPath(shape)
            p.color = Color.BLACK
            p.shader = LinearGradient(0f, min(onLeft(x0, y0), onRight(x1, y0)), 0f, y1, withAlpha(Color.WHITE, 70), withAlpha(Color.WHITE, 0), Shader.TileMode.CLAMP)
            c.drawPath(shape, p); p.shader = null
            c.restore()
        }
        val frame = col(0xFF2B323C.toInt(), 0xFF1E2028.toInt())
        val n = 9
        for (i in 0..n) {
            val x = x0 + (x1 - x0) * i / n
            val ya = if (left) onLeft(x, y0) else onRight(x, y0)
            val yb = if (left) onLeft(x, y1) else onRight(x, y1)
            line(c, x, ya, x, yb, frame, 1f)
        }
        val mid = y0 + (y1 - y0) * 0.45f
        line(c, x0, if (left) onLeft(x0, mid) else onRight(x0, mid), x1, if (left) onLeft(x1, mid) else onRight(x1, mid), frame, 0.8f)
    }

    // ---- The right wall and entrance block ------------------------------------------------------

    private fun rightWall(c: Canvas) {
        val xc = d.CORNER_X
        val x1 = BLOCK_X
        val sun = sunRight
        val poly = col(0xFFD2D8DF.toInt(), 0xFF8592BE.toInt(), sun)
        fill(c, rightBand(xc, x1, d.ROOF, d.BAND), poly)
        val grid = withAlpha(col(0xFFA3ADB8.toInt(), 0xFF6A74A0.toInt()), 190)
        line(c, xc, onRight(xc, 581f), x1, onRight(x1, 581f), grid, 0.6f)
        run {
            var t = 0f
            while (t < 1f) {
                val x = rightX(t * (1f / (d.RIGHT_VP - x1) - 1f / (d.RIGHT_VP - xc)))
                line(c, x, onRight(x, d.ROOF), x, onRight(x, d.BAND), grid, 0.5f)
                t += 0.07f
            }
        }
        line(c, xc, d.ROOF, x1, onRight(x1, d.ROOF), col(0xFF2D3E52.toInt(), 0xFF1B2A44.toInt()), 1.6f)
        line(c, xc, d.BAND, x1, onRight(x1, d.BAND), col(0xFF5E7088.toInt(), 0xFF3A4C70.toInt()), 1.4f)

        // Dark grey corrugated cladding, catching the evening sky towards the far end.
        val near = col(0xFF5C6270.toInt(), 0xFF46475F.toInt(), sun)
        val far = col(0xFF6A707E.toInt(), 0xFF8E7E92.toInt(), sun)
        val clad = rightBand(xc, x1, d.BAND, d.CLAD)
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(xc, 0f, x1, 0f, near, far, Shader.TileMode.CLAMP)
        c.drawPath(clad, p); p.shader = null
        run {
            var t = 0f
            val span = 1f / (d.RIGHT_VP - x1) - 1f / (d.RIGHT_VP - xc)
            while (t < 1f) {
                val x = rightX(t * span)
                val base = mix(near, far, (x - xc) / (x1 - xc))
                val odd = ((t / 0.009f).toInt() % 2) == 0
                val ribCol = if (odd) withAlpha(scale(base, 0.66f), 200) else withAlpha(scale(base, 1.3f), 120)
                line(c, x, onRight(x, d.BAND), x, onRight(x, d.CLAD), ribCol, 0.45f)
                t += 0.009f
            }
        }

        // Pale horizontal-ribbed band, then the louvred glazing over the entrance.
        val band = col(0xFFC8CCD2.toInt(), 0xFF8E8298.toInt(), sun)
        fill(c, rightBand(xc, x1, d.CLAD, 797f), band)
        for (k in 0..4) {
            val yy = 756f + k * 8f
            line(c, xc, onRight(xc, yy), x1, onRight(x1, yy), withAlpha(scale(band, 0.82f), 170), 0.5f)
        }
        val louvreEnd = 555f
        val louvre = litCol(0xFF3D4148.toInt(), 0xFF6A554B.toInt())
        fill(c, rightBand(xc, louvreEnd, 797f, 842f), louvre)
        var yy = 801f
        while (yy < 842f) {
            line(c, xc, onRight(xc, yy), louvreEnd, onRight(louvreEnd, yy), withAlpha(scale(louvre, 0.65f), 200), 0.6f)
            yy += 3.2f
        }
        // Small downlights along the top of the louvres.
        if (lights) {
            var t = 0.03f
            while (t < 1f) {
                val x = xc + (louvreEnd - xc) * t
                glow(c, x, onRight(x, 800f) + 1f, 2.2f, 0xFFFFF0C8.toInt(), 230)
                t += 0.09f
            }
        }
        foyerGlass(c, xc, louvreEnd, 842f, 866f, left = false)
        // Lower block: the light sign panel, a louvred panel and a small window.
        val signPanel = col(0xFFEDEDEB.toInt(), 0xFFD6B6A0.toInt(), sun)
        fill(c, rightBand(louvreEnd, 611f, 797f, 905f), signPanel)
        fill(c, rightBand(611f, x1, 797f, 905f), col(0xFF9298A2.toInt(), 0xFF7A6872.toInt(), sun))
        yy = 800f
        while (yy < 905f) { line(c, 611f, onRight(611f, yy), x1, onRight(x1, yy), withAlpha(col(0xFF6C727C.toInt(), 0xFF5A4A56.toInt()), 200), 0.6f); yy += 4f }
        fill(c, rightBand(613f, 636f, 862f, 900f), litCol(0xFF2E3440.toInt(), 0xFF5A4636.toInt()))
        word(c, floatArrayOf(566f, 829.5f, 605f, 843.5f, 605f, 857f, 566f, 844.5f), col(0xFF1B2C55.toInt(), 0xFF18224A.toInt()), teal = false)
        // Ground floor glazing behind the canopy.
        foyerGlass(c, xc, x1, 866f, d.GROUND, left = false)
    }

    // ---- The taller silver block at the far right -------------------------------------------------

    private fun silverBlock(c: Canvas) {
        val x0 = BLOCK_X
        val x1 = 732f
        val sun = sunRight
        val silver = col(0xFFD5D9DE.toInt(), 0xFFD6C0C2.toInt(), sun)
        val silverFar = col(0xFFC9CDD3.toInt(), 0xFFC7B2BC.toInt(), sun)
        val shape = blockBand(x0, x1, 723f, 952f)
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(x0, 0f, x1, 0f, silver, silverFar, Shader.TileMode.CLAMP)
        c.drawPath(shape, p); p.shader = null
        // Panel joints.
        var x = x0 + 8f
        while (x < x1) { line(c, x, onBlock(x, 723f), x, onBlock(x, 905f), withAlpha(scale(silver, 0.88f), 150), 0.4f); x += 9f }
        // Its dark corner cap, the window band and the small sign.
        fill(c, Path().apply { moveTo(643f, 721f); lineTo(658f, 721f); lineTo(658f, 741f); lineTo(645f, 736f); close() }, col(0xFF1F4C70.toInt(), 0xFF173A5E.toInt()))
        line(c, x0, 723f, x1, onBlock(x1, 723f), col(0xFF3D4958.toInt(), 0xFF2A3044.toInt()), 1.2f)
        fill(c, blockBand(x0, x1, 877f, 907f), litCol(0xFF56606C.toInt(), 0xFF8C7A6A.toInt()))
        x = x0
        while (x < x1) { line(c, x, onBlock(x, 877f), x, onBlock(x, 907f), col(0xFF2E3440.toInt(), 0xFF2A2630.toInt()), 0.9f); x += 7f }
        word(c, floatArrayOf(712.5f, 825f, 729f, 837f, 729f, 849f, 712.5f, 838f), col(0xFF1B2C55.toInt(), 0xFF2A2C50.toInt()), teal = false)
        // A small wall light by the sign.
        if (lights) glow(c, 728f, 862f, 4f, 0xFFFFF4D8.toInt(), 220)
    }

    // ---- Canopies --------------------------------------------------------------------------------

    /**
     * The long carport along the left wall, seen from below: deep purlins running the length of
     * the roof, lamps tucked up near the wall, and a lit front beam.
     */
    private fun carport(c: Canvas) {
        val back = floatArrayOf(290f, 851f, 10f, 881f)   // where the roof meets the wall side
        val front = floatArrayOf(290f, 884f, 10f, 905f)  // its outer edge, nearer the camera
        fun rx(t: Float) = back[0] + (back[2] - back[0]) * t
        fun ry(u: Float, t: Float): Float {
            val yb = back[1] + (back[3] - back[1]) * t
            val yf = front[1] + (front[3] - front[1]) * t
            return yb + (yf - yb) * u
        }
        fun strip(u0: Float, u1: Float): Path = Path().apply {
            moveTo(rx(0f), ry(u0, 0f)); lineTo(rx(1f), ry(u0, 1f)); lineTo(rx(1f), ry(u1, 1f)); lineTo(rx(0f), ry(u1, 0f)); close()
        }
        // Underside: warm where the lamps are, falling off towards the outer edge.
        val warmUnder = litCol(0xFF56514E.toInt(), 0xFF6A5038.toInt())
        val coolUnder = litCol(0xFF3E3B3A.toInt(), 0xFF2C221C.toInt())
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, back[1], 0f, front[1], warmUnder, coolUnder, Shader.TileMode.CLAMP)
        c.drawPath(strip(0f, 1f), p); p.shader = null
        // Purlins: dark beams with a lit lower lip, receding along the roof.
        val purlin = col(0xFF2A2A2C.toInt(), 0xFF1A1412.toInt())
        val lip = withAlpha(litCol(0xFF7A7470.toInt(), 0xFFB07E50.toInt()), 200)
        var u = 0.16f
        while (u < 0.95f) {
            fill(c, strip(u, u + 0.055f), purlin)
            line(c, rx(0f), ry(u + 0.055f, 0f), rx(1f), ry(u + 0.055f, 1f), lip, 0.5f)
            u += 0.13f
        }
        // Rafters, closer together with distance.
        val rafter = col(0xFF2E2E32.toInt(), 0xFF22191A.toInt())
        for (i in 0..18) {
            val t = 1f - 1f / (1f + i * 0.32f)
            line(c, rx(t), ry(0f, t), rx(t), ry(1f, t), rafter, 0.6f + 0.6f * (1f - t))
        }
        // Edge beams: the grey back member and the lit front fascia.
        line(c, back[0], back[1], back[2], back[3], col(0xFF46505C.toInt(), 0xFF2A2C38.toInt()), 1.4f)
        val fascia = litCol(0xFF5E6874.toInt(), 0xFF9C5E36.toInt())
        fill(c, Path().apply {
            moveTo(front[0], front[1] - 1.2f); lineTo(front[2], front[3] - 0.8f); lineTo(front[2], front[3] + 1.6f); lineTo(front[0], front[1] + 3f); close()
        }, fascia)
        line(c, front[0], front[1] - 1.2f, front[2], front[3] - 0.8f, withAlpha(mix(fascia, Color.WHITE, 0.35f), 230), 0.6f)
        // Columns with a lit edge, down to the apron.
        val steel = col(0xFF3E4A58.toInt(), 0xFF1E2532.toInt())
        val steelLit = litCol(0xFF6A7684.toInt(), 0xFF6A5A4E.toInt())
        for (x in floatArrayOf(292f, 265f, 228f, 205f, 150f, 108f, 74f, 46f)) {
            val t = (back[0] - x) / (back[0] - back[2])
            val top = ry(1f, t) + 2f
            val foot = 944f + (966f - 944f) * (x / 330f)
            val w = if (x > 200f) 1.8f else 1.1f
            line(c, x, top, x, foot, steel, w)
            line(c, x + w * 0.35f, top, x + w * 0.35f, foot, steelLit, w * 0.3f)
        }
        // Lamps: a hot core, a soft halo and a little horizontal flare.
        if (lights) {
            for (row in floatArrayOf(0.08f, 0.6f)) {
                for (i in 0 until 15) {
                    val t = 1f - 1f / (1f + i * 0.3f + 0.15f)
                    val x = rx(t)
                    val y = ry(row, t) + 1.2f
                    val r = 2.6f + 3.4f * (1f - t)
                    glow(c, x, y, r * 4.5f, 0xFFFFC870.toInt(), 170, sy = 0.65f)
                    glow(c, x, y, r * 2.6f, 0xFFFFEFC8.toInt(), 120, sx = 2.2f, sy = 0.18f)
                    glow(c, x, y, r, 0xFFFFFBEE.toInt(), 255, sy = 0.55f)
                }
            }
        }
    }

    /**
     * The steel shade canopy over the entrance, as it appears in the photo: a blue-grey beam
     * rising from the portal post to the near corner, then shade plates stepping down along the
     * block, each with a lit edge and a bracket. Seen from below, so its deep underside shows,
     * striped with purlins, back to the glass.
     */
    private fun entranceCanopy(c: Canvas) {
        val outer = floatArrayOf(325f, 888f, 477f, 855f, 583f, 879f, 638f, 891f, 680f, 900f, 747f, 912f)
        val inner = floatArrayOf(325f, 909f, 420f, 906f, 500f, 903f, 583f, 903f, 640f, 906f, 747f, 926f)
        fun at(u: Float, x: Float) = yOn(outer, x) + (yOn(inner, x) - yOn(outer, x)) * u
        fun strip(u0: Float, u1: Float): Path = Path().apply {
            val xs = floatArrayOf(325f, 420f, 477f, 500f, 583f, 638f, 640f, 680f, 747f)
            moveTo(xs[0], at(u0, xs[0])); for (x in xs.drop(1)) lineTo(x, at(u0, x))
            for (x in xs.reversed()) lineTo(x, at(u1, x)); close()
        }
        // Underside: dark plates, lit warm along each purlin by the lights below.
        fill(c, strip(0f, 1f), col(0xFF34302F.toInt(), 0xFF1A1416.toInt()))
        val lit = withAlpha(litCol(0xFF5E5A56.toInt(), 0xFF5A4234.toInt()), 150)
        val dark = col(0xFF242224.toInt(), 0xFF100C0E.toInt())
        var u = 0.08f
        while (u < 0.95f) {
            fill(c, strip(u, u + 0.035f), lit)
            fill(c, strip(u + 0.035f, u + 0.08f), dark)
            u += 0.2f
        }
        // Cross members down the underside.
        val member = col(0xFF6E7C8C.toInt(), 0xFF3E4C60.toInt())
        for (x in floatArrayOf(352f, 392f, 432f, 470f, 520f, 562f, 610f, 655f, 700f, 735f)) {
            line(c, x, at(0f, x) + 0.8f, x - 3f, at(1f, x - 3f), member, 0.8f)
        }
        // The rising blue-grey beam on the left, with a highlight on top.
        val beam = col(0xFF7E94AC.toInt(), 0xFF4E6A8C.toInt())
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT; p.shader = null
        p.strokeWidth = 3.2f; p.color = beam
        c.drawLine(325f, 889.5f, 477f, 855.5f, p)
        p.strokeWidth = 0.8f; p.color = mix(beam, Color.WHITE, 0.4f)
        c.drawLine(325f, 887.8f, 477f, 853.8f, p)
        // Plate edges stepping down to the right: a lit lip and a dark bracket at each end.
        val edge = litCol(0xFFC4C0BA.toInt(), 0xFFB4A48C.toInt())
        val bracket = col(0xFF2A2E36.toInt(), 0xFF141218.toInt())
        var k = 2
        while (k < outer.size - 2) {
            val x0 = outer[k]; val y0 = outer[k + 1]; val x1 = outer[k + 2]; val y1 = outer[k + 3]
            p.strokeWidth = 2f; p.color = edge
            c.drawLine(x0 + 1f, y0 + 1.6f, x1 - 1.5f, y1 + 1.6f, p)
            p.strokeWidth = 1.2f; p.color = bracket
            c.drawLine(x0 + 1f, y0 - 0.4f, x1 - 1.5f, y1 - 0.4f, p)
            fill(c, Path().apply { addRect(x1 - 4f, y1 - 2.5f, x1 + 1f, y1 + 3.5f, Path.Direction.CW) }, bracket)
            k += 2
        }
        p.style = Paint.Style.FILL
        // Square posts with a lit face, on small concrete footings.
        val steel = col(0xFF34404E.toInt(), 0xFF1C222C.toInt())
        val steelLit = litCol(0xFF5E6C7C.toInt(), 0xFF4E5664.toInt())
        val footing = litCol(0xFFA8A49E.toInt(), 0xFF6A5C5A.toInt())
        for (x in floatArrayOf(393f, 440f, 512f, 553f, 633f, 663f, 690f, 735f)) {
            val foot = if (x < 600f) 962f else 956f
            val w = if (x < 600f) 2.6f else 1.7f
            val topY = yOn(inner, x) - 2f
            line(c, x, topY, x, foot, steel, w)
            line(c, x - w * 0.3f, topY, x - w * 0.3f, foot, steelLit, w * 0.28f)
            fill(c, Path().apply { addRect(x - w * 1.1f, foot - w * 0.9f, x + w * 1.1f, foot + w * 0.3f, Path.Direction.CW) }, footing)
        }
        // The tall portal post in front, with its long top rail.
        line(c, 322f, 846f, 322f, 968f, steel, 3.6f)
        line(c, 321f, 846f, 321f, 968f, steelLit, 0.9f)
        fill(c, Path().apply { addRect(317f, 965f, 327f, 970f, Path.Direction.CW) }, footing)
        line(c, 320f, 848.5f, 477f, 848.5f, beam, 2.2f)
        line(c, 320f, 847.6f, 477f, 847.6f, mix(beam, Color.WHITE, 0.35f), 0.6f)
        line(c, 476f, 848f, 476f, 856f, beam, 2f)
        // Downlights under the plates.
        if (lights) {
            for (x in floatArrayOf(372f, 418f, 466f, 528f, 590f, 655f, 715f)) {
                glow(c, x, at(0.75f, x), 9f, 0xFFFFE2A6.toInt(), 150, sy = 0.45f)
                glow(c, x, at(0.75f, x), 2.2f, 0xFFFFF8E6.toInt(), 255, sy = 0.6f)
            }
        }
    }

    private fun yOn(pts: FloatArray, x: Float): Float {
        var i = 0
        while (i < pts.size - 2) {
            val x0 = pts[i]; val x1 = pts[i + 2]
            if (x <= x1 || i == pts.size - 4) {
                val t = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
                return pts[i + 1] + (pts[i + 3] - pts[i + 1]) * t
            }
            i += 2
        }
        return pts[1]
    }

    // ---- Car park --------------------------------------------------------------------------------

    private fun foreground(c: Canvas) {
        // Far left: a bakkie and a car parked by the loading bays.
        car(c, 40f, 937f, 36f, 11f, litCol(0xFFF2F2F0.toInt(), 0xFFE6DCCB.toInt()), bakkie = true)
        car(c, 22f, 937f, 18f, 9f, litCol(0xFFE4E6E8.toInt(), 0xFFC9C0B4.toInt()))
        // Blue disabled-parking signs.
        for (x in floatArrayOf(218f, 263f, 345f)) {
            line(c, x, 945f, x, 924f, col(0xFF6A7480.toInt(), 0xFF3A4048.toInt()), 0.9f)
            fill(c, Path().apply { addRect(x - 2.2f, 920f, x + 2.2f, 928f, Path.Direction.CW) }, col(0xFF2A62B8.toInt(), 0xFF2A4C90.toInt()))
        }
        // Parked cars down the right.
        car(c, 637f, 963f, 73f, 26f, col(0xFF9E1C24.toInt(), 0xFF5A1018.toInt()), suv = true)
        car(c, 705f, 959f, 40f, 19f, col(0xFFC6CACF.toInt(), 0xFF9A9AA4.toInt()))
        // A traffic cone by the entrance.
        fill(c, Path().apply { moveTo(612f, 948f); lineTo(618f, 968f); lineTo(606f, 968f); close() }, col(0xFFF0601E.toInt(), 0xFFD05020.toInt()))
        fill(c, Path().apply { moveTo(609.6f, 956f); lineTo(614.4f, 956f); lineTo(615.2f, 959f); lineTo(608.8f, 959f); close() }, col(0xFFF4F4F4.toInt(), 0xFFD8D0D0.toInt()))

        // The planted island: soil behind a rounded concrete kerb, a row of low shrubs and a
        // young tree by the portal post.
        fill(c, Path().apply {
            moveTo(262f, 970f); lineTo(452f, 1010f); quadTo(463f, 1015f, 452f, 1019f); lineTo(262f, 981f); close()
        }, col(0xFF5A4A3E.toInt(), 0xFF2A2226.toInt()))
        val kerbTop = litCol(0xFFB4B6B8.toInt(), 0xFF6C6C80.toInt())
        val kerbSide = col(0xFF7E8286.toInt(), 0xFF3A3A4C.toInt())
        fill(c, Path().apply {
            moveTo(262f, 980f); lineTo(452f, 1018f); quadTo(466f, 1021f, 460f, 1026f); lineTo(262f, 986f); close()
        }, kerbSide)
        fill(c, Path().apply {
            moveTo(262f, 979f); lineTo(452f, 1017f); quadTo(464f, 1019f, 461f, 1023f); quadTo(456f, 1021.5f, 452f, 1020.5f); lineTo(262f, 982.5f); close()
        }, kerbTop)
        val leafDark = col(0xFF3E6230.toInt(), 0xFF1A2618.toInt())
        val leaf = col(0xFF4F7A3A.toInt(), 0xFF26381F.toInt())
        val leafHi = col(0xFF78A050.toInt(), 0xFF3E5634.toInt())
        // Young tree.
        line(c, 357f, 997f, 357f, 948f, col(0xFF5A4636.toInt(), 0xFF2A2020.toInt()), 0.9f)
        val tr = java.util.Random(5)
        for (k in 0 until 26) {
            val a = tr.nextFloat() * 6.283f
            val rr = tr.nextFloat() * 11f
            fill(c, Path().apply { addCircle(357f + kotlin.math.cos(a) * rr * 0.8f, 945f + kotlin.math.sin(a) * rr - (1f - rr / 11f) * 6f, 1.4f + tr.nextFloat() * 1.4f, Path.Direction.CW) },
                withAlpha(if (k % 3 == 0) leafHi else leaf, 200))
        }
        // Shrubs: back row smaller, front row along the kerb.
        val sr = java.util.Random(9)
        for (row in 0..1) {
            val n = if (row == 0) 12 else 14
            for (i in 0 until n) {
                val t = (i + 0.5f * row + 0.3f) / n
                val x = 272f + 182f * t
                val y = 971f + 38f * t + (if (row == 0) -3f else 4f)
                val size = (2.2f + 3.6f * t) * (if (row == 0) 0.8f else 1f)
                for (k in 0 until 7) {
                    val ox = (sr.nextFloat() - 0.5f) * size * 1.6f
                    val oy = -sr.nextFloat() * size * 0.9f
                    fill(c, Path().apply { addCircle(x + ox, y + oy, size * (0.35f + sr.nextFloat() * 0.25f), Path.Direction.CW) }, if (k < 3) leafDark else leaf)
                }
                fill(c, Path().apply { addCircle(x - size * 0.2f, y - size * 0.8f, size * 0.28f, Path.Direction.CW) }, leafHi)
            }
        }

        // Drain grate, manhole, the tar seam and the parking lines.
        fill(c, Path().apply { moveTo(272f, 1001f); lineTo(330f, 1001f); lineTo(334f, 1008f); lineTo(268f, 1008f); close() }, col(0xFF8A8C90.toInt(), 0xFF5A5866.toInt()))
        fill(c, Path().apply { moveTo(282f, 1003f); lineTo(320f, 1003f); lineTo(322f, 1006f); lineTo(280f, 1006f); close() }, col(0xFF2A2A2E.toInt(), 0xFF141218.toInt()))
        for (k in 0 until 12) {
            val gx = 284f + k * 3.1f
            line(c, gx, 1003.2f, gx - 0.4f, 1005.8f, col(0xFF6E7076.toInt(), 0xFF3C3A46.toInt()), 0.5f)
        }
        fill(c, Path().apply { addOval(RectF(457f, 1015f, 496f, 1027f), Path.Direction.CW) }, col(0xFF3A3A40.toInt(), 0xFF1E1A22.toInt()))
        fill(c, Path().apply { addOval(RectF(448f, 1011f, 505f, 1031f), Path.Direction.CW) }, withAlpha(col(0xFFB0AAA4.toInt(), 0xFF6E5E66.toInt()), 120))
        fill(c, Path().apply { addOval(RectF(457f, 1015f, 496f, 1027f), Path.Direction.CW) }, col(0xFF3A3A40.toInt(), 0xFF1E1A22.toInt()))
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.color = col(0xFF2E2A2C.toInt(), 0xFF1C1418.toInt()); p.shader = null
        path.reset(); path.moveTo(556f, 977f); path.cubicTo(520f, 984f, 506f, 994f, 514f, 1010f); path.cubicTo(520f, 1022f, 518f, 1034f, 520f, 1042f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        val kerbLine = col(0xFF707C8A.toInt(), 0xFF3E4660.toInt())
        line(c, 586f, 981f, 747f, 995f, kerbLine, 3f)
        val paint = col(0xFFF4F4F2.toInt(), 0xFFE4DCE2.toInt())
        fill(c, Path().apply { moveTo(728f, 982f); lineTo(737f, 984f); lineTo(656f, 1042f); lineTo(643f, 1042f); close() }, paint)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = paint; p.shader = null
        c.drawPath(Path().apply { moveTo(752f, 1004f); lineTo(708f, 1004f); lineTo(698f, 1034f); lineTo(752f, 1034f) }, p)
        p.style = Paint.Style.FILL
    }

    /** A car seen from the side, nose to the right; [x],[y] is the rear wheel's ground point. */
    private fun car(c: Canvas, x: Float, y: Float, w: Float, h: Float, body: Int, suv: Boolean = false, bakkie: Boolean = false) {
        val glass = col(0xFF2A3440.toInt(), 0xFF141820.toInt())
        val tyre = 0xFF0E0E10.toInt()
        fill(c, Path().apply {
            moveTo(x, y - h * 0.18f)
            lineTo(x, y - h * 0.58f)
            if (bakkie) {
                lineTo(x + w * 0.5f, y - h * 0.6f); lineTo(x + w * 0.56f, y - h); lineTo(x + w * 0.82f, y - h)
                lineTo(x + w * 0.92f, y - h * 0.6f)
            } else {
                lineTo(x + w * 0.12f, y - h * (if (suv) 0.95f else 0.9f)); lineTo(x + w * 0.62f, y - h)
                lineTo(x + w * 0.8f, y - h * 0.62f)
            }
            lineTo(x + w, y - h * 0.5f); lineTo(x + w, y - h * 0.18f); close()
        }, body)
        // Windows.
        fill(c, Path().apply {
            if (bakkie) {
                moveTo(x + w * 0.58f, y - h * 0.94f); lineTo(x + w * 0.8f, y - h * 0.94f); lineTo(x + w * 0.88f, y - h * 0.64f); lineTo(x + w * 0.58f, y - h * 0.64f)
            } else {
                moveTo(x + w * 0.16f, y - h * 0.86f); lineTo(x + w * 0.6f, y - h * 0.92f); lineTo(x + w * 0.74f, y - h * 0.64f); lineTo(x + w * 0.14f, y - h * 0.62f)
            }
            close()
        }, glass)
        // Wheels and lights.
        for (fx in floatArrayOf(0.2f, 0.8f)) fill(c, Path().apply { addCircle(x + w * fx, y - h * 0.16f, h * 0.2f, Path.Direction.CW) }, tyre)
        fill(c, Path().apply { addRect(x, y - h * 0.5f, x + w * 0.035f, y - h * 0.38f, Path.Direction.CW) }, col(0xFFC81E1E.toInt(), 0xFFE02020.toInt()))
        line(c, x, y - h * 0.06f, x + w, y - h * 0.06f, withAlpha(Color.BLACK, 70), h * 0.08f)
    }

    // ---- Lights ---------------------------------------------------------------------------------

    /** Pools of warm light on the apron and the paving, and the sign's halo. */
    private fun lightGlows(c: Canvas) {
        val k = 1f - deep * 0.3f
        glow(c, 130f, 950f, 200f, 0xFFFFC27A.toInt(), (165 * k).toInt(), sy = 0.11f)
        glow(c, 60f, 930f, 90f, 0xFFFFE0A8.toInt(), (120 * k).toInt(), sy = 0.35f)
        glow(c, 470f, 968f, 150f, 0xFFFFD9A0.toInt(), (70 * k).toInt(), sy = 0.14f)
        glow(c, 25f, 892f, 26f, 0xFFFFE7B8.toInt(), (190 * k).toInt())
    }

    // ---- The ALTRON wordmark -------------------------------------------------------------------

    private fun logo(c: Canvas) {
        val x0 = 300f
        val x1 = 444f
        val top = 648f
        val base = 696f
        val quad = floatArrayOf(x0, onLeft(x0, top), x1, onLeft(x1, top), x1, onLeft(x1, base), x0, onLeft(x0, base))
        val white = if (lights) 0xFFF6F8FB.toInt() else col(0xFFF2F4F6.toInt(), 0xFFC9D2DE.toInt(), sunLeft)
        if (lights) word(c, quad, withAlpha(0xFFDDEBFF.toInt(), (150 * (0.8f + 0.2f * deep)).toInt()), teal = false, blur = 7f)
        word(c, quad, white, teal = true)
    }

    /**
     * The ALTRON wordmark, drawn to the company logo's own measurements (in the logo artwork's
     * pixels: caps 71 tall from y 45 to 116, 11 px strokes). The A has a low crossbar; the T is a
     * white bar to the left of its stem, with the teal block to the right. Mapped onto [quad]
     * (top-left, top-right, bottom-right, bottom-left) with true perspective. Signs without
     * the brand colours ([teal] false) draw the block in [color] too.
     */
    private fun word(c: Canvas, quad: FloatArray, color: Int, teal: Boolean, blur: Float = 0f) {
        val src = floatArrayOf(LOGO_L, LOGO_TOP, LOGO_R, LOGO_TOP, LOGO_R, LOGO_BASE, LOGO_L, LOGO_BASE)
        m.setPolyToPoly(src, 0, quad, 0, 4)
        c.save()
        c.concat(m)
        val soft = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color; maskFilter = soft }
        val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER
            strokeWidth = 11.5f; this.color = color; maskFilter = soft
        }
        if (soft != null) strokeP.strokeWidth = 16f
        // O: a true circle, overshooting the cap height a little like the artwork, so it is drawn
        // before the straight cuts below.
        c.drawCircle(358.5f, 81f, 32.2f, strokeP)
        // Straight cuts at cap height and on the baseline for every other letter.
        c.clipRect(LOGO_L - 2f, LOGO_TOP, LOGO_R + 2f, LOGO_BASE)
        val grow = if (soft != null) 2.5f else 0f
        fun rect(l: Float, t: Float, r: Float, b: Float) = c.drawRect(l - grow, t - grow, r + grow, b + grow, fillP)
        // A: two legs meeting at a narrow flat top, and a low crossbar.
        c.drawLine(29.5f, 122f, 66.8f, 40f, strokeP)
        c.drawLine(100f, 122f, 64.6f, 40f, strokeP)
        rect(36f, 91f, 94f, 100.5f)
        // L
        rect(124f, 45f, 134.5f, 116f)
        rect(124f, 106.5f, 165f, 116f)
        // T: bar to the left of the stem; the teal block sits to the right.
        rect(166f, 45f, 204.5f, 55.5f)
        rect(193.5f, 45f, 204.5f, 116f)
        // R: stem, a round bowl and a steep leg.
        rect(245f, 45f, 255.5f, 116f)
        val bowl = Path().apply {
            moveTo(250f, 50.3f); lineTo(276f, 50.3f)
            arcTo(RectF(258.5f, 50.3f, 293.5f, 85.3f), -90f, 180f, false)
            lineTo(250f, 85.3f)
        }
        c.drawPath(bowl, strokeP)
        c.drawLine(273.5f, 81f, 297.5f, 122f, strokeP)
        // N: two stems and a corner-to-corner diagonal.
        rect(423f, 45f, 434.5f, 116f)
        rect(476f, 45f, 487f, 116f)
        c.save()
        c.clipRect(423f, LOGO_TOP, 487f, LOGO_BASE)
        strokeP.strokeWidth = if (soft != null) 17f else 12.5f
        c.drawLine(425f, 37f, 485.5f, 124f, strokeP)
        c.restore()
        // The T's block: teal on the brand sign, otherwise the letter colour.
        if (blur == 0f) {
            fillP.color = if (teal) (if (lights) mix(TEAL, Color.WHITE, 0.12f) else col(TEAL, scale(TEAL, 0.8f))) else color
            rect(211f, 45f, 232.5f, 55.5f)
        } else {
            rect(211f, 45f, 232.5f, 55.5f)
        }
        c.restore()
    }

    private companion object {
        /** Bitmap starts just above the roof (photo y). */
        const val TOP = 535f
        /** Where the main block ends and the silver block begins (photo x). */
        const val BLOCK_X = 658f
        // The logo artwork's letter box, in its own pixels.
        const val LOGO_L = 26f
        const val LOGO_R = 487f
        const val LOGO_TOP = 45f
        const val LOGO_BASE = 116f
        /** The brand teal of the T's block. */
        val TEAL = Color.rgb(8, 184, 224)
        val NIGHT = Color.rgb(10, 14, 28)
        /** How brightly each foyer pane is lit (a fixed, irregular pattern). */
        val PANE_LIGHT = floatArrayOf(0.9f, 0.3f, 0.6f, 0.1f, 1f, 0.5f, 0.2f, 0.8f, 0.4f, 0.7f, 0f, 0.6f)
    }
}
