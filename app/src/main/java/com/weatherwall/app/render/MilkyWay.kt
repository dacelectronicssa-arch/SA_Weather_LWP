package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

/**
 * Paints the Milky Way once into a half-resolution horizontal strip. The scene rotates and
 * positions the strip each frame to follow the turning sky; painting the band itself every
 * frame would be far too expensive.
 */
object MilkyWay {

    // Colours from the in-chat Milky Way designer (after long-exposure photos): blue-violet
    // arms, magenta haze, a golden-peach core and rusty dust lanes, with coloured stars.
    const val COLOUR = 0.55f
    const val CORE_WARMTH = 0.5f
    const val BRIGHTNESS = 1f
    const val HAZE = 0.75f
    const val COLOURED_STARS = 0.85f

    /**
     * Debug/demo only: show the band like a long-exposure photo, rising from the horizon up
     * through the middle of the screen with the core low and centred, instead of where the
     * real sky puts it (which, facing south in portrait, is round the edges).
     */
    @Volatile var heroView = false

    private fun rgb(r: Int, g: Int, b: Int) = android.graphics.Color.rgb(r, g, b)
    private val BLUE = mix(rgb(150, 160, 190), rgb(70, 90, 230), COLOUR)
    private val VIOLET = mix(rgb(170, 165, 195), rgb(150, 90, 220), COLOUR)
    private val PINK = mix(rgb(200, 180, 190), rgb(235, 120, 180), COLOUR)
    private val PEACH = mix(rgb(240, 220, 194), mix(rgb(255, 214, 160), rgb(255, 160, 90), minOf(1f, CORE_WARMTH * 0.6f)), minOf(1f, COLOUR))
    private val GOLD = mix(rgb(250, 235, 210), rgb(255, 236, 190), minOf(1f, COLOUR))
    val STAR_BLUE = rgb(170, 195, 255)
    val STAR_ORANGE = rgb(255, 200, 150)

    /** A star's colour: most white, some blue-white, a few orange. [k] is a 0..1 random pick. */
    fun starColour(base: Int, k: Float): Int = when {
        k < 0.3f -> mix(base, STAR_BLUE, minOf(1f, 0.6f * COLOURED_STARS))
        k < 0.45f -> mix(base, STAR_ORANGE, minOf(1f, 0.6f * COLOURED_STARS))
        else -> base
    }

    /** The strip is rendered at this fraction of screen resolution and scaled up when drawn. */
    const val SCALE = 0.5f

    /**
     * @param screenWidth sets the band's apparent width.
     * @param length strip length in screen pixels.
     * @param thickness strip thickness in screen pixels.
     * @param coreFraction where along the strip (0..1) the bright galactic core sits.
     */
    fun renderStrip(screenWidth: Int, length: Float, thickness: Float, coreFraction: Float): Bitmap {
        val bw = (length * SCALE).toInt().coerceAtLeast(1)
        val bh = (thickness * SCALE).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = java.util.Random(3) // fixed: identical band in every scene, so crossfades are seamless
        val u = screenWidth * SCALE // size unit
        val len = bw.toFloat()
        val mid = bh / 2f
        val halfWidth = 0.17f * u
        val density = len / (2f * u) // keeps detail per unit length constant on any screen

        fun px(t: Float) = t * len
        fun gauss() = rnd.nextGaussian().toFloat()
        fun count(n: Int) = (n * density).toInt()

        val warmColor = 0xFFF0DCC2.toInt()
        val coreSpan = 0.35f * u * 2f / len // core glow spreads over about 0.7 screen widths
        fun warmth(t: Float) = (1f - abs(t - coreFraction) / coreSpan).coerceIn(0f, 1f)

        val blob = Paint(Paint.ANTI_ALIAS_FLAG)
        fun glow(x: Float, y: Float, r: Float, color: Int, alpha: Int) {
            blob.shader = RadialGradient(
                x, y, r, intArrayOf(withAlpha(color, alpha), withAlpha(color, 0)), null, Shader.TileMode.CLAMP,
            )
            c.drawCircle(x, y, r, blob)
        }

        /** Band colour: blue arms, violet, then pink warming into the peach core; bluer at the edges. */
        fun colourAt(t: Float, dy: Float): Int {
            val w = warmth(t)
            val edge = (abs(dy) / halfWidth).coerceAtMost(1f)
            val c1 = when {
                w > 0.55f -> mix(PINK, PEACH, (w - 0.55f) / 0.45f)
                w > 0.15f -> mix(VIOLET, PINK, (w - 0.15f) / 0.4f)
                else -> mix(BLUE, VIOLET, w / 0.15f)
            }
            return mix(c1, BLUE, edge * 0.55f)
        }
        fun a(v: Float) = (v * BRIGHTNESS).toInt().coerceIn(0, 255)
        val maxOff = bh / 2f // keep every glow inside the strip so its edges never show

        // 0. A soft purple haze around the whole band.
        repeat(count(70)) {
            val t = rnd.nextFloat()
            val r = (0.1f + rnd.nextFloat() * 0.12f) * u
            val dy = (gauss() * halfWidth * 0.8f).coerceIn(-(maxOff - r), maxOff - r)
            glow(px(t), mid + dy, r, mix(VIOLET, PINK, warmth(t) * 0.6f), (0.05f * HAZE * 255).toInt())
        }
        // 1. Diffuse glow along the band.
        repeat(count(200)) {
            val t = rnd.nextFloat()
            val r = (0.07f + rnd.nextFloat() * 0.13f) * u
            val dy = gauss() * halfWidth * 0.55f
            glow(px(t), mid + dy, r, colourAt(t, dy), a((9 + rnd.nextInt(12)) * 1.4f))
        }
        // 2. The brighter, warmer galactic core (Sagittarius), with a golden heart.
        if (coreFraction in 0f..1f) {
            repeat(20) {
                val t = coreFraction + gauss() * coreSpan * 0.3f
                glow(px(t), mid + gauss() * halfWidth * 0.35f, (0.06f + rnd.nextFloat() * 0.11f) * u,
                    mix(PEACH, GOLD, rnd.nextFloat() * 0.5f), a((24 + rnd.nextInt(16)) * 1.25f))
            }
            repeat(6) {
                glow(px(coreFraction + gauss() * coreSpan * 0.09f), mid + gauss() * halfWidth * 0.12f,
                    (0.05f + rnd.nextFloat() * 0.05f) * u, GOLD, a(46f))
            }
        }
        // 3. Bright star clouds.
        repeat(count(55)) {
            val t = rnd.nextFloat()
            val dy = gauss() * halfWidth * 0.4f
            glow(px(t), mid + dy, (0.015f + rnd.nextFloat() * 0.03f) * u,
                mix(colourAt(t, dy), rgb(230, 235, 255), 0.35f), a(18f + rnd.nextInt(18)))
        }
        // 4. Dark dust lanes: erase light along a wandering line through the band.
        blob.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        val lanes = ArrayList<FloatArray>()
        repeat(count(120)) {
            val t = rnd.nextFloat()
            val off = sin(t * len / u * 4f) * halfWidth * 0.18f + gauss() * halfWidth * 0.1f
            val r = (0.02f + rnd.nextFloat() * 0.05f) * u * (0.6f + warmth(t))
            glow(px(t), mid + off, r, 0xFF000000.toInt(), 50 + rnd.nextInt(70))
            lanes += floatArrayOf(t, off, r)
        }
        blob.xfermode = null
        // Rusty edges to the dust, as in long-exposure photos.
        for ((i, l) in lanes.withIndex()) if (i % 2 == 1) {
            glow(px(l[0]), mid + l[1], l[2] * 0.9f, mix(rgb(80, 40, 40), rgb(120, 60, 40), warmth(l[0])), (0.18f * COLOUR * 255).toInt())
        }
        blob.shader = null

        // 5. Countless faint stars, densest along the centre line.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(count(3500)) {
            val t = rnd.nextFloat()
            dot.color = withAlpha(starColour(mix(0xFFFFFFFF.toInt(), warmColor, warmth(t) * 0.6f), rnd.nextFloat()), 40 + rnd.nextInt(130))
            c.drawCircle(px(t), mid + gauss() * halfWidth * 0.5f, 0.35f + rnd.nextFloat() * 0.7f, dot)
        }
        return bmp
    }
}
