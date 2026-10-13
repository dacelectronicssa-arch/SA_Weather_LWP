package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Celestial
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.withAlpha

object Scenes {
    fun create(id: SceneId): Scene = when (id) {
        SceneId.HIGHVELD -> HighveldScene()
        SceneId.CAPE_TOWN -> CapeTownScene()
        SceneId.BUSHVELD -> BushveldScene()
        SceneId.FOREST -> ForestScene()
        SceneId.DRAKENSBERG -> DrakensbergScene()
    }

    /** Shared fill paint for actors (all drawing happens on one thread). */
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    /** Contact shadow that stretches away from a low sun (long golden-hour shadows). */
    fun shadow(c: Canvas, f: Frame, x: Float, y: Float, size: Float) {
        val a = f.atmos
        val strength = a.daylight * (1f - a.storm * 0.8f) * (1f - a.cloudCover * 0.5f)
        if (strength < 0.05f) return
        val (sx, _) = Celestial.sunPos(f)
        val away = if (x > sx) 1f else -1f
        val stretch = 1f + 3.5f * a.golden
        val len = size * 0.55f * stretch
        val off = away * len * 0.5f * (stretch - 1f) / stretch
        oval.set(x + off - len, y - size * 0.05f, x + off + len, y + size * 0.05f)
        paint.color = withAlpha(Color.rgb(20, 14, 10), 0.28f * strength)
        c.drawOval(oval, paint)
    }
}
