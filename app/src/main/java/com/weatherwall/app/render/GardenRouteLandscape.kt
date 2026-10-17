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
            if (q.smoke) puff(c, q.x - q.r * 0.2f, q.y - q.r * 0.3f, q.r * 0.65f, a * (if (night) 0.12f else 0.3f), (1f - u) * 0.35f) // light on its top
        }
    }

    // ---- Life in the bay (see GardenBay) ------------------------------------------------------------

    private val bay = GardenBay(w, hz, k, foc) { c, x, y, r, a, d -> puff(c, x, y, r, a, d) }
    private val daytime get() = !night

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
    fun launchDolphins() = bay.launchDolphins()
    fun launchJetski() = bay.launchJetski()
    fun launchBreach() = bay.launchBreach()

    /** A vertical side face at depth d between heights y0 (lower) and y1 (upper), lit from above. */
    private fun sideFace(c: Canvas, xa: Float, xb: Float, y0: Float, y1: Float, d: Float, col: Int, lt: Float = 0.2f, dk: Float = 0.3f) {
        proj(xa, y1, d); val ty = oy; proj(xa, y0, d); val by = oy
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, ty, 0f, by, intArrayOf(mix(col, Color.WHITE, lt), col, mix(col, Color.BLACK, dk)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        quad(c, xa, y0, d, xb, y0, d, xb, y1, d, xa, y1, d)
        p.shader = null
    }

    /** The end of a vehicle (the plane X = x), its top arched like a coach roof when [arch] > 0. */
    private fun endFace(c: Canvas, x: Float, y0: Float, y1: Float, half: Float, arch: Float, col: Int) {
        fill(col)
        path.reset()
        proj(x, y0, -half); path.moveTo(ox, oy)
        var d = -half
        while (d <= half + 0.01f) { val u = d / half; proj(x, y1 - arch * (1f - u * u), d); path.lineTo(ox, oy); d += half / 4f }
        proj(x, y0, half); path.lineTo(ox, oy)
        path.close(); c.drawPath(path, p)
    }

    /** A round thing seen from the side (boiler, chimney): a strip across X, shaded light on top. */
    private fun cylinderX(c: Canvas, xa: Float, xb: Float, yc: Float, r: Float, col: Int) {
        proj(xa, yc - r, 0f); val ty = oy; proj(xa, yc + r, 0f); val by = oy
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(0f, ty, 0f, by, intArrayOf(mix(col, Color.WHITE, 0.32f), mix(col, Color.WHITE, 0.08f), col, mix(col, Color.BLACK, 0.5f)), floatArrayOf(0f, 0.18f, 0.5f, 1f), Shader.TileMode.CLAMP)
        quad(c, xa, yc + r, 0f, xb, yc + r, 0f, xb, yc - r, 0f, xa, yc - r, 0f)
        p.shader = null
    }

    /** An upright round stack (chimney, dome base) between heights y0 and y1, shaded across. */
    private fun stack(c: Canvas, x: Float, y0: Float, y1: Float, r: Float, col: Int) {
        proj(x - r, y0, 0f); val lx = ox
        proj(x + r, y0, 0f); val rx = ox
        p.style = Paint.Style.FILL; p.color = Color.BLACK
        p.shader = LinearGradient(lx, 0f, rx, 0f, intArrayOf(mix(col, Color.BLACK, 0.4f), mix(col, Color.WHITE, 0.3f), col, mix(col, Color.BLACK, 0.5f)), floatArrayOf(0f, 0.3f, 0.55f, 1f), Shader.TileMode.CLAMP)
        quad(c, x - r, y0, 0f, x + r, y0, 0f, x + r, y1, 0f, x - r, y1, 0f)
        p.shader = null
    }

    private fun drawVehicles(c: Canvas) {
        val r = deckT; val d = -1.45f; val wd = 1.45f
        val camX = -GardenDesign.DISTANCE * sth // along the span, the camera sits off the near end
        val starts = FloatArray(consist.size)
        var xx = trainX
        for (i in consist.indices) { starts[i] = xx; xx += lengths[i] + 0.8f }
        val glass = if (night) 0xF2FFD68C.toInt() else 0xFF2A323E.toInt()
        for (i in consist.indices.reversed()) { // the far ones first
            val xa = starts[i]; val xb = xa + lengths[i]
            if (xb < -60f || xa > 200f) continue
            val showEnd = xa > camX
            when (consist[i]) {
                COACH -> {
                    val maroon = if (night) 0xFF2A1214.toInt() else if (twilight > 0.5f) 0xFF7A2C22.toInt() else 0xFF6E1F22.toInt()
                    // The gangway bellows to the vehicle in front.
                    fill(0xFF141416.toInt()); quad(c, xa - 0.8f, r - 0.9f * ts, -0.7f, xa, r - 0.9f * ts, -0.7f, xa, r - 3.6f * ts, -0.7f, xa - 0.8f, r - 3.6f * ts, -0.7f)
                    if (showEnd) {
                        endFace(c, xa, r - 0.6f * ts, r - 3.9f * ts, wd, 0.55f * ts, scale(maroon, 0.62f))
                        fill(scale(glass, if (night) 1f else 0.8f)); quad(c, xa, r - 2.5f * ts, -0.4f, xa, r - 2.5f * ts, 0.4f, xa, r - 3.3f * ts, 0.4f, xa, r - 3.3f * ts, -0.4f)
                    }
                    // The roof, curving away above the eave.
                    proj(xa, r - 4.45f * ts, -0.9f); val rt = oy; proj(xa, r - 3.9f * ts, d); val rb = oy
                    p.style = Paint.Style.FILL; p.color = Color.BLACK
                    p.shader = LinearGradient(0f, rt, 0f, rb, if (night) 0xFF1A1A1E.toInt() else 0xFF5E5A56.toInt(), if (night) 0xFF0E0E10.toInt() else 0xFF2E2A28.toInt(), Shader.TileMode.CLAMP)
                    quad(c, xa, r - 3.9f * ts, d, xb, r - 3.9f * ts, d, xb, r - 4.45f * ts, -0.9f, xa, r - 4.45f * ts, -0.9f); p.shader = null
                    sideFace(c, xa, xb, r - 0.6f * ts, r - 3.9f * ts, d, maroon)
                    sideFace(c, xa + 0.5f, xb - 0.5f, r - 2.35f * ts, r - 3.35f * ts, d - 0.01f, if (night) 0xFF3A2A1C.toInt() else 0xFFE8DCC0.toInt(), 0.1f, 0.15f)
                    var wx = xa + 1.7f
                    while (wx < xb - 1.9f) {
                        fill(glass); quad(c, wx, r - 2.45f * ts, d - 0.02f, wx + 1.4f, r - 2.45f * ts, d - 0.02f, wx + 1.4f, r - 3.25f * ts, d - 0.02f, wx, r - 3.25f * ts, d - 0.02f)
                        if (!night) { fill(0x40FFFFFF); quad(c, wx + 0.2f, r - 3.25f * ts, d - 0.03f, wx + 0.55f, r - 3.25f * ts, d - 0.03f, wx + 0.25f, r - 2.45f * ts, d - 0.03f, wx - 0.1f, r - 2.45f * ts, d - 0.03f) } // a glint
                        wx += 2.2f
                    }
                    for (dx in floatArrayOf(1.1f, lengths[i] - 1.1f)) { fill(withAlpha(Color.BLACK, 90)); quad(c, xa + dx, r - 0.7f * ts, d - 0.02f, xa + dx + 0.08f, r - 0.7f * ts, d - 0.02f, xa + dx + 0.08f, r - 3.8f * ts, d - 0.02f, xa + dx, r - 3.8f * ts, d - 0.02f) } // doors
                    fill(withAlpha(if (night) 0xFF5A4A30.toInt() else 0xFFE0C070.toInt(), 200)); quad(c, xa + 0.4f, r - 1.55f * ts, d - 0.02f, xb - 0.4f, r - 1.55f * ts, d - 0.02f, xb - 0.4f, r - 1.65f * ts, d - 0.02f, xa + 0.4f, r - 1.65f * ts, d - 0.02f) // gold lining
                }
                TENDER -> {
                    val blk = if (night) 0xFF0C0D10.toInt() else 0xFF1C1D20.toInt()
                    if (showEnd) endFace(c, xa, r - 0.6f * ts, r - 3.4f * ts, wd, 0f, scale(blk, 0.7f))
                    sideFace(c, xa, xb, r - 0.6f * ts, r - 3.4f * ts, d, blk, 0.25f, 0.4f)
                    fill(if (night) 0xFF3A1210.toInt() else 0xFF8A2A22.toInt()); quad(c, xa + 0.8f, r - 1.6f * ts, d - 0.02f, xb - 0.8f, r - 1.6f * ts, d - 0.02f, xb - 0.8f, r - 2.9f * ts, d - 0.02f, xa + 0.8f, r - 2.9f * ts, d - 0.02f)
                    fill(blk); quad(c, xa + 1f, r - 1.75f * ts, d - 0.03f, xb - 1f, r - 1.75f * ts, d - 0.03f, xb - 1f, r - 2.75f * ts, d - 0.03f, xa + 1f, r - 2.75f * ts, d - 0.03f) // lined panel
                    fill(if (night) 0xFF050506.toInt() else 0xFF2A2624.toInt()); quad(c, xa + 0.3f, r - 3.4f * ts, -0.9f, xb - 0.3f, r - 3.4f * ts, -0.9f, xb - 0.5f, r - 3.9f * ts, 0f, xa + 0.5f, r - 3.9f * ts, 0f) // coal heaped above
                }
                else -> { // the locomotive
                    val blk = if (night) 0xFF0B0C0F.toInt() else 0xFF17181B.toInt()
                    val xc = xb - 5.5f
                    val yc = r - 2.55f * ts; val rad = 1.05f * ts
                    // Cab: its front plate above the boiler, roof overhang, side with window.
                    endFace(c, xc, yc, r - 4.2f * ts, wd, 0.25f * ts, scale(blk, 0.8f))
                    if (night) { proj(xc, r - 3.6f * ts, -0.75f); val fx0 = ox; val fy0 = oy; fill(0xFFFFA050.toInt()); c.drawCircle(fx0, fy0, 0.3f * ts * foc / oz, p) }
                    else { proj(xc, r - 3.6f * ts, -0.75f); fill(0xFF3A4452.toInt()); c.drawCircle(ox, oy, 0.3f * ts * foc / oz, p) }
                    sideFace(c, xc, xb, r - 1.0f * ts, r - 4.2f * ts, d, blk, 0.22f, 0.4f)
                    fill(if (night) 0xF2FF963C.toInt() else 0xFF323A46.toInt()); quad(c, xc + 1.2f, r - 2.7f * ts, d - 0.02f, xb - 1.4f, r - 2.7f * ts, d - 0.02f, xb - 1.4f, r - 3.7f * ts, d - 0.02f, xc + 1.2f, r - 3.7f * ts, d - 0.02f)
                    fill(if (night) 0xFF050506.toInt() else 0xFF0E0F11.toInt()); quad(c, xc - 0.5f, r - 4.2f * ts, d - 0.3f, xb + 0.4f, r - 4.2f * ts, d - 0.3f, xb + 0.4f, r - 4.5f * ts, -0.6f, xc - 0.5f, r - 4.5f * ts, -0.6f)
                    // Boiler: a black cylinder lit along its top, with bright boiler bands.
                    cylinderX(c, xa + 0.4f, xc, yc, rad, blk)
                    for (bx in floatArrayOf(xa + 1.9f, xa + 5.2f, xa + 9.0f, xa + 12.4f)) {
                        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.08f * ts * foc / oz); p.color = if (night) 0xFF30343C.toInt() else 0xFF7A808A.toInt()
                        proj(bx, yc - rad, 0f); val x0 = ox; val y0 = oy; proj(bx, yc + rad * 0.7f, d); c.drawLine(x0, y0, ox, oy, p)
                        p.style = Paint.Style.FILL
                    }
                    p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 0.06f * ts * foc / oz); p.color = if (night) 0xFF40444C.toInt() else 0xFFB8BCC4.toInt()
                    proj(xa + 1.2f, yc - rad * 0.3f, d); val hx0 = ox; val hy0 = oy; proj(xc, yc - rad * 0.3f, d); c.drawLine(hx0, hy0, ox, oy, p) // handrail
                    p.style = Paint.Style.FILL
                    // Dome, safety valves and the chimney.
                    stack(c, xa + 9.5f, yc - rad * 0.8f, yc - rad - 0.35f * ts, 0.9f, blk)
                    proj(xa + 9.5f, yc - rad - 0.35f * ts, 0f); val dmx = ox; val dmy = oy
                    proj(xa + 8.6f, yc - rad - 0.35f * ts, 0f); val dmr = abs(dmx - ox)
                    p.color = Color.BLACK; p.shader = LinearGradient(dmx - dmr, 0f, dmx + dmr, 0f, intArrayOf(mix(blk, Color.BLACK, 0.4f), mix(blk, Color.WHITE, 0.35f), mix(blk, Color.BLACK, 0.5f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                    c.drawOval(dmx - dmr, dmy - dmr * 0.6f, dmx + dmr, dmy + dmr * 0.6f, p); p.shader = null
                    stack(c, xa + 12.6f, yc - rad * 0.9f, yc - rad - 0.45f * ts, 0.18f, if (night) 0xFF3A3020.toInt() else 0xFFB89848.toInt()) // brass safety valve
                    stack(c, xa + 2.7f, yc - rad * 0.8f, yc - rad - 1.15f * ts, 0.5f, blk)
                    stack(c, xa + 2.7f, yc - rad - 1.0f * ts, yc - rad - 1.2f * ts, 0.62f, blk) // the chimney's lip
                    // Running board with its red valance.
                    sideFace(c, xa, xc, r - 1.2f * ts, r - 1.45f * ts, d - 0.05f, if (night) 0xFF140808.toInt() else 0xFF9E2620.toInt(), 0.15f, 0.3f)
                    // Smokebox front: a dark disc with its rim, door and dart handle.
                    val fxp = xa + 0.4f
                    proj(fxp, yc, 0f); val fx = ox; val fy = oy
                    proj(fxp, yc, -rad); val l = ox; proj(fxp, yc, rad); val rx = max(abs(ox - l) / 2f, 1f)
                    proj(fxp, yc - rad, 0f); val ty = oy; proj(fxp, yc + rad, 0f); val ry = abs(oy - ty) / 2f
                    if (showEnd) {
                        fill(if (night) 0xFF0E0F12.toInt() else 0xFF26282D.toInt()); c.drawOval(fx - rx, fy - ry, fx + rx, fy + ry, p)
                        fill(if (night) 0xFF07080A.toInt() else 0xFF101114.toInt()); c.drawOval(fx - rx * 0.78f, fy - ry * 0.78f, fx + rx * 0.78f, fy + ry * 0.78f, p)
                        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, ry * 0.06f); p.color = if (night) 0xFF30343A.toInt() else 0xFF8A9098.toInt()
                        c.drawOval(fx - rx * 0.78f, fy - ry * 0.78f, fx + rx * 0.78f, fy + ry * 0.78f, p)
                        c.drawLine(fx - rx * 0.25f, fy, fx + rx * 0.25f, fy, p); c.drawLine(fx, fy - ry * 0.25f, fx, fy + ry * 0.25f, p)
                        p.style = Paint.Style.FILL
                        fill(0xFFB02A22.toInt()); c.drawRect(fx - rx * 0.3f, fy + ry * 0.4f, fx + rx * 0.3f, fy + ry * 0.58f, p) // number plate
                        endFace(c, xa, r - 0.6f * ts, r - 1.4f * ts, wd, 0f, if (night) 0xFF2A0A08.toInt() else 0xFFB4281E.toInt()) // buffer beam
                    }
                    // Headlamp on top of the smokebox.
                    proj(fxp, yc - rad - 0.25f * ts, -0.2f)
                    if (night || twilight > 0.5f) glow(c, ox, oy, rad * foc / oz * 2.6f, 0xFFF0C8, 0.9f)
                    fill(0xFFFFF4D8.toInt()); c.drawCircle(ox, oy, max(1.5f * k, 0.22f * ts * foc / oz), p)
                }
            }
        }
    }

    private fun drawTrain(c: Canvas) {
        c.save()
        // Everything of the train lies above the line of the near parapet's top.
        proj(-120f, deckT - 1.1f, -6f); val ax = ox; val ay = oy; proj(250f, deckT - 1.1f, -6f); val m = (oy - ay) / (ox - ax)
        parapet.reset(); parapet.moveTo(-50f, ay + m * (-50f - ax)); parapet.lineTo(w + 50f, ay + m * (w + 50f - ax)); parapet.lineTo(w + 50f, -h); parapet.lineTo(-50f, -h); parapet.close()
        c.clipPath(parapet)
        if (!trainX.isNaN()) drawVehicles(c)
        drawPuffs(c)
        c.restore()
    }

    // ---- Frame -------------------------------------------------------------------------------------

    override fun update(dt: Float) {
        time += dt
        bay.night = night; bay.twilight = twilight; bay.daylightVisitors = daytime
        bay.update(dt)
        updateTrain(dt)
        updatePuffs(dt)
        for (wk in walkers) { wk.X += wk.v * dt; if (wk.X < -125f || wk.X > -6f) wk.v = -wk.v }
        if (jumpT >= 0f) { jumpT += dt; if (jumpT > 13f) jumpT = -1f }
        else if (daytime && !grey) { nextJump -= dt; if (nextJump <= 0f) { jumpT = 0f; nextJump = 120f + rnd.nextFloat() * 180f } }
    }

    /** 2 = something moves quickly (train, jump, jetski, dolphins, a breach); 0 = only the bay drifting (the calm rate). */
    val motion: Int get() {
        return if (!trainX.isNaN() || jumpT >= 0f || bay.busy || puffs.isNotEmpty()) 2 else 0
    }

    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        if (sea == null || key() != bakedKey) bake()
        canvas.drawBitmap(sea!!, 0f, seaTop, null)
        canvas.save(); canvas.translate(0f, dy)
        drawSurf(canvas)
        bay.seaColour = pal.seaMid; bay.hazeColour = pal.haze
        bay.draw(canvas, ::glow)
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
