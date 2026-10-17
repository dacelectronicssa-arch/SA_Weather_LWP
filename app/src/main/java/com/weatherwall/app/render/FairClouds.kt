package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.weatherwall.app.WeatherCondition
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fair-weather cloud settings, from the in-chat layered-and-wispy cloud designer. */
object CloudDesign {
    const val DECK_AMOUNT = 1f
    const val DECK_STRETCH = 1.1f
    const val LAYER_THICKNESS = 1f
    const val PUFFY_BUMPS = 4
    const val WISP_AMOUNT = 1f
    const val WISP_LENGTH = 1f
    const val WISP_THICKNESS = 1f
    const val WIND_TILT = 0.3f
    const val SOFTNESS = 3f
    const val SHADING = 0.6f
    const val SIZE = 1f
    const val SEED = 9

    // The designer's geometry, for mapping its layout onto the phone.
    const val PREVIEW_WIDTH = 680f
    const val PREVIEW_TABLE_TOP = 176.2f // its shoreline (380) minus the table (0.222 × 918)
}

/**
 * Long layered decks of cloud drifting through the middle of the sky, with soft wispy streaks
 * higher up. Each cloud is painted once into a half-resolution bitmap and then just drifts.
 */
class FairClouds(
    condition: WeatherCondition,
    night: Boolean,
    private val w: Float,
    tableTopY: Float,
) {
    private class Palette(val top: Int, val mid: Int, val under: Int, val wisp: Int, val decks: Int, val wisps: Int, val overcast: Boolean = false)

    private class Item(val bmp: Bitmap, val ox: Float, val oy: Float, var x: Float, val y: Float, val speed: Float, val alpha: Int) {
        val w = bmp.width / SCALE
        val h = bmp.height / SCALE
    }

    private val k = w / CloudDesign.PREVIEW_WIDTH          // preview px -> phone px
    private val yScale = tableTopY / CloudDesign.PREVIEW_TABLE_TOP // same place relative to the mountain
    private val decks = ArrayList<Item>()
    private val wisps = ArrayList<Item>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var tint = -1f

    init {
        val base = when (condition) {
            WeatherCondition.CLEAR -> Palette(0xFFFFFFFF.c, 0xFFEEF2F6.c, 0xFFAEB9C6.c, 0xFFFFFFFF.c, 1, 3)
            WeatherCondition.PARTLY_CLOUDY -> Palette(0xFFFFFFFF.c, 0xFFEEF2F6.c, 0xFFAEB9C6.c, 0xFFFFFFFF.c, 4, 4)
            WeatherCondition.FOG -> Palette(0xFFE4E8EC.c, 0xFFC9D0D7.c, 0xFF8A95A1.c, 0xFFEEF1F4.c, 3, 0)
            WeatherCondition.SNOW -> Palette(0xFFE4E8EC.c, 0xFFC9D0D7.c, 0xFF8A95A1.c, 0xFFEEF1F4.c, 6, 0)
            WeatherCondition.CLOUDY -> Palette(0xFFFAFBFC.c, 0xFFE2E7EC.c, 0xFF9CA8B5.c, 0xFFFFFFFF.c, 7, 5)
            WeatherCondition.OVERCAST -> Palette(0xFFE4E8EC.c, 0xFFC9D0D7.c, 0xFF8A95A1.c, 0xFFEEF1F4.c, 9, 3, overcast = true)
            // Only used where the storm ceiling isn't available.
            else -> Palette(0xFF9AA4AE.c, 0xFF7C8691.c, 0xFF4E5762.c, 0xFFA9B2BB.c, 9, 0, overcast = true)
        }
        val pal = if (night) {
            Palette(0xFF6A7590.c, 0xFF4A5468.c, 0xFF262D3C.c, 0xFF7C88A6.c, base.decks, base.wisps, base.overcast)
        } else base
        build(pal)
    }

    private fun build(pal: Palette) {
        val d = CloudDesign
        val r = Mulberry32(d.SEED)
        val overcast = pal.overcast
        val nd = (pal.decks * d.DECK_AMOUNT).roundToInt()
        for (i in 0 until nd) {
            val depth = 0.6f + r.next() * 0.6f
            val bw = d.PREVIEW_WIDTH * (0.38f + 0.25f * r.next()) * depth * d.SIZE * d.DECK_STRETCH * (if (overcast) 1.1f else 1f)
            val (bmp, ox, oy) = bakeDeck(r, bw * k, pal)
            val x = (-bw + r.next() * (d.PREVIEW_WIDTH + bw)) * k
            val y = 400f * (0.22f + (if (overcast) 0.4f else 0.3f) * r.next()) * yScale
            val speed = (5f + r.next() * 7f) * depth * k
            decks += Item(bmp, ox, oy, x, y, speed, (255 * (0.88f + 0.12f * min(1f, depth))).toInt())
        }
        val nw = (pal.wisps * d.WISP_AMOUNT).roundToInt()
        for (i in 0 until nw) {
            val bw = d.PREVIEW_WIDTH * (0.35f + 0.3f * r.next()) * d.WISP_LENGTH * d.SIZE
            val (bmp, ox, oy) = bakeWisp(r, bw * k, pal)
            val x = (-bw + r.next() * (d.PREVIEW_WIDTH + bw)) * k
            val y = 400f * (0.06f + 0.22f * r.next()) * yScale
            val speed = (3f + r.next() * 4f) * k
            wisps += Item(bmp, ox, oy, x, y, speed, 204)
        }
        decks.sortBy { it.speed } // far, slow decks behind
    }

    fun update(dt: Float) {
        for (list in arrayOf(wisps, decks)) for (c in list) {
            c.x += c.speed * dt
            if (c.x - c.ox > w) c.x = -c.w
        }
    }

    /** [twilight] 0..1 warms the clouds at sunrise and sunset. */
    fun draw(canvas: Canvas, twilight: Float) {
        if (kotlin.math.abs(twilight - tint) > 0.01f) {
            tint = twilight
            paint.colorFilter = if (twilight < 0.01f) null else LightingColorFilter(
                mix(0xFFFFFFFF.c, 0xFFFFD0C0.c, twilight),
                Color.rgb((28 * twilight).toInt(), (6 * twilight).toInt(), 0),
            )
        }
        for (list in arrayOf(wisps, decks)) for (c in list) {
            paint.alpha = c.alpha
            rect.set(c.x - c.ox, c.y - c.oy, c.x - c.ox + c.w, c.y - c.oy + c.h)
            canvas.drawBitmap(c.bmp, null, rect, paint)
        }
    }

    // ---- Painting (mirrors the designer) ---------------------------------------------------

    private fun blur(px: Float) = if (px > 0.1f) BlurMaskFilter(px, BlurMaskFilter.Blur.NORMAL) else null

    private fun bakeDeck(r: Mulberry32, bw: Float, pal: Palette): Triple<Bitmap, Float, Float> {
        val d = CloudDesign
        val th = d.LAYER_THICKNESS
        val pad = bw * 0.12f + (d.SOFTNESS * 4f + 10f) * k
        val bh = bw * 0.4f
        val bmp = Bitmap.createBitmap(((bw + pad * 2) * SCALE).toInt().coerceAtLeast(1), ((bh + pad * 2) * SCALE).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        c.translate(pad, pad + bh * 0.75f)
        val path = Path()
        val layers = 3 + floor(r.next() * 3).toInt()
        for (i in 0 until layers) {
            val cx = 0.3f + 0.4f * r.next()
            val cy = -0.025f - i * 0.032f * th - r.next() * 0.015f
            val rx = min(0.5f, (0.2f + 0.2f * r.next()) * d.DECK_STRETCH)
            val ry = (0.035f + 0.03f * r.next()) * th
            path.addOval(RectF((cx - rx) * bw, (cy - ry) * bw, (cx + rx) * bw, (cy + ry) * bw), Path.Direction.CW)
        }
        for (i in 0 until d.PUFFY_BUMPS) {
            val x = 0.22f + 0.56f * r.next()
            val rr = (0.03f + 0.035f * r.next()) * th
            val y = -0.03f - layers * 0.032f * th - rr * 0.2f
            path.addCircle(x * bw, y * bw, rr * bw, Path.Direction.CW)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, -0.22f * th * bw, 0f, 0.03f * bw, intArrayOf(pal.top, pal.mid, pal.under), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            maskFilter = blur(d.SOFTNESS * k)
        }
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        val shade = Paint().apply {
            shader = LinearGradient(0f, -0.06f * bw, 0f, 0.03f * bw, withAlpha(pal.under, 0), pal.under, Shader.TileMode.CLAMP)
            alpha = (255 * 0.6f * d.SHADING).toInt()
        }
        c.drawRect(-bw, -0.06f * bw, bw * 2f, 0.04f * bw, shade)
        val light = Paint().apply {
            shader = LinearGradient(0f, -0.25f * th * bw, 0f, -0.05f * bw, 0xE6FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            alpha = (255 * 0.5f * d.SHADING).toInt()
        }
        c.drawRect(-bw, -0.3f * bw, bw * 2f, -0.05f * bw, light)
        c.restore()
        return Triple(bmp, pad, pad + bh * 0.75f)
    }

    private fun bakeWisp(r: Mulberry32, bw: Float, pal: Palette): Triple<Bitmap, Float, Float> {
        val d = CloudDesign
        val pad = (40f + d.SOFTNESS * 4f) * k
        val bh = bw * 0.35f
        val bmp = Bitmap.createBitmap(((bw + pad * 2) * SCALE).toInt().coerceAtLeast(1), ((bh + pad * 2) * SCALE).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        c.translate(pad, pad + bh * 0.6f)
        val n = 3 + floor(r.next() * 4).toInt()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = blur(max(1.5f, d.SOFTNESS * 0.8f) * k)
        }
        val path = Path()
        for (i in 0 until n) {
            val y = -r.next() * 0.18f * bw
            val x0 = r.next() * 0.25f * bw
            val len = (0.45f + 0.5f * r.next()) * bw * 0.95f
            val curl = (r.next() - 0.5f) * 0.1f * bw
            val lift = -d.WIND_TILT * 0.12f * bw
            stroke.shader = LinearGradient(
                x0, 0f, x0 + len, 0f,
                intArrayOf(withAlpha(pal.wisp, 0), pal.wisp, pal.wisp, withAlpha(pal.wisp, 0)),
                floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP,
            )
            stroke.alpha = (255 * (0.45f + 0.35f * r.next())).toInt()
            stroke.strokeWidth = (1.5f + 4f * r.next()) * d.WISP_THICKNESS * k
            path.reset()
            path.moveTo(x0, y)
            path.quadTo(x0 + len * 0.5f, y + curl + lift * 0.5f, x0 + len, y + lift)
            c.drawPath(path, stroke)
        }
        return Triple(bmp, pad, pad + bh * 0.6f)
    }

    private companion object {
        const val SCALE = 0.5f
        val Long.c get() = toInt()
    }
}
