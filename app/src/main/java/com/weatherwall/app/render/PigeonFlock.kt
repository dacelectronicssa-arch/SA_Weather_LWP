package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * A big flock of city pigeons (from the in-chat pigeon preview): it blows in, wheels over the
 * skyline, spreads out over several rooftops, rests there pecking and turning, then takes off
 * together, wheels once more and leaves.
 */
class PigeonFlock {

    private enum class Mode { NONE, ARRIVE, WHEEL, LAND, SIT, TAKEOFF, LEAVE }

    private class Bird(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val ox: Float, val oy: Float, var ph: Float, val rate: Float,
        val sx: Float, val sy: Float, val delay: Float, val col: Int,
    ) {
        var perched = false
        var peck = 0f
        var face = 1f
    }

    private val rnd = Random(System.nanoTime())
    private val birds = ArrayList<Bird>()
    private var mode = Mode.NONE
    private var mt = 0f
    private var cx = 0f
    private var cy = 0f
    private var ang = 0f
    private var from = 1f
    private var w = 0f
    private var h = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    val active get() = mode != Mode.NONE

    /** Wings going (anything but resting on the roofs): needs smooth frames. */
    val flying get() = mode != Mode.NONE && mode != Mode.SIT


    /** Preview pixels (a 680-wide canvas) to screen pixels. */
    private val k get() = w / 680f

    /** Gap between perched pigeons, for asking the city for roof spots. */
    val roofGap get() = 5.5f * k * SIZE * SPACING

    fun setSize(width: Int, height: Int) { w = width.toFloat(); h = height.toFloat() }

    private fun r() = rnd.nextFloat()

    /**
     * Sends a flock in. [roofs] holds the free spots along each visible rooftop (x, y pairs per
     * roof); the flock spreads over several of them, more birds on the wider roofs.
     */
    fun launch(roofs: List<FloatArray>) {
        if (w == 0f || roofs.isEmpty()) return
        val n = COUNT
        val chosen = roofs.shuffled(rnd).take(max(1, ROOFTOPS))
        val weights = chosen.map { it.size / 2 * (0.5f + r()) }
        val total = weights.sum()
        val slots = ArrayList<FloatArray>()
        for ((i, roof) in chosen.withIndex()) {
            val spots = roof.size / 2
            val want = max(1, round(n * weights[i] / total).toInt()).coerceAtMost(spots)
            val idx = (0 until spots).shuffled(rnd).take(want)
            for (j in idx) slots += floatArrayOf(roof[j * 2] + (r() - 0.5f) * roofGap * 0.3f, roof[j * 2 + 1])
        }
        while (slots.size < n) { val roof = chosen[rnd.nextInt(chosen.size)]; val j = rnd.nextInt(roof.size / 2); slots += floatArrayOf(roof[j * 2], roof[j * 2 + 1]) }
        slots.shuffle(rnd)

        from = if (rnd.nextBoolean()) -1f else 1f
        cx = if (from < 0) -60f * k else w + 60f * k
        cy = h * 0.3f
        ang = 0f
        mode = Mode.ARRIVE
        mt = 0f
        birds.clear()
        for (i in 0 until n) {
            val col = when { r() < 0.12f -> 0xFFE8E6E2.toInt(); r() < 0.2f -> 0xFF8A6A58.toInt(); else -> 0xFF7D8296.toInt() }
            birds += Bird(cx + (r() - 0.5f) * 60 * k, cy + (r() - 0.5f) * 40 * k, -from * 60 * k, 0f,
                (r() - 0.5f) * 2, (r() - 0.5f) * 2, r() * 6, 9 + r() * 4, slots[i][0], slots[i][1], r() * 2.5f, col)
                .also { it.face = if (rnd.nextBoolean()) 1f else -1f }
        }
    }

    /** Everyone up at once (when the flock has rested long enough). */
    private fun startle() {
        mode = Mode.TAKEOFF
        mt = 0f
        for (b in birds) { b.perched = false; b.vy = -(80 + r() * 60) * k; b.vx = (r() - 0.5f) * 80 * k }
    }

    private fun steer(b: Bird, tx: Float, ty: Float, maxV: Float, acc: Float, dt: Float) {
        val dx = tx - b.x; val dy = ty - b.y
        val d = max(hypot(dx, dy), 1e-3f)
        val want = min(maxV * k, d * 1.6f)
        b.vx += (dx / d * want - b.vx) * acc * dt
        b.vy += (dy / d * want - b.vy) * acc * dt
        b.x += b.vx * dt
        b.y += b.vy * dt
    }

    // Wheeling circle: over the city, between the tower's crown and the rooftops.
    private fun wheel(dt: Float) {
        ang += dt * 0.9f * -from
        cx = w * 0.5f + cos(ang) * w * 0.3f
        cy = h * 0.44f + sin(ang) * h * 0.06f
    }

    fun update(dt: Float) {
        if (mode == Mode.NONE) return
        mt += dt
        when (mode) {
            Mode.ARRIVE -> { cx += (w * 0.5f - cx) * dt * 0.5f; cy += (h * 0.44f - cy) * dt * 0.5f; if (mt > 4f) { mode = Mode.WHEEL; mt = 0f } }
            Mode.WHEEL -> { wheel(dt); if (mt > 9f) { mode = Mode.LAND; mt = 0f } }
            Mode.TAKEOFF -> { cx = w * 0.5f; wheel(dt); if (mt > 5f) { mode = Mode.LEAVE; mt = 0f } }
            Mode.LEAVE -> { cx += -from * w * 0.25f * dt * (1 + mt * 0.5f); cy -= h * 0.02f * dt; if (mt > 8f) { mode = Mode.NONE; birds.clear(); return } }
            Mode.LAND -> if (mt < 3f) wheel(dt)
            else -> Unit
        }
        val spread = (if (mode == Mode.ARRIVE || mode == Mode.LEAVE) 38f else 58f) * k
        for ((i, b) in birds.withIndex()) {
            b.ph += dt * b.rate
            // A perched bird stays put until the whole flock takes off. (The landing delay only staggers
            // the descent; mt restarts when the flock switches to SIT, so it mustn't apply here.)
            if (b.perched && (mode == Mode.LAND || mode == Mode.SIT)) {
                b.peck -= dt
                if (b.peck < -r() * 40f) { b.peck = 0.4f; if (r() < 0.3f) b.face = -b.face }
                continue
            }
            if (mode == Mode.LAND && mt > b.delay) {
                if (b.perched) {
                    b.peck -= dt
                    if (b.peck < -r() * 40f) { b.peck = 0.4f; if (r() < 0.3f) b.face = -b.face }
                    continue
                }
                steer(b, b.sx, b.sy - k, 220f, 3f, dt)
                // Close in: flutter down onto the spot rather than circling it.
                val d = hypot(b.sx - b.x, b.sy - k - b.y)
                if (d < 14f * k) { val e = min(1f, dt * 6f); b.x += (b.sx - b.x) * e; b.y += (b.sy - k - b.y) * e }
                if (d < 2.5f * k && mt > 1f) { b.perched = true; b.x = b.sx; b.y = b.sy - k; b.vx = 0f; b.vy = 0f }
                continue
            }
            val wob = sin(mt * 1.3f + i) * 8f * k
            val tx = cx + b.ox * spread + cos(ang * 2 + i) * wob
            val ty = cy + b.oy * spread * 0.55f + sin(ang * 2 + i * 0.7f) * wob
            steer(b, tx, ty, if (mode == Mode.LEAVE) 260f else 190f, 1.8f, dt)
        }
        if (mode == Mode.LAND && birds.all { it.perched }) { mode = Mode.SIT; mt = 0f }
        if (mode == Mode.SIT && mt > SECONDS_ON_ROOFS) startle()
    }

    fun draw(c: Canvas) {
        if (mode == Mode.NONE) return
        p.style = Paint.Style.FILL
        for (b in birds) if (b.perched) sitting(c, b) else flying(c, b)
    }

    private val s get() = 2.2f * k * SIZE

    private fun flying(c: Canvas, b: Bird) {
        val s = s
        val a = atan2(b.vy, b.vx)
        val up = sin(b.ph) * s * 2.6f
        val dir = if (b.vx >= 0f) 1f else -1f
        c.save()
        c.translate(b.x, b.y)
        c.scale(dir, 1f)
        c.rotate(Math.toDegrees((if (dir > 0) a * 0.4f else -(PI.toFloat() - a) * 0.4f).toDouble()).toFloat())
        p.color = b.col
        c.drawOval(-s * 1.4f, -s * 0.55f, s * 1.4f, s * 0.55f, p)
        c.drawCircle(s * 1.3f, -s * 0.3f, s * 0.45f, p)
        p.color = 0xE6282837.toInt()
        tri(c, -s * 1.3f, 0f, -s * 2.2f, -s * 0.3f, -s * 2.2f, s * 0.4f)
        p.color = b.col
        for (kk in floatArrayOf(1f, -0.6f)) {
            path.reset(); path.moveTo(s * 0.5f, 0f); path.lineTo(-s * 0.2f, -up * kk); path.lineTo(-s * 1.1f, -up * kk * 0.8f); path.lineTo(-s * 0.6f, 0f); path.close()
            c.drawPath(path, p)
        }
        p.color = 0xFF3A3A48.toInt()
        tri(c, -s * 0.2f, -up, -s * 1.1f, -up * 0.8f, -s * 0.5f, -up * 0.85f) // dark wingtip
        c.restore()
    }

    private fun sitting(c: Canvas, b: Bird) {
        val s = s
        val pk = if (b.peck > 0f) sin((0.4f - b.peck) / 0.4f * PI.toFloat()) else 0f
        c.save()
        c.translate(b.x, b.y)
        c.scale(b.face, 1f)
        p.color = b.col
        c.save(); c.rotate(Math.toDegrees(-0.15).toFloat(), 0f, -s * 0.9f)
        c.drawOval(-s * 1.25f, -s * 0.9f - s * 0.85f, s * 1.25f, -s * 0.9f + s * 0.85f, p)
        c.restore()
        c.drawCircle(s * 1.05f, -s * 1.9f + pk * s * 1.2f, s * 0.55f, p)
        p.color = 0xFF5AA48A.toInt() // green neck sheen
        c.drawRect(s * 0.5f, -s * 1.5f + pk * s * 0.6f, s * 1.1f, -s * 1.1f + pk * s * 0.6f, p)
        p.color = 0xFF3A3A48.toInt()
        tri(c, -s * 1.1f, -s * 0.8f, -s * 2f, -s * 0.5f, -s, -s * 0.4f)
        p.color = 0xFFE07060.toInt() // feet
        c.drawRect(-s * 0.1f, -s * 0.1f, s * 0.15f, s * 0.2f, p)
        c.restore()
    }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, p)
    }

    companion object {
        const val COUNT = 75
        const val ROOFTOPS = 5
        const val SPACING = 1f
        const val SIZE = 1f
        const val SECONDS_ON_ROOFS = 35f
    }
}
