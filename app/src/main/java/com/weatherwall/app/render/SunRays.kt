package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sun settings, from the in-chat god-rays sun designer. */
object SunDesign {
    const val RAY_COUNT = 14
    const val RAY_LENGTH = 1f
    const val RAY_WIDTH = 1f
    const val RAY_BRIGHTNESS = 1f
    const val TURN_SPEED = 1f
    const val SHIMMER = 0.5f
    const val GLOW_SIZE = 1f
    const val GLOW_STRENGTH = 1f
    const val DISC_SIZE = 1f
    const val DISC_WARMTH = 0.3f
}

/**
 * The sun: a warm disc and soft glow throwing slowly turning, shimmering beams of light.
 * It warms from white-gold towards orange as it gets low in the sky.
 */
class SunRays(screenWidth: Float) {
    private val d = SunDesign
    private val r = screenWidth // the designer measured sizes against the phone's width
    private val rayLen = r * 1.1f * d.RAY_LENGTH
    private val glowR = r * 0.31f * d.GLOW_SIZE
    private val discR = r * 0.092f * d.DISC_SIZE
    private val n = d.RAY_COUNT

    // Each ray is a thin wedge pointing along +x, rotated into place when drawn.
    private val rays: List<Path> = List(n) { i ->
        val half = (0.07f + 0.05f * sin(i * 2.3f)) * d.RAY_WIDTH * 14f / n
        val deg = Math.toDegrees(half.toDouble()).toFloat()
        Path().apply {
            moveTo(0f, 0f)
            arcTo(RectF(-rayLen, -rayLen, rayLen, rayLen), -deg, deg * 2f)
            close()
        }
    }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var warmth = -1f

    private fun setWarmth(w: Float) {
        warmth = w
        val ray = mix(0xFFFFF6D6.toInt(), 0xFFFFBE82.toInt(), w)
        val glow = mix(0xFFFFF4D6.toInt(), 0xFFFFAA6E.toInt(), w)
        val discIn = mix(0xFFFFFDF2.toInt(), 0xFFFFE8CC.toInt(), w)
        val discOut = mix(0xFFFFF0BE.toInt(), 0xFFFFB478.toInt(), w)
        rayPaint.shader = LinearGradient(0f, 0f, rayLen, 0f, ray, withAlpha(ray, 0), Shader.TileMode.CLAMP)
        glowPaint.shader = RadialGradient(
            0f, 0f, glowR,
            intArrayOf(withAlpha(glow, (255 * min(1f, 0.6f * d.GLOW_STRENGTH)).roundToInt()), withAlpha(glow, 0)),
            null, Shader.TileMode.CLAMP,
        )
        discPaint.shader = RadialGradient(
            0f, 0f, discR,
            intArrayOf(discIn, discOut, withAlpha(discOut, 0)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** [altitude] in degrees; [time] in seconds since the scene started. */
    fun draw(c: Canvas, x: Float, y: Float, altitude: Double, time: Float) {
        val low = max(0f, 1f - altitude.toFloat() / 25f)
        val w = min(1f, d.DISC_WARMTH + low * 0.7f)
        if (abs(w - warmth) > 0.01f) setWarmth(w)

        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(time * 0.05 * d.TURN_SPEED).toFloat())
        for (i in 0 until n) {
            val shimmer = 1f - d.SHIMMER * 0.5f + d.SHIMMER * 0.5f * sin(time * 0.7f + i * 1.7f)
            rayPaint.alpha = (255 * min(1f, 0.32f * d.RAY_BRIGHTNESS * shimmer)).roundToInt()
            c.save()
            c.rotate(i * 360f / n)
            c.drawPath(rays[i], rayPaint)
            c.restore()
        }
        c.restore()
        c.save()
        c.translate(x, y)
        c.drawCircle(0f, 0f, glowR, glowPaint)
        c.drawCircle(0f, 0f, discR, discPaint)
        c.restore()
    }
}
