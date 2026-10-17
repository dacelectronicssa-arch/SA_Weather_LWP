package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class Species { ZEBRA, WILDEBEEST, SPRINGBOK, ELEPHANT, CALF, GIRAFFE, LION, LIONESS }

enum class Act { STAND, GRAZE, WALK, RUN, STALK, LIE, PRONK }

/** Everything that changes frame to frame for one animal. Feet are (x, y) pairs, ground at y = 0. */
class Pose {
    var act = Act.STAND
    var phase = 0f      // gait cycle position
    var bob = 0f        // vertical body offset
    var head = 0f       // 0 = grazing, 1 = head up
    var t = 0f          // the animal's own clock, for idle motion
    val feet = FloatArray(8)
    /** Per leg: 0 planted, up to 1 mid-swing (paws curl), negative while pushing off. */
    val flex = FloatArray(4)
    var pitch = 0f      // body tilt in radians (the gallop)
}

/**
 * Side-view savanna animals with jointed legs, painted in units of body length (facing right,
 * standing on y = 0). A port of the in-chat animal studio.
 */
class AnimalArt {

    class Leg(val hipX: Float, val hipY: Float, val l1: Float, val l2: Float, val home: Float, val front: Boolean, val w1: Float, val w2: Float)

    class Skeleton(val legs: Array<Leg>, val walkStride: Float, val runStride: Float, val gait: Gait, val run: Gait)

    class Gait(val beta: Float, val off: FloatArray, val lift: Float)

    companion object {
        private fun leg(hx: Float, hy: Float, l1: Float, l2: Float, home: Float, front: Boolean, w1: Float, w2: Float) =
            Leg(hx, hy, l1, l2, home, front, w1, w2)

        // Legs: [near hind, far hind, near front, far front].
        val WALK = Gait(0.64f, floatArrayOf(0f, 0.5f, 0.25f, 0.75f), 0.06f)
        val PACE = Gait(0.6f, floatArrayOf(0f, 0.5f, 0.08f, 0.58f), 0.07f)
        val GALLOP = Gait(0.36f, floatArrayOf(0f, 0.1f, 0.55f, 0.45f), 0.12f)
        val AMBLE = Gait(0.7f, floatArrayOf(0f, 0.5f, 0.25f, 0.75f), 0.04f)

        private fun four(h: Leg, f: Leg) = arrayOf(h, h, f, f)

        val SKELETONS = mapOf(
            Species.ZEBRA to Skeleton(four(leg(-0.29f, -0.63f, 0.31f, 0.34f, -0.27f, false, 0.11f, 0.05f), leg(0.26f, -0.6f, 0.28f, 0.34f, 0.28f, true, 0.1f, 0.05f)), 0.36f, 0.9f, WALK, GALLOP),
            Species.WILDEBEEST to Skeleton(four(leg(-0.27f, -0.58f, 0.29f, 0.33f, -0.26f, false, 0.1f, 0.04f), leg(0.24f, -0.62f, 0.3f, 0.35f, 0.26f, true, 0.1f, 0.045f)), 0.34f, 0.85f, WALK, GALLOP),
            Species.SPRINGBOK to Skeleton(four(leg(-0.26f, -0.56f, 0.27f, 0.31f, -0.25f, false, 0.075f, 0.028f), leg(0.23f, -0.55f, 0.24f, 0.32f, 0.25f, true, 0.065f, 0.028f)), 0.32f, 0.9f, WALK, GALLOP),
            Species.ELEPHANT to Skeleton(four(leg(-0.3f, -0.62f, 0.29f, 0.32f, -0.3f, false, 0.16f, 0.13f), leg(0.24f, -0.62f, 0.29f, 0.32f, 0.25f, true, 0.16f, 0.13f)), 0.34f, 0.5f, AMBLE, AMBLE),
            Species.CALF to Skeleton(four(leg(-0.3f, -0.62f, 0.29f, 0.32f, -0.3f, false, 0.16f, 0.13f), leg(0.24f, -0.62f, 0.29f, 0.32f, 0.25f, true, 0.16f, 0.13f)), 0.34f, 0.5f, AMBLE, AMBLE),
            Species.GIRAFFE to Skeleton(four(leg(-0.2f, -1.12f, 0.5f, 0.62f, -0.2f, false, 0.1f, 0.045f), leg(0.22f, -1.22f, 0.56f, 0.66f, 0.24f, true, 0.09f, 0.045f)), 0.6f, 1.1f, PACE, GALLOP),
            Species.LION to Skeleton(four(leg(-0.3f, -0.44f, 0.23f, 0.22f, -0.28f, false, 0.12f, 0.05f), leg(0.25f, -0.42f, 0.2f, 0.23f, 0.27f, true, 0.1f, 0.055f)), 0.32f, 0.8f, WALK, GALLOP),
            Species.LIONESS to Skeleton(four(leg(-0.3f, -0.44f, 0.23f, 0.22f, -0.28f, false, 0.12f, 0.05f), leg(0.25f, -0.42f, 0.2f, 0.23f, 0.27f, true, 0.1f, 0.055f)), 0.32f, 0.8f, WALK, GALLOP),
        )

        private fun frac(x: Float) = x - floor(x)

        // Lion gaits from the lion studio: duty factor, leg offsets, lift, foot travel, crouch.
        class LionGait(val beta: Float, val off: FloatArray, val lift: Float, val travel: Float, val low: Float)
        private val LION_WALK = LionGait(.66f, floatArrayOf(0f, .5f, .25f, .75f), .07f, .3f, 0f)
        private val LION_STALK = LionGait(.78f, floatArrayOf(0f, .5f, .25f, .75f), .05f, .2f, .11f)
        private val LION_GALLOP = LionGait(.34f, floatArrayOf(0f, .1f, .55f, .65f), .13f, .62f, 0f)
        private val LION_HOME = floatArrayOf(-.27f, -.27f, .27f, .27f)

        private fun isLion(sp: Species) = sp == Species.LION || sp == Species.LIONESS

        private fun lionGait(act: Act) = when (act) {
            Act.WALK -> LION_WALK
            Act.STALK -> LION_STALK
            Act.RUN -> LION_GALLOP
            else -> null
        }

        private fun placeLionFeet(pose: Pose) {
            val q = lionGait(pose.act)
            if (q == null) {
                for (i in 0 until 4) {
                    pose.feet[i * 2] = LION_HOME[i] + (if (i % 2 == 1) .03f else -.02f)
                    pose.feet[i * 2 + 1] = 0f
                    pose.flex[i] = 0f
                }
                pose.pitch = 0f
                return
            }
            val ph = pose.phase
            for (i in 0 until 4) {
                val u = frac(ph + q.off[i])
                if (u < q.beta) {
                    pose.feet[i * 2] = LION_HOME[i] + q.travel * (.5f - u / q.beta)
                    pose.feet[i * 2 + 1] = 0f
                    pose.flex[i] = -.25f * u / q.beta
                } else {
                    val s = (u - q.beta) / (1f - q.beta)
                    val e = .5f - .5f * cos(PI.toFloat() * s)
                    pose.feet[i * 2] = LION_HOME[i] + q.travel * (-.5f + e)
                    pose.feet[i * 2 + 1] = -q.lift * sin(PI.toFloat() * s)
                    pose.flex[i] = sin(PI.toFloat() * s)
                }
            }
            val tau = 2f * PI.toFloat()
            if (pose.act == Act.RUN) {
                pose.bob = -.05f * max(0f, sin(tau * (ph + .15f)))
                pose.pitch = .07f * sin(tau * (ph + .35f))
            } else {
                pose.bob = -.012f * abs(sin(tau * ph))
                pose.pitch = 0f
            }
        }

        /** Body lengths travelled per gait cycle: advancing the phase by distance / this keeps planted feet still. */
        fun cycleLength(sp: Species, act: Act): Float {
            if (isLion(sp)) {
                val q = lionGait(act) ?: LION_WALK
                return q.travel / q.beta
            }
            val sk = SKELETONS.getValue(sp)
            val g = if (act == Act.RUN) sk.run else sk.gait
            return strideOf(sp, act) / g.beta
        }

        /** Places the feet for the pose's gait phase (or planted, when standing or grazing). */
        fun placeFeet(sp: Species, pose: Pose) {
            if (isLion(sp)) { placeLionFeet(pose); return }
            val sk = SKELETONS.getValue(sp)
            val legs = sk.legs
            if (pose.act == Act.PRONK) {
                // Stiff-legged leap: the feet stay under the body.
                val air = (-pose.bob / 0.48f).coerceIn(0f, 1f)
                for (i in 0 until 4) {
                    pose.feet[i * 2] = legs[i].home + (if (legs[i].front) 0.03f else -0.03f) * air
                    pose.feet[i * 2 + 1] = pose.bob
                }
                return
            }
            val moving = pose.act == Act.WALK || pose.act == Act.RUN || pose.act == Act.STALK
            val g = if (pose.act == Act.RUN) sk.run else sk.gait
            val stride = strideOf(sp, pose.act)
            for (i in 0 until 4) {
                val l = legs[i]
                if (!moving) { pose.feet[i * 2] = l.home; pose.feet[i * 2 + 1] = 0f; continue }
                val u = frac(pose.phase + g.off[i])
                if (u < g.beta) {
                    pose.feet[i * 2] = l.home + stride * (0.5f - u / g.beta)
                    pose.feet[i * 2 + 1] = 0f
                } else {
                    val s = (u - g.beta) / (1f - g.beta)
                    pose.feet[i * 2] = l.home + stride * (-0.5f + s)
                    pose.feet[i * 2 + 1] = -g.lift * sin(PI.toFloat() * s) * (if (l.front) 1.1f else 1f)
                }
            }
        }

        fun strideOf(sp: Species, act: Act): Float {
            val sk = SKELETONS.getValue(sp)
            return when (act) {
                Act.RUN -> sk.runStride
                Act.STALK -> sk.walkStride * 0.6f
                else -> sk.walkStride
            }
        }

        /** Body bob for the current phase. */
        fun bobFor(act: Act, phase: Float, t: Float) = when (act) {
            Act.RUN -> -abs(sin(phase * PI.toFloat() * 2f)) * 0.05f
            Act.WALK, Act.STALK -> -abs(sin(phase * PI.toFloat() * 2f)) * 0.012f
            else -> sin(t * 0.8f) * 0.003f
        }
    }

    // ---- Drawing toolkit -----------------------------------------------------------

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val path2 = Path()
    private val clip = Path()
    private var kx = 0f
    private var ky = 0f
    private var fx = 0f
    private var fy = 0f

    /** 0..1: how strongly bodies get their volume shading (off for silhouettes). */
    var shading = 1f

    private val shadeBounds = android.graphics.RectF()
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Rounds out the body outline in [clip]: a soft light along the back, shade under the belly
     * and a little at the rump and chest, so flat shapes read as solid.
     */
    private fun shadeBody(cv: Canvas) {
        if (shading <= 0.02f) return
        @Suppress("DEPRECATION") clip.computeBounds(shadeBounds, true)
        val a = shading
        cv.save()
        cv.clipPath(clip)
        shadePaint.shader = LinearGradient(0f, shadeBounds.top, 0f, shadeBounds.bottom,
            intArrayOf(withAlpha(0xFFFFFFFF.toInt(), (46 * a).toInt()), 0x00FFFFFF, 0x00000000, withAlpha(0xFF000000.toInt(), (70 * a).toInt())),
            floatArrayOf(0f, 0.32f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(shadeBounds, shadePaint)
        val wEdge = (shadeBounds.right - shadeBounds.left) * 0.14f
        shadePaint.shader = LinearGradient(shadeBounds.left, 0f, shadeBounds.left + wEdge, 0f,
            withAlpha(0xFF000000.toInt(), (40 * a).toInt()), 0x00000000, Shader.TileMode.CLAMP)
        cv.drawRect(shadeBounds, shadePaint)
        shadePaint.shader = LinearGradient(shadeBounds.right, 0f, shadeBounds.right - wEdge, 0f,
            withAlpha(0xFF000000.toInt(), (28 * a).toInt()), 0x00000000, Shader.TileMode.CLAMP)
        cv.drawRect(shadeBounds, shadePaint)
        shadePaint.shader = null
        cv.restore()
    }

    /** Colour transform applied to every colour (silhouette at dusk, haze in the distance). */
    var tint: (Int) -> Int = { it }

    private fun c(rgb: Long) = tint(rgb.toInt())
    private fun c(rgb: Int) = tint(rgb)

    private fun fill(color: Int) { p.style = Paint.Style.FILL; p.shader = null; p.color = color }
    private fun stroke(color: Int, w: Float) { p.style = Paint.Style.STROKE; p.shader = null; p.color = color; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND }

    private fun ik(hx: Float, hy: Float, tx: Float, ty: Float, l1: Float, l2: Float, front: Boolean) {
        var dx = tx - hx
        var dy = ty - hy
        var d = hypot(dx, dy)
        val m = l1 + l2 - 1e-4f
        var ex = tx
        var ey = ty
        if (d > m) { ex = hx + dx / d * m; ey = hy + dy / d * m; d = m }
        val a = atan2(ey - hy, ex - hx)
        val b = acos(((l1 * l1 + d * d - l2 * l2) / (2f * l1 * max(d, 1e-4f))).coerceIn(-1f, 1f))
        val k1x = hx + l1 * cos(a + b)
        val k1y = hy + l1 * sin(a + b)
        val k2x = hx + l1 * cos(a - b)
        val k2y = hy + l1 * sin(a - b)
        val first = if (front) k1x > k2x else k1x < k2x
        kx = if (first) k1x else k2x
        ky = if (first) k1y else k2y
        fx = ex; fy = ey
    }

    private fun seg(cv: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float, color: Int) {
        val a = atan2(y2 - y1, x2 - x1)
        val nx = -sin(a)
        val ny = cos(a)
        fill(color)
        path.reset()
        path.moveTo(x1 + nx * w1 / 2, y1 + ny * w1 / 2)
        path.lineTo(x2 + nx * w2 / 2, y2 + ny * w2 / 2)
        path.lineTo(x2 - nx * w2 / 2, y2 - ny * w2 / 2)
        path.lineTo(x1 - nx * w1 / 2, y1 - ny * w1 / 2)
        path.close()
        cv.drawPath(path, p)
        cv.drawCircle(x2, y2, w2 / 2, p)
        cv.drawCircle(x1, y1, w1 / 2, p)
    }

    /** Smooth closed outline through points (x, y pairs). */
    private fun outline(out: Path, pts: FloatArray) {
        out.reset()
        out.moveTo(pts[0], pts[1])
        val n = pts.size / 2
        for (i in 1 until n) {
            val ax = pts[i * 2]; val ay = pts[i * 2 + 1]
            val j = (i + 1) % n
            out.quadTo(ax, ay, (ax + pts[j * 2]) / 2, (ay + pts[j * 2 + 1]) / 2)
        }
        out.close()
    }

    private fun drawLegs(cv: Canvas, sp: Species, pose: Pose, far: Boolean, lowered: Float, color: Int, hoof: Int, bands: Int?) {
        val legs = SKELETONS.getValue(sp).legs
        for (i in (if (far) intArrayOf(1, 3) else intArrayOf(0, 2))) {
            val l = legs[i]
            val hx = l.hipX
            val hy = l.hipY + pose.bob + lowered
            ik(hx, hy, pose.feet[i * 2], pose.feet[i * 2 + 1], l.l1, l.l2 - lowered * 0.5f, l.front)
            val col = if (far) scale(color, 0.78f) else color
            val k0x = kx; val k0y = ky; val f0x = fx; val f0y = fy
            seg(cv, hx, hy, k0x, k0y, l.w1, l.w2 * 1.15f, col)
            seg(cv, k0x, k0y, f0x, f0y, l.w2 * 1.1f, l.w2 * 0.85f, col)
            if (bands != null) {
                stroke(if (far) withAlpha(bands, 190) else bands, 0.022f)
                // Thigh and upper arm: bands across the whole width of the leg.
                var u = 0.25f
                while (u < 0.98f) {
                    val x = hx + (k0x - hx) * u
                    val y = hy + (k0y - hy) * u
                    val hw = (l.w1 + (l.w2 * 1.15f - l.w1) * u) * 0.5f
                    cv.drawLine(x - hw, y - 0.01f, x + hw, y + 0.01f, p)
                    u += 0.16f
                }
                p.strokeWidth = 0.018f
                var k = 0.12f
                while (k < 0.95f) {
                    val x = k0x + (f0x - k0x) * k
                    val y = k0y + (f0y - k0y) * k
                    cv.drawLine(x - 0.035f, y, x + 0.035f, y + 0.005f, p)
                    k += 0.11f
                }
            }
            fill(hoof)
            cv.drawOval(f0x + 0.01f - l.w2 * 0.55f, f0y - 0.03f, f0x + 0.01f + l.w2 * 0.55f, f0y + 0.006f, p)
        }
    }

    private class HeadStyle(
        val len: Float, val w: Float, val col: Int, val muzzle: Int,
        val ear: Boolean = false, val earCol: Int? = null, val earLen: Float = 1f,
        val stripe: Int? = null, val eyeStripe: Int? = null,
        val horns: Boolean = false, val lyre: Boolean = false, val beard: Int? = null,
    )

    private fun head(cv: Canvas, x: Float, y: Float, neckAng: Float, pose: Pose, o: HeadStyle, detail: Boolean) {
        val ha = neckAng + (if (pose.head < 0.5f) 0.9f else 1.35f) + sin(pose.t * 0.5f) * 0.03f
        cv.save()
        cv.translate(x, y)
        cv.rotate(Math.toDegrees(ha.toDouble()).toFloat())
        val chew = if (pose.head < 0.3f) max(0f, sin(pose.t * 6f)) * 0.01f else 0f
        fill(o.col)
        path.reset()
        path.moveTo(-0.04f, -o.w * 0.6f)
        path.quadTo(o.len * 0.5f, -o.w * 0.75f, o.len, -o.w * 0.35f)
        path.quadTo(o.len * 1.06f, 0f, o.len, o.w * 0.35f + chew)
        path.quadTo(o.len * 0.5f, o.w * 0.7f + chew, -0.04f, o.w * 0.6f)
        path.close()
        cv.drawPath(path, p)
        if (detail && o.stripe != null) {
            stroke(o.stripe, 0.014f)
            var k = 0.15f
            while (k < 0.7f) { cv.drawLine(o.len * k, -o.w * 0.65f, o.len * k + 0.02f, o.w * 0.4f, p); k += 0.11f }
        }
        if (o.eyeStripe != null) {
            stroke(o.eyeStripe, o.w * 0.28f)
            path.reset(); path.moveTo(o.len * 0.18f, -o.w * 0.2f); path.quadTo(o.len * 0.55f, o.w * 0.05f, o.len * 0.92f, o.w * 0.15f)
            cv.drawPath(path, p)
        }
        fill(o.muzzle)
        cv.drawOval(o.len * 0.9f - o.len * 0.14f, -(o.w * 0.45f + chew), o.len * 0.9f + o.len * 0.14f, o.w * 0.45f + chew, p)
        fill(c(0xFF0C0C0C))
        cv.drawCircle(o.len * 0.22f, -o.w * 0.28f, 0.012f, p)
        if (o.ear) {
            val flick = if (max(0f, sin(pose.t * 0.9f + 1f)) > 0.95f) 0.4f else 0f
            cv.save()
            cv.translate(0f, -o.w * 0.55f)
            cv.rotate(Math.toDegrees((-2.2f + flick).toDouble()).toFloat())
            fill(o.earCol ?: o.col)
            cv.drawOval(0.05f * o.earLen - 0.06f * o.earLen, -0.022f, 0.05f * o.earLen + 0.06f * o.earLen, 0.022f, p)
            fill(o.stripe ?: o.muzzle)
            cv.drawOval(0.09f * o.earLen - 0.02f, -0.015f, 0.09f * o.earLen + 0.02f, 0.015f, p)
            cv.restore()
        }
        if (o.horns) {
            stroke(c(0xFF322E28), 0.025f)
            path.reset(); path.moveTo(0f, -o.w * 0.5f); path.quadTo(-0.06f, -o.w * 1.6f, 0.05f, -o.w * 1.9f)
            cv.drawPath(path, p)
        }
        if (o.lyre) {
            for ((dx, w) in arrayOf(0.012f to 0.02f, -0.006f to 0.017f)) {
                stroke(c(0xFF161412), w)
                path.reset()
                path.moveTo(0.02f + dx, -o.w * 0.5f)
                path.cubicTo(-0.07f + dx, -o.w * 1.3f, -0.02f + dx, -o.w * 2.0f, -0.07f + dx, -o.w * 2.5f)
                path.quadTo(-0.08f + dx, -o.w * 2.7f, -0.04f + dx, -o.w * 2.75f)
                cv.drawPath(path, p)
            }
        }
        if (o.beard != null) {
            stroke(o.beard, 0.03f)
            cv.drawLine(o.len * 0.3f, o.w * 0.55f, o.len * 0.32f, o.w * 1.6f, p)
        }
        cv.restore()
    }

    private fun neckQuad(out: Path, bx: Float, by: Float, nx: Float, ny: Float, pa: Float, top: Float, tipTop: Float, tipBottom: Float, bottomX: Float, bottomY: Float) {
        out.reset()
        out.moveTo(bx - top, by - top)
        out.lineTo(nx - cos(pa) * tipTop, ny - sin(pa) * tipTop)
        out.lineTo(nx + cos(pa) * tipBottom, ny + sin(pa) * tipBottom)
        out.lineTo(bx + bottomX, by + bottomY)
        out.close()
    }

    // ---- Species -------------------------------------------------------------------------

    fun draw(cv: Canvas, sp: Species, pose: Pose, detail: Boolean) {
        when (sp) {
            Species.ZEBRA -> zebra(cv, pose, detail)
            Species.WILDEBEEST -> wildebeest(cv, pose, detail)
            Species.SPRINGBOK -> springbok(cv, pose, detail)
            Species.ELEPHANT -> elephant(cv, pose, detail, calf = false)
            Species.CALF -> elephant(cv, pose, detail, calf = true)
            Species.GIRAFFE -> giraffe(cv, pose, detail)
            Species.LION -> lion(cv, pose, detail, male = true)
            Species.LIONESS -> lion(cv, pose, detail, male = false)
        }
    }

    private val zebraBody = floatArrayOf(0.43f, -0.6f, 0.34f, -0.8f, 0.22f, -0.87f, 0f, -0.84f, -0.24f, -0.86f, -0.4f, -0.82f, -0.48f, -0.7f, -0.45f, -0.55f, -0.34f, -0.46f, -0.05f, -0.42f, 0.2f, -0.45f, 0.38f, -0.5f)

    private fun zebra(cv: Canvas, pose: Pose, detail: Boolean) {
        val white = c(0xFFF2F0E8); val blk = c(0xFF161618); val hoof = c(0xFF1E1E1E)
        drawLegs(cv, Species.ZEBRA, pose, true, 0f, white, hoof, blk)
        cv.save(); cv.translate(0f, pose.bob)
        outline(clip, zebraBody)
        fill(white); cv.drawPath(clip, p)
        val na = -0.95f + (1f - pose.head) * 1.75f
        val nbx = 0.3f; val nby = -0.74f
        val nx = nbx + cos(na) * 0.36f; val ny = nby + sin(na) * 0.36f; val pa = na + PI.toFloat() / 2f
        neckQuad(path2, nbx, nby, nx, ny, pa, 0.1f, 0.06f, 0.06f, 0.14f, 0.16f)
        cv.drawPath(path2, p)
        run { // stripes cover the whole body, near or far
            cv.save(); cv.clipPath(clip)
            stroke(blk, 0.03f)
            // Shoulder and barrel: upright stripes wrapping down to the belly.
            var x = 0.46f
            while (x > -0.2f) { path.reset(); path.moveTo(x, -0.95f); path.quadTo(x + 0.025f, -0.66f, x - 0.035f, -0.36f); cv.drawPath(path, p); x -= 0.052f }
            // Saddle: stripes sweeping back over the loins (kept clear of the rump so they don't cross).
            cv.save(); cv.clipRect(-0.36f, -1f, 0f, 0f)
            for (k in 0 until 4) { path.reset(); path.moveTo(-0.2f - k * 0.05f, -0.95f); path.quadTo(-0.26f - k * 0.05f, -0.66f, -0.37f - k * 0.04f, -0.4f); cv.drawPath(path, p) }
            cv.restore()
            // Haunch and rump: near-horizontal stripes, behind the saddle.
            cv.save(); cv.clipRect(-0.7f, -1f, -0.36f, 0f)
            p.strokeWidth = 0.028f
            for (k in 0 until 8) { val y = -0.9f + k * 0.065f; path.reset(); path.moveTo(-0.6f, y); path.quadTo(-0.47f, y - 0.03f, -0.34f, y + 0.045f); cv.drawPath(path, p) }
            cv.restore()
            cv.restore()
            cv.save(); cv.clipPath(path2)
            stroke(blk, 0.03f)
            for (k in 0 until 7) {
                val t = k / 6.5f
                val x0 = nbx + (nx - nbx) * t; val y0 = nby + (ny - nby) * t
                cv.drawLine(x0 - cos(pa) * 0.15f, y0 - sin(pa) * 0.15f, x0 + cos(pa) * 0.15f, y0 + sin(pa) * 0.15f, p)
            }
            cv.restore()
        }
        outline(clip, zebraBody); shadeBody(cv)
        stroke(blk, 0.035f)
        cv.drawLine(nbx - 0.1f, nby - 0.12f, nx - cos(pa) * 0.07f, ny - sin(pa) * 0.07f, p)
        head(cv, nx, ny, na, pose, HeadStyle(0.26f, 0.085f, white, blk, ear = true, stripe = blk), detail)
        tail(cv, pose, white, blk, -0.45f, -0.75f, -0.52f, -0.44f, 1.6f)
        cv.restore()
        drawLegs(cv, Species.ZEBRA, pose, false, 0f, white, hoof, blk)
    }

    private fun tail(cv: Canvas, pose: Pose, col: Int, tuft: Int, x0: Float, y0: Float, x1: Float, y1: Float, speed: Float) {
        val tw = sin(pose.t * speed) * 0.05f
        stroke(col, 0.03f)
        path.reset(); path.moveTo(x0, y0); path.quadTo(x1 - 0.01f, (y0 + y1) / 2f + tw, x1 + tw, y1)
        cv.drawPath(path, p)
        fill(tuft)
        cv.drawOval(x1 + tw - 0.02f, y1 - 0.03f, x1 + tw + 0.02f, y1 + 0.07f, p)
    }

    private val gnuBody = floatArrayOf(0.42f, -0.62f, 0.36f, -0.86f, 0.2f, -0.96f, 0f, -0.88f, -0.22f, -0.78f, -0.38f, -0.74f, -0.46f, -0.64f, -0.42f, -0.5f, -0.3f, -0.44f, -0.02f, -0.42f, 0.22f, -0.46f, 0.38f, -0.52f)

    private fun wildebeest(cv: Canvas, pose: Pose, detail: Boolean) {
        val body = c(0xFF60584E); val dk = c(0xFF221E1C); val hoof = c(0xFF1E1E1E)
        drawLegs(cv, Species.WILDEBEEST, pose, true, 0f, scale(body, 0.75f), hoof, null)
        cv.save(); cv.translate(0f, pose.bob)
        outline(clip, gnuBody)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, -0.95f, 0f, -0.42f, scale(body, 1.08f), scale(body, 0.86f), Shader.TileMode.CLAMP)
        cv.drawPath(clip, p); p.shader = null
        if (detail) {
            cv.save(); cv.clipPath(clip)
            stroke(withAlpha(dk, 128), 0.022f)
            var x = 0.04f
            while (x < 0.42f) { path.reset(); path.moveTo(x, -0.98f); path.quadTo(x + 0.02f, -0.7f, x - 0.02f, -0.45f); cv.drawPath(path, p); x += 0.045f }
            cv.restore()
        }
        val na = -0.7f + (1f - pose.head) * 1.55f
        val nbx = 0.3f; val nby = -0.78f
        val nx = nbx + cos(na) * 0.3f; val ny = nby + sin(na) * 0.3f; val pa = na + PI.toFloat() / 2f
        fill(body)
        neckQuad(path2, nbx, nby, nx, ny, pa, 0.12f, 0.08f, 0.09f, 0.14f, 0.18f)
        cv.drawPath(path2, p)
        stroke(dk, 0.06f)
        cv.drawLine(nbx - 0.13f, nby - 0.14f, nx - cos(pa) * 0.08f, ny - sin(pa) * 0.08f, p)
        head(cv, nx, ny, na, pose, HeadStyle(0.27f, 0.1f, scale(body, 0.8f), dk, ear = true, horns = true, beard = c(0xFFDCD4C4)), detail)
        tail(cv, pose, dk, dk, -0.45f, -0.66f, -0.5f, -0.3f, 1.4f)
        cv.restore()
        drawLegs(cv, Species.WILDEBEEST, pose, false, 0f, scale(body, 0.85f), hoof, null)
    }

    private val springbokBody = floatArrayOf(0.36f, -0.56f, 0.3f, -0.72f, 0.18f, -0.76f, -0.02f, -0.73f, -0.26f, -0.77f, -0.38f, -0.74f, -0.43f, -0.64f, -0.38f, -0.53f, -0.26f, -0.49f, 0f, -0.48f, 0.2f, -0.49f, 0.32f, -0.51f)

    private fun springbok(cv: Canvas, pose: Pose, detail: Boolean) {
        val tan = c(0xFFC48C54); val str = c(0xFF763C26); val wh = c(0xFFF6F2EA); val hoof = c(0xFF1E1E1E)
        val leap = pose.act == Act.PRONK && pose.bob < -0.03f
        drawLegs(cv, Species.SPRINGBOK, pose, true, 0f, mix(wh, tan, 0.25f), hoof, null)
        cv.save(); cv.translate(0f, pose.bob)
        if (leap) cv.rotate(2.9f)
        outline(clip, springbokBody)
        fill(wh); cv.drawPath(clip, p)
        shadeBody(cv)
        cv.save(); cv.clipPath(clip)
        fill(tan)
        path.reset(); path.moveTo(-0.5f, -0.9f); path.lineTo(0.45f, -0.9f); path.lineTo(0.45f, -0.6f)
        path.quadTo(0.1f, -0.62f, -0.18f, -0.6f); path.quadTo(-0.32f, -0.62f, -0.34f, -0.72f); path.lineTo(-0.5f, -0.72f); path.close()
        cv.drawPath(path, p)
        stroke(str, 0.04f)
        path.reset(); path.moveTo(0.36f, -0.59f); path.quadTo(0.1f, -0.6f, -0.18f, -0.585f); path.quadTo(-0.3f, -0.6f, -0.33f, -0.66f)
        cv.drawPath(path, p)
        fill(wh); cv.drawOval(-0.45f, -0.76f, -0.33f, -0.56f, p)
        if (leap) { // the white dorsal fan, flared at the top of a pronk
            path.reset(); path.moveTo(-0.3f, -0.77f); path.quadTo(-0.12f, -0.86f, 0.05f, -0.75f); path.quadTo(-0.1f, -0.74f, -0.3f, -0.77f); path.close()
            cv.drawPath(path, p)
        }
        cv.restore()
        val na = -1.0f + (1f - pose.head) * 1.7f + (if (leap) 0.25f else 0f)
        val nbx = 0.27f; val nby = -0.66f
        val nx = nbx + cos(na) * 0.3f; val ny = nby + sin(na) * 0.3f; val pa = na + PI.toFloat() / 2f
        fill(tan)
        neckQuad(path2, nbx, nby, nx, ny, pa, 0.08f, 0.045f, 0.05f, 0.1f, 0.12f)
        cv.drawPath(path2, p)
        if (detail) {
            cv.save(); cv.clipPath(path2)
            stroke(wh, 0.06f)
            cv.drawLine(nbx + 0.1f, nby + 0.12f, nx + cos(pa) * 0.05f, ny + sin(pa) * 0.05f, p)
            cv.restore()
        }
        head(cv, nx, ny, na, pose, HeadStyle(0.2f, 0.065f, wh, c(0xFF3C2C24), ear = true, earCol = tan, earLen = 1.4f, eyeStripe = c(0xFF6E3E28), lyre = true), detail)
        val tw = sin(pose.t * 2.2f) * 0.03f
        stroke(wh, 0.025f); cv.drawLine(-0.42f, -0.7f, -0.47f + tw * 0.5f, -0.6f, p)
        stroke(c(0xFF161412), 0.02f); cv.drawLine(-0.46f + tw * 0.5f, -0.62f, -0.48f + tw * 0.5f, -0.57f, p)
        cv.restore()
        drawLegs(cv, Species.SPRINGBOK, pose, false, 0f, mix(wh, tan, 0.15f), hoof, null)
    }

    private val elephantBody = floatArrayOf(0.42f, -0.5f, 0.38f, -0.9f, 0.2f, -1.0f, -0.05f, -0.98f, -0.3f, -0.92f, -0.46f, -0.78f, -0.5f, -0.58f, -0.42f, -0.42f, -0.1f, -0.38f, 0.2f, -0.4f, 0.36f, -0.42f)

    private fun elephant(cv: Canvas, pose: Pose, detail: Boolean, calf: Boolean) {
        val sk = c(0xFF807B74); val dk = c(0xFF46423E)
        val sp = if (calf) Species.CALF else Species.ELEPHANT
        drawLegs(cv, sp, pose, true, 0f, sk, scale(sk, 0.8f), null)
        cv.save(); cv.translate(0f, pose.bob)
        outline(clip, elephantBody)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, -1f, 0f, -0.38f, scale(sk, 1.06f), scale(sk, 0.85f), Shader.TileMode.CLAMP)
        cv.drawPath(clip, p); p.shader = null
        if (detail) {
            cv.save(); cv.clipPath(clip)
            stroke(withAlpha(dk, 90), 0.008f)
            for (k in 0 until 9) { path.reset(); path.moveTo(-0.45f + k * 0.08f, -0.5f); path.quadTo(-0.42f + k * 0.08f, -0.7f, -0.46f + k * 0.08f, -0.9f); cv.drawPath(path, p) }
            cv.restore()
        }
        val hx = 0.44f; val hy = -0.86f
        fill(sk); cv.drawOval(hx - 0.17f, hy - 0.2f, hx + 0.17f, hy + 0.2f, p)
        val sw = sin(pose.t * 0.6f) * 0.04f
        val low = if (pose.head < 0.5f) 0.1f else 0f
        stroke(sk, 0.13f)
        path.reset(); path.moveTo(hx + 0.1f, hy + 0.05f); path.quadTo(hx + 0.2f + sw, hy + 0.32f, hx + 0.16f + sw * 2, hy + 0.6f + low)
        cv.drawPath(path, p)
        p.strokeWidth = 0.08f
        path.reset(); path.moveTo(hx + 0.16f + sw * 2, hy + 0.58f + low); path.quadTo(hx + 0.15f + sw * 2, hy + 0.74f + low, hx + 0.2f + sw * 2.5f, hy + 0.78f + low)
        cv.drawPath(path, p)
        if (detail) {
            stroke(withAlpha(dk, 100), 0.006f)
            for (k in 0 until 8) { val t = 0.25f + k * 0.08f; cv.drawLine(hx + 0.13f + sw * t * 2, hy + 0.1f + t * 0.5f, hx + 0.21f + sw * t * 2, hy + 0.1f + t * 0.5f, p) }
        }
        if (!calf) {
            stroke(c(0xFFECE4CE), 0.04f)
            path.reset(); path.moveTo(hx + 0.1f, hy + 0.12f); path.quadTo(hx + 0.22f, hy + 0.3f, hx + 0.32f, hy + 0.26f)
            cv.drawPath(path, p)
        }
        // Ear, shaped like Africa, gently flapping.
        cv.save(); cv.translate(hx - 0.08f, hy - 0.02f); cv.scale(0.85f + 0.15f * sin(pose.t * 0.8f), 1f)
        fill(scale(sk, 0.9f))
        path.reset(); path.moveTo(0f, -0.16f); path.quadTo(-0.2f, -0.2f, -0.22f, 0f); path.quadTo(-0.2f, 0.18f, -0.06f, 0.26f); path.quadTo(0.02f, 0.12f, 0.02f, 0f); path.close()
        cv.drawPath(path, p)
        if (detail) { stroke(withAlpha(dk, 100), 0.008f); cv.drawPath(path, p) }
        cv.restore()
        fill(c(0xFF111111)); cv.drawCircle(hx + 0.06f, hy - 0.04f, 0.014f, p)
        stroke(dk, 0.02f)
        val tw = sin(pose.t * 1.1f) * 0.03f
        path.reset(); path.moveTo(-0.48f, -0.78f); path.quadTo(-0.53f, -0.6f, -0.52f + tw, -0.48f); cv.drawPath(path, p)
        cv.restore()
        drawLegs(cv, sp, pose, false, 0f, sk, scale(sk, 0.8f), null)
        if (detail) {
            fill(c(0xFFD6CEBE))
            for (i in intArrayOf(0, 2)) for (k in -1..1) cv.drawCircle(pose.feet[i * 2] + 0.02f + k * 0.035f, pose.feet[i * 2 + 1] - 0.012f, 0.012f, p)
        }
    }

    private val giraffeBody = floatArrayOf(0.38f, -1.2f, 0.3f, -1.42f, 0.1f, -1.44f, -0.12f, -1.32f, -0.3f, -1.22f, -0.36f, -1.08f, -0.3f, -0.98f, -0.05f, -0.98f, 0.2f, -1.02f, 0.34f, -1.06f)

    private fun giraffe(cv: Canvas, pose: Pose, detail: Boolean) {
        val base = c(0xFFE4BA76); val pat = c(0xFF78401E); val legC = mix(base, c(0xFFC8AA78), 0.2f)
        drawLegs(cv, Species.GIRAFFE, pose, true, 0f, legC, c(0xFF2A2420), null)
        cv.save(); cv.translate(0f, pose.bob)
        outline(clip, giraffeBody)
        fill(base); cv.drawPath(clip, p)
        shadeBody(cv)
        val na = -1.05f + (1f - pose.head) * 0.35f + sin(pose.t * 0.7f) * 0.03f
        val bx = 0.28f; val by = -1.3f
        val nx = bx + cos(na) * 0.95f; val ny = by + sin(na) * 0.95f; val pa = na + PI.toFloat() / 2f
        neckQuad(path2, bx, by, nx, ny, pa, 0.1f, 0.045f, 0.05f, 0.1f, 0.16f)
        path2.offset(0f, 0f)
        cv.drawPath(path2, p)
        if (detail) {
            cells(cv, clip, -0.4f, -1.5f, 0.42f, -0.95f, pat)
            cells(cv, path2, -0.2f, -2.4f, 0.9f, -1.0f, pat)
        } else {
            fill(withAlpha(pat, 110)); cv.drawPath(clip, p)
        }
        stroke(scale(pat, 0.8f), 0.03f)
        cv.drawLine(bx - 0.1f, by - 0.06f, nx - cos(pa) * 0.05f, ny - sin(pa) * 0.05f, p)
        cv.save(); cv.translate(nx, ny); cv.rotate(Math.toDegrees((na + 1.25f).toDouble()).toFloat())
        fill(base)
        path.reset(); path.moveTo(-0.03f, -0.05f); path.quadTo(0.12f, -0.07f, 0.2f, -0.03f); path.quadTo(0.22f, 0.02f, 0.19f, 0.04f); path.quadTo(0.08f, 0.06f, -0.03f, 0.05f); path.close()
        cv.drawPath(path, p)
        fill(scale(base, 0.7f)); cv.drawCircle(0.18f, 0.01f, 0.03f, p)
        stroke(scale(pat, 0.7f), 0.018f)
        cv.drawLine(0f, -0.05f, -0.02f, -0.12f, p); cv.drawLine(0.03f, -0.05f, 0.03f, -0.12f, p)
        fill(c(0xFF1A1A1A)); cv.drawCircle(-0.02f, -0.12f, 0.012f, p); cv.drawCircle(0.03f, -0.12f, 0.012f, p)
        fill(c(0xFF0C0C0C)); cv.drawCircle(0.05f, -0.015f, 0.01f, p)
        cv.restore()
        tail(cv, pose, scale(base, 0.8f), c(0xFF1A1A1A), -0.35f, -1.16f, -0.4f, -0.78f, 1.2f)
        cv.restore()
        drawLegs(cv, Species.GIRAFFE, pose, false, 0f, legC, c(0xFF2A2420), null)
    }

    /** A giraffe's patch network: irregular polygons laid in offset rows, clipped to the shape. */
    private fun cells(cv: Canvas, shape: Path, x0: Float, y0: Float, x1: Float, y1: Float, col: Int) {
        cv.save(); cv.clipPath(shape)
        fill(col)
        var r = 7
        var y = y0
        while (y < y1) {
            var x = x0
            val row = Math.round(y / 0.075f)
            while (x < x1) {
                r = (r * 9301 + 49297) % 233280
                val j = r / 233280f
                val cx = x + (j - 0.5f) * 0.03f + (row % 2) * 0.035f
                path.reset()
                path.moveTo(cx - 0.03f, y - 0.02f); path.lineTo(cx, y - 0.033f); path.lineTo(cx + 0.032f, y - 0.015f)
                path.lineTo(cx + 0.028f, y + 0.02f); path.lineTo(cx - 0.005f, y + 0.032f); path.lineTo(cx - 0.033f, y + 0.012f); path.close()
                cv.drawPath(path, p)
                x += 0.075f
            }
            y += 0.075f
        }
        cv.restore()
    }

    // ---- Lions (from the in-chat lion studio) -------------------------------------------
    // Three-jointed legs: hind = thigh, forward stifle, hock, paw; front = upper arm, elbow
    // tucked behind, forearm, wrist, paw. The near legs go over the body but under the mane.

    private val lionShape = floatArrayOf(.36f, -.42f, .3f, -.56f, .2f, -.6f, .06f, -.555f, -.1f, -.545f, -.26f, -.565f, -.38f, -.53f, -.45f, -.44f, -.42f, -.33f, -.3f, -.3f, -.12f, -.27f, .08f, -.27f, .26f, -.28f, .36f, -.33f)
    private val lieShape = floatArrayOf(-.46f, -.04f, -.47f, -.22f, -.3f, -.31f, 0f, -.3f, .24f, -.36f, .34f, -.3f, .34f, -.1f, .2f, -.02f, -.1f, 0f)
    private val lionTmp = FloatArray(28)

    /** Smooth closed outline through midpoints (matches the studio's blob()). */
    private fun blob(out: Path, pts: FloatArray, n: Int = pts.size / 2) {
        out.reset()
        out.moveTo((pts[(n - 1) * 2] + pts[0]) / 2, (pts[(n - 1) * 2 + 1] + pts[1]) / 2)
        for (i in 0 until n) {
            val j = (i + 1) % n
            out.quadTo(pts[i * 2], pts[i * 2 + 1], (pts[i * 2] + pts[j * 2]) / 2, (pts[i * 2 + 1] + pts[j * 2 + 1]) / 2)
        }
        out.close()
    }

    private fun oval(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f) {
        if (rot != 0f) { cv.save(); cv.rotate(Math.toDegrees(rot.toDouble()).toFloat(), cx, cy) }
        cv.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)
        if (rot != 0f) cv.restore()
    }

    private fun lionLeg(cv: Canvas, i: Int, pose: Pose, low: Float, far: Boolean, coat: Int, dk: Int, detail: Boolean) {
        val front = i >= 2
        val hx = if (front) .24f else -.3f
        val hy = (if (front) -.48f else -.5f) + pose.bob + low
        val fx = pose.feet[i * 2]
        val fy = pose.feet[i * 2 + 1]
        val fl = pose.flex[i]
        val col = if (far) mix(coat, c(0xFF281A0E), .28f) else coat
        if (!front) {
            val th = .9f * max(0f, fl) + min(0f, fl)
            val vx = -.045f; val vy = -.15f
            val hkx = fx + vx * cos(th) - vy * sin(th)
            val hky = fy + vx * sin(th) + vy * cos(th)
            ik(hx, hy, hkx, hky, .22f, .23f, true)
            val k0x = kx; val k0y = ky
            fill(col); oval(cv, (hx + k0x) / 2 - .01f, (hy + k0y) / 2 - .02f, .13f, .085f, atan2(k0y - hy, k0x - hx))
            seg(cv, hx, hy, k0x, k0y, .17f, .1f, col)
            seg(cv, k0x, k0y, hkx, hky, .1f, .06f, col)
            seg(cv, hkx, hky, fx + .02f, fy - .03f, .06f, .055f, col)
            fill(col); oval(cv, fx + .035f, fy - .025f, .055f, .028f)
        } else {
            val th = 1.5f * max(0f, fl)
            val vx = -.012f; val vy = -.075f
            val wx = fx + vx * cos(th) - vy * sin(th)
            val wy = fy + vx * sin(th) + vy * cos(th)
            ik(hx, hy, wx, wy, .19f, .25f, false)
            val k0x = kx; val k0y = ky
            seg(cv, hx + .02f, hy - .05f, k0x, k0y, .16f, .1f, col)
            seg(cv, k0x, k0y, wx, wy, .095f, .07f, col)
            fill(col)
            if (fl > .2f) oval(cv, wx - .02f, wy + .03f, .058f, .03f, 1.2f) else oval(cv, fx + .035f, fy - .026f, .058f, .03f)
        }
        if (detail) {
            fill(dk)
            for (t in -1..1) cv.drawCircle(fx + .04f + t * .016f, fy - .006f, .007f, p)
        }
    }

    private fun mane(cv: Canvas, t: Float) {
        val z = SavannaDesign.LION_MANE_SIZE
        val dark = SavannaDesign.LION_MANE_DARK
        val outer = mix(c(0xFF8C5C2E), c(0xFF362012), dark)
        val inner = mix(c(0xFFC48C48), c(0xFF784E28), dark * .8f)
        val n = 46
        for (j in 0..1) {
            val k = if (j == 0) 1.15f else .85f
            fill(if (j == 0) outer else inner)
            path.reset()
            for (i in 0..n) {
                val a = i / n.toFloat() * PI.toFloat() * 2f
                val back = if (cos(a) < 0f) 1f else 0f
                var r = (.15f + .05f * back + .05f * max(0f, sin(a))) * z * k
                r += (if (i % 2 == 1) .03f else -.005f) * z * (if (j == 1) .6f else 1f) + .006f * sin(t * 1.3f + i)
                val x = cos(a) * r * 1.05f - .03f
                val y = sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            cv.drawPath(path, p)
        }
    }

    private fun lionHead(cv: Canvas, x: Float, y: Float, ang: Float, male: Boolean, coat: Int, t: Float) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        if (male) mane(cv, t)
        val dk = mix(coat, c(0xFF3C2412), .45f)
        val lt = mix(coat, c(0xFFF8E8C8), .55f)
        fill(coat); oval(cv, -.01f, -.01f, .095f, .085f)
        path.reset(); path.moveTo(.02f, -.07f); path.quadTo(.13f, -.075f, .165f, -.03f); path.quadTo(.18f, .02f, .155f, .045f); path.quadTo(.08f, .075f, 0f, .06f); path.close()
        cv.drawPath(path, p)
        fill(lt); oval(cv, .12f, .03f, .05f, .03f); oval(cv, .07f, .06f, .05f, .022f)
        fill(c(0xFF3A2420))
        path.reset(); path.moveTo(.15f, -.035f); path.lineTo(.178f, -.03f); path.lineTo(.172f, 0f); path.lineTo(.152f, -.008f); path.close()
        cv.drawPath(path, p)
        stroke(c(0xFF2A1A12), .008f)
        path.reset(); path.moveTo(.165f, .005f); path.quadTo(.15f, .04f, .11f, .045f); cv.drawPath(path, p)
        fill(c(0xFF2A1A10)); oval(cv, .05f, -.045f, .016f, .009f, -.2f)
        fill(lt); oval(cv, .05f, -.06f, .02f, .006f, -.2f)
        fill(coat); oval(cv, -.045f, -.08f, .032f, .028f, -.4f)
        fill(dk); oval(cv, -.045f, -.078f, .018f, .016f, -.4f)
        cv.restore()
    }

    private fun lion(cv: Canvas, pose: Pose, detail: Boolean, male: Boolean) {
        val coat = mix(c(0xFFCEA062), c(0xFFB08046), SavannaDesign.LION_COAT)
        val belly = mix(coat, c(0xFFF0DEB8), .55f)
        val back = mix(coat, c(0xFF785028), .25f)
        val dk = c(0xFF462C18)
        val t = pose.t
        if (pose.act == Act.LIE) {
            fill(coat); oval(cv, -.24f, -.12f, .15f, .11f, -.2f)
            seg(cv, -.16f, -.05f, 0f, -.025f, .06f, .05f, coat); oval(cv, .02f, -.022f, .05f, .025f)
            blob(clip, lieShape)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, -.34f, 0f, 0f, back, belly, Shader.TileMode.CLAMP)
            cv.drawPath(clip, p); p.shader = null
            fill(coat); oval(cv, -.27f, -.13f, .13f, .1f, -.25f)
            val farC = mix(coat, c(0xFF281A0E), .2f)
            seg(cv, .24f, -.12f, .52f, -.035f, .1f, .065f, farC); oval(cv, .555f, -.026f, .055f, .027f)
            seg(cv, .2f, -.1f, .48f, -.03f, .1f, .065f, coat); oval(cv, .515f, -.026f, .058f, .028f)
            val tw = sin(t * .8f) * .04f
            stroke(coat, .028f)
            path.reset(); path.moveTo(-.44f, -.08f); path.cubicTo(-.6f, -.02f, -.7f, -.03f, -.76f, -.05f + tw); cv.drawPath(path, p)
            fill(dk); oval(cv, -.78f, -.05f + tw, .035f, .022f)
            lionHead(cv, .37f, -.45f, -.05f + sin(t * .4f) * .04f, male, coat, t)
            return
        }
        val gallop = pose.act == Act.RUN
        val stalk = pose.act == Act.STALK
        val low = if (stalk) .11f else 0f
        val fl = if (gallop) .05f * sin(2f * PI.toFloat() * pose.phase) else 0f
        val sh = if (stalk) -.05f else 0f
        val pitchDeg = Math.toDegrees(pose.pitch.toDouble()).toFloat()

        for (i in intArrayOf(1, 3)) lionLeg(cv, i, pose, low, true, coat, dk, detail)

        cv.save(); cv.translate(0f, pose.bob + low); cv.rotate(pitchDeg)
        for (i in 0 until 14) {
            val x = lionShape[i * 2]
            var y = lionShape[i * 2 + 1]
            if (i == 1) y += sh * .4f
            if (i == 2) y += sh
            lionTmp[i * 2] = if (x < 0f) x * (1f + fl) else x
            lionTmp[i * 2 + 1] = y
        }
        blob(clip, lionTmp, 14)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, -.6f, 0f, -.27f, intArrayOf(back, coat, belly), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        cv.drawPath(clip, p); p.shader = null
        if (detail) { fill(withAlpha(mix(coat, c(0xFFFFF0D2), .25f), 64)); oval(cv, -.3f, -.42f, .12f, .1f) }
        val ta = if (gallop) -.1f else sin(t * 1.1f) * .05f
        stroke(coat, .03f)
        path.reset(); path.moveTo(-.43f, -.5f)
        if (gallop) path.cubicTo(-.6f, -.52f, -.72f, -.5f, -.82f, -.56f) else path.cubicTo(-.56f, -.42f, -.6f, -.2f, -.6f + ta, -.12f)
        cv.drawPath(path, p)
        fill(dk)
        if (gallop) oval(cv, -.84f, -.57f, .04f, .022f, -.3f) else oval(cv, -.6f + ta, -.1f, .024f, .04f)
        cv.restore()

        for (i in intArrayOf(0, 2)) lionLeg(cv, i, pose, low, false, coat, dk, detail)

        cv.save(); cv.translate(0f, pose.bob + low); cv.rotate(pitchDeg)
        val nod = if (pose.act == Act.WALK) .03f * sin(4f * PI.toFloat() * pose.phase) else 0f
        var hx = .42f
        var hy = -.55f
        var ha = .05f
        if (stalk) { hx = .44f; hy = -.43f; ha = .15f }
        if (gallop) { hx = .45f; hy = -.52f; ha = .1f }
        if (pose.act == Act.STAND) ha = -.05f + sin(t * .3f) * .06f
        fill(coat)
        path.reset(); path.moveTo(.2f, -.6f + sh); path.quadTo(hx - .05f, hy - .06f, hx, hy - .06f); path.lineTo(hx + .02f, hy + .07f); path.quadTo(.36f, -.36f, .3f, -.32f); path.close()
        cv.drawPath(path, p)
        if (stalk) { fill(withAlpha(back, 153)); oval(cv, .22f, -.59f, .05f, .03f) }
        lionHead(cv, hx, hy + nod, ha, male, coat, t)
        cv.restore()
    }

    // ---- Vulture ---------------------------------------------------------------------

    /** A perched or flying vulture, drawn in units of ~one body height. */
    fun vulture(cv: Canvas, perched: Boolean, flap: Float, soaring: Boolean, bob: Float, look: Float = 0f) {
        val body = c(0xFF463C34); val neck = c(0xFFC4AA96); val pale = c(0xFFDCCEB4)
        if (perched) {
            fill(body); cv.save(); cv.rotate(-14.3f, 0f, -1f); cv.drawOval(-0.75f, -2.05f, 0.75f, 0.05f, p); cv.restore()
            fill(pale); cv.drawOval(0.1f, -2.13f, 0.74f, -1.77f, p)
            stroke(neck, 0.22f); path.reset(); path.moveTo(0.4f, -1.9f); path.quadTo(0.7f, -2.1f + bob, 0.62f + look, -2.35f + bob); cv.drawPath(path, p)
            fill(neck); cv.drawCircle(0.66f + look, -2.38f + bob, 0.2f, p)
            fill(c(0xFF3C3834)); path.reset(); path.moveTo(0.82f + look, -2.42f + bob); path.lineTo(1f + look, -2.3f + bob); path.lineTo(0.82f + look, -2.3f + bob); path.close(); cv.drawPath(path, p)
            fill(scale(body, 0.8f)); path.reset(); path.moveTo(-0.5f, -0.4f); path.lineTo(-0.9f, 0.3f); path.lineTo(-0.3f, -0.1f); path.close(); cv.drawPath(path, p)
            return
        }
        val up = if (soaring) -0.2f else sin(flap) * 0.8f
        val span = 4.2f
        fill(body)
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(0.35f * side, -0.05f)
            path.quadTo(span * 0.3f * side, -0.25f + up * 0.6f, span * 0.5f * side, up)
            for (k in 0 until 5) path.lineTo((span * 0.5f - 0.08f - k * 0.12f) * side, up + 0.18f + (k % 2) * 0.06f)
            path.quadTo(span * 0.25f * side, 0.22f + up * 0.4f, 0.35f * side, 0.12f)
            path.close()
            cv.drawPath(path, p)
        }
        cv.drawOval(-0.45f, -0.2f, 0.45f, 0.24f, p)
        path.reset(); path.moveTo(-0.4f, 0f); path.lineTo(-0.85f, 0.12f); path.lineTo(-0.4f, 0.15f); path.close(); cv.drawPath(path, p)
        fill(neck); cv.drawCircle(0.55f, -0.02f, 0.13f, p)
    }
}
