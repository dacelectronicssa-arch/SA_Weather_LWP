package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import za.co.saweather.lwp.engine.Atmosphere
import za.co.saweather.lwp.engine.Frame

/** When an animal is around. */
enum class Activity {
    DAY, NIGHT, ANY, TWILIGHT_AND_NIGHT, DAWN, DAY_CALM;

    fun active(a: Atmosphere): Boolean = when (this) {
        DAY -> a.daylight > 0.35f
        NIGHT -> a.night > 0.55f
        ANY -> true
        TWILIGHT_AND_NIGHT -> a.daylight < 0.55f
        DAWN -> a.hour in 5.2f..9.5f
        DAY_CALM -> a.daylight > 0.35f && a.storm < 0.45f
    }
}

/**
 * A wildlife element with idle / move / react-to-storm / night behaviour. [presence] fades the
 * animal in and out as its activity window opens and closes.
 */
abstract class Actor(val activity: Activity, val depth: Float) {
    var presence = 0f
        private set
    /** How quickly the animal appears/leaves, presence units per second. */
    open val fadeRate = 0.6f
    /** Walkers leave by walking off-screen rather than fading. */
    open val fades = true

    open fun wanted(f: Frame): Boolean {
        val s = f.settings
        if (!s.showAnimals) return false
        if (s.allAnimalsAlways) return true
        if (s.stormBehaviour && hidesInStorm && f.atmos.storm > 0.6f) return false
        return activity.active(f.atmos)
    }

    open val hidesInStorm = false

    fun tick(f: Frame) {
        val target = if (wanted(f)) 1f else 0f
        val step = fadeRate * f.dt
        presence = if (presence < target) minOf(target, presence + step) else maxOf(target, presence - step)
        if (!fades || presence > 0f) update(f, target > 0f)
    }

    /** Fading actors show while present; walkers override this with "currently on screen". */
    open fun isShown(): Boolean = presence > 0.003f

    /** Storm reaction is active (animals flee / hide). */
    protected fun stormReact(f: Frame): Float =
        if (f.settings.stormBehaviour && !f.settings.allAnimalsAlways) f.atmos.storm else 0f

    abstract fun update(f: Frame, active: Boolean)
    abstract fun draw(c: Canvas, f: Frame)

    fun drawWithPresence(c: Canvas, f: Frame) {
        if (!fades || presence >= 0.999f) { draw(c, f); return }
        val save = c.saveLayerAlpha(null, (presence * 255).toInt())
        draw(c, f)
        c.restoreToCount(save)
    }
}
