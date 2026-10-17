package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.sin
import kotlin.random.Random

/**
 * Boeing 737-800s in South African airline colours crossing the Jhb sky (from the in-chat
 * livery designer). Drawn in units of the aircraft's length, nose to the right.
 */
class JhbAirTraffic {

    enum class Airline { SAA, MANGO, LIFT, FLYSAFAIR, KULULA, AIRLINK }

    private class Flight(val airline: Airline, val dir: Float, val alt: Float, var x: Float, val speed: Float, val climb: Float)

    private val rnd = Random(System.nanoTime())
    private val flights = ArrayList<Flight>()
    private var w = 0f
    private var h = 0f
    private var t = 0f
    var night = false

    val active get() = flights.isNotEmpty()
    val count get() = flights.size

    fun setSize(width: Int, height: Int) { w = width.toFloat(); h = height.toFloat() }

    fun launch() {
        if (w == 0f) return
        val airline = Airline.entries[rnd.nextInt(Airline.entries.size)]
        val dir = if (rnd.nextBoolean()) 1f else -1f
        val alt = rnd.nextFloat()
        flights += Flight(airline, dir, alt, if (dir > 0) -0.15f else 1.15f, 0.035f + 0.02f * (1f - alt), (rnd.nextFloat() - 0.5f) * 0.02f)
    }

    fun update(dt: Float) {
        t += dt
        val it = flights.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.x += f.dir * f.speed * dt
            if (f.x < -0.3f || f.x > 1.3f) it.remove() // fully off screen
        }
    }

    fun draw(c: Canvas) {
        for (f in flights) {
            // Higher (alt 0) = smaller and further up; lower = bigger.
            val y = h * (0.12f + 0.24f * f.alt) + f.climb * (f.x - 0.5f) * h * 0.5f
            val s = w * (0.11f + 0.1f * f.alt) * SIZE
            val x = f.x * w
            if (CONTRAILS && f.alt < 0.45f) contrail(c, f, x, y, s)
            c.save()
            c.translate(x, y)
            c.scale(f.dir * s, s)
            c.rotate(Math.toDegrees((-f.climb * 0.6f * f.dir).toDouble()).toFloat())
            mirrored = f.dir < 0f
            plane(c, f.airline)
            c.restore()
        }
    }

    private fun contrail(c: Canvas, f: Flight, x: Float, y: Float, s: Float) {
        val tl = w * 0.8f
        val a = if (night) 38 else 140
        trail.shader = LinearGradient(x, 0f, x - f.dir * tl, 0f, Color.argb(a, 255, 255, 255), 0x00FFFFFF, Shader.TileMode.CLAMP)
        trail.strokeWidth = maxOf(1f, s * 0.014f)
        for (o in floatArrayOf(0.08f, 0.075f)) c.drawLine(x, y + s * o, x - f.dir * tl, y + s * o - f.climb * h * 0.4f * f.dir, trail)
    }

    // ---- Aircraft ------------------------------------------------------------------------

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val fus = Path().apply {
        moveTo(.5f, .012f); quadTo(.495f, -.022f, .47f, -.036f); quadTo(.43f, -.05f, .36f, -.05f); lineTo(-.28f, -.05f)
        quadTo(-.42f, -.048f, -.5f, -.03f); lineTo(-.505f, -.018f); quadTo(-.42f, -.004f, -.3f, .042f); quadTo(-.2f, .05f, -.1f, .05f)
        lineTo(.38f, .05f); quadTo(.47f, .048f, .5f, .012f); close()
    }
    private val fin = Path().apply { moveTo(-.2f, -.05f); quadTo(-.28f, -.06f, -.32f, -.08f); lineTo(-.43f, -.24f); lineTo(-.485f, -.24f); lineTo(-.5f, -.032f); close() }
    private val stab = Path().apply { moveTo(-.4f, -.02f); lineTo(-.51f, -.055f); lineTo(-.53f, -.05f); lineTo(-.47f, -.008f); close() }
    private val wing = Path().apply { moveTo(.12f, .035f); lineTo(0f, .035f); lineTo(-.22f, .105f); lineTo(-.17f, .105f); close() }
    private val wingShade = Path().apply { moveTo(0f, .035f); lineTo(-.22f, .105f); lineTo(-.2f, .105f); lineTo(.03f, .04f); close() }
    private val winglet = Path().apply { moveTo(-.17f, .105f); lineTo(-.22f, .105f); lineTo(-.235f, .06f); lineTo(-.215f, .058f); close() }
    private val path = Path()

    /** The plane is flying left (drawn flipped), so writing must be flipped back to read correctly. */
    private var mirrored = false

    private class Livery(val body: Int, val belly: Int, val eng: Int, val lip: Int, val winglet: Int)

    private fun livery(a: Airline) = when (a) {
        Airline.SAA -> Livery(0xFFF4F5F7.toInt(), 0xFFDFE3EA.toInt(), 0xFFE8EBF0.toInt(), 0xFFA8B0BC.toInt(), 0xFF001489.toInt())
        Airline.MANGO -> Livery(0xFFFF7F00.toInt(), 0xFFF26B00.toInt(), 0xFFFF8F1F.toInt(), 0xFFC25500.toInt(), 0xFFFF8F1F.toInt())
        Airline.LIFT -> Livery(0xFFF7F7FA.toInt(), 0xFFE6E6EE.toInt(), 0xFF2B2B3A.toInt(), 0xFF88889A.toInt(), 0xFF2B2B3A.toInt())
        Airline.FLYSAFAIR -> Livery(0xFFF6F7F9.toInt(), 0xFFE2E6EC.toInt(), 0xFF1F4FA3.toInt(), 0xFFFF7A1A.toInt(), 0xFF1F4FA3.toInt())
        Airline.KULULA -> Livery(0xFF8DC63F.toInt(), 0xFF7DB532.toInt(), 0xFF8DC63F.toInt(), 0xFF4D7A1A.toInt(), 0xFF8DC63F.toInt())
        Airline.AIRLINK -> Livery(0xFFF5F6F8.toInt(), 0xFFE1E5EB.toInt(), 0xFFE8EBF0.toInt(), 0xFF1B2B5A.toInt(), 0xFFE8EBF0.toInt())
    }

    private fun fill(color: Int) { p.style = Paint.Style.FILL; p.shader = null; p.color = color }

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f) {
        if (rot != 0f) { c.save(); c.rotate(Math.toDegrees(rot.toDouble()).toFloat(), cx, cy) }
        c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)
        if (rot != 0f) c.restore()
    }

    /** Text in plane units (tiny sizes are drawn scaled up so fonts stay crisp). */
    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(if (mirrored) -0.001f else 0.001f, 0.001f)
        text.color = color
        text.textSize = size * 1000f
        c.drawText(s, 0f, size * 1000f * 0.36f, text)
        c.restore()
    }

    private fun engine(c: Canvas, x: Float, y: Float, col: Int, lip: Int) {
        fill(col)
        path.reset()
        path.moveTo(x + .075f, y - .026f); path.lineTo(x - .05f, y - .02f); path.quadTo(x - .075f, y + .002f, x - .05f, y + .018f)
        path.lineTo(x + .075f, y + .022f); path.quadTo(x + .084f, y, x + .075f, y - .026f); path.close()
        c.drawPath(path, p)
        fill(lip); oval(c, x + .077f, y - .002f, .008f, .024f)
        fill(0x38000000); c.drawRect(x - .05f, y + .006f, x + .075f, y + .02f, p)
        fill(0x59000000)
        path.reset(); path.moveTo(x - .05f, y - .01f); path.lineTo(x - .08f, y - .004f); path.lineTo(x - .05f, y + .008f); path.close()
        c.drawPath(path, p)
        fill(col); c.drawRect(x - .02f, y - .042f, x + .03f, y - .022f, p) // pylon
    }

    private fun plane(c: Canvas, a: Airline) {
        val l = livery(a)
        engine(c, .135f, .07f, scale(l.eng, 0.75f), l.lip) // far engine
        fill(if (night) 0xFF2A2E38.toInt() else 0xFFC8CCD4.toInt()); c.drawPath(stab, p)
        fill(l.body); c.drawPath(fus, p)
        c.save(); c.clipPath(fus)
        fill(l.belly); c.drawRect(-.6f, .022f, .6f, .072f, p)
        deco(c, a)
        c.restore()
        fill(l.body); c.drawPath(fin, p)
        c.save(); c.clipPath(fin); tail(c, a); c.restore()
        // Cabin windows, with gaps at the doors and over-wing exits.
        fill(if (night) 0xFFFFE8A8.toInt() else 0xFF2B3240.toInt())
        var x = .37f
        while (x > -.3f) {
            if (!(kotlin.math.abs(x - .33f) < .01f || kotlin.math.abs(x - .03f) < .014f || kotlin.math.abs(x + .27f) < .01f)) c.drawRect(x, -.02f, x + .007f, -.01f, p)
            x -= .019f
        }
        fill(0xFF1D2430.toInt())
        path.reset(); path.moveTo(.45f, -.04f); path.lineTo(.483f, -.032f); path.lineTo(.476f, -.022f); path.lineTo(.446f, -.028f); path.close()
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.color = 0x26000000; p.strokeWidth = .0025f
        for (dx in floatArrayOf(.4f, .03f, -.27f)) c.drawRect(dx - .008f, -.03f, dx + .008f, .02f, p)
        fill(if (night) 0xFF3A3F4C.toInt() else 0xFFD7DBE2.toInt()); c.drawPath(wing, p)
        fill(0x1F000000); c.drawPath(wingShade, p)
        fill(if (night) 0xFF3A3F4C.toInt() else l.winglet); c.drawPath(winglet, p)
        engine(c, .1f, .083f, l.eng, l.lip)
        p.style = Paint.Style.STROKE; p.color = 0x2E000000; p.strokeWidth = .002f; c.drawPath(fus, p)
        if (night) lights(c)
    }

    private fun lights(c: Canvas) {
        if (sin(t * 3.2f) > 0f) { fill(0xFFFF2A2A.toInt()); c.drawCircle(.05f, -.054f, .007f, p); c.drawCircle(-.02f, .053f, .007f, p) }
        fill(0xFF2AFF5A.toInt()); c.drawCircle(-.215f, .1f, .006f, p)
        if (sin(t * 6f) > .6f) { fill(Color.WHITE); c.drawCircle(-.225f, .1f, .012f, p); c.drawCircle(-.505f, -.022f, .01f, p) }
        fill(0x80FFFFFF.toInt()); c.drawCircle(-.43f, -.13f, .035f, p) // tail logo floodlight
    }

    private fun deco(c: Canvas, a: Airline) {
        when (a) {
            Airline.SAA -> { fill(0xFF001489.toInt()); c.drawRect(-.3f, .014f, .4f, .02f, p); txt(c, "SOUTH AFRICAN", .06f, -.026f, .03f, 0xFF001489.toInt()) }
            Airline.MANGO -> txt(c, "mango", .08f, -.014f, .048f, Color.WHITE)
            Airline.LIFT -> txt(c, "lift", .1f, -.016f, .05f, 0xFF20203A.toInt())
            Airline.FLYSAFAIR -> { txt(c, "FlySafair", .06f, -.022f, .036f, 0xFF1F4FA3.toInt()); fill(0xFFFF7A1A.toInt()); c.drawRect(-.33f, .018f, .39f, .023f, p) }
            Airline.KULULA -> txt(c, "kulula.com", .04f, -.014f, .042f, Color.WHITE)
            Airline.AIRLINK -> { txt(c, "Airlink", .06f, -.022f, .036f, 0xFF1B2B5A.toInt()); fill(0xFFE2231A.toInt()); c.drawRect(-.33f, .018f, .39f, .023f, p) }
        }
    }

    private fun tail(c: Canvas, a: Airline) {
        when (a) {
            Airline.SAA -> saFlag(c)
            Airline.MANGO -> {
                fill(0xFFFF9A1F.toInt()); c.drawPath(fin, p)
                fill(0xFF3A9A2C.toInt()); oval(c, -.45f, -.18f, .038f, .017f, -.6f)
                fill(0xFFFFD23A.toInt()); oval(c, -.44f, -.125f, .033f, .038f)
            }
            Airline.LIFT -> {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(-.5f, -.24f, -.3f, -.04f, 0xFF5B2BD6.toInt(), 0xFF20C4C4.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(fin, p); p.shader = null
                txt(c, "lift", -.44f, -.11f, .05f, Color.WHITE)
            }
            Airline.FLYSAFAIR -> {
                fill(0xFF1F4FA3.toInt()); c.drawPath(fin, p)
                fill(0xFFFF7A1A.toInt())
                path.reset(); path.moveTo(-.52f, -.06f); path.lineTo(-.3f, -.18f); path.lineTo(-.3f, -.145f); path.lineTo(-.52f, -.03f); path.close()
                c.drawPath(path, p)
            }
            Airline.KULULA -> { fill(0xFF7DB532.toInt()); c.drawPath(fin, p); txt(c, "k", -.45f, -.13f, .08f, Color.WHITE) }
            Airline.AIRLINK -> {
                fill(0xFF1B2B5A.toInt()); c.drawPath(fin, p)
                fill(0xFFE2231A.toInt())
                path.reset(); path.moveTo(-.5f, -.08f); path.quadTo(-.4f, -.13f, -.33f, -.22f); path.lineTo(-.3f, -.19f); path.quadTo(-.38f, -.1f, -.5f, -.05f); path.close()
                c.drawPath(path, p)
            }
        }
    }

    /** The South African flag swept up the fin. */
    private fun saFlag(c: Canvas) {
        c.save()
        c.translate(-.43f, -.15f)
        c.rotate(Math.toDegrees(-.95).toFloat())
        val fw = .36f; val fh = .22f
        fill(0xFFE03C31.toInt()); c.drawRect(-fw / 2, -fh / 2, fw / 2, 0f, p)
        fill(0xFF001489.toInt()); c.drawRect(-fw / 2, 0f, fw / 2, fh / 2, p)
        path.reset(); path.moveTo(-fw / 2, -fh / 2); path.lineTo(-.02f, 0f); path.lineTo(-fw / 2, fh / 2); path.moveTo(-.02f, 0f); path.lineTo(fw / 2, 0f)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
        p.color = Color.WHITE; p.strokeWidth = .075f; c.drawPath(path, p)
        p.color = 0xFF007749.toInt(); p.strokeWidth = .048f; c.drawPath(path, p)
        fill(0xFFFFB81C.toInt())
        path.reset(); path.moveTo(-fw / 2, -fh * .32f); path.lineTo(-fw / 2 + .085f, 0f); path.lineTo(-fw / 2, fh * .32f); path.close(); c.drawPath(path, p)
        fill(Color.BLACK)
        path.reset(); path.moveTo(-fw / 2, -fh * .24f); path.lineTo(-fw / 2 + .065f, 0f); path.lineTo(-fw / 2, fh * .24f); path.close(); c.drawPath(path, p)
        c.restore()
    }

    companion object {
        const val SIZE = 1f
        const val CONTRAILS = true
    }
}
