package za.co.saweather.lwp.scenes

import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Rng

/**
 * An animal that crosses the scene. It enters from an edge while its activity window is open,
 * walks (or flees, when a storm builds) to the far side, rests off-screen, then comes back.
 */
abstract class Walker(activity: Activity, depth: Float, seed: Long) : Actor(activity, depth) {
    override val fades = false
    protected val rng = Rng(seed)
    var x = 0f
    var dir = 1f
    var onScreen = false
    var gait = 0f
    var stride = 0f
    /** Seconds until the next crossing starts. */
    protected var wait = rng.range(1f, 8f)

    /** Walking speed in screen-widths per second. */
    abstract val walkSpeed: Float
    open val fleeSpeed: Float get() = walkSpeed * 4f
    /** Horizontal extent of the animal in px, to know when it's fully off-screen. */
    abstract fun extent(f: Frame): Float
    open val restRange = 6f..20f
    /** Chance per second of pausing mid-crossing (to graze, look around). */
    open val pauseChance = 0.05f
    protected var paused = 0f
    private var started = false

    override fun isShown() = onScreen

    override fun update(f: Frame, active: Boolean) {
        val flee = stormReact(f) > 0.25f
        if (!started && active) {
            // Don't leave the scene empty at first launch: begin part-way through a crossing.
            started = true
            onScreen = true
            dir = if (flee) -1f else rng.sign()
            x = f.w * rng.range(0.15f, 0.85f)
            onEnter(f)
        }
        if (!onScreen) {
            if (!active) return
            wait -= f.dt
            if (wait > 0f) return
            dir = if (flee) -1f else rng.sign()
            x = if (dir > 0) -extent(f) else f.w + extent(f)
            onScreen = true
            paused = 0f
            onEnter(f)
        }
        if (flee && dir > 0) dir = -1f // storms come in from the east (right); flee west
        val speed = if (flee) fleeSpeed else walkSpeed
        if (paused > 0f && !flee) {
            paused -= f.dt
            stride = (stride - f.dt * 2f).coerceAtLeast(0f)
        } else {
            if (rng.float() < pauseChance * f.dt && !flee) paused = rng.range(2f, 6f)
            val targetStride = if (flee) 1f else 0.35f
            stride += (targetStride - stride) * minOf(1f, f.dt * 2f)
            x += dir * speed * f.w * f.dt * (0.3f + stride)
            gait += f.dt * (3.2f + stride * 9f)
        }
        val e = extent(f)
        if ((dir > 0 && x > f.w + e) || (dir < 0 && x < -e)) {
            onScreen = false
            wait = rng.range(restRange.start, restRange.endInclusive)
        }
    }

    open fun onEnter(f: Frame) {}
}

/** A herd crossing together. Members are offset in body lengths and depth. */
open class Herd(
    activity: Activity,
    depth: Float,
    seed: Long,
    val quad: za.co.saweather.lwp.engine.Quad,
    /** Ground y as a fraction of the land band (0 = horizon, 1 = bottom of screen). */
    val groundT: Float,
    /** Body length as a fraction of screen width. */
    val size: Float,
    val members: List<Member>,
    override val walkSpeed: Float,
    val pronk: Boolean = false,
    val silhouetteBias: Float = 0.8f,
    val grazes: Boolean = true,
) : Walker(activity, depth, seed) {
    class Member(val dx: Float, val dy: Float, val scale: Float, val phase: Float)

    private val ordered = members.sortedBy { it.dy }

    private val spread = members.maxOf { it.dx } + 2f

    /** Covers the whole herd so stragglers are off-screen too before it counts as gone. */
    override fun extent(f: Frame) = f.w * size * spread
    override val hidesInStorm = false

    override fun draw(c: android.graphics.Canvas, f: Frame) {
        val p = Scenes.paint
        val land = f.h - f.horizonY
        val baseY = f.horizonY + land * groundT
        val color = f.animal(quad.color, silhouetteBias)
        val darker = za.co.saweather.lwp.engine.lerpColor(color, android.graphics.Color.BLACK, 0.25f)
        val graze = if (grazes && paused > 0f) (0.5f + 0.5f * kotlin.math.sin(paused * 0.8f)).coerceIn(0f, 1f) else 0f
        for (m in ordered) {
            val s = f.w * size * m.scale
            val gx = x + f.shiftX(depth) - dir * m.dx * f.w * size
            var gy = baseY + m.dy * land
            val g = gait + m.phase
            if (pronk && stride > 0.6f) gy -= kotlin.math.abs(kotlin.math.sin(g)) * s * 0.5f
            Scenes.shadow(c, f, gx, baseY + m.dy * land, s)
            za.co.saweather.lwp.engine.Wildlife.quad(c, p, quad, gx, gy, s, dir, g, stride, color, graze * ((m.phase * 7f) % 1f), darker = darker)
        }
    }
}
