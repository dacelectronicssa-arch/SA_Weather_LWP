package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * A skein of Egyptian geese crossing the savanna sky in a V (from the in-chat birds preview).
 * Seen from below: the near wing hangs under the body, the far wing shows above it, and both
 * beat together, bending at the wrist.
 */
class GooseSkein {

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f
    private var x = 0f
    private var y = 0f
    private var dir = 1f
    private var t = 0f
    var active = false
        private set

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun setSize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
    }

    /** Body length of one goose, in pixels. */
    private val s get() = 4.2f * (w / PREVIEW_W) * SavannaDesign.FLOCK_SIZE

    fun launch() {
        if (w == 0f) return
        dir = if (rnd.nextBoolean()) 1f else -1f
        x = if (dir > 0) -w * 0.15f else w * 1.15f
        y = h * (0.2f + rnd.nextFloat() * 0.18f)
        t = 0f
        active = true
    }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        x += dir * w * 0.075f * dt
        // The whole V (trailing birds included) has left the screen.
        val tail = (SavannaDesign.FLOCK_BIRDS / 2 + 1) * s * 4.2f * 1.1f + s * 3f
        if ((dir > 0 && x - tail > w) || (dir < 0 && x + tail < 0f)) active = false
    }

    fun draw(c: Canvas) {
        if (!active) return
        val sp = s * 4.2f
        for (i in 0 until SavannaDesign.FLOCK_BIRDS) {
            val k = ceil(i / 2f)
            val side = if (i % 2 == 1) 1f else -1f
            val bx = x - dir * k * sp * 1.1f
            val by = y + side * k * sp * 0.62f + sin(t * 1.3f + k) * s * 0.8f
            goose(c, bx, by, t * 6f - k * 0.45f + (i % 2) * 0.2f)
        }
    }

    private fun goose(c: Canvas, x: Float, y: Float, fl: Float) {
        c.save()
        c.translate(x, y)
        c.scale(s * dir, s)
        wing(c, fl, near = false, col = 0xFF3E3024.toInt())
        p.style = Paint.Style.FILL
        p.color = 0xFF6A5440.toInt(); oval(c, -0.1f, 0f, 1.15f, 0.36f)
        p.color = 0xFFD8C6A8.toInt(); oval(c, -0.1f, 0.12f, 0.9f, 0.18f)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 0.3f; p.color = 0xFF6A5440.toInt()
        path.reset(); path.moveTo(0.85f, -0.08f); path.quadTo(1.35f, -0.2f, 1.8f, -0.22f); c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF5A4634.toInt(); oval(c, 1.9f, -0.24f, 0.26f, 0.17f)
        p.color = 0xFF7A3A22.toInt(); oval(c, 1.95f, -0.26f, 0.08f, 0.07f)
        p.color = 0xFFE8A2A0.toInt(); tri(c, 2.1f, -0.29f, 2.45f, -0.22f, 2.1f, -0.16f)
        p.color = 0xFFE0A070.toInt(); tri(c, -1.15f, 0.05f, -1.55f, 0.12f, -1.15f, 0.15f)
        p.color = 0xFF2A2018.toInt(); oval(c, -1.2f, -0.02f, 0.18f, 0.12f)
        wing(c, fl, near = true, col = 0xFF5A4632.toInt())
        c.restore()
    }

    private fun wing(c: Canvas, fl: Float, near: Boolean, col: Int) {
        val a1 = 0.12f + 0.8f * sin(fl)
        val a2 = a1 + 0.5f * sin(fl - 1.1f)
        val side = if (near) 1f else -1f
        val y1 = L1 * (side * cos(a1) * SE - sin(a1) * CE)
        val y2 = y1 + L2 * (side * cos(a2) * SE - sin(a2) * CE)
        val sc = if (near) 1f else 0.85f
        val sg = if (y1 == 0f) 1f else sign(y1)
        val tx = -0.55f
        p.style = Paint.Style.FILL
        p.color = col
        path.reset()
        path.moveTo(0.35f, 0f)
        path.quadTo(0.3f, y1 * 0.5f * sc, 0.18f, y1 * sc)
        path.quadTo(0f, (y1 + y2) / 2 * sc, tx, y2 * sc)
        path.quadTo(-0.45f, (y1 + y2) / 2 * sc + 0.1f, -0.35f, y1 * sc + 0.12f * sg)
        path.quadTo(-0.4f, y1 * 0.4f * sc, -0.3f, 0.08f)
        path.close()
        c.drawPath(path, p)
        if (near) {
            p.color = 0xFFEFE8DC.toInt() // the white wing patch
            path.reset()
            path.moveTo(0.25f, y1 * 0.25f)
            path.quadTo(0.2f, y1 * 0.7f, 0.12f, y1 * 0.95f)
            path.lineTo(-0.22f, y1 * 0.95f + 0.05f * sg)
            path.quadTo(-0.25f, y1 * 0.5f, -0.15f, y1 * 0.2f)
            path.close()
            c.drawPath(path, p)
            p.color = 0xFF2A2018.toInt() // dark primaries
            tri(c, 0.1f, y1 + (y2 - y1) * 0.45f, tx, y2, -0.4f, y1 + (y2 - y1) * 0.5f)
        }
    }

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) = c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, p)
    }

    private companion object {
        const val PREVIEW_W = 680f
        const val L1 = 1.05f
        const val L2 = 1.35f
        // The flock is seen from below at about 31°.
        val SE = sin(0.55f)
        val CE = cos(0.55f)
    }
}
