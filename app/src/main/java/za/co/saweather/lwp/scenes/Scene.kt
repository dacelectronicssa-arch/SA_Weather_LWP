package za.co.saweather.lwp.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Atmosphere
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Weather
import za.co.saweather.lwp.engine.fbm
import za.co.saweather.lwp.engine.lerpColor
import za.co.saweather.lwp.engine.withAlpha
import kotlin.math.max
import kotlin.math.sin

/** A pre-rendered landscape strip, wider than the screen so it can slide for parallax. */
class LandLayer(val bitmap: Bitmap, val top: Float, val depth: Float, val margin: Float) {
    fun draw(c: Canvas, f: Frame, paint: Paint = f.layerPaint) {
        c.drawBitmap(bitmap, -margin + f.shiftX(depth), top + f.shiftY(depth), paint)
    }
}

/**
 * One of the five South African scenes. The renderer draws the shared sky, celestial and
 * weather layers; the scene supplies its landscape, wildlife and scene-specific effects.
 */
abstract class Scene(val id: SceneId, val config: SceneConfig) {
    private val layers = ArrayList<LandLayer>()
    private var builtW = 0
    private var builtH = 0
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    protected val path = Path()
    protected val rng = Rng(id.ordinal * 1000L + 7)
    protected val actors = ArrayList<Actor>()

    fun ensureBuilt(f: Frame) {
        if (f.w == builtW && f.h == builtH) return
        release()
        builtW = f.w; builtH = f.h
        build(f)
    }

    fun release() {
        layers.forEach { it.bitmap.recycle() }
        layers.clear()
        actors.clear()
        builtW = 0; builtH = 0
    }

    /** Create landscape layers (via [layer]) and actors. */
    protected abstract fun build(f: Frame)

    /** Scene-specific tweaks to the shared weather state (after the cycle update). */
    open fun adjust(a: Atmosphere) {}

    open fun update(f: Frame, weather: Weather) {
        for (a in actors) a.tick(f)
    }

    /** Midground, wildlife: everything behind the precipitation. */
    abstract fun drawWorld(c: Canvas, f: Frame)

    /** Foreground elements drawn over the rain (grass tips, foam, leaves). */
    open fun drawForeground(c: Canvas, f: Frame) {}

    /** Light sources not affected by ambient tint (lighthouse, harbour lights, glow). */
    open fun drawEmissive(c: Canvas, f: Frame) {}

    protected fun drawActors(c: Canvas, f: Frame, depthMin: Float, depthMax: Float) {
        for (a in actors) if (a.depth in depthMin..depthMax && a.isShown()) a.drawWithPresence(c, f)
    }

    /**
     * Pre-render a landscape strip covering [top]..[bottom] of the screen. The draw block uses
     * screen coordinates; x may range over -margin..w+margin.
     */
    protected fun layer(f: Frame, top: Float, bottom: Float, depth: Float, draw: Canvas.(left: Float, right: Float) -> Unit): LandLayer {
        val margin = f.maxShift() * depth + 2f
        val bw = (f.w + margin * 2).toInt()
        val bh = max(1f, bottom - top + f.maxShift() * depth * 0.4f).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(margin, -top)
        c.draw(-margin, f.w + margin)
        return LandLayer(bmp, top, depth, margin).also { layers.add(it) }
    }

    // ---- Shared landscape helpers --------------------------------------------------------

    /** A filled ridge line from fractal noise. Returns the path for reuse. */
    protected fun ridge(
        left: Float, right: Float, baseY: Float, amp: Float, freq: Float, seed: Int, bottom: Float,
        octaves: Int = 5, sharp: Boolean = false,
    ): Path {
        val p = Path()
        p.moveTo(left, bottom)
        val steps = 160
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            var n = fbm(x / (right - left) * freq + seed * 3.1f, octaves, seed)
            if (sharp) n = 1f - 2f * kotlin.math.abs(n)
            p.lineTo(x, baseY - n * amp)
        }
        p.lineTo(right, bottom)
        p.close()
        return p
    }

    protected fun ridgeY(x: Float, span: Float, baseY: Float, amp: Float, freq: Float, seed: Int, octaves: Int = 5, sharp: Boolean = false): Float {
        var n = fbm(x / span * freq + seed * 3.1f, octaves, seed)
        if (sharp) n = 1f - 2f * kotlin.math.abs(n)
        return baseY - n * amp
    }

    protected fun Canvas.gradientFill(p: Path, top: Float, bottom: Float, c0: Int, c1: Int) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        pt.shader = LinearGradient(0f, top, 0f, bottom, c0, c1, Shader.TileMode.CLAMP)
        drawPath(p, pt)
    }

    /** Atmospheric perspective: distant layers fade toward the haze colour. */
    protected fun haze(color: Int, hazeColor: Int, amount: Float): Int = lerpColor(color, hazeColor, amount)

    protected fun Canvas.grassTufts(r: Rng, left: Float, right: Float, top: Float, bottom: Float, count: Int, h: Float, colors: IntArray, width: Float) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = width }
        repeat(count) {
            val x = r.range(left, right)
            val y = r.range(top, bottom)
            val depthScale = 0.4f + 0.6f * (y - top) / (bottom - top + 1f)
            pt.color = colors[r.int(colors.size)]
            val blades = 3 + r.int(4)
            for (b in 0 until blades) {
                val lean = r.range(-0.5f, 0.5f)
                val hh = h * depthScale * r.range(0.6f, 1.2f)
                drawLine(x, y, x + lean * hh * 0.5f, y - hh, pt)
            }
        }
    }

    /** Swaying foreground grass blades, drawn live (wind-bent). */
    protected fun drawLiveGrass(c: Canvas, f: Frame, seed: Int, count: Int, baseY: Float, height: Float, color: Int, tip: Int, depth: Float) {
        val r = Rng(seed.toLong())
        val wind = f.atmos.wind
        stroke.strokeWidth = 3.2f * f.u
        val shift = f.shiftX(depth)
        val n = (count * (0.5f + 0.5f * f.particleScale)).toInt()
        for (i in 0 until n) {
            val x = r.range(-0.05f, 1.05f) * f.w + shift
            val y = baseY + r.range(0f, f.h - baseY)
            val hh = height * r.range(0.5f, 1.2f) * (0.6f + 0.4f * (y - baseY) / (f.h - baseY + 1f))
            val phase = r.float() * 6.28f
            val gust = sin(f.t * (1.4f + wind * 3f) + x * 0.01f + phase) * (0.12f + wind * 0.35f)
            val bend = (wind * 0.9f + gust) * hh
            val tipCol = if (r.float() < 0.5f) tip else color
            stroke.color = f.tint(tipCol)
            path.reset()
            path.moveTo(x, y)
            path.quadTo(x + bend * 0.3f, y - hh * 0.6f, x + bend, y - hh * (1f - kotlin.math.abs(bend) / hh * 0.25f))
            c.drawPath(path, stroke)
        }
    }

    protected fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        paint.alpha = 255
        paint.shader = android.graphics.RadialGradient(x, y, r, withAlpha(color, alpha), 0, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint)
        paint.shader = null
    }
}
