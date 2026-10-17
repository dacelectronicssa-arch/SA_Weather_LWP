package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

/** Flock settings, as exported from the in-chat hadeda preview. */
object HadedaDesign {
    const val MAX_FLOCK = 7
    const val BIRD_SIZE = 1.3f
    const val CROSSING_SECONDS = 16f
    const val WINGBEATS_PER_SECOND = 2.6f
    const val SHEEN = 0.65f
}

/**
 * A flock of hadeda ibises crossing the sky in a loose line or ragged V: grey-brown bodies,
 * long down-curved bills, short trailing legs, and slow deep wingbeats with a green sheen.
 */
class HadedaFlock(private val dp: Float) {

    private class Bird(val dx: Float, val dy: Float, val phase: Float, val rate: Float, val scale: Float, val bob: Float)

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f
    private val birds = ArrayList<Bird>()
    private var dir = 1f
    private var baseY = 0f
    private var t = 0f
    private var alpha = 255

    var active = false
        private set

    fun setSize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        active = false
    }

    fun launch(haze: Float = 1f) {
        if (w == 0f) return
        dir = if (rnd.nextBoolean()) 1f else -1f
        baseY = h * (0.3f + rnd.nextFloat() * 0.2f)
        alpha = (255 * haze).toInt().coerceIn(0, 255)
        val n = 3 + rnd.nextInt(HadedaDesign.MAX_FLOCK - 2)
        val vee = rnd.nextBoolean()
        birds.clear()
        for (i in 0 until n) {
            val k = if (vee) ceil(i / 2f) * (if (i % 2 == 1) 1 else -1) else i.toFloat()
            birds += Bird(
                dx = -abs(k) * 0.045f - (if (vee) 0f else i * 0.01f) + (rnd.nextFloat() - 0.5f) * 0.02f,
                dy = (if (vee) abs(k) * 0.018f else (rnd.nextFloat() - 0.5f) * 0.05f) + (rnd.nextFloat() - 0.5f) * 0.012f,
                phase = rnd.nextFloat() * TWO_PI,
                rate = 0.92f + rnd.nextFloat() * 0.16f,
                scale = 0.9f + rnd.nextFloat() * 0.2f,
                bob = rnd.nextFloat() * TWO_PI,
            )
        }
        t = 0f
        active = true
    }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        if (t / HadedaDesign.CROSSING_SECONDS > 1.05f) active = false
    }

    fun draw(c: Canvas) {
        if (!active) return
        val prog = t / HadedaDesign.CROSSING_SECONDS
        val lead = if (dir > 0) -0.15f + prog * 1.25f else 1.15f - prog * 1.25f
        val len = 0.036f * w * HadedaDesign.BIRD_SIZE
        val unit = w * 1.35f
        for (b in birds) {
            val x = (lead + dir * b.dx) * w
            val y = baseY + b.dy * unit * 0.6f + sin(t * 1.3f + b.bob) * 3f * dp
            drawBird(c, x, y, len * b.scale, t * HadedaDesign.WINGBEATS_PER_SECOND * TWO_PI * b.rate + b.phase)
        }
    }

    // ---- One bird, facing right, in units of its body length -------------------------------

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B665B.toInt() }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4E4A42.toInt() }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF6E8C60.toInt(), (255 * HadedaDesign.SHEEN).toInt()) }
    private val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A4A44.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.018f; strokeCap = Paint.Cap.ROUND
    }
    private val neckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B665B.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.07f; strokeCap = Paint.Cap.ROUND
    }
    private val billPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4E4A42.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.024f; strokeCap = Paint.Cap.ROUND
    }
    private val alphaPaints = listOf(bodyPaint, darkPaint, legPaint, neckPaint, billPaint)
    private val path = Path()
    private val bodyOval = RectF(-0.32f, -0.085f, 0.32f, 0.085f)
    private val tailPath = Path().apply { moveTo(-0.3f, -0.01f); lineTo(-0.47f, 0.03f); lineTo(-0.3f, 0.05f); close() }
    private val neckPath = Path().apply { moveTo(0.26f, -0.02f); quadTo(0.38f, -0.05f, 0.47f, -0.045f) }
    private val billPath = Path().apply { moveTo(0.53f, -0.04f); quadTo(0.7f, -0.05f, 0.8f, 0.05f) }

    private fun wing(c: Canvas, tip: Float, k: Float, paint: Paint) {
        path.reset()
        path.moveTo(0.14f, -0.05f)
        path.quadTo(0.12f, tip * 0.55f * k, 0f, tip * k)
        path.quadTo(-0.14f, tip * 0.75f * k, -0.24f, tip * 0.5f * k)
        path.quadTo(-0.2f, -0.02f, -0.14f, -0.04f)
        path.close()
        c.drawPath(path, paint)
    }

    private fun drawBird(c: Canvas, x: Float, y: Float, s: Float, phase: Float) {
        for (p in alphaPaints) p.alpha = alpha
        sheenPaint.alpha = (alpha * HadedaDesign.SHEEN).toInt()
        c.save()
        c.translate(x, y)
        if (dir < 0) c.scale(-1f, 1f)
        c.scale(s, s)
        val tip = -0.72f * sin(phase) + 0.08f
        wing(c, tip, 0.8f, darkPaint)                      // far wing
        c.drawLine(-0.22f, 0.05f, -0.44f, 0.09f, legPaint)  // legs trailing
        c.drawPath(tailPath, bodyPaint)
        c.save()
        c.rotate(-2.9f)
        c.drawOval(bodyOval, bodyPaint)
        c.restore()
        c.drawPath(neckPath, neckPaint)
        c.drawCircle(0.49f, -0.045f, 0.052f, bodyPaint)
        c.drawPath(billPath, billPaint)
        wing(c, tip, 1f, bodyPaint)                        // near wing
        // Green-bronze sheen on the near wing's coverts.
        path.reset()
        path.moveTo(0.12f, -0.05f)
        path.quadTo(0.1f, tip * 0.4f, 0f, tip * 0.6f)
        path.quadTo(-0.08f, tip * 0.45f, -0.12f, -0.04f)
        path.close()
        c.drawPath(path, sheenPaint)
        c.restore()
    }
}
