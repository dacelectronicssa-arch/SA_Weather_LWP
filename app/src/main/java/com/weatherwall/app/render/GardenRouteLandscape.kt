package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Garden Route settings, from the in-chat scene preview. */
object GardenDesign {
    /** The sea's horizon, as a fraction of the screen (the landscape sits low, under open sky). */
    const val HORIZON = 0.655f
    /** Bridge camera: angle of the span to the view, distance to its middle (m), lens. */
    const val ANGLE_DEG = 36f
    const val DISTANCE = 92f
    const val LENS = 700f        // focal length in preview px (760 wide)
    /** The train is drawn this much taller than life, the sea life this much bigger, to read on a phone. */
    const val TRAIN_SCALE = 1.7f
    const val TRAIN_SPEED = 12f  // m/s
    const val TRAIN_EVERY_MIN = 4f
    const val TRAIN_EVERY_MAX = 8f
}

/**
 * The Garden Route: Bloukrans Bridge seen from inside the gorge, just below deck height, swung at
 * an angle so its near end runs off the top-left and the far end recedes to the right. Through the
 * arch the gorge opens on a turquoise inlet and the open Indian Ocean. Southern right whales,
 * dolphins, boats, yachts and a jetski in the bay; bungy jumpers and Skywalk walkers under the
 * deck; and now and then the Outeniqua Choo Tjoe steams across from the far end.
 *
 * Laid out in the preview's own frame (horizon at 0.405 of the height, units of a 760-wide
 * canvas scaled by [k]) and drawn [dy] lower on the screen.
 */
class GardenRouteLandscape(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
    private val dp: Float,
) : Landscape {
    private val w = width.toFloat()
    private val h = height.toFloat()
    private val k = w / 760f
    private val hz = h * 0.405f
    private val dy = h * (GardenDesign.HORIZON - 0.405f)
    private var time = 0f
    private val rnd = Random(System.nanoTime())

    override val tableTopY get() = dy + hz - 60f * k
    override val mountainUnit get() = min(w, h * 0.8f) * 1.35f

    // ---- Light -------------------------------------------------------------------------------

    private class Pal(
        val seaDeep: Int, val seaMid: Int, val seaShallow: Int, val seaHi: Int, val foam: Int,
        val grass: Int, val grassLt: Int, val earth: Int, val rock: Int, val conc: Int, val concSh: Int, val haze: Int,
    ) {
        fun to(o: Pal, t: Float) = Pal(
            mix(seaDeep, o.seaDeep, t), mix(seaMid, o.seaMid, t), mix(seaShallow, o.seaShallow, t), mix(seaHi, o.seaHi, t), mix(foam, o.foam, t),
            mix(grass, o.grass, t), mix(grassLt, o.grassLt, t), mix(earth, o.earth, t), mix(rock, o.rock, t), mix(conc, o.conc, t), mix(concSh, o.concSh, t), mix(haze, o.haze, t),
        )
    }

    private var twilight = 0f
    private val night = cfg.night
    private val grey = cfg.overcast || cfg.rain > 0 || cfg.fog
    private var glintX = w * 0.25f
    private var glintK = 0f
    private var pal = palette()

    override fun setTwilight(amount: Float) { twilight = amount }
    override fun setGlint(x: Float, strength: Float) { glintX = (x / (w / 8f)).toInt() * (w / 8f) + w / 16f; glintK = (strength * 4f).toInt() / 4f }

    private fun palette(): Pal {
        var p = if (night) NIGHT.to(GOLD, twilight * 0.3f) else DAY.to(GOLD, twilight)
        if (grey) p = p.to(GREYED, 0.3f)
        return Pal(p.seaDeep, p.seaMid, p.seaShallow, p.seaHi, p.foam, p.grass, p.grassLt, p.earth, p.rock, p.conc, p.concSh, mix(p.haze, cfg.skyBottom, 0.4f))
    }

    private var bakedKey = -1
    private fun key() = (twilight * 10).toInt() + (if (night) 100 else 0) + (glintX / (w / 8f)).toInt() * 1000 + (glintK * 4).toInt() * 100000

    // ---- Projection: the bridge in metres -------------------------------------------------------
    // X along the span (+ to the right), Y down from eye level, D across the deck (+ away from us).

    private val th = Math.toRadians(GardenDesign.ANGLE_DEG.toDouble()).toFloat()
    private val cth = cos(th)
    private val sth = sin(th)
    private val foc = GardenDesign.LENS * k
    private val cxb = w * 0.4f
    private var ox = 0f
    private var oy = 0f
    private var oz = 0f

    private fun proj(X: Float, Y: Float, D: Float) {
        val x = X * cth - D * sth
        oz = max(1f, GardenDesign.DISTANCE + X * sth + D * cth)
        ox = cxb + foc * x / oz
        oy = hz + foc * Y / oz
    }

    private val deckT = -14f
    private val deckB = -10f
    private val half = 136f
    private fun archTop(X: Float) = -7f + 87f * (X / half).pow(2)
    private fun archBot(X: Float) = archTop(X) + 5f + 4f * (X / half).pow(2)
    private fun ground(X: Float) = 80f - (abs(X) - half) * 1.15f
    private val xMin = -110f
    private val xMax = 260f

    // ---- Drawing toolkit -----------------------------------------------------------------------

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun fill(c: Int) { p.style = Paint.Style.FILL; p.shader = null; p.color = c }

    private fun polyPath(pts: FloatArray, close: Boolean = true): Path {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) path.close()
        return path
    }

    /** Fractions of the frame to pixels. */
    private fun S(vararg f: Float) = FloatArray(f.size) { if (it % 2 == 0) f[it] * w else f[it] * h }

    /** Roughens a polyline: each segment split up and nudged sideways, more where it's nearer (lower). */
    private fun rough(pts: FloatArray, amp: Float, seed: Int): FloatArray {
        val r = Mulberry32(seed)
        val out = ArrayList<Float>()
        out += pts[0]; out += pts[1]
        var i = 2
        while (i < pts.size) {
            val ax = pts[i - 2]; val ay = pts[i - 1]; val bx = pts[i]; val by = pts[i + 1]
            val len = max(1f, hypot(bx - ax, by - ay))
            val nx = -(by - ay) / len; val ny = (bx - ax) / len
            for (s in 1..6) {
                val u = s / 6f
                val x = ax + (bx - ax) * u; val y = ay + (by - ay) * u
                val a = if (s == 6) 0f else (r.next() - 0.5f) * 2f * amp * k * (0.5f + (y - hz) / (h - hz))
                out += x + nx * a; out += y + ny * a
            }
            i += 2
        }
        return out.toFloatArray()
    }

    private class Shore(val pts: FloatArray, val haze: Float, val path: Path)
    private val shores = ArrayList<Shore>()

    /** Coastal grass and scrub with orange earth cuts and grey rock faces, inside the current clip. */
    private fun slope(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, haze: Float, seed: Int, n: Int) {
        val r = Mulberry32(seed)
        val grass = mix(pal.grass, pal.haze, haze); val lt = mix(pal.grassLt, pal.haze, haze)
        val earth = mix(pal.earth, pal.haze, haze); val rock = mix(pal.rock, pal.haze, haze)
        val k0 = (1f - haze * 0.6f) * k
        for (i in 0 until n / 110) {
            val cx = x0 + r.next() * (x1 - x0); val cy = y0 + r.next() * (y1 - y0)
            val rw = (24f + r.next() * 60f) * k0; val rh = (36f + r.next() * 80f) * k0
            val isEarth = r.next() < 0.4f
            fill(withAlpha(if (isEarth) earth else rock, 235))
            path.reset()
            for (s in 0 until 10) {
                val a = s / 10f * TWO_PI; val q = 0.6f + r.next() * 0.5f
                val px = cx + cos(a) * rw * q; val py = cy + sin(a) * rh * q
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * k
            p.color = withAlpha(mix(if (isEarth) earth else rock, Color.BLACK, 0.3f), 128)
            for (s in 0 until 6) { val sx = cx + (r.next() - 0.5f) * rw; c.drawLine(sx, cy - rh * 0.5f, sx + (r.next() - 0.5f) * 10f * k, cy + rh * 0.5f, p) }
        }
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val x = x0 + r.next() * (x1 - x0); val y = y0 + r.next() * (y1 - y0)
            val s = (1.5f + r.next() * 5f) * (0.4f + (y - hz) / (h - hz)) * k0
            p.color = withAlpha(if (r.next() < 0.35f) lt else mix(grass, Color.BLACK, r.next() * 0.35f), 230)
            c.drawOval(x - s * 1.3f, y - s, x + s * 1.3f, y + s, p)
        }
    }

    /** A land mass: fill, texture and shading into the gorge; its shore gets shallows (sea layer) and live surf. */
    private fun landMass(sea: Canvas, land: Canvas, pts: FloatArray, shore: FloatArray?, haze: Float, seed: Int, n: Int, inner: Float? = null) {
        if (shore != null) {
            p.style = Paint.Style.STROKE; p.shader = null; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
            p.color = withAlpha(mix(pal.seaShallow, pal.haze, haze * 0.5f), 216); p.strokeWidth = 34f * k * (1f - haze * 0.8f)
            sea.drawPath(polyPath(shore, false), p)
            shores += Shore(shore, haze, Path(polyPath(shore, false)))
        }
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var i = 0
        while (i < pts.size) { x0 = min(x0, pts[i]); x1 = max(x1, pts[i]); y0 = min(y0, pts[i + 1]); y1 = max(y1, pts[i + 1]); i += 2 }
        val shape = Path(polyPath(pts))
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, y0, 0f, y1, mix(pal.grass, pal.haze, haze), mix(mix(pal.grass, Color.BLACK, 0.35f), pal.haze, haze), Shader.TileMode.CLAMP)
        land.drawPath(shape, p); p.shader = null
        land.save(); land.clipPath(shape)
        slope(land, x0, y0, x1, y1, haze, seed, n)
        if (inner != null) {
            // The face falls away into the gorge: darker towards the cliff edge, with spurs and gullies.
            val outer = if (inner > (x0 + x1) / 2f) x0 else x1
            p.color = Color.BLACK
            p.shader = LinearGradient(outer, 0f, inner, 0f, 0x0FFFFADC, withAlpha(0xFF0A100A.toInt(), (87 * (1f - haze)).toInt()), Shader.TileMode.CLAMP)
            land.drawRect(x0, y0, x1, y1, p); p.shader = null
            val rr = Mulberry32(seed + 99)
            val dir = if (inner > outer) 1f else -1f
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            for (s in 0 until 5) {
                val sx = outer + dir * (x1 - x0) * (0.1f + rr.next() * 0.6f); val sy = y0 + (y1 - y0) * (0.05f + rr.next() * 0.3f)
                val ex = sx + dir * (90f + rr.next() * 80f) * k; val ey = sy + (260f + rr.next() * 200f) * k
                p.color = withAlpha(0xFF080E08.toInt(), (46 * (1f - haze)).toInt()); p.strokeWidth = (16f + rr.next() * 26f) * k
                path.reset(); path.moveTo(sx, sy); path.quadTo(sx + dir * 60f * k, sy + 120f * k, ex, ey); land.drawPath(path, p)
                p.color = withAlpha(0xFFFFFAD2.toInt(), (18 * (1f - haze)).toInt()); p.strokeWidth = 8f * k
                path.reset(); path.moveTo(sx - dir * 22f * k, sy); path.quadTo(sx - dir * 22f * k + dir * 60f * k, sy + 120f * k, ex - dir * 22f * k, ey); land.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        }
        land.restore()
    }

    private fun rock(sea: Canvas, land: Canvas, x: Float, y: Float, s: Float, seed: Int) {
        val r = Mulberry32(seed)
        val pts = FloatArray(16)
        for (i in 0 until 8) { val a = PI.toFloat() + i / 7f * PI.toFloat(); val q = 0.7f + r.next() * 0.4f; pts[i * 2] = x + cos(a) * s * 1.3f * q; pts[i * 2 + 1] = y + sin(a) * s * 1.6f * q }
        p.style = Paint.Style.STROKE; p.color = withAlpha(pal.seaShallow, 180); p.strokeWidth = s * 0.6f
        sea.drawLine(x - s * 1.5f, y, x + s * 1.5f, y, p)
        fill(mix(pal.rock, Color.BLACK, 0.1f)); land.drawPath(polyPath(pts), p)
        val shade = floatArrayOf(x, y - s * 1.5f, *pts.copyOfRange(8, 16))
        fill(mix(pal.rock, Color.BLACK, 0.4f)); land.drawPath(polyPath(shade), p)
        val sh = floatArrayOf(x - s * 1.6f, y + 1f, x, y + s * 0.3f, x + s * 1.6f, y + 1f)
        shores += Shore(sh, 0.15f, Path(polyPath(sh, false)))
    }

    // ---- Baked layers ----------------------------------------------------------------------------
    // The sea (from the horizon down) and the land with the bridge (from above the deck down),
    // each as tall as it needs to be, drawn at [seaTop] and [landTop] on the screen.

    private val seaTop = dy + hz - 2f
    private val landTop = h * 0.44f
    private var sea: Bitmap? = null
    private var landBmp: Bitmap? = null

    private fun bake() {
        pal = palette()
        shores.clear()
        val sb = sea ?: Bitmap.createBitmap(w.toInt(), (h - seaTop).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { sea = it }
        val lb = landBmp ?: Bitmap.createBitmap(w.toInt(), (h - landTop).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { landBmp = it }
        sb.eraseColor(Color.TRANSPARENT); lb.eraseColor(Color.TRANSPARENT)
        val s = Canvas(sb).apply { translate(0f, dy - seaTop) }
        val l = Canvas(lb).apply { translate(0f, dy - landTop) }
        val r = Mulberry32(7)
        // Open sea: hazy at the horizon, deep blue, a little brighter towards the coast.
        val seaEnd = h * 0.56f
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, hz, 0f, seaEnd, intArrayOf(mix(pal.seaMid, cfg.skyBottom, 0.5f), pal.seaDeep, pal.seaMid), floatArrayOf(0f, 0.12f, 1f), Shader.TileMode.CLAMP)
        s.drawRect(0f, hz, w, h, p); p.shader = null
        for (i in 0 until 700) { // wind ripples, larger as they come closer
            val u = r.next().pow(1.4f); val y = hz + 3f * k + u * (seaEnd - hz); val sc = (0.3f + u * 2.4f) * k
            p.color = if (r.next() < 0.5f) withAlpha(mix(pal.seaMid, Color.WHITE, 0.25f), 56) else withAlpha(mix(pal.seaDeep, Color.BLACK, 0.2f), 56)
            val x = r.next() * w
            s.drawRect(x, y, x + (6f + r.next() * 16f) * sc, y + max(1f, sc * 0.8f), p)
        }
        if (glintK > 0f) for (i in 0 until 120) { // the sun's (or moon's) glitter path
            val u = r.next().pow(2f); val y = hz + 2f * k + u * (seaEnd - hz); val ww = (3f + r.next() * 14f) * (0.4f + u * 2f) * k
            p.color = withAlpha(pal.seaHi, ((0.2f + r.next() * 0.55f) * 255 * glintK).toInt())
            val x = glintX + (r.next() - 0.5f) * (24f + u * 380f) * k
            s.drawRect(x, y, x + ww, y + (1.5f + u * 1.5f) * k, p)
        }
        // The inlet: the sea running into the gorge mouth, turning turquoise, then the river.
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, h * 0.52f, 0f, h * 0.82f, intArrayOf(pal.seaMid, pal.seaShallow, mix(pal.seaShallow, pal.grass, 0.35f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        s.drawPath(polyPath(S(0.26f, 0.515f, 0.74f, 0.515f, 0.64f, 0.66f, 0.57f, 0.79f, 0.55f, 0.84f, 0.45f, 0.84f, 0.43f, 0.79f, 0.36f, 0.66f)), p); p.shader = null
        fill(mix(pal.earth, Color.rgb(240, 225, 190), 0.55f)); s.drawOval(w * 0.44f, h * 0.781f, w * 0.56f, h * 0.809f, p) // a little beach
        fill(mix(pal.grass, Color.BLACK, 0.5f)); s.drawRect(w * 0.4f, h * 0.81f, w * 0.6f, h, p)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 6f * k; p.color = mix(pal.seaShallow, pal.grass, 0.4f)
        path.reset(); path.moveTo(w * 0.5f, h * 0.8f); path.cubicTo(w * 0.47f, h * 0.86f, w * 0.54f, h * 0.9f, w * 0.49f, h * 0.96f); path.quadTo(w * 0.46f, h * 0.99f, w * 0.5f, h * 1.01f)
        s.drawPath(path, p)
        // A sea stack and rocks in the bay, ringed with surf.
        rock(s, l, w * 0.6f, hz + 48f * k, 14f * k, 3); rock(s, l, w * 0.4f, hz + 92f * k, 6f * k, 4)
        rock(s, l, w * 0.36f, h * 0.535f, 9f * k, 5); rock(s, l, w * 0.66f, h * 0.54f, 7f * k, 6)
        // The gorge mouth: middle walls dropping in cliffs to the inlet.
        val lEdge = rough(S(0.3f, 0.515f, 0.37f, 0.58f, 0.41f, 0.67f, 0.44f, 0.74f, 0.47f, 0.79f), 14f, 51)
        val rEdge = rough(S(0.7f, 0.515f, 0.64f, 0.58f, 0.6f, 0.66f, 0.56f, 0.74f, 0.53f, 0.79f), 14f, 52)
        landMass(s, l, S(0f, 0.44f, 0.18f, 0.47f) + lEdge + S(0.485f, 0.83f, 0.49f, 1f, 0f, 1f), lEdge, 0.14f, 31, 700, w * 0.45f)
        landMass(s, l, S(1f, 0.46f, 0.84f, 0.49f) + rEdge + S(0.515f, 0.83f, 0.51f, 1f, 1f, 1f), rEdge, 0.14f, 32, 700, w * 0.55f)
        // The near walls, steep and close, framing the V at the foot of the screen.
        landMass(s, l, rough(S(0f, 0.52f, 0.1f, 0.59f, 0.2f, 0.7f, 0.28f, 0.82f, 0.34f, 0.92f, 0.38f, 1f), 16f, 53) + floatArrayOf(0f, h), null, 0f, 33, 1100, w * 0.36f)
        landMass(s, l, rough(S(1f, 0.56f, 0.9f, 0.62f, 0.8f, 0.72f, 0.7f, 0.84f, 0.63f, 0.94f, 0.6f, 1f), 16f, 54) + floatArrayOf(w, h), null, 0f, 34, 1100, w * 0.62f)
        bridge(l)
        bakedKey = key()
    }

    // ---- The bridge ----------------------------------------------------------------------------

    /** A surface swept along the span between f0 at D0 and f1 at D1. */
    private inline fun strip(c: Canvas, x0: Float, x1: Float, d0: Float, d1: Float, step: Float, f0: (Float) -> Float, f1: (Float) -> Float) {
        path.reset()
        var X = x0
        var first = true
        while (X <= x1) { proj(X, f0(X), d0); if (first) path.moveTo(ox, oy) else path.lineTo(ox, oy); first = false; X += step }
        X -= step
        while (X >= x0) { proj(X, f1(X), d1); path.lineTo(ox, oy); X -= step }
        path.close()
        c.drawPath(path, p)
    }

    private fun quad(c: Canvas, x0: Float, y0: Float, d0: Float, x1: Float, y1: Float, d1: Float, x2: Float, y2: Float, d2: Float, x3: Float, y3: Float, d3: Float) {
        path.reset()
        proj(x0, y0, d0); path.moveTo(ox, oy); proj(x1, y1, d1); path.lineTo(ox, oy)
        proj(x2, y2, d2); path.lineTo(ox, oy); proj(x3, y3, d3); path.lineTo(ox, oy)
        path.close(); c.drawPath(path, p)
    }

    private fun column(c: Canvas, X: Float, D: Float, foot: Float, conc: Int, sh: Int, lightLeft: Boolean) {
        val wd = 1.1f
        proj(X - wd, deckB, D); val ax = ox
        proj(X + wd, deckB, D); val cx = ox
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(ax, 0f, cx, 0f, intArrayOf(if (lightLeft) conc else sh, mix(conc, sh, 0.3f), if (lightLeft) sh else conc), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        quad(c, X - wd, deckB, D, X + wd, deckB, D, X + wd, foot, D, X - wd, foot, D)
        p.shader = null
        fill(mix(sh, conc, if (lightLeft) 0.55f else 0.1f)) // its side face, turned towards us by the angle
        quad(c, X - wd, deckB, D - 2.2f, X - wd, deckB, D, X - wd, foot, D, X - wd, foot, D - 2.2f)
    }

    private fun bridge(c: Canvas) {
        val conc = pal.conc; val sh = pal.concSh
        val lightLeft = twilight < 0.5f || night
        val cols = ArrayList<Float>()
        var cxv = xMin
        while (cxv <= xMax) { if (abs(cxv) > 26f) cols += cxv; cxv += 17.5f }
        fun foot(X: Float) = if (abs(X) <= half) archTop(X) + 0.5f else ground(X)
        for (X in cols) column(c, X, 4f, foot(X), mix(conc, pal.haze, 0.12f), mix(sh, pal.haze, 0.12f), lightLeft)
        val ax0 = max(xMin, -half)
        fill(mix(conc, Color.WHITE, 0.05f)); strip(c, ax0, half, -5f, 5f, 3f, ::archTop, ::archTop)      // the rib's top (seen low down)
        fill(mix(sh, Color.BLACK, 0.1f)); strip(c, ax0, half, -5f, 5f, 3f, ::archBot, ::archBot)         // its underside (near the crown)
        proj(0f, -7f, -5f); val gy0 = oy; proj(half, 80f, -5f); val gy1 = oy
        p.color = Color.BLACK; p.shader = LinearGradient(0f, gy0, 0f, gy1, conc, mix(conc, sh, 0.45f), Shader.TileMode.CLAMP)
        strip(c, ax0, half, -5f, -5f, 3f, ::archTop, ::archBot); p.shader = null                        // the near face
        for (X in cols) column(c, X, -4f, foot(X), conc, sh, lightLeft)
        // Deck: underside, near face, parapet; weathered streaks and weeds (no traffic any more).
        fill(mix(sh, Color.BLACK, 0.15f)); strip(c, xMin - 20f, xMax, -6f, 6f, 4f, { deckB }, { deckB })
        proj(0f, deckT, -6f); val dy0 = oy; proj(0f, deckB, -6f); val dy1 = oy
        p.color = Color.BLACK; p.shader = LinearGradient(0f, dy0, 0f, dy1, mix(conc, Color.WHITE, 0.1f), conc, Shader.TileMode.CLAMP)
        strip(c, xMin - 20f, xMax, -6f, -6f, 4f, { deckT }, { deckB }); p.shader = null
        fill(mix(conc, sh, 0.25f)); strip(c, xMin - 20f, xMax, -6f, -6f, 4f, { deckT - 1.1f }, { deckT })
        val r = Mulberry32(91)
        var X = xMin - 20f
        while (X < xMax) {
            proj(X, deckT - 1.1f, -6f); val x0 = ox; val y0 = oy; val z = oz
            proj(X, deckT + 1f + r.next() * 3f, -6f); val y1 = oy
            proj(X + 0.5f + r.next(), 0f, -6f); val x2 = ox
            fill(withAlpha(Color.rgb(40, 34, 28), ((0.12f + r.next() * 0.14f) * 255).toInt()))
            c.drawRect(x0, y0, x0 + max(1f, x2 - x0), y1, p)
            if (r.next() < 0.35f) { fill(mix(pal.grass, Color.BLACK, 0.1f)); val sz = (3f + 160f / z) * k; c.drawOval(x0 - sz, y0 - sz * 0.6f, x0 + sz, y0 + sz * 0.6f, p) }
            X += 3f + r.next() * 6f
        }
        // The Skywalk: a mesh cage under the near half of the deck, out to the jump platform at the crown.
        fill(withAlpha(mix(sh, Color.BLACK, 0.3f), 178)); strip(c, xMin - 20f, -3f, -3f, 1.5f, 4f, { deckB + 2.6f }, { deckB + 2.6f })
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * k; p.color = withAlpha(mix(conc, Color.WHITE, 0.2f), 216)
        X = xMin - 20f
        while (X < -3f) {
            proj(X, deckB, -3f); val ax = ox; val ay = oy; proj(X + 2.5f, deckB + 2.6f, -3f); val bx = ox; val by = oy
            proj(X + 2.5f, deckB, -3f); val cx = ox; val cy = oy; proj(X, deckB + 2.6f, -3f); val ex = ox; val ey = oy
            c.drawLine(ax, ay, bx, by, p); c.drawLine(cx, cy, ex, ey, p)
            X += 2.5f
        }
        for ((yy, dd) in listOf(deckB to -3f, deckB + 2.6f to -3f, deckB + 2.6f to 1.5f)) {
            path.reset(); var xx = xMin - 20f; var first = true
            while (xx <= -3f) { proj(xx, yy, dd); if (first) path.moveTo(ox, oy) else path.lineTo(ox, oy); first = false; xx += 4f }
            c.drawPath(path, p)
        }
        fill(mix(sh, Color.BLACK, 0.15f)); quad(c, -3f, deckB, -4f, 6f, deckB, -4f, 6f, deckB + 3.2f, -4f, -3f, deckB + 3.2f, -4f)
        fill(if (night) 0xFFF5C46A.toInt() else 0xFFE64B2C.toInt()); quad(c, -3f, deckB + 2.7f, -4f, 6f, deckB + 2.7f, -4f, 6f, deckB + 3.2f, -4f, -3f, deckB + 3.2f, -4f)
        if (night) { // lights along the Skywalk, and a soft floodlight on the arch
            X = xMin
            while (X < 0f) { proj(X, deckB + 2.6f, -3f); glow(c, ox, oy, 600f / oz * 3f * k, 0xFFD68C, 0.7f); X += 10f }
            proj(40f, 20f, -5f); glow(c, ox, oy, 300f * k, 0xFFDCAA, 0.16f)
        }
    }

    private fun glow(c: Canvas, x: Float, y: Float, rad: Float, rgb: Int, a: Float) {
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = RadialGradient(x, y, max(1f, rad), withAlpha(rgb, (a * 255).toInt()), rgb and 0xFFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); p.shader = null
    }

    // ---- Puffs: smoke, steam, spouts and spray ------------------------------------------------------
    // Two soft discs, white and smoke-dark, drawn scaled: smoke starts dark and pales as it
    // rises by fading from the dark disc to the white one.

    private val puffBmp: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 32f, pp)
    }
    private val smokeBmp: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFF3A3838.toInt(), 0xB33A3838.toInt(), 0x003A3838), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 32f, pp)
    }
    private val puffPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val puffSrc = Rect(0, 0, 64, 64)
    private val puffDst = RectF()

    private fun puff(c: Canvas, x: Float, y: Float, r: Float, a: Float, dark: Float = 0f) {
        if (r <= 0.5f || a <= 0.01f) return
        puffDst.set(x - r, y - r, x + r, y + r)
        val tint = if (night) 0.62f else 1f
        puffPaint.alpha = (a * (1f - dark) * 255 * tint).toInt().coerceIn(0, 255)
        if (puffPaint.alpha > 0) c.drawBitmap(puffBmp, puffSrc, puffDst, puffPaint)
        if (dark > 0f) { puffPaint.alpha = (a * dark * 255).toInt().coerceIn(0, 255); c.drawBitmap(smokeBmp, puffSrc, puffDst, puffPaint) }
    }

    private class Puff { var x = 0f; var y = 0f; var r = 0f; var grow = 0f; var vx = 0f; var vy = 0f; var age = 0f; var life = 0f; var smoke = false }
    private val puffs = ArrayList<Puff>()
    private val spare = ArrayList<Puff>()

    private fun emit(x: Float, y: Float, r: Float, grow: Float, vx: Float, vy: Float, life: Float, smoke: Boolean) {
        if (puffs.size > 160) return
        val q = if (spare.isEmpty()) Puff() else spare.removeAt(spare.size - 1)
        q.x = x; q.y = y; q.r = r; q.grow = grow; q.vx = vx; q.vy = vy; q.age = 0f; q.life = life; q.smoke = smoke
        puffs += q
    }

    private fun updatePuffs(dt: Float) {
        val wind = (6f + cfg.wind * 20f) * k
        val it = puffs.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.age += dt
            if (q.age >= q.life) { it.remove(); spare += q; continue }
            q.x += (q.vx + wind) * dt; q.y += q.vy * dt; q.vy *= exp(-dt * 0.35f); q.r += q.grow * dt
        }
    }

    private fun drawPuffs(c: Canvas) {
        for (q in puffs) {
            val u = q.age / q.life
            val a = (1f - u).pow(1.4f) * (if (q.smoke) 0.75f else 0.7f)
            puff(c, q.x, q.y, q.r, a, if (q.smoke) (1f - u) * 0.9f else 0f)
        }
    }

    // ---- Life in the bay -------------------------------------------------------------------------
    // Size from the distance a water line lies at: about (y - hz) / 200 px per metre, scaled up.

    private fun ppm(y: Float, ex: Float) = max(0.05f, (y - hz) / (200f * k)) * ex * k

    private class Whale(var x: Float, val y: Float, var dir: Float, var t: Float, var cycle: Int)
    private val whales = listOf(Whale(w * 0.3f, hz + 120f * k, 1f, 2f, 0), Whale(w * 0.62f, hz + 195f * k, -1f, 9f, 1))
    private class Boat(val kind: Int, var x: Float, val y: Float, var dir: Float, val v: Float, var t: Float = 0f)
    private val boats = listOf(
        Boat(CAT, w * 0.6f, hz + 128f * k, -1f, 2f), Boat(SKI, w * 0.1f, hz + 180f * k, 1f, 5f),
        Boat(YACHT, w * 0.45f, hz + 70f * k, 1f, 1.6f), Boat(YACHT, w * 0.85f, hz + 95f * k, -1f, 2f),
    )
    private val jetski = Boat(JETSKI, w * 0.5f, hz + 225f * k, 1f, 14f)
    private var podX = w * 0.2f
    private val podY = hz + 205f * k
    private var podDir = 1f
    private var podT = 0f
    // The jetski and the dolphins come and go (each is about for a minute or so).
    private var jetskiFor = 0f
    private var jetskiIn = 30f + rnd.nextFloat() * 120f
    private var podFor = 0f
    private var podIn = 10f + rnd.nextFloat() * 60f
    private val daytime get() = !night

    private fun wake(c: Canvas, x: Float, y: Float, s: Float, dir: Float, len: Float, a: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, s * 0.5f); p.color = withAlpha(Color.WHITE, (a * 255).toInt())
        c.drawLine(x, y, x - dir * len * s, y - s * 1.2f, p)
        c.drawLine(x, y + s * 0.3f, x - dir * len * s, y + s * 1.8f, p)
        p.style = Paint.Style.FILL
    }

    private fun wrap(x: Float) = if (x > w + 80f * k) -80f * k else if (x < -80f * k) w + 80f * k else x

    private fun updateBay(dt: Float) {
        for (wh in whales) {
            val s = ppm(wh.y, 5f)
            val before = wh.t % 16f
            wh.t += dt
            if (wh.t % 16f < before) wh.cycle++
            wh.x = wrap(wh.x + wh.dir * 1.2f * s * dt)
        }
        for (b in boats) { val s = ppm(b.y, 4.5f); b.x = wrap(b.x + b.dir * b.v * s * 0.25f * dt) }
        if (jetskiFor > 0f) {
            jetskiFor -= dt; jetski.t += dt
            val s = ppm(jetski.y, 6f)
            jetski.x += jetski.dir * jetski.v * s * 0.25f * dt
            if (jetski.x > w * 0.8f) jetski.dir = -1f; if (jetski.x < w * 0.25f) jetski.dir = 1f
        } else if (daytime) { jetskiIn -= dt; if (jetskiIn <= 0f) { jetskiFor = 60f + rnd.nextFloat() * 40f; jetskiIn = 240f + rnd.nextFloat() * 360f } }
        if (podFor > 0f) {
            podFor -= dt; podT += dt
            val s = ppm(podY, 6f)
            podX += podDir * 4f * s * dt
            if (podX > w * 0.85f) podDir = -1f; if (podX < w * 0.15f) podDir = 1f
        } else if (daytime) { podIn -= dt; if (podIn <= 0f) { podFor = 50f + rnd.nextFloat() * 40f; podIn = 150f + rnd.nextFloat() * 240f } }
    }

    private fun drawBay(c: Canvas) {
        val dark = if (night) 0xFF0C1118.toInt() else 0xFF1D2228.toInt()
        for (wh in whales) {
            val s = ppm(wh.y, 5f); val u = wh.t % 16f
            val breach = wh.cycle % 3 == 2
            if (breach && u < 3.2f) { // out of the water at a steep angle, then crashing back
                val kk = u / 3.2f; val rise = sin(kk * PI.toFloat())
                c.save(); c.translate(wh.x, wh.y); c.rotate(Math.toDegrees((-wh.dir * (1.1f - kk * 1.6f)).toDouble()).toFloat())
                fill(dark); c.drawOval(-8f * s, -rise * 8f * s - 2.4f * s, 8f * s, -rise * 8f * s + 2.4f * s, p)
                fill(if (night) 0xFF3A4250.toInt() else 0xFFE8E6E0.toInt()); c.drawOval(-5f * s, -rise * 8f * s + 0.1f * s, 3f * s, -rise * 8f * s + 1.9f * s, p)
                c.restore()
                if (kk > 0.7f) { val sp = (kk - 0.7f) / 0.3f; puff(c, wh.x, wh.y - 4f * s * sp, 9f * s * (0.4f + sp), 0.85f * (1f - sp * 0.5f)) }
            } else if (breach && u < 6f) {
                val sp = (u - 3.2f) / 2.8f; puff(c, wh.x, wh.y - 3f * s * (1f - sp), 10f * s, 0.7f * (1f - sp))
            } else if (u > 6f && u < 11f) { // the back rolling at the surface, callosities on the head, the V-shaped blow
                val kk = (u - 6f) / 5f; val up = sin(kk * PI.toFloat())
                c.save(); c.clipRect(wh.x - 20f * s, wh.y - 10f * s, wh.x + 20f * s, wh.y)
                fill(dark); c.drawOval(wh.x - 7f * s, wh.y + 1.6f * s * (1f - up) - 1.8f * s, wh.x + 7f * s, wh.y + 1.6f * s * (1f - up) + 1.8f * s, p)
                fill(0xD9EBE6D7.toInt()); for (i in 0..2) c.drawCircle(wh.x + wh.dir * (5f + i * 0.7f) * s, wh.y - up * 0.9f * s, 0.35f * s, p)
                c.restore()
                if (kk < 0.35f) { val bk = kk / 0.35f; for (side in intArrayOf(-1, 1)) puff(c, wh.x + wh.dir * 5f * s + side * bk * 2.2f * s, wh.y - (1f + bk * 6f) * s, (1f + bk * 2.6f) * s, 0.8f * (1f - bk * 0.6f)) }
            } else if (u > 11f && u < 13.5f) { // flukes up as it dives
                val kk = (u - 11f) / 2.5f; val up = sin(kk * PI.toFloat())
                c.save(); c.translate(wh.x - wh.dir * 4f * s, wh.y); fill(dark)
                c.drawRect(-0.5f * s, -up * 3f * s, 0.5f * s, 0f, p)
                path.reset(); path.moveTo(0f, -up * 3f * s)
                path.quadTo(-3f * s, -up * 4.5f * s, -4.2f * s, -up * 3.2f * s); path.quadTo(-2f * s, -up * 2.8f * s, 0f, -up * 2.4f * s)
                path.quadTo(2f * s, -up * 2.8f * s, 4.2f * s, -up * 3.2f * s); path.quadTo(3f * s, -up * 4.5f * s, 0f, -up * 3f * s)
                c.drawPath(path, p); c.restore()
                wake(c, wh.x - wh.dir * 4f * s, wh.y + 0.3f * s, s * 0.5f, wh.dir, 3f, 0.5f * up)
            }
        }
        if (podFor > 0f) { // a dolphin pod porpoising along, each leaping in turn
            val s = ppm(podY, 6f)
            for (i in 0 until 6) {
                val ph = (podT * 0.8f + i * 0.37f) % 1.6f
                val dx = podX - podDir * i * 3.2f * s; val dyy = podY + (i % 2) * 2f * s
                if (ph >= 1f) continue
                val hx = dx + podDir * (ph - 0.5f) * 5f * s; val hy = dyy - sin(PI.toFloat() * ph) * 2f * s
                c.save(); c.translate(hx, hy); c.rotate(Math.toDegrees((podDir * (ph - 0.5f) * 1.6f).toDouble()).toFloat()); c.scale(podDir, 1f)
                fill(0xFF3D4A58.toInt()); c.drawOval(-1.5f * s, -0.4f * s, 1.5f * s, 0.4f * s, p)
                path.reset(); path.moveTo(-0.2f * s, -0.3f * s); path.lineTo(0.2f * s, -0.85f * s); path.lineTo(0.5f * s, -0.3f * s); path.close(); c.drawPath(path, p)
                c.restore()
                if (ph < 0.12f || ph > 0.88f) { fill(0xCCFFFFFF.toInt()); c.drawOval(hx - 0.9f * s, dyy - 0.3f * s, hx + 0.9f * s, dyy + 0.3f * s, p) }
            }
        }
        for (b in boats) {
            if (night && b.kind == YACHT) continue
            drawBoat(c, b, ppm(b.y, 4.5f), b.y)
        }
        if (jetskiFor > 0f) {
            val s = ppm(jetski.y, 6f); val yy = jetski.y + sin(jetski.t * 0.9f) * 12f * k; val x = jetski.x; val d = jetski.dir
            wake(c, x, yy, s, d, 9f, 0.7f)
            for (i in 0 until 6) puff(c, x - d * (1f + i * 0.7f) * s, yy - (1.2f + sin(i * 0.8f) * 1.2f) * s, (0.5f + i * 0.25f) * s, 0.55f - i * 0.07f) // rooster tail
            fill(0xFFE8E8EA.toInt()); c.drawRect(x - 1.4f * s, yy - 0.7f * s, x + 1.4f * s, yy, p)
            fill(0xFFD6322A.toInt()); c.drawRect(x - 0.2f * s, yy - 1.1f * s, x + 1f * s, yy - 0.7f * s, p)
            fill(0xFF1B1E24.toInt()); c.drawCircle(x + d * 0.1f * s, yy - 2.3f * s, 0.35f * s, p); c.drawRect(x - 0.3f * s, yy - 2f * s, x + 0.3f * s, yy - s, p)
        }
        if (night) { // a chokka (squid) boat working under its bright lamps
            val x = w * 0.58f + sin(time * 0.05f) * 20f * k; val y = hz + 80f * k; val s = ppm(y, 4.5f)
            glow(c, x, y - 2f * s, 30f * s, 0xE6F0FF, 0.55f)
            fill(0xFF1A2030.toInt()); c.drawRect(x - 6f * s, y - 1.5f * s, x + 6f * s, y, p)
            fill(0xFFF4F8FF.toInt()); var kx = -5; while (kx <= 5) { c.drawRect(x + kx * s, y - 3f * s, x + kx * s + 0.6f * s, y - 2.4f * s, p); kx += 2 }
            fill(0x59E6F0FF); for (i in 0 until 8) c.drawRect(x - 3f * s + sin(time + i) * s, y + i * 1.5f * s, x - 3f * s + sin(time + i) * s + 6f * s * (1f - i / 9f), y + i * 1.5f * s + 0.5f * s, p) // its reflection
        }
    }

    private fun drawBoat(c: Canvas, b: Boat, s: Float, y: Float) {
        val x = b.x; val d = b.dir
        wake(c, x - d * (if (b.kind == CAT) 7f else 4f) * s, y, s, d, if (b.kind == YACHT) 6f else 12f, if (b.kind == YACHT) 0.35f else 0.6f)
        c.save(); c.translate(x, y); c.scale(d, 1f)
        when (b.kind) {
            CAT -> { // whale-watching catamaran, passengers along the rail
                fill(if (night) 0xFF3A4150.toInt() else 0xFFF2F2EE.toInt()); c.drawRect(-7f * s, -1.4f * s, 7f * s, 0f, p)
                fill(if (night) 0xFF2C3240.toInt() else 0xFFDFE3E6.toInt()); c.drawRect(-4f * s, -3.2f * s, 4f * s, -1.4f * s, p)
                fill(0xFF28323E.toInt()); c.drawRect(-3.4f * s, -2.8f * s, 3.4f * s, -2f * s, p)
                fill(0xFF2B2F36.toInt()); var kx = -6f; while (kx < 6f) { c.drawRect(kx * s, -2.2f * s, (kx + 0.5f) * s, -1.4f * s, p); kx += 1.4f }
            }
            SKI -> { // fishing ski-boat with its outboard
                fill(if (night) 0xFF3A4150.toInt() else 0xFFF4F4F0.toInt())
                path.reset(); path.moveTo(-3.5f * s, -s); path.lineTo(3.8f * s, -1.2f * s); path.lineTo(3f * s, 0f); path.lineTo(-3.5f * s, 0f); path.close(); c.drawPath(path, p)
                fill(0xFF2A5D9A.toInt()); c.drawRect(-3.5f * s, -0.45f * s, 3.5f * s, -0.15f * s, p)
                fill(0xFF20242A.toInt()); c.drawRect(-4f * s, -1.4f * s, -3.4f * s, 0f, p); c.drawRect(-s, -2.4f * s, -0.5f * s, -s, p)
            }
            else -> { // yacht heeling under main and jib
                c.rotate(-7f)
                fill(0xFFFFFFFF.toInt())
                path.reset(); path.moveTo(-4.5f * s, -s); path.lineTo(5f * s, -s); path.lineTo(3.6f * s, 0f); path.lineTo(-4f * s, 0f); path.close(); c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.25f * s); p.color = 0xFF3A3E46.toInt(); c.drawLine(0f, -s, 0f, -14f * s, p)
                val sail = if (twilight > 0.5f) 0xFFFFE3C2.toInt() else 0xFFF8F6F0.toInt()
                fill(sail)
                path.reset(); path.moveTo(-0.2f * s, -13.5f * s); path.quadTo(-3f * s, -7f * s, -3.8f * s, -1.6f * s); path.lineTo(-0.2f * s, -1.6f * s); path.close(); c.drawPath(path, p)
                fill(mix(sail, Color.rgb(120, 130, 140), 0.15f))
                path.reset(); path.moveTo(0.3f * s, -13f * s); path.quadTo(2.6f * s, -7f * s, 4.4f * s, -1.4f * s); path.lineTo(0.3f * s, -1.4f * s); path.close(); c.drawPath(path, p)
            }
        }
        c.restore()
        if (night) { // navigation lights
            val r = max(1.5f * k, 0.4f * s)
            fill(if (d > 0f) 0xFF3CFF7A.toInt() else 0xFFFF3C3C.toInt()); c.drawCircle(x + d * 3f * s, y - 1.6f * s, r, p)
            fill(0xFFFFF6E0.toInt()); c.drawCircle(x, y - 4f * s, r, p)
        }
    }

    // ---- Surf ------------------------------------------------------------------------------------

    private fun drawSurf(c: Canvas) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.shader = null
        val dim = if (night) 0.5f else 1f
        for ((i, s) in shores.withIndex()) {
            val wash = 0.5f + 0.5f * sin(time * 0.9f + i * 1.7f); val near = 1f - s.haze
            p.color = withAlpha(mix(pal.foam, pal.haze, s.haze * 0.5f), ((0.45f + 0.5f * wash) * dim * 255).toInt())
            p.strokeWidth = (2f + 9f * near) * (0.6f + 0.5f * wash) * k
            p.pathEffect = DashPathEffect(floatArrayOf((5f + 12f * near) * k, (3f + 6f * wash) * k), -time * 5f * k)
            c.drawPath(s.path, p)
        }
        p.pathEffect = null
        for (j in 0 until 5) { // swells across the bay, funnelling up the inlet
            val u = (time * 0.03f + j / 5f) % 1f; val y = hz + 30f * k + u * (h * 0.74f - hz - 30f * k)
            val into = max(0f, (y - h * 0.52f) / (h * 0.22f))
            val ww = (90f + u * 260f) * k * (1f - into * 0.75f); val cx = w * (0.5f + sin(j * 2.3f) * 0.05f * (1f - into))
            p.color = withAlpha(pal.foam, (sin(u * PI.toFloat()) * 0.3f * dim * 255).toInt()); p.strokeWidth = (1f + u * 3f) * k
            path.reset(); path.moveTo(cx - ww, y + 5f * k); path.quadTo(cx, y - 5f * k, cx + ww, y + 5f * k); c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    // ---- Under the deck: Skywalk walkers and bungy jumpers -------------------------------------------

    private class Walker(var X: Float, var v: Float)
    private val walkers = List(4) { Walker(-100f + it * 22f, (if (it % 2 == 0) 1f else -1f) * (0.9f + it * 0.15f)) }
    private var jumpT = -1f
    private var nextJump = 60f + rnd.nextFloat() * 120f

    /**
     * Metres below the platform: free fall to full stretch, then damped bounces on the cord.
     * (Shorter than the real 216 m drop so the jumper stays on screen.)
     */
    private fun drop(t: Float): Float {
        val stretch = 55f
        if (t < 3.2f) { val u = t / 3.2f; return stretch * (1f - cos(u * PI.toFloat() / 2f)).pow(1.15f) }
        val kk = t - 3.2f
        return stretch - stretch * 0.55f * abs(sin(kk * 1.4f)) * exp(-kk * 0.45f)
    }

    private fun drawFigure(c: Canvas, x: Float, y: Float, sc: Float, upside: Boolean, col: Int) {
        c.save(); c.translate(x, y); c.scale(sc, sc); if (upside) c.rotate(180f + sin(time * 2f) * 11f)
        fill(col)
        if (upside) {
            c.drawRect(-3.5f, 2f, 3.5f, 18f, p); c.drawCircle(0f, 22f, 4f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = col
            c.drawLine(-3f, 8f, -10f, 20f, p); c.drawLine(3f, 8f, 10f, 20f, p)
        } else { c.drawRect(-3f, -18f, 3f, -4f, p); c.drawCircle(0f, -21f, 3.5f, p) }
        c.restore()
    }

    private fun drawUnderDeck(c: Canvas) {
        if (daytime) for (wk in walkers) {
            proj(wk.X, deckB + 0.4f, -1f); val x = ox; val y0 = oy; proj(wk.X, deckB + 2.5f, -1f); val hh = oy - y0
            val step = sin(time * 6f + wk.X) * hh * 0.08f
            fill(0xFF2B2F36.toInt())
            c.drawCircle(x, y0 + hh * 0.12f, hh * 0.1f, p)
            c.drawRect(x - hh * 0.08f, y0 + hh * 0.22f, x + hh * 0.08f, y0 + hh * 0.64f, p)
            c.drawRect(x - hh * 0.07f + step, y0 + hh * 0.62f, x - hh * 0.01f + step, y0 + hh, p)
            c.drawRect(x + hh * 0.02f - step, y0 + hh * 0.62f, x + hh * 0.08f - step, y0 + hh, p)
        }
        proj(1.5f, deckB + 3.2f, -3.5f); val px = ox; val top = oy; val sc = 60f / oz * k
        val col = if (night) 0xFFF0D9A8.toInt() else 0xFFE0462A.toInt()
        if (jumpT >= 0f) {
            proj(1.5f + sin(jumpT * 0.9f) * 1.5f, deckB + 3.2f + drop(jumpT), -3.5f)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * k; p.color = 0xFF1D1D1D.toInt()
            c.drawLine(px, top, ox, oy, p)
            drawFigure(c, ox, oy, sc, true, col)
        } else if (daytime) drawFigure(c, px, top, sc, false, col)
    }

    // ---- The Outeniqua Choo Tjoe -----------------------------------------------------------------------

    private val ts = GardenDesign.TRAIN_SCALE
    private val consist = intArrayOf(LOCO, TENDER, COACH, COACH, COACH, COACH, COACH)
    private val lengths = floatArrayOf(20f, 9f, 18f, 18f, 18f, 18f, 18f)
    private val trainLen = lengths.sum() + consist.size * 0.8f
    private var trainX = Float.NaN
    private var whistle = 0f
    private var chuffT = 0f
    private var nextTrain = 20f + rnd.nextFloat() * 40f
    private val parapet = Path()

    private fun face(c: Canvas, xa: Float, xb: Float, y0: Float, y1: Float, d: Float, col: Int) {
        fill(col); quad(c, xa, y0, d, xb, y0, d, xb, y1, d, xa, y1, d)
    }

    private fun updateTrain(dt: Float) {
        if (trainX.isNaN()) {
            nextTrain -= dt
            if (nextTrain <= 0f) launchTrain()
            return
        }
        trainX -= GardenDesign.TRAIN_SPEED * dt
        if (whistle > 0f) whistle -= dt
        if (trainX + trainLen < -80f) {
            trainX = Float.NaN
            nextTrain = 60f * (GardenDesign.TRAIN_EVERY_MIN + rnd.nextFloat() * (GardenDesign.TRAIN_EVERY_MAX - GardenDesign.TRAIN_EVERY_MIN))
            return
        }
        // Smoke on every chuff, steam from the cylinders, and the whistle as it rolls onto the bridge.
        val xa = trainX; val r0 = deckT; val d = -1.6f
        chuffT -= dt
        if (chuffT <= 0f && xa > -70f) {
            chuffT = 0.32f
            proj(xa + 2.8f, r0 - 5.1f * ts, d + 0.9f); val kk = foc / oz
            emit(ox, oy, 2.6f * kk, 0.9f * kk * 2.2f, (2f + rnd.nextFloat() * 2f) * kk * 0.25f, -(9f + rnd.nextFloat() * 3f) * kk * 0.25f, 9f + rnd.nextFloat() * 3f, true)
            if (rnd.nextBoolean()) { proj(xa + 1.5f, r0 - 1.2f * ts, d - 0.4f); val k2 = foc / oz; emit(ox, oy, 1.6f * k2, 0.7f * k2 * 2f, -(1f + rnd.nextFloat()) * k2 * 0.3f, -1.2f * k2 * 0.25f, 2.2f, false) }
        }
        if (whistle > 1.2f && rnd.nextFloat() < 0.6f) { proj(xa + 9.4f, r0 - 4.6f * ts, d + 0.9f); val k3 = foc / oz; emit(ox, oy, 1.2f * k3, 0.7f * k3 * 2f, 0.4f * k3 * 0.25f, -9f * k3 * 0.25f, 2.5f, false) }
    }

    fun launchTrain() { if (trainX.isNaN()) { trainX = 175f; whistle = 2.6f } }
    fun launchJump() { if (jumpT < 0f) jumpT = 0f }
    fun launchDolphins() { podFor = 70f }
    fun launchJetski() { jetskiFor = 80f }
    fun launchBreach() { whales[1].cycle = 2; whales[1].t = (whales[1].t / 16f).toInt() * 16f + 15.9f }

    private fun drawTrain(c: Canvas) {
        c.save()
        // Everything of the train lies above the line of the near parapet's top.
        proj(-120f, deckT - 1.1f, -6f); val ax = ox; val ay = oy; proj(250f, deckT - 1.1f, -6f); val m = (oy - ay) / (ox - ax)
        parapet.reset(); parapet.moveTo(-50f, ay + m * (-50f - ax)); parapet.lineTo(w + 50f, ay + m * (w + 50f - ax)); parapet.lineTo(w + 50f, -h); parapet.lineTo(-50f, -h); parapet.close()
        c.clipPath(parapet)
        if (!trainX.isNaN()) {
            val r = deckT; val d = -1.6f
            val starts = FloatArray(consist.size)
            var xx = trainX
            for (i in consist.indices) { starts[i] = xx; xx += lengths[i] + 0.8f }
            for (i in consist.indices.reversed()) { // the far ones first
                val xa = starts[i]; val xb = xa + lengths[i]
                if (xb < -60f || xa > 200f) continue
                when (consist[i]) {
                    COACH -> {
                        face(c, xa, xb, r - 0.6f * ts, r - 3.9f * ts, d, if (night) 0xFF2A1214.toInt() else if (twilight > 0.5f) 0xFF7A2C22.toInt() else 0xFF6E1F22.toInt())
                        face(c, xa + 0.6f, xb - 0.6f, r - 2.4f * ts, r - 3.3f * ts, d, if (night) 0xFF3A2A1C.toInt() else 0xFFE8DCC0.toInt())
                        var wx = xa + 1.6f
                        while (wx < xb - 1.6f) { face(c, wx, wx + 1.3f, r - 2.5f * ts, r - 3.2f * ts, d, if (night) 0xF2FFD68C.toInt() else 0xD928303A.toInt()); wx += 2.2f }
                        face(c, xa, xb, r - 3.9f * ts, r - 4.4f * ts, d, if (night) 0xFF121214.toInt() else 0xFF3A3634.toInt())
                    }
                    TENDER -> {
                        face(c, xa, xb, r - 0.6f * ts, r - 3.4f * ts, d, if (night) 0xFF0C0D10.toInt() else 0xFF1C1D20.toInt())
                        face(c, xa, xb, r - 3.0f * ts, r - 3.4f * ts, d, if (night) 0xFF1A1A1E.toInt() else 0xFF3A3C40.toInt())
                        face(c, xa + 1f, xb - 1f, r - 1.6f * ts, r - 2.1f * ts, d, if (night) 0xFF3A1210.toInt() else 0xFF8A2A22.toInt())
                    }
                    else -> { // the locomotive: smokebox and boiler, dome, chimney, cab at the back
                        val xc = xb - 5.5f
                        val blk = if (night) 0xFF0B0C0F.toInt() else 0xFF16171A.toInt()
                        face(c, xa, xb, r - 0.6f * ts, r - 1.4f * ts, d, if (night) 0xFF140808.toInt() else 0xFF8E1F1A.toInt())
                        face(c, xa + 0.4f, xc, r - 1.4f * ts, r - 3.6f * ts, d - 0.2f, blk)
                        face(c, xa + 0.4f, xc, r - 3.0f * ts, r - 3.3f * ts, d - 0.2f, if (night) 0xFF1D1F24.toInt() else 0xFF4A4E56.toInt())
                        face(c, xa + 2.2f, xa + 3.4f, r - 3.6f * ts, r - 5.0f * ts, d + 0.9f, blk)
                        face(c, xa + 8.5f, xa + 10.2f, r - 3.6f * ts, r - 4.4f * ts, d + 0.9f, blk)
                        face(c, xc, xb, r - 1.0f * ts, r - 4.3f * ts, d, blk)
                        face(c, xc + 1.2f, xb - 1.2f, r - 2.6f * ts, r - 3.6f * ts, d, if (night) 0xF2FF963C.toInt() else 0xE632363E.toInt())
                        // The smokebox front, facing us as it approaches.
                        proj(xa, r - 2.5f * ts, 0f); val fx = ox; val fy = oy; val rr = 1.25f * ts * foc / oz
                        proj(xa, r - 2.5f * ts, -1.2f); val lx = ox; proj(xa, r - 2.5f * ts, 1.2f); val rx = max(abs(ox - lx) / 2f, rr * 0.2f)
                        fill(if (night) 0xFF0A0B0D.toInt() else 0xFF111214.toInt()); c.drawOval(fx - rx, fy - rr, fx + rx, fy + rr, p)
                        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, rr * 0.12f); p.color = if (night) 0xFF2A2C30.toInt() else 0xFF5A5E66.toInt()
                        c.drawOval(fx - rx * 0.7f, fy - rr * 0.7f, fx + rx * 0.7f, fy + rr * 0.7f, p)
                        proj(xa, r - 4.0f * ts, 0f)
                        if (night || twilight > 0.5f) glow(c, ox, oy, rr * 3f, 0xFFF0C8, 0.9f)
                        fill(0xFFFFF4D8.toInt()); c.drawCircle(ox, oy, max(1.5f * k, rr * 0.22f), p)
                    }
                }
            }
        }
        drawPuffs(c)
        c.restore()
    }

    // ---- Frame -------------------------------------------------------------------------------------

    override fun update(dt: Float) {
        time += dt
        updateBay(dt)
        updateTrain(dt)
        updatePuffs(dt)
        for (wk in walkers) { wk.X += wk.v * dt; if (wk.X < -125f || wk.X > -6f) wk.v = -wk.v }
        if (jumpT >= 0f) { jumpT += dt; if (jumpT > 13f) jumpT = -1f }
        else if (daytime && !grey) { nextJump -= dt; if (nextJump <= 0f) { jumpT = 0f; nextJump = 120f + rnd.nextFloat() * 180f } }
    }

    /** 2 = something moves quickly (train, jump, jetski, dolphins, a breach); 0 = only the bay drifting (the calm rate). */
    val motion: Int get() {
        val breach = whales.any { it.cycle % 3 == 2 && it.t % 16f < 6f }
        return if (!trainX.isNaN() || jumpT >= 0f || jetskiFor > 0f || podFor > 0f || breach || puffs.isNotEmpty()) 2 else 0
    }

    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        if (sea == null || key() != bakedKey) bake()
        canvas.drawBitmap(sea!!, 0f, seaTop, null)
        canvas.save(); canvas.translate(0f, dy)
        drawSurf(canvas)
        drawBay(canvas)
        canvas.restore()
        canvas.drawBitmap(landBmp!!, 0f, landTop, null)
        canvas.save(); canvas.translate(0f, dy)
        drawUnderDeck(canvas)
        drawTrain(canvas)
        canvas.restore()
    }

    private companion object {
        const val CAT = 0; const val SKI = 1; const val YACHT = 2; const val JETSKI = 3
        const val LOCO = 0; const val TENDER = 1; const val COACH = 2

        val DAY = Pal(0xFF1D4F9A.toInt(), 0xFF2A7BBD.toInt(), 0xFF3CC4C8.toInt(), 0xFF9FD0E6.toInt(), 0xFFF4FBFF.toInt(), 0xFF5C8F2C.toInt(), 0xFFA3C84C.toInt(), 0xFFB8643A.toInt(), 0xFFA19C90.toInt(), 0xFFD8D3C8.toInt(), 0xFF8F8A82.toInt(), 0xFFC7DDE6.toInt())
        val GOLD = Pal(0xFF2B3D6B.toInt(), 0xFF4C6189.toInt(), 0xFF6FA6A2.toInt(), 0xFFFFCF8A.toInt(), 0xFFFFE9CF.toInt(), 0xFF6B6E2A.toInt(), 0xFFC0A94A.toInt(), 0xFFC9713E.toInt(), 0xFFB8977A.toInt(), 0xFFF0C79A.toInt(), 0xFF7D6A5C.toInt(), 0xFFE9B88F.toInt())
        val NIGHT = Pal(0xFF0A1426.toInt(), 0xFF11213B.toInt(), 0xFF1B3A48.toInt(), 0xFFC9D6EA.toInt(), 0xFF9FB2C8.toInt(), 0xFF16231A.toInt(), 0xFF25331F.toInt(), 0xFF3A2A24.toInt(), 0xFF34383E.toInt(), 0xFF5A6170.toInt(), 0xFF262A33.toInt(), 0xFF22324A.toInt())
        val GREYED = Pal(0xFF3A4A5A.toInt(), 0xFF566878.toInt(), 0xFF6E9298.toInt(), 0xFFB8C4CC.toInt(), 0xFFE8EEF0.toInt(), 0xFF4E6A3A.toInt(), 0xFF7E9858.toInt(), 0xFF8E6248.toInt(), 0xFF8A8A88.toInt(), 0xFFB8B8B4.toInt(), 0xFF7A7A78.toInt(), 0xFFB0B8BE.toInt())
    }
}
