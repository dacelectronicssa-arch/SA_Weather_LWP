package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Boat settings, from the in-chat boat preview. */
object BoatDesign {
    const val YACHT_SIZE = 1f
    const val JETSKI_SIZE = 1f
    const val CONTAINER_SHIP_SIZE = 1f
    const val SPINNAKER_CHANCE = 0.4f
    const val MAX_BOATS = 3
}

/**
 * A boat crossing Table Bay. [depth] 0..1 is how far out it sails (0 = by the far shore,
 * 1 = near the beach); nearer boats sit lower on screen and are drawn bigger.
 */
abstract class Boat(protected val dp: Float) {
    var active = false
        protected set
    var depth = 0f
        protected set

    protected val rnd = Random(System.nanoTime())
    protected var w = 0f
    protected var h = 0f
    protected var dir = 1f
    protected var x = 0f      // left edge of the boat
    protected var len = 0f
    protected var speed = 0f
    protected var t = 0f
    protected var phase = 0f
    protected var night = false

    fun setSize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        active = false
    }

    protected fun waterY() = h * 0.72f + h * 0.215f * (0.08f + 0.7f * depth)
    protected fun perspective() = 0.55f + 0.9f * depth

    /** Starts a crossing at [depth] taking [seconds]; [length] as a fraction of screen width. */
    protected fun start(depth: Float, length: Float, seconds: Float, night: Boolean) {
        this.depth = depth
        this.night = night
        len = w * length * perspective()
        dir = if (rnd.nextBoolean()) 1f else -1f
        x = if (dir > 0) -len * 1.1f else w + len * 0.1f
        speed = (w + len * 1.2f) / seconds
        t = 0f
        phase = rnd.nextFloat() * TWO_PI
        active = true
    }

    open fun update(dt: Float) {
        if (!active) return
        t += dt
        x += dir * speed * dt
        if ((dir > 0 && x > w + 5f) || (dir < 0 && x + len < -5f)) active = false
    }

    abstract fun draw(c: Canvas, haze: Float)

    // ---- Shared water effects ---------------------------------------------------------

    private val wake = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    protected fun drawWake(c: Canvas, y: Float, alpha: Int, spacing: Float) {
        wake.color = if (night) 0xFFA0AABE.toInt() else 0xFFFFFFFF.toInt()
        wake.strokeWidth = maxOf(0.6f * dp, len * 0.004f)
        val stern = if (dir > 0) x else x + len
        val bow = if (dir > 0) x + len else x
        val k = dp * 0.5f
        for (i in 1..6) {
            wake.alpha = (alpha * (1f - i * 0.14f) * (if (night) 0.5f else 0.85f)).toInt()
            val d = i * len * spacing
            c.drawLine(stern - dir * d, y + (0.3f + i * 0.25f) * k, stern - dir * (d + len * 0.08f), y + (0.5f + i * 0.45f) * k, wake)
            c.drawLine(stern - dir * d, y - 0.1f * k, stern - dir * (d + len * 0.08f), y - (0.2f + i * 0.1f) * k, wake)
        }
        wake.alpha = (alpha * 0.8f).toInt()
        c.drawLine(bow - dir * len * 0.06f, y + 0.4f * k, bow + dir * 0.6f * k, y, wake)
    }

    /** Draws a bitmap painted facing right (bow at local x = 1) with its waterline at [oy]. */
    protected fun drawBaked(c: Canvas, bmp: Bitmap, ox: Float, oy: Float, scale: Float, y: Float, alpha: Int) {
        bakedPaint.alpha = alpha
        c.save()
        c.translate(x, y)
        if (dir < 0) { c.translate(len, 0f); c.scale(-1f, 1f) }
        val bw = bmp.width / scale
        val bh = bmp.height / scale
        dst.set(-ox, -oy, -ox + bw, -oy + bh)
        c.drawBitmap(bmp, null, dst, bakedPaint)
        c.restore()
    }

    private val bakedPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    // ---- Shape helpers in units of the boat's length -----------------------------------

    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val path = Path()

    protected fun line(c: Canvas, width: Float, color: Int, vararg pts: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = color
        path.reset()
        path.moveTo(pts[0], pts[1])
        for (i in 2 until pts.size step 2) path.lineTo(pts[i], pts[i + 1])
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    protected fun rect(c: Canvas, color: Int, x: Float, y: Float, rw: Float, rh: Float) {
        p.color = color
        c.drawRect(x, y, x + rw, y + rh, p)
    }

    protected fun circle(c: Canvas, color: Int, cx: Float, cy: Float, r: Float) {
        p.color = color
        c.drawCircle(cx, cy, r, p)
    }

    protected fun shape(c: Canvas, color: Int, build: Path.() -> Unit) {
        p.color = color
        path.reset()
        path.build()
        path.close()
        c.drawPath(path, p)
    }

    protected fun bake(lenPx: Float, left: Float, top: Float, right: Float, bottom: Float, paint: (Canvas) -> Unit): Triple<Bitmap, Float, Float> {
        val scale = 2f.coerceAtMost(1400f / (lenPx * (right - left))) // crisp, but bounded in size
        val bw = ((right - left) * lenPx * scale).toInt().coerceAtLeast(1)
        val bh = ((bottom - top) * lenPx * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-left * lenPx, -top * lenPx)
        c.scale(lenPx, lenPx)
        paint(c)
        bakeScale = scale
        return Triple(bmp, -left * lenPx, -top * lenPx)
    }

    protected var bakeScale = 1f

    companion object {
        val Long.c get() = toInt()
    }
}

/** A sailing yacht: mainsail and jib (or a striped spinnaker), rigging, cabin, crew and flag. */
class Yacht(dp: Float) : Boat(dp) {
    private var bmp: Bitmap? = null
    private var ox = 0f
    private var oy = 0f
    private var scale = 1f

    fun launch(depth: Float, night: Boolean) {
        start(depth, 0.11f * BoatDesign.YACHT_SIZE, 60f + rnd.nextFloat() * 30f, night)
        val spin = !night && rnd.nextFloat() < BoatDesign.SPINNAKER_CHANCE
        val hue = longArrayOf(0xFFC8312B, 0xFF2B6CB0, 0xFFE2A21B, 0xFF1F8A70)[rnd.nextInt(4)].toInt()
        bmp?.recycle()
        val (b, x0, y0) = bake(len, -0.08f, -1.0f, 1.32f, 0.04f) { paintYacht(it, spin, hue) }
        bmp = b; ox = x0; oy = y0; scale = bakeScale
    }

    override fun draw(c: Canvas, haze: Float) {
        val b = bmp ?: return
        if (!active) return
        val y = waterY() + sin(t * 1.2f + phase) * 0.3f * dp
        val a = (255 * haze).toInt()
        drawWake(c, y, a, 0.1f)
        c.save()
        c.rotate(-2.6f * dir, x + len / 2f, y) // heeling in the breeze
        drawBaked(c, b, ox, oy, scale, y, a)
        c.restore()
    }

    private fun paintYacht(c: Canvas, spin: Boolean, hue: Int) {
        val n = night
        val hull = if (n) 0xFF6F7684.c else 0xFFF6F7F9.c
        val rig = if (n) 0xFF565C68.c else 0xFF7E858F.c
        val sail = if (n) 0xFF8A90A0.c else 0xFFFCFCFB.c
        val sailShade = if (n) 0xFF717787.c else 0xFFE3E6EA.c
        shape(c, if (n) 0xFF4A2A2A.c else 0xFF9C2B26.c) { moveTo(0.1f, 0f); lineTo(0.84f, 0f); lineTo(0.8f, 0.022f); lineTo(0.13f, 0.022f) }
        shape(c, hull) { moveTo(0f, -0.072f); quadTo(0.5f, -0.058f, 1f, -0.075f); quadTo(0.93f, 0f, 0.84f, 0.004f); lineTo(0.1f, 0.004f); quadTo(0.03f, 0f, 0f, -0.072f) }
        shape(c, 0xFF1C2A4C.c) { moveTo(0.04f, -0.038f); quadTo(0.5f, -0.03f, 0.95f, -0.045f); lineTo(0.94f, -0.034f); quadTo(0.5f, -0.02f, 0.05f, -0.028f) }
        shape(c, if (n) 0xFF7E8592.c else 0xFFFFFFFF.c) { moveTo(0.33f, -0.068f); lineTo(0.36f, -0.115f); lineTo(0.6f, -0.112f); lineTo(0.64f, -0.066f) }
        for (i in 0 until 4) circle(c, if (n) 0xFFFFD98A.c else 0xFF33475E.c, 0.4f + i * 0.055f, -0.09f, 0.009f)
        // Bow pulpit, stanchions and lifelines.
        line(c, 0.004f, rig, 0.93f, -0.075f, 0.95f, -0.11f, 0.86f, -0.11f)
        var sx = 0.1f
        while (sx < 0.9f) { line(c, 0.003f, rig, sx, -0.07f, sx, -0.1f); sx += 0.09f }
        line(c, 0.003f, rig, 0.1f, -0.1f, 0.93f, -0.11f)
        // Crew in the cockpit.
        circle(c, if (n) 0xFF3A3F4A.c else 0xFF2A2E36.c, 0.16f, -0.1f, 0.014f)
        rect(c, if (n) 0xFF3A3F4A.c else 0xFF2A2E36.c, 0.155f, -0.088f, 0.012f, 0.02f)
        circle(c, if (n) 0xFF5A4030.c else 0xFF2B6CB0.c, 0.22f, -0.098f, 0.012f)
        rect(c, if (n) 0xFF5A4030.c else 0xFF2B6CB0.c, 0.215f, -0.087f, 0.011f, 0.018f)
        // Ensign at the stern.
        line(c, 0.003f, rig, 0.02f, -0.072f, 0f, -0.14f)
        rect(c, if (n) 0xFF4A4F5A.c else 0xFFC8312B.c, -0.06f, -0.14f, 0.06f, 0.035f)
        rect(c, if (n) 0xFF4A4F5A.c else 0xFF1C3F8A.c, -0.06f, -0.1225f, 0.06f, 0.0175f)
        // Mast, spreaders, stays and shrouds, boom.
        line(c, 0.012f, rig, 0.45f, -0.07f, 0.45f, -0.97f)
        line(c, 0.005f, rig, 0.41f, -0.58f, 0.49f, -0.58f)
        line(c, 0.005f, rig, 0.42f, -0.33f, 0.48f, -0.33f)
        line(c, 0.0025f, rig, 0.45f, -0.96f, 0.97f, -0.075f)
        line(c, 0.0025f, rig, 0.45f, -0.96f, 0.02f, -0.075f)
        line(c, 0.0025f, rig, 0.41f, -0.58f, 0.4f, -0.07f)
        line(c, 0.0025f, rig, 0.49f, -0.58f, 0.5f, -0.07f)
        line(c, 0.016f, rig, 0.45f, -0.16f, 0.02f, -0.15f)
        // Mainsail: the South African flag, hoist at the mast, with battens over it.
        val mainsail = Path().apply { moveTo(0.44f, -0.94f); quadTo(0.12f, -0.5f, 0.03f, -0.165f); lineTo(0.44f, -0.16f); close() }
        c.save()
        c.clipPath(mainsail)
        paintSouthAfricanFlag(c, n)
        shape(c, 0x1A000000) { moveTo(0.44f, -0.94f); quadTo(0.3f, -0.5f, 0.24f, -0.165f); lineTo(0.44f, -0.16f) } // the sail's belly
        c.restore()
        for (k in 1 until 5) {
            val y = -0.16f - k * 0.155f
            val bx = 0.44f - 0.4f * (1f - k / 5.3f)
            line(c, 0.003f, if (n) 0xFF666C7A.c else 0xFFC9CDD3.c, 0.44f, y, bx + 0.02f, y + 0.01f)
        }
        if (spin) {
            shape(c, hue) { moveTo(0.46f, -0.88f); quadTo(1.28f, -0.62f, 0.99f, -0.11f); lineTo(0.52f, -0.16f) }
            shape(c, 0xEBFFFFFF.c) { moveTo(0.48f, -0.72f); quadTo(1.16f, -0.52f, 1.06f, -0.38f); lineTo(0.5f, -0.58f) }
            shape(c, 0x1F000000) { moveTo(0.46f, -0.88f); quadTo(0.8f, -0.5f, 0.52f, -0.16f); lineTo(0.5f, -0.16f) }
        } else {
            shape(c, sail) { moveTo(0.46f, -0.7f); quadTo(0.66f, -0.36f, 0.84f, -0.09f); lineTo(0.48f, -0.11f) }
            shape(c, sailShade) { moveTo(0.46f, -0.7f); quadTo(0.56f, -0.34f, 0.6f, -0.1f); lineTo(0.48f, -0.11f) }
            line(c, 0.0025f, rig, 0.8f, -0.1f, 0.6f, -0.1f)
        }
        if (n) {
            circle(c, 0xFFFFFFFF.c, 0.45f, -0.98f, 0.016f)
            circle(c, 0xFF3CFF7A.c, 0.95f, -0.07f, 0.014f) // starboard (green): the boat is drawn bow-right
            circle(c, 0xFFFFFFFF.c, 0.01f, -0.07f, 0.012f)
        }
    }
    /**
     * The South African flag stretched over the mainsail's area: flag x runs from the hoist
     * at the mast (0) to the fly (3), flag y from the head (0) down to the boom (2).
     */
    private fun paintSouthAfricanFlag(c: Canvas, n: Boolean) {
        c.save()
        c.translate(0.44f, -0.94f)
        c.scale(-(0.44f - 0.02f) / 3f, (0.94f - 0.155f) / 2f)
        fun col(day: Long) = if (n) scale(day.toInt(), 0.45f) else day.toInt()
        rect(c, col(0xFFE03C31), 0f, 0f, 3f, 1f)    // red
        rect(c, col(0xFF001489), 0f, 1f, 3f, 1f)    // blue
        p.strokeJoin = Paint.Join.MITER
        line(c, 0.667f, col(0xFFFFFFFF), 0f, 0f, 1f, 1f, 3.1f, 1f) // white border of the Y
        line(c, 0.667f, col(0xFFFFFFFF), 0f, 2f, 1f, 1f)
        line(c, 0.4f, col(0xFF007749), 0f, 0f, 1f, 1f, 3.1f, 1f)   // green Y
        line(c, 0.4f, col(0xFF007749), 0f, 2f, 1f, 1f)
        shape(c, col(0xFFFFB81C)) { moveTo(0f, 0.2f); lineTo(0.9f, 1f); lineTo(0f, 1.8f) }   // gold
        shape(c, col(0xFF000000)) { moveTo(0f, 0.4f); lineTo(0.67f, 1f); lineTo(0f, 1.6f) } // black
        c.restore()
    }
}

/** A jetski with its rider, bouncing over the swell and throwing a rooster tail of spray. */
class JetSki(dp: Float) : Boat(dp) {
    private var hue = 0
    private var body = 0
    private val sprayX = FloatArray(80)
    private val sprayY = FloatArray(80)
    private val sprayVX = FloatArray(80)
    private val sprayVY = FloatArray(80)
    private val sprayLife = FloatArray(80)
    private val sprayR = FloatArray(80)
    private var sprayN = 0
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun launch(depth: Float) {
        start(depth, 0.04f * BoatDesign.JETSKI_SIZE, 7f + rnd.nextFloat() * 3f, night = false)
        hue = longArrayOf(0xFFC8312B, 0xFF2B6CB0, 0xFFE2A21B, 0xFF1F8A70)[rnd.nextInt(4)].toInt()
        body = longArrayOf(0xFFF2F2F2, 0xFF1C1F26)[rnd.nextInt(2)].toInt()
        sprayN = 0
    }

    private fun bounce() = -abs(sin(t * 7f)) * len * 0.06f

    override fun update(dt: Float) {
        super.update(dt)
        // Spray: spawn at the stern, fly back and up, fall under gravity.
        var i = 0
        while (i < sprayN) {
            sprayLife[i] -= dt
            sprayVY[i] += h * 0.5f * dt
            sprayX[i] += sprayVX[i] * dt
            sprayY[i] += sprayVY[i] * dt
            if (sprayLife[i] <= 0f) {
                val last = --sprayN
                sprayX[i] = sprayX[last]; sprayY[i] = sprayY[last]; sprayVX[i] = sprayVX[last]
                sprayVY[i] = sprayVY[last]; sprayLife[i] = sprayLife[last]; sprayR[i] = sprayR[last]
            } else i++
        }
        if (active) repeat(3) {
            if (sprayN < sprayX.size) {
                val stern = if (dir > 0) x else x + len
                val j = sprayN++
                sprayX[j] = stern
                sprayY[j] = waterY() + bounce() - len * 0.03f
                sprayVX[j] = -dir * len * (1.2f + rnd.nextFloat() * 1.6f)
                sprayVY[j] = -len * (1.4f + rnd.nextFloat() * 1.6f)
                sprayLife[j] = 0.7f
                sprayR[j] = len * 0.02f * (0.6f + rnd.nextFloat())
            }
        }
    }

    override fun draw(c: Canvas, haze: Float) {
        val a = (255 * haze).toInt()
        sprayPaint.color = 0xFFFFFFFF.toInt()
        for (i in 0 until sprayN) {
            sprayPaint.alpha = (a * 0.92f * sprayLife[i] / 0.7f).toInt()
            c.drawCircle(sprayX[i], sprayY[i], sprayR[i], sprayPaint)
        }
        if (!active) return
        val y = waterY() + sin(t * 1.2f + phase) * 0.3f * dp + bounce()
        drawWake(c, waterY(), a, 0.35f)
        p.alpha = a
        c.save()
        c.translate(x, y)
        if (dir < 0) { c.translate(len, 0f); c.scale(-1f, 1f) }
        c.scale(len, len)
        c.rotate(Math.toDegrees((-0.07 * sin(t * 7.0) - 0.06)).toFloat())
        paintJetSki(c)
        c.restore()
    }

    private fun paintJetSki(c: Canvas) {
        val dark = 0xFF2A2E36.c
        p.strokeCap = Paint.Cap.ROUND
        shape(c, dark) { moveTo(0.05f, -0.02f); lineTo(0.95f, -0.04f); lineTo(0.92f, 0.02f); lineTo(0.12f, 0.03f) }
        shape(c, body) { moveTo(0f, -0.13f); lineTo(0.2f, -0.15f); lineTo(0.62f, -0.19f); quadTo(0.9f, -0.19f, 1.02f, -0.1f); quadTo(1.02f, -0.04f, 0.95f, -0.03f); lineTo(0.06f, -0.01f) }
        shape(c, hue) { moveTo(0.03f, -0.07f); lineTo(0.98f, -0.1f); quadTo(1f, -0.07f, 0.97f, -0.05f); lineTo(0.05f, -0.035f) }
        line(c, 0.006f, 0x40000000, 0.1f, -0.11f, 0.95f, -0.14f)
        shape(c, 0xFF1E2128.c) { moveTo(0.2f, -0.15f); quadTo(0.25f, -0.24f, 0.55f, -0.225f); lineTo(0.58f, -0.18f) }
        shape(c, hue) { moveTo(0.6f, -0.19f); lineTo(0.78f, -0.27f); lineTo(0.83f, -0.19f) }
        line(c, 0.025f, 0xFF1E2128.c, 0.74f, -0.27f, 0.72f, -0.33f)
        line(c, 0.018f, 0xFF1E2128.c, 0.66f, -0.33f, 0.8f, -0.34f)
        rect(c, 0xFF9AA3AE.c, 0.79f, -0.37f, 0.03f, 0.02f)
        // Rider: legs, torso in an orange life vest, arms to the bars, head with cap and sunglasses.
        line(c, 0.06f, 0xFF26405E.c, 0.38f, -0.2f, 0.52f, -0.2f, 0.6f, -0.08f)
        line(c, 0.05f, 0xFFE0B896.c, 0.6f, -0.08f, 0.64f, -0.06f)
        line(c, 0.075f, 0xFF26405E.c, 0.38f, -0.21f, 0.45f, -0.27f)
        line(c, 0.1f, 0xFFF07A1E.c, 0.45f, -0.27f, 0.5f, -0.47f)
        line(c, 0.04f, 0xFFC45A10.c, 0.47f, -0.36f, 0.53f, -0.36f)
        line(c, 0.04f, 0xFFE0B896.c, 0.52f, -0.44f, 0.62f, -0.38f, 0.72f, -0.34f)
        circle(c, 0xFFE0B896.c, 0.53f, -0.56f, 0.06f)
        p.color = 0xFFC8312B.c
        c.drawArc(RectF(0.468f, -0.647f, 0.592f, -0.523f), 180f, 180f, true, p)
        rect(c, 0xFFC8312B.c, 0.53f, -0.595f, 0.1f, 0.02f)
        rect(c, 0xFF111111.c, 0.555f, -0.57f, 0.045f, 0.018f)
    }
}

/** A container ship: stacked, ribbed containers, a superstructure with radar mast and funnel. */
class ContainerShip(dp: Float) : Boat(dp) {
    private var bmp: Bitmap? = null
    private var ox = 0f
    private var oy = 0f
    private var scale = 1f
    private val boxColours = longArrayOf(
        0xFFB23A2E, 0xFF2D5D8A, 0xFFD9A23A, 0xFF3E7D4F, 0xFF8C8F94,
        0xFFE7E2D6, 0xFFC46A2B, 0xFF5B3F7A, 0xFF7A2F2A, 0xFF2F6E73,
    )

    fun launch(depth: Float, night: Boolean) {
        // Same size whichever lane it takes: 55% of the screen at the far lane's perspective.
        start(depth, 0.55f * 0.64f / (0.55f + 0.9f * depth) * BoatDesign.CONTAINER_SHIP_SIZE, 160f + rnd.nextFloat() * 60f, night)
        val cols = IntArray(400) { boxColours[rnd.nextInt(boxColours.size)].toInt() }
        bmp?.recycle()
        val (b, x0, y0) = bake(len, -0.01f, -0.36f, 1.01f, 0.02f) { paintShip(it, cols) }
        bmp = b; ox = x0; oy = y0; scale = bakeScale
    }

    override fun draw(c: Canvas, haze: Float) {
        val b = bmp ?: return
        if (!active) return
        val y = waterY() + sin(t * 0.6f + phase) * 0.2f * dp
        val a = (255 * haze).toInt()
        drawWake(c, y, a, 0.1f)
        drawBaked(c, b, ox, oy, scale, y, a)
    }

    private fun paintShip(c: Canvas, cols: IntArray) {
        val n = night
        // Containers: ribbed boxes stacked to varying heights, lashing bridges between bays.
        var bay = 0
        var bx = 0.17f
        while (bx < 0.9f) {
            val hgt = if (bx > 0.84f) 3 else 4 + (if ((bay * 7) % 3 == 0) 1 else 0) + (if ((bay * 3) % 5 == 0) 1 else 0) - (if ((bay * 11) % 7 == 0) 1 else 0)
            for (k in 0 until hgt) {
                val y = -0.085f - (k + 1) * 0.03f
                rect(c, if (n) 0xFF252A35.c else cols[(bay * 6 + k) % cols.size], bx, y, 0.026f, 0.029f)
                var r = 0.0035f
                while (r < 0.026f) { line(c, 0.0012f, 0x38000000, bx + r, y + 0.002f, bx + r, y + 0.027f); r += 0.0035f }
                rect(c, 0x2E000000, bx, y + 0.026f, 0.026f, 0.003f)
            }
            if (bay % 2 == 1) {
                line(c, 0.0018f, if (n) 0xFF3A3F4A.c else 0xFF5E646E.c, bx - 0.0012f, -0.085f, bx - 0.0012f, -0.145f)
                line(c, 0.0018f, if (n) 0xFF3A3F4A.c else 0xFF5E646E.c, bx - 0.0012f, -0.115f, bx + 0.002f, -0.115f)
            }
            bay++
            bx += 0.0275f
        }
        // Hull, red boot-top, sheer line, draft marks, anchor, forecastle and bow mast.
        shape(c, if (n) 0xFF151C2B.c else 0xFF1F2E4A.c) {
            moveTo(0.01f, 0f); lineTo(0.94f, 0f); quadTo(0.985f, -0.045f, 1f, -0.1f)
            lineTo(0.9f, -0.093f); lineTo(0.9f, -0.085f); lineTo(0f, -0.085f); lineTo(0f, -0.03f)
        }
        rect(c, 0xFF8E2B22.c, 0.012f, -0.012f, 0.93f, 0.012f)
        line(c, 0.0015f, if (n) 0xFF2A3244.c else 0xFF3A4A68.c, 0.01f, -0.06f, 0.95f, -0.065f)
        for (k in 0 until 4) rect(c, if (n) 0xFFC9CDD6.c else 0xFFF2F2F2.c, 0.93f, -0.015f - k * 0.012f, 0.008f, 0.002f)
        rect(c, if (n) 0xFFC9CDD6.c else 0xFFF2F2F2.c, 0.03f, -0.015f, 0.008f, 0.002f)
        circle(c, if (n) 0xFF30353E.c else 0xFF2A2E36.c, 0.955f, -0.07f, 0.006f)
        shape(c, if (n) 0xFF2A3244.c else 0xFF26364F.c) { moveTo(0.9f, -0.093f); lineTo(0.88f, -0.11f); lineTo(0.905f, -0.11f); lineTo(0.92f, -0.094f) }
        line(c, 0.003f, if (n) 0xFF4A505C.c else 0xFF8A9099.c, 0.95f, -0.1f, 0.95f, -0.16f)
        // Superstructure: decks of windows, bridge wings, radar mast with domes, funnel.
        val sup = if (n) 0xFF8A8F9C.c else 0xFFF0F2F4.c
        val win = if (n) 0xFFFFD98A.c else 0xFF2E4258.c
        rect(c, sup, 0.035f, -0.25f, 0.1f, 0.165f)
        rect(c, sup, 0.015f, -0.262f, 0.14f, 0.016f)
        rect(c, sup, 0.05f, -0.29f, 0.07f, 0.03f)
        for (r in 0 until 5) for (k in 0 until 8) rect(c, win, 0.04f + k * 0.0115f, -0.237f + r * 0.028f, 0.008f, 0.009f)
        rect(c, win, 0.02f, -0.258f, 0.13f, 0.008f)
        line(c, 0.003f, if (n) 0xFF4A505C.c else 0xFF8A9099.c, 0.085f, -0.29f, 0.085f, -0.34f)
        line(c, 0.002f, if (n) 0xFF4A505C.c else 0xFF8A9099.c, 0.07f, -0.325f, 0.1f, -0.325f)
        circle(c, if (n) 0xFFC9CDD6.c else 0xFFFFFFFF.c, 0.07f, -0.3f, 0.007f)
        circle(c, if (n) 0xFFC9CDD6.c else 0xFFFFFFFF.c, 0.1f, -0.3f, 0.007f)
        rect(c, if (n) 0xFF3A3F4A.c else 0xFF2A2E36.c, 0.035f, -0.33f, 0.025f, 0.06f)
        rect(c, if (n) 0xFF4A4F5A.c else 0xFFC9302C.c, 0.035f, -0.315f, 0.025f, 0.012f)
        // Free-fall lifeboat on its stern ramp.
        c.save(); c.translate(0.012f, -0.1f); c.rotate(-28.6f)
        rect(c, if (n) 0xFF6A4A30.c else 0xFFF26A1B.c, 0f, 0f, 0.04f, 0.014f)
        c.restore()
        line(c, 0.0015f, if (n) 0xFF4A505C.c else 0xFF9AA0A8.c, 0f, -0.088f, 0.17f, -0.088f)
        line(c, 0.0015f, if (n) 0xFF4A505C.c else 0xFF9AA0A8.c, 0.9f, -0.096f, 1f, -0.103f)
        if (n) {
            var lx = 0.18f
            while (lx < 0.9f) { circle(c, 0xFFFFF3D6.c, lx, -0.09f, 0.0025f); lx += 0.05f }
            circle(c, 0xFFFFFFFF.c, 0.085f, -0.345f, 0.004f)
            circle(c, 0xFFFFFFFF.c, 0.95f, -0.165f, 0.004f)
            circle(c, 0xFF3CFF7A.c, 0.15f, -0.255f, 0.004f)
        }
    }
}
