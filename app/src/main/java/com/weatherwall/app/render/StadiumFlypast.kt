package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * The Airlink stadium flypast (Springboks v All Blacks, August 2026): two Embraer E-jets come
 * in from the left, dive across the city to just above Cape Town Stadium's roof and stay low
 * as they run out to the right, while smoke cannons along the roof rim fire coloured plumes
 * and sparks in turn as the jets pass. From the in-chat flypast preview.
 */
class StadiumFlypast {

    private class Puff(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, val max: Float, val col: Int, var delay: Float) { var life = 0f }
    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val max: Float, var delay: Float) { var life = 0f }

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f
    private var t = -1f
    private val fired = BooleanArray(CANNONS)
    private val smoke = ArrayList<Puff>()
    private val sparks = ArrayList<Spark>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val path = Path()
    private val tints = HashMap<Int, Paint>()
    private val dst = android.graphics.RectF()
    private val puff: android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).also {
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = android.graphics.RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0xB0FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        Canvas(it).drawCircle(32f, 32f, 32f, pp)
    }

    val active get() = t >= 0f

    fun setSize(width: Int, height: Int) { w = width.toFloat(); h = height.toFloat() }

    // Stadium geometry, matching CapeTownLandscape.
    private val k get() = w / PREVIEW_W
    private val stCx get() = w * 0.865f
    private val stW get() = w * 0.2f
    private val stH get() = stW * 0.19f
    private val stTop get() = h * 0.72f + 1f - stH
    private val stX0 get() = stCx - stW / 2f

    fun launch() {
        if (w == 0f || active) return
        t = 0f
        fired.fill(false)
        smoke.clear(); sparks.clear()
    }

    /** Where a jet is at progress [u] (0..1.4) along the run, [lag] behind the lead. */
    private fun pos(u0: Float, lag: Float, out: FloatArray) {
        val u = (u0 - lag).coerceIn(0f, 1.4f)
        val x = stCx - w * 1.15f + u * w * 1.55f
        val dx = (x - stCx) / w
        val roof = stTop - stH * 0.35f * LOWNESS
        val y = if (dx < 0f) roof - h * 0.22f * min(1f, (-dx).pow(1.6f)) else roof - h * 0.012f * min(1f, dx / 0.3f)
        out[0] = x; out[1] = y
    }

    private val a = FloatArray(2)
    private val b = FloatArray(2)

    fun update(dt: Float) {
        if (!active) return
        t += dt
        val u = t / DURATION
        pos(u, 0f, a)
        for (i in 0 until CANNONS) {
            if (fired[i]) continue
            val fx = stX0 + stW * 0.06f + i / (CANNONS - 1f) * stW * 0.88f
            if (a[0] > fx - stW * 0.15f) { fired[i] = true; fire(i) }
        }
        for (s in smoke) {
            if (s.delay > 0f) { s.delay -= dt; continue }
            s.life += dt; s.x += s.vx * dt; s.y += s.vy * dt
            s.vy *= 0.55f.pow(dt); s.vx += 2f * k * dt; s.r += dt * (2.2f + s.life * 0.25f) * k
        }
        for (s in sparks) {
            if (s.delay > 0f) { s.delay -= dt; continue }
            s.life += dt; s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 60f * k * dt
        }
        smoke.removeAll { it.life >= it.max }
        sparks.removeAll { it.life >= it.max }
        if (u > 1.3f && smoke.isEmpty()) t = -1f
    }

    private fun fire(i: Int) {
        val u = i / (CANNONS - 1f)
        val x = stX0 + stW * 0.06f + u * stW * 0.88f
        val y = stTop - stH * 0.05f + abs(u - 0.5f) * stH * 0.12f
        val col = COLOURS[i % COLOURS.size]
        repeat((26 * PYRO).toInt()) { n ->
            smoke += Puff(x, y, ((rnd.nextFloat() - 0.5f) * 4f + 3f) * k, -(20f + rnd.nextFloat() * 26f) * (0.7f + 0.3f * PYRO) * k,
                (1f + rnd.nextFloat() * 1.5f) * k, 7f + rnd.nextFloat() * 6f, col, n * 0.04f)
        }
        repeat((14 * PYRO).toInt()) { n ->
            sparks += Spark(x, y, (rnd.nextFloat() - 0.5f) * 14f * k, -(40f + rnd.nextFloat() * 40f) * k, 1.2f + rnd.nextFloat() * 0.8f, n * 0.03f)
        }
    }

    fun draw(c: Canvas) {
        if (!active) return
        p.style = Paint.Style.FILL
        p.shader = null
        // Soft, feathered puffs (one white sprite, tinted per colour) so the plumes billow.
        for (s in smoke) {
            if (s.delay > 0f) continue
            val tint = tints.getOrPut(s.col) { Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.PorterDuffColorFilter(s.col, android.graphics.PorterDuff.Mode.SRC_IN) } }
            tint.alpha = (255 * 0.7f * (1f - s.life / s.max)).toInt().coerceIn(0, 255)
            val r = s.r * 1.6f
            dst.set(s.x - r, s.y - r, s.x + r, s.y + r)
            c.drawBitmap(puff, null, dst, tint)
        }
        for (s in sparks) {
            if (s.delay > 0f) continue
            p.color = Color.argb((255 * (1f - s.life / s.max)).toInt().coerceIn(0, 255), 255, 200 + rnd.nextInt(56), 120)
            c.drawRect(s.x, s.y, s.x + k, s.y + k, p)
        }
        val u = t / DURATION
        val len = stW * 0.12f * PLANE_SIZE
        // Lead jet in black; the second (green) tucked in just behind and above.
        for ((black, lag, dy) in listOf(Triple(true, 0f, 0f), Triple(false, 0.035f, -stH * 0.9f))) {
            if (u - lag > 1.4f) continue
            pos(u, lag, a); pos(u + 0.01f, lag, b)
            c.save()
            c.translate(a[0], a[1] + dy)
            c.rotate(Math.toDegrees(atan2((b[1] - a[1]).toDouble(), (b[0] - a[0]).toDouble())).toFloat())
            c.scale(len, len)
            ejet(c, black)
            c.restore()
        }
    }

    // ---- Embraer E-jet, units of its length, nose to the right --------------------------

    private val fus = Path().apply {
        moveTo(.5f, .008f); quadTo(.497f, -.02f, .47f, -.032f); quadTo(.42f, -.042f, .34f, -.042f); lineTo(-.3f, -.042f)
        quadTo(-.43f, -.04f, -.5f, -.026f); lineTo(-.505f, -.015f); quadTo(-.42f, -.002f, -.32f, .036f); quadTo(-.2f, .042f, -.1f, .042f)
        lineTo(.38f, .042f); quadTo(.47f, .04f, .5f, .008f); close()
    }
    private val fin = Path().apply { moveTo(-.24f, -.042f); quadTo(-.3f, -.05f, -.34f, -.07f); lineTo(-.45f, -.22f); lineTo(-.5f, -.22f); lineTo(-.5f, -.028f); close() }
    private val stab = Path().apply { moveTo(-.4f, -.016f); lineTo(-.51f, -.05f); lineTo(-.53f, -.045f); lineTo(-.47f, -.006f); close() }
    private val wing = Path().apply { moveTo(.1f, .03f); lineTo(0f, .03f); lineTo(-.2f, .1f); lineTo(-.16f, .1f); close() }
    private val winglet = Path().apply { moveTo(-.16f, .1f); lineTo(-.2f, .1f); lineTo(-.215f, .065f); lineTo(-.198f, .063f); close() }
    private val cockpit = Path().apply { moveTo(.45f, -.034f); lineTo(.482f, -.026f); lineTo(.476f, -.016f); lineTo(.446f, -.022f); close() }

    private fun nacelle(c: Canvas, x: Float, y0: Float) {
        path.reset()
        path.moveTo(x, y0); path.lineTo(x - .12f, y0 + .005f); path.quadTo(x - .145f, y0 + .017f, x - .12f, y0 + .028f)
        path.lineTo(x, y0 + .032f); path.quadTo(x + .008f, y0 + .016f, x, y0); path.close()
        c.drawPath(path, p)
    }

    private fun ejet(c: Canvas, black: Boolean) {
        val body = if (black) 0xFF151A26.toInt() else 0xFF1F6A3A.toInt()
        val belly = if (black) 0xFF0E1119.toInt() else 0xFF164D2A.toInt()
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0xFF2C3140.toInt(); nacelle(c, .08f, .06f)            // far engine
        p.color = 0xFF202534.toInt(); c.drawPath(stab, p)
        p.color = body; c.drawPath(fus, p)
        c.save(); c.clipPath(fus)
        p.color = belly; c.drawRect(-.6f, .016f, .6f, .066f, p)
        if (!black) { // green sweep along the rear fuselage
            p.color = Color.BLACK
            p.shader = LinearGradient(-.5f, 0f, -.05f, 0f, 0xFF7ED957.toInt(), 0x007ED957, Shader.TileMode.CLAMP)
            path.reset(); path.moveTo(-.55f, -.05f); path.quadTo(-.25f, -.04f, -.05f, .05f); path.lineTo(-.55f, .05f); path.close()
            c.drawPath(path, p); p.shader = null
        }
        c.save(); c.translate(.1f, -.016f); c.scale(.001f, .001f); text.textSize = 34f; c.drawText("AIRLINK", 0f, 12f, text); c.restore()
        c.restore()
        p.color = body; c.drawPath(fin, p)
        c.save(); c.clipPath(fin)
        if (!black) {
            p.color = Color.BLACK
            p.shader = LinearGradient(-.5f, -.22f, -.3f, -.04f, 0xFFB8F07A.toInt(), 0xFF1F6A3A.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(fin, p); p.shader = null
        }
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = .012f
        c.drawCircle(-.43f, -.13f, .03f, p); c.drawLine(-.46f, -.13f, -.4f, -.13f, p) // tail logo
        p.style = Paint.Style.FILL
        c.restore()
        p.color = 0xBFFFFFFF.toInt()
        var x = .36f
        while (x > -.28f) { c.drawRect(x, -.018f, x + .007f, -.01f, p); x -= .02f }
        p.color = 0xFF0A0C12.toInt(); c.drawPath(cockpit, p)
        p.color = 0xFF3A4050.toInt(); c.drawPath(wing, p); c.drawPath(winglet, p)
        p.color = 0xFF2C3140.toInt(); nacelle(c, .1f, .055f)            // near engine
        p.color = 0xFF8A90A0.toInt(); c.drawOval(.095f, .055f, .107f, .087f, p)
    }

    companion object {
        const val PREVIEW_W = 240f
        const val DURATION = 12f
        const val CANNONS = 12
        const val PLANE_SIZE = 3f   // × real size over the stadium
        const val LOWNESS = 0.8f    // how high above the roof (1 = the preview default)
        const val PYRO = 1f
        const val MINUTES_BETWEEN = 15
        private val COLOURS = intArrayOf(Color.rgb(70, 140, 240), Color.WHITE, Color.rgb(240, 60, 70), Color.rgb(60, 200, 110), Color.rgb(255, 215, 60), Color.rgb(240, 90, 170))
    }
}
