package za.co.saweather.lwp.engine

import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.WallpaperSettings

/** Everything a layer needs to draw one frame. One instance is reused across frames. */
class Frame {
    var w = 1
    var h = 1
    /** Seconds since the renderer started (animation clock, independent of the weather clock). */
    var t = 0f
    var dt = 0f
    val atmos = Atmosphere()
    var settings = WallpaperSettings()
    var theme = Theme(settings.theme)
    var scene = SceneId.CAPE_TOWN
    /** Parallax input, roughly -1..1 on each axis. */
    var parX = 0f
    var parY = 0f
    var horizonY = 0f

    val particleScale: Float get() = settings.performance.particleScale

    /** Horizontal parallax shift in px for a layer at [depth] (0 = infinitely far, 1 = foreground). */
    fun shiftX(depth: Float): Float = parX * depth * maxShift()
    fun shiftY(depth: Float): Float = parY * depth * maxShift() * 0.35f
    fun maxShift(): Float = w * MAX_SHIFT

    /** Scale factor for sizes so scenes look the same on any resolution. */
    val u: Float get() = minOf(w, h) / 1080f

    // --- Ambient lighting as a cached filter for pre-rendered landscape bitmaps.
    private var lastLit = 0
    private var lightFilter = LightingColorFilter(Color.WHITE, 0)
    val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun beginFrame() {
        val lit = atmos.lit()
        if (lit != lastLit) {
            lastLit = lit
            lightFilter = LightingColorFilter(lit and 0xFFFFFF, 0)
            layerPaint.colorFilter = lightFilter
        }
    }

    /** A base daylight colour as it appears under the current light. */
    fun tint(color: Int): Int = multiply(color, atmos.lit())

    /** Wildlife colour: silhouetted against bright skies in cinematic themes or at golden hour. */
    fun animal(color: Int, silhouetteBias: Float = 0f): Int {
        val sil = if (theme.silhouettes) 1f else clamp01(atmos.golden * silhouetteBias)
        val lit = tint(color)
        return if (sil <= 0f) lit else lerpColor(lit, Color.rgb(20, 14, 12), sil * 0.85f)
    }

    companion object {
        const val MAX_SHIFT = 0.045f
    }
}
