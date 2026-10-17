package com.weatherwall.app.render

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos

object MoonPhase {
    /** 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    fun phase(nowMs: Long = System.currentTimeMillis()): Float = Astro.moonPhase(nowMs)

    /** Fraction of the disc that is lit, 0..1. */
    fun illumination(phase: Float) = ((1 - cos(phase * TWO_PI)) / 2f)

    fun name(phase: Float) = when {
        phase < 0.03f || phase >= 0.97f -> "New moon"
        phase < 0.22f -> "Waxing crescent"
        phase < 0.28f -> "First quarter"
        phase < 0.47f -> "Waxing gibbous"
        phase < 0.53f -> "Full moon"
        phase < 0.72f -> "Waning gibbous"
        phase < 0.78f -> "Last quarter"
        else -> "Waning crescent"
    }

    /**
     * The lit part of a moon of radius [r] centred at ([cx], [cy]), as seen from the northern
     * hemisphere (waxing = lit on the right). Mirror it horizontally for the southern hemisphere.
     */
    fun litPath(phase: Float, cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        val disc = RectF(cx - r, cy - r, cx + r, cy + r)
        val rx = r * abs(cos(phase * TWO_PI))
        val terminator = RectF(cx - rx, cy - r, cx + rx, cy + r)
        val waxing = phase < 0.5f
        val crescent = phase < 0.25f || phase > 0.75f

        // Lit limb: top -> bottom around the lit side.
        path.arcTo(disc, -90f, if (waxing) 180f else -180f, true)
        // Terminator: bottom -> top, bulging towards the limb for a crescent, away from it for a gibbous moon.
        val bulgeRight = if (waxing) crescent else !crescent
        path.arcTo(terminator, 90f, if (bulgeRight) -180f else 180f)
        path.close()
        return path
    }
}
