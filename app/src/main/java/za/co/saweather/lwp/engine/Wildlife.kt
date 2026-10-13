package za.co.saweather.lwp.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

enum class Horns { NONE, LYRE, SPIRAL, TWISTED, SHORT }

/**
 * Proportions of a four-legged animal, in units of body length. One parametric rig draws
 * lions, antelope, elephants, giraffes, leopards and more.
 */
data class Quad(
    val color: Int,
    val bodyH: Float = 0.42f,
    val legLen: Float = 0.55f,
    val legW: Float = 0.09f,
    val neckLen: Float = 0.35f,
    val neckAngle: Float = 35f,
    val neckW: Float = 0.16f,
    val headLen: Float = 0.3f,
    val headH: Float = 0.17f,
    val ear: Float = 0.06f,
    val tailLen: Float = 0.45f,
    val tailDroop: Float = 60f,
    val horns: Horns = Horns.NONE,
    val hornLen: Float = 0.3f,
    val mane: Int? = null,
    val spots: Int? = null,
    val belly: Int? = null,
    val trunk: Boolean = false,
    val shoulderHump: Float = 0f,
)

/** Drawing kit for animated animal silhouettes built from paths and stroked limbs. */
object Wildlife {
    private val path = Path()
    private val oval = RectF()
    private val limb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /**
     * @param x,y ground point under the body centre
     * @param s body length in px
     * @param facing +1 faces right, -1 faces left
     * @param gait walk-cycle phase in radians
     * @param stride 0 standing .. 1 full gallop
     * @param graze 0 head up .. 1 head down to the ground
     */
    fun quad(
        c: Canvas, p: Paint, q: Quad, x: Float, y: Float, s: Float, facing: Float,
        gait: Float, stride: Float, color: Int, graze: Float = 0f, tail: Float = 0f, darker: Int = color,
    ) {
        c.save()
        c.translate(x, y)
        c.scale(facing * s, s)
        val leg = q.legLen
        val bodyY = -(leg + q.bodyH * 0.5f)
        val run = stride > 0.6f
        // Body bob with the gait.
        val bob = if (stride > 0f) sin(gait * 2f) * 0.015f * (1f + stride * 2f) else 0f
        c.translate(0f, bob)

        limb.strokeWidth = q.legW
        // Far legs first (darker), then body, then near legs.
        limb.color = darker
        legPair(c, q, bodyY, gait + PI_F, stride, run)
        p.color = color

        // Tail
        limb.color = color
        limb.strokeWidth = q.legW * 0.4f
        val ta = Math.toRadians((q.tailDroop - tail * 25f + sin(gait) * 6f * stride).toDouble())
        val tx = -0.5f; val ty = bodyY - q.bodyH * 0.3f
        path.reset()
        path.moveTo(tx, ty)
        path.quadTo(tx - q.tailLen * 0.6f, ty, tx - q.tailLen * cos(ta).toFloat(), ty + q.tailLen * sin(ta).toFloat())
        c.drawPath(path, limb)
        if (q.mane != null) {
            p.color = q.mane
            c.drawCircle(tx - q.tailLen * cos(ta).toFloat(), ty + q.tailLen * sin(ta).toFloat(), q.legW * 0.6f, p)
            p.color = color
        }

        // Body: torso oval, plus haunch and chest masses.
        oval.set(-0.52f, bodyY - q.bodyH / 2, 0.52f, bodyY + q.bodyH / 2)
        c.drawOval(oval, p)
        c.drawCircle(-0.33f, bodyY - q.bodyH * 0.05f, q.bodyH * 0.55f, p)
        c.drawCircle(0.32f, bodyY - q.bodyH * 0.02f - q.shoulderHump * 0.1f, q.bodyH * (0.56f + q.shoulderHump * 0.3f), p)
        if (q.belly != null) {
            p.color = q.belly
            oval.set(-0.38f, bodyY + q.bodyH * 0.12f, 0.38f, bodyY + q.bodyH * 0.5f)
            c.drawOval(oval, p)
            p.color = color
        }
        if (q.spots != null) {
            p.color = q.spots
            val r = q.bodyH * 0.07f
            var k = 0
            for (i in -4..4) for (j in -1..1) {
                k++
                c.drawCircle(i * 0.1f + (j and 1) * 0.05f, bodyY + j * q.bodyH * 0.25f, r * (0.8f + (k % 3) * 0.2f), p)
            }
            p.color = color
        }

        // Neck and head.
        val neckA = Math.toRadians((q.neckAngle - graze * (q.neckAngle + 55f)).toDouble())
        val nx0 = 0.38f; val ny0 = bodyY - q.bodyH * 0.25f
        val nx1 = nx0 + q.neckLen * cos(neckA).toFloat()
        val ny1 = ny0 - q.neckLen * sin(neckA).toFloat()
        limb.color = color
        limb.strokeWidth = q.neckW
        c.drawLine(nx0, ny0, nx1, ny1, limb)
        if (q.mane != null) {
            p.color = q.mane
            c.drawCircle(nx0 + (nx1 - nx0) * 0.8f, ny0 + (ny1 - ny0) * 0.8f, q.neckW * 1.0f, p)
            c.drawCircle(nx0 + (nx1 - nx0) * 0.35f, ny0 + (ny1 - ny0) * 0.35f, q.neckW * 0.8f, p)
            p.color = color
        }
        c.save()
        c.translate(nx1, ny1)
        val headTilt = -15f + graze * 70f
        c.rotate(headTilt)
        oval.set(-q.headLen * 0.25f, -q.headH * 0.5f, q.headLen * 0.75f, q.headH * 0.5f)
        c.drawOval(oval, p)
        // Ears
        if (q.ear > 0f) {
            if (q.trunk) {
                oval.set(-q.headLen * 0.55f, -q.headH * 0.9f, q.headLen * 0.15f, q.headH * 0.9f)
                p.color = darker
                c.drawOval(oval, p)
                p.color = color
            } else {
                path.reset()
                path.moveTo(-q.headLen * 0.05f, -q.headH * 0.35f)
                path.lineTo(-q.headLen * 0.12f, -q.headH * 0.5f - q.ear * 1.6f)
                path.lineTo(q.headLen * 0.12f, -q.headH * 0.4f)
                path.close()
                c.drawPath(path, p)
            }
        }
        when (q.horns) {
            Horns.NONE -> {}
            Horns.LYRE -> {
                limb.strokeWidth = q.legW * 0.35f
                path.reset()
                path.moveTo(0f, -q.headH * 0.4f)
                path.cubicTo(-q.hornLen * 0.2f, -q.hornLen * 0.5f, q.hornLen * 0.1f, -q.hornLen * 0.8f, -q.hornLen * 0.05f, -q.hornLen)
                c.drawPath(path, limb)
            }
            Horns.SPIRAL -> {
                limb.strokeWidth = q.legW * 0.35f
                path.reset()
                path.moveTo(0f, -q.headH * 0.4f)
                for (i in 1..12) {
                    val t = i / 12f
                    path.lineTo(-t * q.hornLen * 0.5f + sin(t * 14f) * 0.03f, -q.headH * 0.4f - t * q.hornLen)
                }
                c.drawPath(path, limb)
            }
            Horns.TWISTED -> {
                limb.strokeWidth = q.legW * 0.4f
                c.drawLine(0f, -q.headH * 0.4f, -q.hornLen * 0.45f, -q.headH * 0.4f - q.hornLen * 0.8f, limb)
            }
            Horns.SHORT -> {
                limb.strokeWidth = q.legW * 0.4f
                c.drawLine(0.02f, -q.headH * 0.4f, -0.02f, -q.headH * 0.4f - q.hornLen, limb)
            }
        }
        if (q.trunk) {
            limb.strokeWidth = q.headH * 0.35f
            path.reset()
            path.moveTo(q.headLen * 0.6f, 0f)
            val sway = sin(gait * 0.5f) * 0.05f
            path.cubicTo(q.headLen * 0.9f, q.headH * 0.6f, q.headLen * 0.7f + sway, q.headH * 1.4f, q.headLen * 0.85f + sway, q.headH * 2.2f)
            c.drawPath(path, limb)
            limb.strokeWidth = q.headH * 0.12f
            limb.color = 0xFFEDE6D2.toInt()
            c.drawLine(q.headLen * 0.55f, q.headH * 0.3f, q.headLen * 0.9f, q.headH * 0.65f, limb)
            limb.color = color
        }
        c.restore()

        limb.strokeWidth = q.legW
        limb.color = color
        legPair(c, q, bodyY, gait, stride, run)
        c.restore()
    }

    private fun legPair(c: Canvas, q: Quad, bodyY: Float, gait: Float, stride: Float, run: Boolean) {
        // Rear and front leg on one side; the front leg is offset by a quarter cycle (walk) or
        // runs in phase-pairs (gallop).
        val frontOffset = if (run) 0.6f else PI_F / 2
        leg(c, q, -0.36f, bodyY + q.bodyH * 0.2f, gait, stride, true)
        leg(c, q, 0.34f, bodyY + q.bodyH * 0.2f, gait + frontOffset, stride, false)
    }

    private fun leg(c: Canvas, q: Quad, hx: Float, hy: Float, ph: Float, stride: Float, hind: Boolean) {
        val swing = (0.15f + 0.5f * stride) * sin(ph)
        val lift = (0.5f + 0.5f * cos(ph)).let { if (it > 0.5f) (it - 0.5f) * 2f else 0f } * stride
        val upper = q.legLen * 0.5f
        val lower = q.legLen * 0.52f
        val a1 = swing
        val kx = hx + upper * sin(a1)
        val ky = hy + upper * cos(a1)
        // Knees bend backwards on hind legs, forwards on fore legs.
        val bend = (0.12f + lift * 0.9f) * (if (hind) -1f else 1f)
        val a2 = a1 - bend
        val fx = kx + lower * sin(a2)
        val fy = ky + lower * cos(a2)
        c.drawLine(hx, hy, kx, ky, limb)
        c.drawLine(kx, ky, fx, fy, limb)
    }

    /** A bird in flight. [flap] -1..1 drives wing position; 0 is a flat glide. */
    fun bird(c: Canvas, p: Paint, x: Float, y: Float, span: Float, flap: Float, facing: Float, color: Int, tailFan: Boolean = false, accent: Int? = null) {
        c.save()
        c.translate(x, y)
        c.scale(facing * span, span)
        p.color = color
        val tip = -flap * 0.32f
        val mid = -flap * 0.12f - 0.03f
        path.reset()
        // Left wing (trailing), body, right wing — drawn as one closed silhouette.
        path.moveTo(-0.08f, 0f)
        path.quadTo(-0.25f, mid - 0.05f, -0.5f, tip)
        path.quadTo(-0.28f, mid + 0.06f, -0.06f, 0.05f)
        path.lineTo(0.06f, 0.05f)
        path.quadTo(0.28f, mid + 0.06f, 0.5f, tip)
        path.quadTo(0.25f, mid - 0.05f, 0.08f, 0f)
        path.close()
        c.drawPath(path, p)
        // Body and head along the flight direction (perspective: slight).
        oval.set(-0.06f, -0.03f, 0.18f, 0.05f)
        c.drawOval(oval, p)
        c.drawCircle(0.2f, -0.005f, 0.035f, p)
        if (tailFan) {
            path.reset(); path.moveTo(-0.04f, 0f); path.lineTo(-0.16f, -0.04f); path.lineTo(-0.16f, 0.06f); path.close()
            c.drawPath(path, p)
        }
        if (accent != null) {
            p.color = accent
            path.reset()
            path.moveTo(-0.2f, mid * 0.6f); path.quadTo(-0.35f, mid, -0.45f, tip * 0.9f); path.quadTo(-0.3f, mid + 0.04f, -0.15f, 0.03f); path.close()
            c.drawPath(path, p)
            path.reset()
            path.moveTo(0.2f, mid * 0.6f); path.quadTo(0.35f, mid, 0.45f, tip * 0.9f); path.quadTo(0.3f, mid + 0.04f, 0.15f, 0.03f); path.close()
            c.drawPath(path, p)
        }
        c.restore()
    }

    /** Side-on bird with long legs (secretary bird) or perched bird. */
    fun walkingBird(c: Canvas, p: Paint, x: Float, y: Float, h: Float, facing: Float, step: Float, color: Int, legColor: Int, crest: Boolean) {
        c.save()
        c.translate(x, y)
        c.scale(facing * h, h)
        limb.color = legColor
        limb.strokeWidth = 0.035f
        val sw = sin(step) * 0.12f
        c.drawLine(0f, -0.48f, sw, 0f, limb)
        c.drawLine(0f, -0.48f, -sw, 0f, limb)
        p.color = color
        oval.set(-0.22f, -0.72f, 0.18f, -0.44f)
        c.drawOval(oval, p)
        // Tail streamers
        limb.color = color
        limb.strokeWidth = 0.05f
        c.drawLine(-0.18f, -0.55f, -0.42f, -0.36f, limb)
        limb.strokeWidth = 0.05f
        c.drawLine(0.12f, -0.66f, 0.18f, -0.9f, limb)
        c.drawCircle(0.2f, -0.94f, 0.06f, p)
        if (crest) {
            limb.strokeWidth = 0.015f
            for (i in 0 until 5) c.drawLine(0.16f, -0.96f, 0.02f - i * 0.02f, -1.02f + i * 0.025f, limb)
        }
        c.restore()
    }

    /** Slithering snake: a tapered sine-wave stroke. */
    fun snake(c: Canvas, p: Paint, x: Float, y: Float, len: Float, phase: Float, facing: Float, thick: Float, color: Int, pattern: Int? = null) {
        val segs = 22
        var px = x
        var py = y
        for (i in 1..segs) {
            val t = i / segs.toFloat()
            val nx = x - facing * len * t
            val ny = y + sin(phase - t * 9f) * thick * 1.6f * (0.4f + t)
            limb.color = if (pattern != null && i % 3 == 0) pattern else color
            limb.strokeWidth = thick * (1f - t * 0.85f).coerceAtLeast(0.15f)
            c.drawLine(px, py, nx, ny, limb)
            px = nx; py = ny
        }
        p.color = color
        oval.set(x - thick * 0.9f, y - thick * 0.55f, x + thick * 1.1f, y + thick * 0.55f)
        c.drawOval(oval, p)
    }

    fun stroke(): Paint = limb
}
