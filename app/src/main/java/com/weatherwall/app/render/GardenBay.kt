package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Life in the Garden Route bay, seen from high in the gorge: southern right whales, a dolphin pod,
 * a whale-watching catamaran, a ski-boat, yachts, a jetski and ships on the horizon.
 *
 * Positions are a screen x and a fixed water line y (in the landscape's own frame). Size comes
 * from how far away that line lies: about (y - [hz]) / 200 preview px per metre, scaled up by
 * each kind's exaggeration so it reads on a phone. On the water plane, depth is foreshortened by
 * (y - hz) / [foc] against width, which shapes the wakes and splashes.
 *
 * Models are drawn in metres (x forward, y down, waterline at 0) and scaled.
 */
class GardenBay(
    private val w: Float,
    private val hz: Float,
    private val k: Float,
    private val foc: Float,
    /** Soft white (or smoke-dark) puff: canvas, x, y, radius, alpha, darkness. */
    private val puff: (Canvas, Float, Float, Float, Float, Float) -> Unit,
) {
    var night = false
    var twilight = 0f
    var seaColour = 0xFF2A7BBD.toInt()
    var hazeColour = 0xFFC7DDE6.toInt()
    var daylightVisitors = true
    private var time = 0f
    private val rnd = Random(System.nanoTime())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun ppm(y: Float, ex: Float) = max(0.05f, (y - hz) / (200f * k)) * ex * k
    private fun fs(y: Float) = (y - hz) / foc
    private fun fill(c: Int) { p.style = Paint.Style.FILL; p.shader = null; p.color = c }

    private fun shape(pts: FloatArray, dst: Path = path): Path {
        dst.reset(); dst.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size - 3) { dst.quadTo(pts[i], pts[i + 1], (pts[i] + pts[i + 2]) / 2f, (pts[i + 1] + pts[i + 3]) / 2f); i += 2 }
        dst.lineTo(pts[pts.size - 2], pts[pts.size - 1]); dst.close()
        return dst
    }

    // ---- Wakes -------------------------------------------------------------------------------------

    /**
     * A Kelvin wake behind a stern at (x, y): two foam arms spreading at about 19.5° on the water
     * (foreshortened on screen), white churn close behind, fading with distance.
     */
    private fun wake(c: Canvas, x: Float, y: Float, dir: Float, s: Float, len: Float, power: Float) {
        val f = fs(y)
        val n = 18
        p.style = Paint.Style.FILL; p.shader = null
        for (i in 1..n) {
            val u = i / n.toFloat(); val d = u * len
            val fade = (1f - u).pow(1.3f) * power
            val px = x - dir * d * s
            val lat = 0.36f * d * s * f * 2.2f
            val jitter = sin(time * 3f + i * 1.7f) * 0.2f * s
            val wd = (0.5f + d * 0.06f) * s
            p.color = withAlpha(Color.WHITE, (fade * 200).toInt())
            c.drawOval(px - wd, y - lat - wd * 0.18f + jitter * f, px + wd * 0.4f, y - lat + wd * 0.18f + jitter * f, p)
            c.drawOval(px - wd, y + lat - wd * 0.22f - jitter * f, px + wd * 0.4f, y + lat + wd * 0.22f - jitter * f, p)
            if (u < 0.45f) puff(c, px, y + sin(time * 4f + i) * 0.15f * s * f, (0.7f + d * 0.08f) * s, fade * 0.55f, 0f) // churned water
        }
        // Faint transverse waves between the arms.
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.15f * s)
        for (i in 1..4) {
            val d = (i + (time * 0.6f) % 1f) / 4.5f * len; val lat = 0.36f * d * s * f * 2.2f
            p.color = withAlpha(Color.WHITE, ((1f - d / len) * 70 * power).toInt())
            c.drawLine(x - dir * d * s, y - lat * 0.8f, x - dir * d * s, y + lat * 0.8f, p)
        }
        p.style = Paint.Style.FILL
    }

    /** The hull's shimmering reflection under it, broken up by the ripples. */
    private fun reflection(c: Canvas, hull: Path, s: Float, col: Int) {
        c.save(); c.scale(1f, -0.6f)
        p.style = Paint.Style.FILL; p.shader = null
        p.color = withAlpha(mix(col, seaColour, 0.55f), 90)
        c.drawPath(hull, p)
        c.restore()
        p.color = withAlpha(seaColour, 120)
        var yy = 0.2f * s
        while (yy < 1.6f * s) { c.drawRect(-20f * s, yy, 20f * s, yy + 0.12f * s, p); yy += 0.35f * s }
    }

    // ---- Southern right whales -----------------------------------------------------------------------

    private class Whale(var x: Float, val y: Float, var dir: Float, var t: Float, var cycle: Int)
    private val whales = listOf(Whale(w * 0.3f, hz + 120f * k, 1f, 2f, 0), Whale(w * 0.62f, hz + 195f * k, -1f, 9f, 1))
    private val cycleLen = 18f

    // Side profile in metres, 15 m long, head to the right, waterline through the middle: the
    // arched rostrum and bowed jaw, a broad finless back, rotund belly, tail stock.
    private val whaleBody = floatArrayOf(
        7.6f, -0.2f, 7.1f, -1.2f, 5.9f, -2.0f, 4.4f, -2.35f, 1.6f, -2.6f, -1.6f, -2.45f, -4.2f, -1.8f, -6.4f, -0.95f, -7.6f, -0.45f,
        -7.6f, 0.25f, -5.6f, 1.15f, -2.2f, 2.3f, 1.2f, 2.6f, 4.0f, 2.25f, 6.0f, 1.3f, 7.3f, 0.4f,
    )
    private val bodyPath = Path()
    private val fluke = Path()
    private fun calloLight() = if (night) 0xFF6A7080.toInt() else 0xFFE9E3D2.toInt()
    private fun whaleDark() = if (night) 0xFF0B0F15.toInt() else 0xFF1A1F26.toInt()

    /** The whole whale in side view, in metres, at the canvas origin (head toward +x). */
    private fun drawWhaleBody(c: Canvas, s: Float, sheen: Float) {
        shape(whaleBody, bodyPath)
        val dk = whaleDark()
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, -2.6f, 0f, 2.6f, intArrayOf(mix(dk, Color.WHITE, 0.16f * sheen), dk, mix(dk, Color.BLACK, 0.4f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(bodyPath, p); p.shader = null
        // Pale belly blaze and the bowed jaw line.
        fill(withAlpha(if (night) 0xFF4A5060.toInt() else 0xFFD8D4CC.toInt(), 200)); c.drawOval(-2.6f, 1.2f, 1.6f, 2.35f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.12f; p.color = mix(dk, Color.BLACK, 0.5f)
        path.reset(); path.moveTo(7.3f, 0.1f); path.quadTo(6.0f, -1.1f, 3.6f, 0.6f); c.drawPath(path, p)
        // Callosities: rough pale patches on the rostrum, lip and above the eye.
        fill(calloLight())
        for ((cx, cy, r) in listOf(Triple(7.0f, -1.05f, 0.34f), Triple(6.4f, -1.55f, 0.3f), Triple(5.7f, -1.9f, 0.26f), Triple(6.6f, 0.8f, 0.28f), Triple(4.2f, -0.35f, 0.22f), Triple(5.1f, -2.1f, 0.2f))) {
            c.drawCircle(cx, cy, r, p); c.drawCircle(cx + r * 0.6f, cy + r * 0.3f, r * 0.6f, p); c.drawCircle(cx - r * 0.5f, cy + r * 0.4f, r * 0.5f, p)
        }
        fill(0xFF050608.toInt()); c.drawCircle(4.0f, -0.1f, 0.12f, p) // eye
        // Paddle flipper.
        fill(mix(dk, Color.BLACK, 0.2f)); path.reset(); path.moveTo(3.4f, 1.2f); path.quadTo(1.4f, 2.6f, 1.8f, 3.6f); path.quadTo(3.2f, 3.2f, 4.0f, 1.6f); path.close(); c.drawPath(path, p)
        // Flukes seen edge-on.
        fill(dk); path.reset(); path.moveTo(-7.4f, -0.4f); path.quadTo(-9.2f, -1.4f, -10f, -1.1f); path.quadTo(-9.2f, -0.2f, -10f, 0.7f); path.quadTo(-9f, 0.6f, -7.4f, 0.25f); path.close(); c.drawPath(path, p)
        if (sheen > 0f) { // wet light along the back
            p.style = Paint.Style.STROKE; p.strokeWidth = 0.18f; p.color = withAlpha(Color.WHITE, (90 * sheen).toInt())
            path.reset(); path.moveTo(5.5f, -2.05f); path.quadTo(1.5f, -2.75f, -3.5f, -2.05f); c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun hash(i: Int): Float { val v = sin(i * 12.9898f) * 43758.547f; return v - kotlin.math.floor(v) }

    /**
     * A breach's splash [tt] seconds after the body hits the water: droplets flung out on real
     * ballistic arcs, churned white water low down, and a thin ring of foam spreading out.
     */
    private fun splash(c: Canvas, x: Float, y: Float, s: Float, f: Float, tt: Float) {
        val fade = (1f - tt / 3.6f).coerceIn(0f, 1f)
        if (fade <= 0f) return
        for (i in 0 until 46) {
            val vx = (hash(i) - 0.5f) * 10f; val vy = 3f + hash(i + 97) * 8f
            val hgt = vy * tt - 4.9f * tt * tt
            if (hgt < 0f) continue
            fill(withAlpha(Color.WHITE, (230 * fade * (0.6f + 0.4f * hash(i + 31))).toInt()))
            c.drawCircle(x + vx * tt * s, y - hgt * s, (0.14f + hash(i + 13) * 0.22f) * s, p)
        }
        for (i in 0 until 6) puff(c, x + (hash(i + 50) - 0.5f) * 7f * s * (0.6f + tt * 0.3f), y - (0.4f + hash(i + 70) * 1.2f) * s * fade, (1f + tt * 0.4f + hash(i + 60)) * s, 0.6f * fade, 0f)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.18f * s); p.color = withAlpha(Color.WHITE, (110 * fade).toInt())
        val ring = 3.5f + tt * 3.2f
        c.drawOval(x - ring * s, y - ring * s * f * 2f, x + ring * s, y + ring * s * f * 2f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawWhales(c: Canvas) {
        for (wh in whales) {
            val s = ppm(wh.y, WHALE_EX); val u = wh.t % cycleLen; val f = fs(wh.y)
            val breach = wh.cycle % 3 == 2
            when {
                breach && u < 3.4f -> { // heaving two-thirds out at a steep angle, water pouring off, then crashing back
                    val kk = u / 3.4f; val rise = sin(kk * PI.toFloat())
                    val ang = (62f - kk * 95f) * wh.dir
                    c.save(); c.clipRect(wh.x - 30f * s, wh.y - 30f * s, wh.x + 30f * s, wh.y)
                    c.translate(wh.x, wh.y + (1f - rise) * 6f * s); c.scale(wh.dir * s, s); c.rotate(-ang * wh.dir)
                    c.translate(-1f + rise * 2.5f, 0f)
                    if (kk < 0.84f) drawWhaleBody(c, 1f, 1f) // then it's under the splash
                    c.restore()
                    p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.15f * s) // sheets of water streaming off
                    for (i in 0 until 9) {
                        val sx = wh.x + wh.dir * (i - 3) * 0.9f * s; val top = wh.y - rise * (3f + (i % 4)) * s
                        p.color = withAlpha(Color.WHITE, (150 * rise).toInt()); c.drawLine(sx, top, sx + wh.dir * 0.2f * s, wh.y, p)
                    }
                    p.style = Paint.Style.FILL
                    for (i in 0 until 6) puff(c, wh.x + (i - 2.5f) * 1.3f * s, wh.y - (0.3f + (i % 3) * 0.4f) * s, (1.1f + (i % 2) * 0.5f) * s, 0.7f, 0f) // white water round its base
                    if (kk > 0.72f) splash(c, wh.x, wh.y, s, f, (kk - 0.72f) * 3.4f)
                }
                breach && u < 7f -> { // the splash collapsing, a broad white slick spreading
                    val sp = (u - 3.4f) / 3.6f
                    splash(c, wh.x, wh.y, s, f, 0.95f + (u - 3.4f))
                }
                u > 6f && u < 12f -> { // the broad back rolling at the surface, then the V-shaped blow
                    val kk = (u - 6f) / 6f; val up = sin(kk * PI.toFloat()).pow(0.7f)
                    c.save(); c.clipRect(wh.x - 20f * s, wh.y - 10f * s, wh.x + 20f * s, wh.y)
                    c.translate(wh.x, wh.y + (2.6f - up * 2.5f) * s); c.scale(wh.dir * s, s)
                    drawWhaleBody(c, 1f, 1f)
                    c.restore()
                    // Water sheeting off the back and a foam collar at the waterline.
                    fill(withAlpha(Color.WHITE, (130 * up).toInt()))
                    c.drawOval(wh.x - 8f * s, wh.y - 0.35f * s, wh.x + 8f * s, wh.y + 0.5f * s + 1.6f * s * f, p)
                    puff(c, wh.x + wh.dir * 7f * s, wh.y - 0.2f * s, 1.4f * s, 0.6f * up, 0f)
                    puff(c, wh.x - wh.dir * 6.5f * s, wh.y - 0.1f * s, 1.2f * s, 0.5f * up, 0f)
                    if (kk in 0.1f..0.45f) { // two bushy plumes from the paired blowholes
                        val bk = (kk - 0.1f) / 0.35f
                        for (side in intArrayOf(-1, 1)) for (j in 0..2) {
                            val hgt = (1f + bk * 5.5f) * (0.5f + j * 0.25f)
                            puff(c, wh.x + wh.dir * 4.4f * s + side * hgt * 0.42f * s, wh.y - (0.8f + hgt) * s, (0.7f + bk * 1.8f) * (0.7f + j * 0.2f) * s, 0.75f * (1f - bk * 0.7f), 0f)
                        }
                    }
                }
                u > 12f && u < 15f -> { // flukes up as it dives, water streaming from the trailing edge
                    val kk = (u - 12f) / 3f; val up = sin(kk * PI.toFloat())
                    c.save(); c.translate(wh.x - wh.dir * 3f * s, wh.y); c.scale(s, s)
                    val dk = whaleDark()
                    fill(dk); path.reset(); path.moveTo(-0.7f, 0f); path.quadTo(-0.55f, -up * 2.5f, -0.35f, -up * 3.2f); path.lineTo(0.35f, -up * 3.2f); path.quadTo(0.55f, -up * 2.5f, 0.7f, 0f); path.close(); c.drawPath(path, p)
                    val ty = -up * 3.2f
                    fluke.reset(); fluke.moveTo(0f, ty + 0.2f)
                    fluke.cubicTo(-1.4f, ty - 0.4f, -2.8f, ty - 1.4f, -2.9f, ty - 1.9f)
                    fluke.quadTo(-1.6f, ty - 1.2f, -0.2f, ty - 1.1f); fluke.lineTo(0f, ty - 0.8f); fluke.lineTo(0.2f, ty - 1.1f)
                    fluke.quadTo(1.6f, ty - 1.2f, 2.9f, ty - 1.9f); fluke.cubicTo(2.8f, ty - 1.4f, 1.4f, ty - 0.4f, 0f, ty + 0.2f); fluke.close()
                    p.color = Color.BLACK; p.shader = LinearGradient(0f, ty - 2f, 0f, ty + 0.3f, mix(dk, Color.WHITE, 0.15f), dk, Shader.TileMode.CLAMP)
                    c.drawPath(fluke, p); p.shader = null
                    fill(withAlpha(calloLight(), 140)); c.drawCircle(-1.6f, ty - 1.2f, 0.18f, p); c.drawCircle(1.3f, ty - 1.05f, 0.14f, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = 0.08f // water pouring off the trailing edge
                    for (i in -5..5) {
                        val fx = i * 0.5f; val drip = ((time * 2.4f + i * 0.37f) % 1f)
                        p.color = withAlpha(Color.WHITE, (180 * up * (1f - drip)).toInt())
                        c.drawLine(fx, ty - 1.3f + abs(fx) * -0.12f, fx, ty - 1.3f + drip * (-ty + 1.3f), p)
                    }
                    c.restore()
                    fill(withAlpha(Color.WHITE, (120 * up).toInt())); c.drawOval(wh.x - wh.dir * 3f * s - 2.5f * s, wh.y - 0.4f * s, wh.x - wh.dir * 3f * s + 2.5f * s, wh.y + 0.6f * s + s * f, p)
                }
                u >= 15f -> { // the smooth "footprint" it leaves on the water
                    val kk = (u - 15f) / 3f
                    val fx = wh.x - wh.dir * 3f * s
                    for (j in 0 until 4) { // smooth, glassy patches where the dive calmed the ripples
                        val ox = (j - 1.5f) * 2.6f * s * (0.7f + kk); val r0 = (2.2f + (j % 2) * 1.2f) * s * (0.7f + kk)
                        fill(withAlpha(mix(seaColour, Color.WHITE, 0.25f), (45 * (1f - kk)).toInt()))
                        c.drawOval(fx + ox - r0, wh.y - r0 * f * 2f, fx + ox + r0, wh.y + r0 * f * 2f, p)
                    }
                }
            }
        }
    }

    // ---- Dolphins --------------------------------------------------------------------------------------

    private val dolphinBody = floatArrayOf(
        1.3f, 0.02f, 1.05f, -0.1f, 0.85f, -0.27f, 0.3f, -0.34f, -0.5f, -0.24f, -1.05f, -0.07f, -1.25f, -0.24f, -1.18f, 0.0f, -1.25f, 0.2f,
        -1.0f, 0.06f, -0.3f, 0.2f, 0.5f, 0.24f, 1.0f, 0.1f,
    )
    private val dPath = Path()
    private var podX = w * 0.2f
    private val podY = hz + 205f * k
    private var podDir = 1f
    private var podT = 0f
    var podFor = 0f
    private var podIn = 10f + rnd.nextFloat() * 60f

    private fun drawDolphin(c: Canvas) {
        shape(dolphinBody, dPath)
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, -0.34f, 0f, 0.24f, intArrayOf(0xFF2E3742.toInt(), 0xFF46525F.toInt(), 0xFFB8BEC6.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(dPath, p); p.shader = null
        fill(0xFF2A323C.toInt()); path.reset(); path.moveTo(0.05f, -0.32f); path.quadTo(-0.05f, -0.62f, -0.32f, -0.72f); path.quadTo(-0.22f, -0.5f, -0.3f, -0.27f); path.close(); c.drawPath(path, p) // dorsal fin
        fill(0xFF333C47.toInt()); path.reset(); path.moveTo(0.55f, 0.15f); path.quadTo(0.35f, 0.42f, 0.15f, 0.48f); path.quadTo(0.3f, 0.28f, 0.35f, 0.15f); path.close(); c.drawPath(path, p) // flipper
        fill(0xFF0A0C10.toInt()); c.drawCircle(0.86f, -0.06f, 0.035f, p)
    }

    private fun drawPod(c: Canvas) {
        if (podFor <= 0f) return
        val s = ppm(podY, 8f); val f = fs(podY)
        for (i in 0 until 6) {
            val ph = (podT * 0.8f + i * 0.37f) % 1.6f
            val dx = podX - podDir * i * 3.2f * s; val dyy = podY + (i % 2) * 2.2f * s * f * 3f
            if (ph >= 1f) { // just under: a dark shape and a ripple
                fill(withAlpha(mix(seaColour, Color.BLACK, 0.4f), 60)); c.drawOval(dx - 1.2f * s, dyy - 0.2f * s, dx + 1.2f * s, dyy + 0.2f * s, p)
                continue
            }
            val hx = dx + podDir * (ph - 0.5f) * 5f * s; val hy = dyy - sin(PI.toFloat() * ph) * 2.2f * s
            c.save(); c.clipRect(hx - 4f * s, hy - 4f * s, hx + 4f * s, dyy)
            c.translate(hx, hy); c.scale(podDir * s, s); c.rotate(-(0.5f - ph) * 95f)
            drawDolphin(c)
            c.restore()
            if (ph < 0.14f || ph > 0.86f) { // splash where it breaks the surface
                val e = if (ph < 0.14f) 1f - ph / 0.14f else (ph - 0.86f) / 0.14f
                puff(c, hx, dyy - 0.3f * s, (0.6f + e * 0.6f) * s, 0.75f * e, 0f)
                fill(withAlpha(Color.WHITE, (160 * e).toInt())); c.drawOval(hx - 1.1f * s, dyy - 0.2f * s, hx + 1.1f * s, dyy + 0.25f * s + 0.6f * s * f, p)
            }
        }
    }

    // ---- Boats ------------------------------------------------------------------------------------------

    private class Boat(val kind: Int, var x: Float, val y: Float, var dir: Float, val v: Float, var t: Float = 0f)
    private val boats = listOf(
        Boat(CAT, w * 0.6f, hz + 128f * k, -1f, 2.2f), Boat(SKI, w * 0.1f, hz + 180f * k, 1f, 6f),
        Boat(YACHT, w * 0.45f, hz + 70f * k, 1f, 1.6f), Boat(YACHT, w * 0.85f, hz + 95f * k, -1f, 2f),
    )
    private val jetski = Boat(JETSKI, w * 0.5f, hz + 225f * k, 1f, 14f)
    var jetskiFor = 0f
    private var jetskiIn = 30f + rnd.nextFloat() * 120f
    private val hull = Path()

    private fun hullPath(len: Float, fb: Float, bowRake: Float): Path {
        // Side profile: a sheer line rising to a raked bow, a flat transom, keel curving up forward.
        hull.reset()
        hull.moveTo(-len / 2f, -fb)
        hull.quadTo(0f, -fb * 0.92f, len / 2f - bowRake * 0.2f, -fb * 1.25f)
        hull.lineTo(len / 2f + bowRake, -fb * 1.3f)
        hull.quadTo(len / 2f, -fb * 0.2f, len / 2f - bowRake * 1.5f, 0.15f)
        hull.lineTo(-len / 2f, 0.15f)
        hull.close()
        return hull
    }

    private fun drawHull(c: Canvas, len: Float, fb: Float, rake: Float, col: Int, boot: Int) {
        val hp = hullPath(len, fb, rake)
        reflection(c, hp, 1f, col)
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, -fb * 1.3f, 0f, 0.15f, mix(col, Color.WHITE, 0.15f), mix(col, Color.BLACK, 0.25f), Shader.TileMode.CLAMP)
        c.drawPath(hp, p); p.shader = null
        c.save(); c.clipPath(hp); fill(boot); c.drawRect(-len, -0.25f, len, 0.2f, p); c.restore() // boot stripe at the waterline
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.08f; p.color = mix(col, Color.BLACK, 0.35f)
        path.reset(); path.moveTo(-len / 2f, -fb + 0.1f); path.quadTo(0f, -fb * 0.92f + 0.1f, len / 2f, -fb * 1.2f + 0.1f); c.drawPath(path, p) // rubbing strake
        p.style = Paint.Style.FILL
    }

    private fun drawBoat(c: Canvas, b: Boat) {
        val ex = if (b.kind == JETSKI) 6f else 4.5f
        val s = ppm(b.y, ex)
        val y = if (b.kind == JETSKI) b.y + sin(b.t * 0.9f) * 12f * k else b.y
        val d = b.dir
        val bob = sin(time * 1.3f + b.x * 0.01f) * 0.08f
        val white = if (night) 0xFF3A4150.toInt() else 0xFFF2F2EE.toInt()
        when (b.kind) {
            CAT -> wake(c, b.x - d * 7f * s, y, d, s, 26f, 0.8f)
            SKI -> wake(c, b.x - d * 3.6f * s, y, d, s, 30f, 1f)
            YACHT -> wake(c, b.x - d * 4f * s, y, d, s, 12f, 0.45f)
            else -> wake(c, b.x - d * 1.4f * s, y, d, s, 22f, 1f)
        }
        c.save(); c.translate(b.x, y); c.scale(d * s, s); c.translate(0f, bob)
        when (b.kind) {
            CAT -> { // whale-watching catamaran: twin hulls (one shows), a cabin deck and an upper deck with people
                drawHull(c, 15f, 1.6f, 0.6f, white, 0xFF20304A.toInt())
                fill(mix(white, Color.BLACK, 0.06f)); c.drawRect(-5.5f, -3.6f, 4.5f, -1.6f, p)
                fill(if (night) 0xF2FFD68C.toInt() else 0xFF2A3644.toInt()); c.drawRect(-5f, -3.2f, 4f, -2.2f, p)
                if (!night) { fill(0x44FFFFFF); path.reset(); path.moveTo(-3f, -3.2f); path.lineTo(-2.2f, -3.2f); path.lineTo(-3.4f, -2.2f); path.lineTo(-4.2f, -2.2f); path.close(); c.drawPath(path, p) }
                fill(white); c.drawRect(-4.5f, -4.0f, 2.5f, -3.6f, p) // the upper deck
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.08f; p.color = mix(white, Color.BLACK, 0.4f); c.drawLine(-4.5f, -4.9f, 2.5f, -4.9f, p); p.style = Paint.Style.FILL
                val cols = intArrayOf(0xFFD14B3A.toInt(), 0xFF2B4C7E.toInt(), 0xFFE0B040.toInt(), 0xFF3A3A3A.toInt(), 0xFF4E8A4A.toInt())
                for (i in 0 until 6) { val px = -4f + i * 1.15f; fill(if (night) 0xFF1A1E26.toInt() else cols[i % cols.size]); c.drawRect(px - 0.22f, -4.85f, px + 0.22f, -4.0f, p); fill(if (night) 0xFF1A1E26.toInt() else 0xFFE0B894.toInt()); c.drawCircle(px, -5.1f, 0.22f, p) }
                fill(0xFF4A505A.toInt()); c.drawRect(-0.2f, -6.2f, 0.1f, -4.0f, p) // radar mast
            }
            SKI -> { // fishing ski-boat: a centre console, rod holders and a twin outboard
                drawHull(c, 7.5f, 1.1f, 0.5f, white, 0xFF2A5D9A.toInt())
                fill(mix(white, Color.BLACK, 0.08f)); c.drawRect(-0.6f, -2.3f, 0.9f, -1.0f, p)
                fill(0xFF2A3644.toInt()); c.drawRect(0.6f, -2.2f, 0.9f, -1.6f, p)
                fill(0xFF2B2F36.toInt()); c.drawCircle(-0.1f, -2.75f, 0.24f, p); c.drawRect(-0.35f, -2.5f, 0.15f, -1.1f, p) // skipper
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.06f; p.color = 0xFF2A2A2A.toInt()
                c.drawLine(-2.6f, -1.1f, -3.4f, -3.2f, p); c.drawLine(-2.2f, -1.1f, -2.6f, -3.4f, p); p.style = Paint.Style.FILL
                fill(0xFF1E2228.toInt()); c.drawRect(-4.2f, -1.6f, -3.6f, 0.2f, p)
                c.restore()
                puff(c, b.x - d * 4f * s, y - 0.2f * s, 0.9f * s, 0.7f, 0f) // prop wash
                return
            }
            YACHT -> { // a yacht heeling under main and genoa, sails curved and shaded
                c.rotate(-7f)
                drawHull(c, 10f, 1.1f, 0.8f, 0xFFFFFFFF.toInt(), 0xFF233A5E.toInt())
                fill(mix(0xFFFFFFFF.toInt(), Color.BLACK, 0.08f)); c.drawRect(-2.4f, -1.9f, 1.2f, -1.2f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.14f; p.color = 0xFF3A3E46.toInt(); c.drawLine(0f, -1.2f, 0f, -15f, p)
                p.strokeWidth = 0.1f; c.drawLine(0f, -2.3f, -4.2f, -2.1f, p) // boom
                p.strokeWidth = 0.04f; p.color = 0xFF6A6E76.toInt(); c.drawLine(0f, -15f, 5.1f, -1.4f, p); c.drawLine(0f, -15f, -4.6f, -1.3f, p) // stays
                val sail = if (twilight > 0.5f) 0xFFFFE3C2.toInt() else 0xFFF8F6F0.toInt()
                p.style = Paint.Style.FILL; p.color = Color.BLACK
                p.shader = LinearGradient(-4f, 0f, 0f, 0f, mix(sail, Color.rgb(120, 130, 145), 0.25f), sail, Shader.TileMode.CLAMP)
                path.reset(); path.moveTo(-0.15f, -14.6f); path.cubicTo(-1.8f, -10f, -3.6f, -5.5f, -4.1f, -2.4f); path.lineTo(-0.15f, -2.4f); path.close(); c.drawPath(path, p)
                p.shader = LinearGradient(0f, 0f, 4.5f, 0f, sail, mix(sail, Color.rgb(120, 130, 145), 0.3f), Shader.TileMode.CLAMP)
                path.reset(); path.moveTo(0.35f, -14.2f); path.cubicTo(2.6f, -9.5f, 4.3f, -5f, 4.9f, -1.6f); path.quadTo(2.6f, -1.2f, 0.35f, -1.5f); path.close(); c.drawPath(path, p)
                p.shader = null
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.05f; p.color = withAlpha(Color.BLACK, 40)
                for (i in 1..3) { path.reset(); path.moveTo(-0.15f, -2.4f - i * 3f); path.quadTo(-1.4f, -2.6f - i * 3f, -3.6f + i * 0.9f, -2.4f - i * 3.2f); c.drawPath(path, p) } // battens
                p.style = Paint.Style.FILL
            }
            else -> { // jetski, rider leaning in
                c.restore()
                for (i in 0 until 7) puff(c, b.x - d * (1f + i * 0.8f) * s, y - (1.1f + sin(i * 0.7f) * 1.4f) * s, (0.5f + i * 0.28f) * s, 0.6f - i * 0.07f, 0f) // rooster tail
                c.save(); c.translate(b.x, y); c.scale(d * s, s)
                drawHull(c, 3.2f, 0.7f, 0.3f, 0xFFE8E8EA.toInt(), 0xFF202024.toInt())
                fill(0xFFD6322A.toInt()); c.drawRect(-0.6f, -1.05f, 0.9f, -0.65f, p)
                fill(0xFF1B1E24.toInt()); path.reset(); path.moveTo(-0.6f, -0.9f); path.lineTo(-0.1f, -2.1f); path.lineTo(0.35f, -2.0f); path.lineTo(0.0f, -0.9f); path.close(); c.drawPath(path, p)
                c.drawCircle(0.1f, -2.4f, 0.3f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 0.18f; p.color = 0xFF1B1E24.toInt(); c.drawLine(0.1f, -1.8f, 0.9f, -1.3f, p); p.style = Paint.Style.FILL
            }
        }
        c.restore()
        if (b.kind == CAT) puff(c, b.x + d * 7.2f * s, y - 0.3f * s, 0.8f * s, 0.6f, 0f) // bow wave
        if (night) { // navigation lights: red to port, green to starboard, white masthead
            val r = max(1.5f * k, 0.4f * s)
            fill(if (d > 0f) 0xFF3CFF7A.toInt() else 0xFFFF3C3C.toInt()); c.drawCircle(b.x + d * 3f * s, y - 1.6f * s, r, p)
            fill(0xFFFFF6E0.toInt()); c.drawCircle(b.x, y - 5f * s, r, p)
        }
    }

    // ---- Ships on the horizon ---------------------------------------------------------------------------

    private class Ship(var x: Float, val y: Float, val dir: Float, val tanker: Boolean, val v: Float)
    private val ships = listOf(Ship(w * 0.25f, hz + 14f * k, 1f, false, 7.5f), Ship(w * 0.95f, hz + 24f * k, -1f, true, 6f))
    private val boxCols = intArrayOf(0xFFB8402E.toInt(), 0xFF2F5C8A.toInt(), 0xFFD9A441.toInt(), 0xFF5A7F45.toInt(), 0xFF8C8C88.toInt(), 0xFF7A3A2E.toInt(), 0xFFDDDDD6.toInt())

    private fun drawShips(c: Canvas) {
        for (sh in ships) {
            val s = ppm(sh.y, 4f)
            val haze = 0.45f
            fun hz(col: Int) = mix(col, hazeColour, haze)
            c.save(); c.translate(sh.x, sh.y); c.scale(sh.dir * s, s)
            // Hull: dark topsides over a red bottom, a long sheer, bow to the right.
            fill(hz(0xFF8A2A22.toInt())); c.drawRect(-110f, -1.5f, 112f, 0f, p)
            fill(hz(if (sh.tanker) 0xFF2A2C30.toInt() else 0xFF1E2A40.toInt()))
            path.reset(); path.moveTo(-112f, -8f); path.lineTo(108f, -9f); path.lineTo(118f, -11f); path.lineTo(112f, -1.5f); path.lineTo(-110f, -1.5f); path.close(); c.drawPath(path, p)
            if (sh.tanker) { // pipe deck and the catwalk
                fill(hz(0xFF6A6E74.toInt())); c.drawRect(-70f, -10.5f, 100f, -8.6f, p)
            } else { // stacked containers in bays
                var bx = -66f; var i = 0
                while (bx < 100f) { for (tier in 0 until 4) { fill(hz(boxCols[(i * 3 + tier * 5) % boxCols.size])); c.drawRect(bx, -9f - (tier + 1) * 2.6f, bx + 12.4f, -9f - tier * 2.6f - 0.2f, p) }; bx += 13f; i++ }
            }
            // Superstructure aft with the bridge windows, and the funnel.
            fill(hz(0xFFEDEDE8.toInt())); c.drawRect(-102f, -26f, -82f, -8f, p)
            fill(hz(0xFF2A3644.toInt())); c.drawRect(-101f, -24.5f, -83f, -23f, p)
            fill(hz(0xFFEDEDE8.toInt())); c.drawRect(-104f, -26.8f, -80f, -26f, p)
            fill(hz(if (sh.tanker) 0xFFC8402E.toInt() else 0xFF2A3644.toInt())); c.drawRect(-98f, -32f, -90f, -26.8f, p)
            c.restore()
            if (night) {
                fill(0xFFFFE6B0.toInt())
                for (i in 0 until 10) c.drawCircle(sh.x + sh.dir * (-100f + i * 22f) * s, sh.y - 9.5f * s, max(1f, 0.6f * s), p)
                fill(0xFFFFFFFF.toInt()); c.drawCircle(sh.x + sh.dir * (-92f) * s, sh.y - 34f * s, max(1.2f, 0.8f * s), p); c.drawCircle(sh.x + sh.dir * 80f * s, sh.y - 20f * s, max(1.2f, 0.8f * s), p)
            } else {
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.5f * s); p.color = withAlpha(Color.WHITE, 90)
                c.drawLine(sh.x - sh.dir * 112f * s, sh.y, sh.x - sh.dir * 170f * s, sh.y, p); p.style = Paint.Style.FILL // a faint wake line
            }
        }
    }

    // ---- Chokka boats at night -------------------------------------------------------------------------

    private fun drawChokka(c: Canvas, glow: (Canvas, Float, Float, Float, Int, Float) -> Unit) {
        val x = w * 0.58f + sin(time * 0.05f) * 20f * k; val y = hz + 80f * k; val s = ppm(y, 4.5f)
        glow(c, x, y - 2f * s, 30f * s, 0xE6F0FF, 0.55f)
        c.save(); c.translate(x, y); c.scale(s, s)
        drawHull(c, 14f, 1.4f, 0.6f, 0xFF242C3C.toInt(), 0xFF101418.toInt())
        fill(0xFF1A2030.toInt()); c.drawRect(-2f, -3.6f, 3f, -1.4f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.12f; p.color = 0xFF2A3040.toInt(); c.drawLine(-6.5f, -1.4f, 6.5f, -1.4f, p); c.drawLine(-6.5f, -3.4f, 6.5f, -3.4f, p)
        p.style = Paint.Style.FILL
        fill(0xFFF4F8FF.toInt()); var kx = -6f; while (kx <= 6f) { c.drawCircle(kx, -3.3f, 0.3f, p); kx += 1.5f }
        c.restore()
        fill(0x59E6F0FF); for (i in 0 until 8) c.drawRect(x - 4f * s + sin(time + i) * s, y + i * 1.2f * s, x - 4f * s + sin(time + i) * s + 8f * s * (1f - i / 9f), y + i * 1.2f * s + 0.4f * s, p) // its reflection
    }

    // ---- Frame ----------------------------------------------------------------------------------------

    private fun wrap(x: Float) = if (x > w + 80f * k) -80f * k else if (x < -80f * k) w + 80f * k else x

    fun update(dt: Float) {
        time += dt
        for (wh in whales) {
            val s = ppm(wh.y, WHALE_EX)
            val before = wh.t % cycleLen
            wh.t += dt
            if (wh.t % cycleLen < before) wh.cycle++
            wh.x = wrap(wh.x + wh.dir * 1.0f * s * dt)
        }
        for (b in boats) { val s = ppm(b.y, 4.5f); b.x = wrap(b.x + b.dir * b.v * s * 0.25f * dt) }
        for (sh in ships) {
            sh.x += sh.dir * sh.v * ppm(sh.y, 4f) * 0.25f * dt
            if (sh.x > w + 400f * k) sh.x = -400f * k; if (sh.x < -400f * k) sh.x = w + 400f * k
        }
        if (jetskiFor > 0f) {
            jetskiFor -= dt; jetski.t += dt
            jetski.x += jetski.dir * jetski.v * ppm(jetski.y, 6f) * 0.25f * dt
            if (jetski.x > w * 0.8f) jetski.dir = -1f; if (jetski.x < w * 0.25f) jetski.dir = 1f
        } else if (daylightVisitors) { jetskiIn -= dt; if (jetskiIn <= 0f) { jetskiFor = 60f + rnd.nextFloat() * 40f; jetskiIn = 240f + rnd.nextFloat() * 360f } }
        if (podFor > 0f) {
            podFor -= dt; podT += dt
            podX += podDir * 4f * ppm(podY, 8f) * dt
            if (podX > w * 0.85f) podDir = -1f; if (podX < w * 0.15f) podDir = 1f
        } else if (daylightVisitors) { podIn -= dt; if (podIn <= 0f) { podFor = 50f + rnd.nextFloat() * 40f; podIn = 150f + rnd.nextFloat() * 240f } }
    }

    fun draw(c: Canvas, glow: (Canvas, Float, Float, Float, Int, Float) -> Unit) {
        drawShips(c)
        drawWhales(c)
        drawPod(c)
        for (b in boats) if (!(night && b.kind == YACHT)) drawBoat(c, b)
        if (jetskiFor > 0f) drawBoat(c, jetski)
        if (night) drawChokka(c, glow)
        p.shader = null
    }

    /** Something quick on the water: a breach, the dolphins or the jetski. */
    val busy get() = jetskiFor > 0f || podFor > 0f || whales.any { it.cycle % 3 == 2 && it.t % cycleLen < 7f }

    fun launchDolphins() { podFor = 70f }
    fun launchJetski() { jetskiFor = 80f }
    fun launchBreach() { val wh = whales[1]; wh.t = (wh.t / cycleLen).toInt() * cycleLen + cycleLen - 0.05f; wh.cycle = 1 }

    private companion object {
        const val CAT = 0; const val SKI = 1; const val YACHT = 2; const val JETSKI = 3
        const val WHALE_EX = 6.5f
    }
}
