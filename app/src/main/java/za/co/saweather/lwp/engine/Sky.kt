package za.co.saweather.lwp.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** Layer 1: the dynamic sky gradient, sun glow and horizon light pollution. */
class Sky {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTop = 0
    private var lastHorizon = 0
    private var lastH = 0f

    fun draw(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        val hy = f.horizonY
        if (a.skyTop != lastTop || a.skyHorizon != lastHorizon || hy != lastH) {
            lastTop = a.skyTop; lastHorizon = a.skyHorizon; lastH = hy
            val mid = lerpColor(a.skyTop, a.skyHorizon, 0.45f)
            paint.alpha = 255
            paint.shader = LinearGradient(
                0f, 0f, 0f, hy + f.h * 0.08f,
                intArrayOf(a.skyTop, mid, a.skyHorizon), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, f.w.toFloat(), f.h.toFloat(), paint)

        // Warm glow around the sun, broad and strong at golden hour, dulled by cloud.
        val sunVis = smoothstep(-0.25f, 0.05f, a.sunElev) * (1f - a.storm * 0.85f)
        if (sunVis > 0.01f) {
            val (sx, sy) = Celestial.sunPos(f)
            val r = f.h * (0.35f + 0.55f * a.golden)
            val warm = lerpColor(Color.rgb(255, 245, 220), Color.rgb(255, 150, 60), a.golden)
            val alpha = sunVis * (0.28f + 0.42f * a.golden) * f.theme.bloom
            glowPaint.shader = RadialGradient(
                sx, sy, r, intArrayOf(withAlpha(warm, alpha), withAlpha(warm, alpha * 0.35f), 0), floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, f.w.toFloat(), hy + f.h * 0.1f, glowPaint)
        }

        // City glow washing out the lower sky at night.
        val lp = cfg.lightPollution * a.night * (1f - a.storm * 0.5f)
        if (lp > 0.01f) {
            glowPaint.shader = LinearGradient(
                0f, hy - f.h * 0.28f, 0f, hy,
                0, withAlpha(cfg.lightPollutionColor, 0.42f * lp), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, hy - f.h * 0.28f, f.w.toFloat(), hy + f.h * 0.05f, glowPaint)
        }
    }
}
