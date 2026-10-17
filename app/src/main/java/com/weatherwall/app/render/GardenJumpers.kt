package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bloukrans' jumpers, from the platform just off the near side of the arch's crown:
 *
 * - **Bungy**: stands on the edge, swan-dives out, falls with the cord trailing slack, bounces on
 *   it (a spring with damping, sized so the first stretch stays on screen), swings and spins to a
 *   stop hanging upside down, then is winched back up.
 * - **BASE**: exits, freefalls a few seconds, throws out a ram-air canopy that snaps open, then
 *   glides down the gorge in gentle banking S-turns to the beach at the head of the inlet.
 *
 * People are drawn in metres from their feet (bungy) or harness (BASE), scaled by distance and a
 * little larger than life so they read on a phone.
 */
class GardenJumpers(
    private val foc: Float,
    private val cxb: Float,
    private val hz: Float,
    private val k: Float,
    /** The lowest screen y (landscape frame) anyone may reach. */
    private val bottom: Float,
    /** Where the BASE jumpers land (screen, landscape frame): a little beach at sea level, 200 m down. */
    private val beachX: Float,
    private val beachY: Float,
    /** Projects bridge metres (X along the span, Y down, D across) to screen x, y and depth z. */
    private val project: (Float, Float, Float, FloatArray) -> Unit,
) {
    var night = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val o = FloatArray(3)
    private val rnd = Random(System.nanoTime())
    private var time = 0f

    // The jump point, just off the platform's outer edge.
    private val jx = 1.5f
    private val jy = -6.8f
    private val jd = -7.2f
    private val ex = 1.6f // people a little larger than life

    // ---- Bungy -----------------------------------------------------------------------------------

    private enum class B { OFF, STAND, FALL, HANG, WINCH }
    private var b = B.OFF
    private var bt = 0f
    private var drop = 0f     // metres below the jump point
    private var vel = 0f
    private var slack = 30f   // unstretched cord length
    private var spring = 1f   // stiffness per kg
    private var swing = 0f
    private var bounces = 0
    private var wasUp = false
    private var restFor = 0f
    private var clothes = 0
    val bungyActive get() = b != B.OFF

    /** Fits the cord to the screen: the first stretch bottoms out just above the lowest allowed point. */
    private fun fitCord() {
        project(jx, jy, jd, o)
        val pxPerM = foc / o[2]
        val maxDrop = ((bottom - 30f * k - o[1]) / pxPerM - 2.2f * ex).coerceIn(18f, 160f)
        slack = maxDrop * 0.45f
        val stretch = maxDrop - slack
        spring = 2f * 9.81f * maxDrop / (stretch * stretch)
    }

    fun launchBungy() {
        if (b != B.OFF || base != S.OFF) return
        fitCord(); b = B.STAND; bt = 0f; drop = 0f; vel = 0f; swing = 0f; bounces = 0; wasUp = false; restFor = 0f
        clothes = rnd.nextInt(CLOTHES.size)
    }

    private fun updateBungy(dt: Float) {
        bt += dt
        when (b) {
            B.STAND -> if (bt > 2.6f) { b = B.FALL; bt = 0f; vel = 1.5f }
            B.FALL -> {
                // Free fall, then the cord: a spring with damping once it's past its slack length.
                var left = dt
                while (left > 0f) {
                    val h = min(left, 1f / 120f); left -= h
                    val ext = drop - slack
                    var a = 9.81f - 0.004f * vel * abs(vel)
                    if (ext > 0f) a -= spring * ext + 0.22f * vel
                    vel += a * h; drop += vel * h
                }
                val up = vel < 0f
                if (up && !wasUp) bounces++
                wasUp = up
                swing += dt
                restFor = if (abs(vel) < 0.8f && drop > slack) restFor + dt else 0f
                if (restFor > 1.5f || bt > 26f) { b = B.HANG; bt = 0f }
            }
            B.HANG -> { swing += dt; drop += ((slack + 9.81f / spring) - drop) * min(1f, dt); if (bt > 4f) { b = B.WINCH; bt = 0f } }
            B.WINCH -> { swing += dt; drop -= 2.2f * dt; if (drop <= 0f) { b = B.OFF } }
            B.OFF -> Unit
        }
    }

    // ---- BASE ------------------------------------------------------------------------------------

    private enum class S { OFF, STAND, FREEFALL, OPEN, GLIDE, LANDED }
    private var base = S.OFF
    private var st = 0f
    private val s0 = FloatArray(3)  // camera-space start (exit point)
    private val s1 = FloatArray(3)  // where the canopy opens
    private val e = FloatArray(3)   // the beach
    private val pos = FloatArray(3)
    private var bank = 0f
    private var canopyCol = 0
    private val glideTime = 42f
    val baseActive get() = base != S.OFF

    private fun toCamera(x: Float, y: Float, z: Float, out: FloatArray) { out[0] = (x - cxb) * z / foc; out[1] = (y - hz) * z / foc; out[2] = z }

    fun launchBase() {
        if (base != S.OFF || b != B.OFF) return
        project(jx, jy, jd, o); toCamera(o[0], o[1], o[2], s0)
        s1[0] = s0[0] - 2f; s1[1] = s0[1] + 42f; s1[2] = s0[2] + 6f
        val zb = foc * 200f / max(1f, beachY - hz)
        toCamera(beachX, beachY, zb, e); e[1] -= 1f
        base = S.STAND; st = 0f; canopyCol = rnd.nextInt(CANOPY.size); clothes = rnd.nextInt(CLOTHES.size)
    }

    private fun updateBase(dt: Float) {
        st += dt
        when (base) {
            S.STAND -> if (st > 2.2f) { base = S.FREEFALL; st = 0f }
            S.FREEFALL -> if (st > 2.9f) { base = S.OPEN; st = 0f }
            S.OPEN -> if (st > 1.4f) { base = S.GLIDE; st = 0f }
            S.GLIDE -> if (st > glideTime) { base = S.LANDED; st = 0f }
            S.LANDED -> if (st > 4f) base = S.OFF
            S.OFF -> Unit
        }
        // Where the jumper is, in camera space (metres: x right, y down, z away).
        when (base) {
            S.STAND -> { pos[0] = s0[0]; pos[1] = s0[1]; pos[2] = s0[2] }
            S.FREEFALL -> { val u = min(1f, st / 2.9f); pos[0] = s0[0] + (s1[0] - s0[0]) * u; pos[1] = s0[1] + (s1[1] - s0[1]) * u * u; pos[2] = s0[2] + (s1[2] - s0[2]) * u }
            S.OPEN -> { val u = st / 1.4f; pos[0] = s1[0]; pos[1] = s1[1] + 6f * u; pos[2] = s1[2] + 1f * u }
            S.GLIDE -> {
                val u = st / glideTime; val sm = u * u * (3f - 2f * u)
                val turn = sin(u * PI.toFloat() * 2.5f) * 22f * (1f - u)
                pos[0] = s1[0] + (e[0] - s1[0]) * sm + turn
                pos[1] = s1[1] + 6f + (e[1] - s1[1] - 6f) * u
                pos[2] = s1[2] + 1f + (e[2] - s1[2] - 1f) * sm
                bank = cos(u * PI.toFloat() * 2.5f) * 0.35f * (1f - u)
            }
            S.LANDED -> { pos[0] = e[0]; pos[1] = e[1]; pos[2] = e[2] }
            S.OFF -> Unit
        }
    }

    fun update(dt: Float) {
        time += dt
        if (b != B.OFF) updateBungy(dt)
        if (base != S.OFF) updateBase(dt)
    }

    // ---- Drawing -----------------------------------------------------------------------------------

    /** A stroke [w] wide in the canvas's current units (metres inside a person, pixels outside). */
    private fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = w; p.color = col; c.drawLine(x0, y0, x1, y1, p)
    }

    /**
     * A person in metres, feet at the origin, "up" towards the head along -y before [rot] (0 =
     * upright, PI = head down). Limb angles: arms from the body's up axis, legs apart.
     */
    private fun person(c: Canvas, x: Float, y: Float, sc: Float, rot: Float, armA: Float, armB: Float, legSpread: Float, kneeBend: Float) {
        val col = if (night) 0xFF1A1C22.toInt() else CLOTHES[clothes]
        val skin = if (night) 0xFF2A2A2E.toInt() else 0xFFC9946E.toInt()
        val legs = if (night) 0xFF14161A.toInt() else 0xFF2E3440.toInt()
        val harness = if (night) 0xFF30302A.toInt() else 0xFFE8C030.toInt()
        c.save(); c.translate(x, y); c.scale(sc, sc); c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        // Legs from the feet up to the hips (0, -0.95), knees bending.
        for (sgn in intArrayOf(-1, 1)) {
            val fx = sgn * legSpread * 0.5f
            val kx = fx * 0.6f + sgn * kneeBend * 0.15f; val ky = -0.5f
            line(c, fx, 0f, kx, ky, 0.17f, legs); line(c, kx, ky, sgn * 0.08f, -0.95f, 0.2f, legs)
        }
        line(c, 0f, -0.95f, 0f, -1.5f, 0.34f, col) // torso
        line(c, -0.1f, -0.95f, 0.1f, -0.95f, 0.22f, harness) // harness at the waist
        // Arms from the shoulders (0, -1.45): armA/armB are angles from the head direction.
        for ((i, a) in floatArrayOf(armA, armB).withIndex()) {
            val sgn = if (i == 0) -1f else 1f
            val ex1 = sin(a) * 0.32f * sgn; val ey1 = -1.45f - cos(a) * 0.32f
            val ex2 = sin(a * 1.1f) * 0.62f * sgn; val ey2 = -1.45f - cos(a * 1.1f) * 0.62f
            line(c, 0f, -1.45f, ex1, ey1, 0.13f, col); line(c, ex1, ey1, ex2, ey2, 0.11f, skin)
        }
        p.style = Paint.Style.FILL; p.color = skin; c.drawCircle(0f, -1.72f, 0.14f, p) // head
        p.color = if (night) 0xFF101114.toInt() else 0xFF3A2A22.toInt(); c.drawCircle(0f, -1.76f, 0.12f, p)
        c.restore()
    }

    private fun drawBungy(c: Canvas) {
        project(jx, jy, jd, o); val px = o[0]; val py = o[1]; val pz = o[2]
        val sc = foc / pz * ex
        // A crew member on the platform edge, steadying things.
        if (b == B.STAND || b == B.WINCH || b == B.HANG) person(c, px - 1.6f * sc, py, sc / ex * ex, 0f, 2.6f, 2.8f, 0.3f, 0f)
        val sway = when (b) {
            B.FALL, B.HANG, B.WINCH -> (sin(swing * 1.25f) * 2.2f * exp(-swing / 9f) + sin(swing * 0.4f) * 0.4f)
            else -> 0f
        }
        val fx = px + sway * sc / ex
        val fy = py + drop * foc / pz
        val cordCol = if (night) 0xFF8A7A50.toInt() else 0xFF22262C.toInt()
        // The cord: trailing slack in a loop above while it falls, straight and thinner when stretched.
        if (b == B.STAND) {
            path.reset(); path.moveTo(px, py); path.quadTo(px - 0.6f * sc, py + 1.4f * sc, px + 0.1f * sc, py + 0.05f * sc)
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1.2f, 0.12f * sc); p.color = cordCol; c.drawPath(path, p)
        } else if (drop < slack) {
            val low = py + ((slack + drop) / 2f) * foc / pz
            path.reset(); path.moveTo(px, py); path.quadTo(px + (fx - px) * 0.3f - 1.2f * sc, low, (px + fx) / 2f, low)
            path.quadTo(fx + 1.2f * sc, low, fx, fy)
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1.2f, 0.12f * sc); p.color = cordCol; c.drawPath(path, p)
        } else {
            val stretch = (drop / slack).coerceIn(1f, 2.2f)
            line(c, px, py, fx, fy, max(1f, 0.16f * sc / stretch), cordCol)
        }
        // The jumper, feet on the cord.
        when (b) {
            B.STAND -> { val crouch = ((bt - 1.6f) / 1f).coerceIn(0f, 1f); person(c, px + 0.15f * sc, py, sc, 0f, 1.6f + crouch * 0.6f, 1.6f + crouch * 0.6f, 0.25f, crouch) }
            B.FALL -> {
                // A swan dive rolling head-down, arms wide; flailing a little on the rebounds.
                val roll = (bt / 0.9f).coerceIn(0f, 1f)
                val upward = (-vel / 14f).coerceIn(0f, 1f)
                val rot = PI.toFloat() * roll - upward * 0.9f + sin(bt * 2.3f) * 0.08f
                val flail = if (bounces > 0) sin(bt * 7f) * 0.5f * upward else 0f
                person(c, fx, fy, sc, rot, 1.35f + flail, 1.45f - flail, 0.18f + upward * 0.25f, upward)
            }
            else -> person(c, fx, fy, sc, PI.toFloat() + sin(swing * 0.9f) * 0.12f, 0.35f + sin(swing * 1.7f) * 0.15f, 0.3f, 0.16f, 0f) // hanging, arms dangling
        }
    }

    private fun drawBase(c: Canvas) {
        val z = max(5f, pos[2])
        val sx = cxb + foc * pos[0] / z; val sy = hz + foc * pos[1] / z
        val sc = foc / z * ex
        when (base) {
            S.STAND -> person(c, sx, sy, sc, 0f, 0.4f, 0.4f, 0.25f, ((st - 1.6f) / 0.6f).coerceIn(0f, 1f))
            S.FREEFALL -> person(c, sx, sy, sc, (PI / 2.4).toFloat() * min(1f, st / 0.8f), 2.0f, 2.0f, 0.4f, 0.3f) // stable, arched, tracking out
            S.OPEN, S.GLIDE, S.LANDED -> {
                val open = if (base == S.OPEN) (st / 1.2f).coerceIn(0f, 1f) else 1f
                val snap = if (base == S.OPEN && st < 1.4f) sin(st / 1.4f * PI.toFloat()) * 0.12f else 0f
                val collapse = if (base == S.LANDED) (st / 2.5f).coerceIn(0f, 1f) else 0f
                c.save(); c.translate(sx, sy); c.rotate(Math.toDegrees(bank.toDouble()).toFloat())
                // Lines up to a ram-air canopy (cells, a darker underside), 4.5 m above the harness.
                val span = 8.5f * open * (1f + snap) * (1f - collapse * 0.6f) * sc / ex * 1.25f
                val hgt = 4.6f * sc / ex * 1.25f * (1f - collapse * 0.8f)
                val top = -hgt - 1.4f * sc / ex * 1.25f
                p.style = Paint.Style.STROKE; p.strokeWidth = max(0.8f, 0.03f * sc); p.color = withAlpha(if (night) 0xFF606060.toInt() else 0xFF505560.toInt(), 200)
                for (i in -3..3) c.drawLine(0f, -1.4f * sc, i / 3f * span / 2f, top + hgt * 0.15f + abs(i) * hgt * 0.04f, p)
                if (open > 0.05f) {
                    path.reset()
                    path.moveTo(-span / 2f, top + hgt * 0.22f)
                    path.quadTo(0f, top - hgt * 0.32f, span / 2f, top + hgt * 0.22f)
                    path.lineTo(span / 2f, top + hgt * 0.36f)
                    path.quadTo(0f, top - hgt * 0.12f, -span / 2f, top + hgt * 0.36f)
                    path.close()
                    val col = if (night) 0xFF30303A.toInt() else CANOPY[canopyCol]
                    p.style = Paint.Style.FILL; p.color = withAlpha(col, (255 * (1f - collapse * 0.7f)).toInt()); c.drawPath(path, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = max(0.8f, 0.04f * sc); p.color = mix(col, Color.BLACK, 0.3f)
                    for (i in -3..3) { val cx = i / 3.5f * span / 2f; val arch = (1f - (i / 3.5f) * (i / 3.5f)); c.drawLine(cx, top + hgt * 0.36f - arch * hgt * 0.47f, cx, top + hgt * 0.22f - arch * hgt * 0.53f + hgt * 0.13f, p) }
                }
                c.restore()
                person(c, sx, sy + (if (base == S.LANDED) 0f else 0.2f * sc), sc, 0f, 2.6f, 2.6f, 0.35f, if (base == S.LANDED) 0f else 0.9f) // hands up on the toggles, legs forward
            }
            S.OFF -> Unit
        }
    }

    fun draw(c: Canvas) {
        if (b != B.OFF) drawBungy(c)
        if (base != S.OFF) drawBase(c)
    }

    private companion object {
        val CLOTHES = intArrayOf(0xFFD8402E.toInt(), 0xFF2E6FB8.toInt(), 0xFF3A9A5A.toInt(), 0xFFE8A030.toInt(), 0xFF6A3A9A.toInt(), 0xFF202428.toInt())
        val CANOPY = intArrayOf(0xFFE53A2E.toInt(), 0xFFF2C230.toInt(), 0xFF2A7BD6.toInt(), 0xFF2EB86A.toInt(), 0xFFF06A1E.toInt(), 0xFF8A3AD0.toInt())
    }
}
