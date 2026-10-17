package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** An umbrella acacia, in units of its height (base at the origin). */
class Acacia(r: Mulberry32) {
    class Blob(val x: Float, val y: Float, val rx: Float, val ry: Float)
    class Arm(val to: Float, val curve: Float)

    val blobs = ArrayList<Blob>()
    val lean: Float
    val split: Float
    val arms = ArrayList<Arm>()

    init {
        val n = 14
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val x = (u - 0.5f) + (r.next() - 0.5f) * 0.08f
            val y = -(r.next() * 0.08f) - 0.04f * sin(u * PI.toFloat())
            val rx = 0.09f + r.next() * 0.07f
            val ry = 0.045f + r.next() * 0.035f
            blobs += Blob(x, y, rx, ry)
        }
        for (i in 0 until 6) {
            val x = (r.next() - 0.5f) * 0.7f
            val y = -0.08f - r.next() * 0.05f
            val rx = 0.08f + r.next() * 0.06f
            val ry = 0.04f + r.next() * 0.03f
            blobs += Blob(x, y, rx, ry)
        }
        lean = (r.next() - 0.5f) * 0.25f
        split = 0.45f + r.next() * 0.15f
        val na = 3 + floor(r.next() * 2).toInt()
        for (i in 0 until na) {
            val to = (i / (na - 1f) - 0.5f) * 0.75f + (r.next() - 0.5f) * 0.1f
            arms += Arm(to, (r.next() - 0.5f) * 0.15f)
        }
    }

    private val path = Path()

    fun draw(c: Canvas, x: Float, y: Float, size: Float, p: Paint, trunk: Int, canopy: Int, simple: Boolean = false) {
        c.save()
        c.translate(x, y)
        c.scale(size, size)
        val tx = lean * 0.3f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = trunk
        p.strokeWidth = 0.05f
        path.reset(); path.moveTo(0f, 0f); path.quadTo(lean * 0.2f, -split * 0.5f, tx, -split)
        c.drawPath(path, p)
        p.strokeWidth = 0.025f
        for (a in arms) {
            path.reset(); path.moveTo(tx, -split)
            path.quadTo(tx + a.to * 0.4f + a.curve, -split - 0.2f, tx + a.to, -0.88f)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        val cy = -0.92f
        if (!simple) {
            p.color = scale(canopy, 0.62f)
            for (b in blobs) c.drawOval(tx + b.x - b.rx, cy + b.y + 0.02f - b.ry, tx + b.x + b.rx, cy + b.y + 0.02f + b.ry, p)
        }
        p.color = canopy
        for (b in blobs) c.drawOval(tx + b.x - b.rx * 0.92f, cy + b.y - b.ry * 0.8f, tx + b.x + b.rx * 0.92f, cy + b.y + b.ry * 0.8f, p)
        if (!simple) {
            p.color = withAlpha(mix(canopy, 0xFFC8C890.toInt(), 0.3f), 150)
            for (b in blobs) if (b.y < -0.05f) c.drawOval(tx + b.x - b.rx * 0.6f, cy + b.y - b.ry * 0.85f, tx + b.x + b.rx * 0.5f, cy + b.y - b.ry * 0.2f, p)
        }
        c.restore()
    }
}

/** Where everything sits in the savanna; shared by the land and the animals. */
class SavannaLayout(val w: Float, val h: Float) {
    val hz = h * SavannaDesign.HORIZON
    val gH = h - hz
    /** The designers' pixel scale. */
    val k = w / SavannaDesign.PREVIEW_WIDTH

    class Far(val x: Float, val s: Float, val kind: Boolean)
    class Patch(val x: Float, val y: Float, val w: Float, val h: Float)

    val hills: FloatArray
    val kx: Float
    val far = ArrayList<Far>()
    val patches = ArrayList<Patch>()
    val bladeX = FloatArray(BLADES)
    val bladeY = FloatArray(BLADES)
    val bladeH = FloatArray(BLADES)
    val bladePh = FloatArray(BLADES)
    val bladeC = FloatArray(BLADES)
    val bigX: Float
    val smallX: Float
    val trees: Array<Acacia>

    init {
        val r = Mulberry32(SavannaDesign.LAYOUT_SEED)
        hills = FloatArray(41) { r.next() }
        kx = 0.62f + r.next() * 0.3f
        for (i in 0 until 60) {
            val x = r.next(); val s = 0.4f + r.next() * 0.8f; val kind = r.next() < 0.35f
            far += Far(x, s, kind)
        }
        for (i in 0 until 14) {
            val x = r.next(); val y = r.next(); val pw = 0.15f + r.next() * 0.35f; val ph = 0.01f + r.next() * 0.03f
            patches += Patch(x, y, pw, ph)
        }
        for (i in 0 until BLADES) {
            bladeY[i] = r.next().pow(0.6f)
            bladeX[i] = r.next()
            bladeH[i] = 0.5f + r.next()
            bladePh[i] = r.next() * 6.28f
            bladeC[i] = r.next()
        }
        bigX = 0.55f + r.next() * 0.25f
        smallX = 0.08f + r.next() * 0.2f
        trees = arrayOf(Acacia(r), Acacia(r))
    }

    val bigSize = w * 0.62f * SavannaDesign.BIG_TREE_SIZE
    val smallSize = w * 0.32f * SavannaDesign.BIG_TREE_SIZE
    val bigY = hz + gH * BIG_DEPTH
    val smallY = hz + gH * SMALL_DEPTH
    /** Where vulture [i] (0..4) lands, across the big tree's crown, relative to its trunk (tree units). */
    fun perchOffset(i: Int) = PERCH[i.coerceIn(0, PERCH.size - 1)]

    /** Screen position of perch [i]: on top of the canopy blob under that spot. */
    fun perch(i: Int): Pair<Float, Float> {
        val tree = trees[1]
        val tx = tree.lean * 0.3f
        val px = tx + perchOffset(i)
        var top = -0.92f
        for (b in tree.blobs) {
            val rx = b.rx * 0.92f
            val dx = (px - (tx + b.x)) / rx
            if (dx * dx >= 1f) continue
            val y = -0.92f + b.y - b.ry * 0.8f * kotlin.math.sqrt(1f - dx * dx)
            if (y < top) top = y
        }
        return Pair(bigX * w + px * bigSize, bigY + top * bigSize)
    }

    companion object {
        const val BLADES = 900
        const val BIG_DEPTH = 0.45f
        const val SMALL_DEPTH = 0.22f
        private val PERCH = floatArrayOf(-.34f, -.12f, .08f, .27f, .42f)
    }
}

/**
 * The Savanna: golden grassland under the shared sky, hazy hills, umbrella acacias and the
 * herd (see [SavannaWildlife], which lives on in the renderer across weather changes).
 */
class SavannaLandscape(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
    private val wildlife: SavannaWildlife?,
) : Landscape {
    private val wf = width.toFloat()
    private val hf = height.toFloat()
    private val L = SavannaLayout(wf, hf)
    private val hz = L.hz
    private val gH = L.gH
    private val k = L.k

    override val tableTopY get() = hz - hf * 0.12f
    override val mountainUnit get() = min(wf, hf * 0.8f) * 1.35f

    private val haze = mix(cfg.skyBottom, 0xFFE8D8C0.toInt(), 0.3f)
    private var twilight = 0f
    private var silh = if (cfg.night) 0.9f else 0f
    private var bakedSilh = -1f
    private var land: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var time = 0f

    // The season sets the grass colour: green in winter and spring, golden-brown in summer and autumn.
    private val greenness = Season.greenness(SkyTime.now(), cfg.latitude)
    private val grass = SavannaDesign.GRASS_DRY + (SavannaDesign.GRASS_GREEN - SavannaDesign.GRASS_DRY) * greenness

    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    override fun setTwilight(amount: Float) {
        twilight = amount
        silh = (twilight * 0.8f + (if (cfg.night) 0.9f else 0f)).coerceIn(0f, 1f)
    }

    override fun update(dt: Float) {
        time += dt
    }

    // ---- Static land, baked to a bitmap (rebaked when the light changes) ----------------

    private fun bake() {
        val bmp = land ?: Bitmap.createBitmap(wf.toInt(), hf.toInt(), Bitmap.Config.ARGB_8888).also { land = it }
        bmp.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bmp)
        val path = Path()
        val gg = grass

        // Far hills.
        val back = mix(mix(rgb(92, 110, 140), haze, 0.45f), rgb(60, 50, 70), silh * 0.6f)
        path.reset(); path.moveTo(0f, hz + 1)
        for (i in 0..40) {
            val u = i / 40f
            val ht = (0.012f + 0.01f * L.hills[i] + 0.035f * sin(3.1f * u + 1f) * sin(1.3f * u) +
                0.05f * exp(-((u - L.kx) / 0.09f).pow(2))) * hf * SavannaDesign.HILLS
            path.lineTo(u * wf, hz - ht.coerceAtLeast(0f))
        }
        path.lineTo(wf, hz + 1); path.close()
        p.style = Paint.Style.FILL; p.color = back
        c.drawPath(path, p)
        val near = mix(mix(rgb(120, 118, 96), haze, 0.4f), rgb(55, 45, 50), silh * 0.6f)
        path.reset(); path.moveTo(0f, hz + 1)
        for (i in 0..60) {
            val u = i / 60f
            val ht = (0.006f + 0.012f * sin(7f * u + 2f) * sin(2.1f * u + 0.4f)) * hf
            path.lineTo(u * wf, hz - ht.coerceAtLeast(0f))
        }
        path.lineTo(wf, hz + 1); path.close()
        p.color = near
        c.drawPath(path, p)

        // Grass plain.
        val gG = mix(mix(mix(rgb(217, 184, 96), rgb(150, 170, 80), gg), haze, 0.25f), rgb(90, 60, 60), silh * 0.5f)
        val gN = mix(mix(rgb(200, 150, 46), rgb(110, 140, 50), gg), rgb(60, 40, 30), silh * 0.55f)
        p.shader = LinearGradient(0f, hz, 0f, hf, gG, gN, Shader.TileMode.CLAMP)
        c.drawRect(0f, hz, wf, hf, p)
        p.shader = null

        // Bush line on the horizon.
        p.color = mix(mix(rgb(78, 84, 52), haze, 0.4f), rgb(40, 32, 38), silh * 0.6f)
        for (i in 0 until 120) {
            val x = (i * 37.13f % 1f) * wf
            val bw = (3f + (i * 13.7f % 6f)) * k
            val bh = (1.5f + (i * 7.3f % 3f)) * k
            c.drawOval(x - bw, hz - bh, x + bw, hz + bh * 0.5f, p)
        }

        // Distant trees.
        val farTrunk = mix(mix(rgb(70, 60, 50), haze, 0.4f), rgb(40, 32, 38), silh * 0.6f)
        val farCanopy = mix(mix(rgb(70, 86, 46), haze, 0.4f), rgb(40, 32, 38), silh * 0.6f)
        for (i in 0 until min(SavannaDesign.HORIZON_TREES, L.far.size)) {
            val f = L.far[i]
            L.trees[if (f.kind) 1 else 0].draw(c, f.x * wf, hz + 2 * k + f.s * 2 * k, 10f * f.s * k, p, farTrunk, farCanopy, simple = true)
        }

        // Green patches.
        p.color = withAlpha(mix(rgb(124, 146, 64), rgb(40, 40, 30), silh * 0.6f), (255 * SavannaDesign.GREEN_PATCHES * (0.4f + 0.8f * greenness) * 0.7f).toInt().coerceIn(0, 255))
        for (pt in L.patches) {
            val dd = pt.y.pow(1.4f)
            val y = hz + dd * gH
            val sc = 0.3f + dd * 1.4f
            c.drawOval(pt.x * wf - pt.w * wf * sc / 2, y - pt.h * gH * sc, pt.x * wf + pt.w * wf * sc / 2, y + pt.h * gH * sc, p)
        }

        // Red earth in the foreground.
        val red = mix(rgb(150, 72, 40), rgb(50, 30, 26), silh * 0.6f)
        p.shader = LinearGradient(0f, hf * 0.86f, 0f, hf, withAlpha(red, 0), withAlpha(red, (255 * SavannaDesign.RED_EARTH).toInt()), Shader.TileMode.CLAMP)
        c.drawRect(0f, hf * 0.86f, wf, hf, p)
        p.shader = null
        bakedSilh = silh
    }

    // ---- Grass blades, bucketed by colour and width for a few drawLines calls -------------

    private val bucketPts = Array(BUCKETS) { FloatArray(LayoutBlades * 4) }
    private val bucketN = IntArray(BUCKETS)
    private val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private fun drawBlades(c: Canvas) {
        java.util.Arrays.fill(bucketN, 0)
        val sway = SavannaDesign.GRASS_SWAY * (0.6f + cfg.wind)
        for (i in 0 until SavannaLayout.BLADES) {
            val d = L.bladeY[i]
            if (d < 0.15f) continue
            val x = L.bladeX[i] * wf
            val y = hz + d * gH
            val len = (2f + 12f * d) * L.bladeH[i] * SavannaDesign.GRASS_DETAIL * k
            val s = (sin(0.7f * time + L.bladePh[i] + 3f * L.bladeX[i]) * 0.8f + 0.2f) * len * 0.2f * sway
            val b = min(COLOURS - 1, (L.bladeC[i] * COLOURS).toInt()) * WIDTHS + min(WIDTHS - 1, ((d - 0.15f) / 0.85f * WIDTHS).toInt())
            val n = bucketN[b]
            val a = bucketPts[b]
            a[n] = x; a[n + 1] = y; a[n + 2] = x + s; a[n + 3] = y - len
            bucketN[b] = n + 4
        }
        val gg = grass
        val gN = mix(mix(rgb(200, 150, 46), rgb(110, 140, 50), gg), rgb(60, 40, 30), silh * 0.55f)
        for (ci in 0 until COLOURS) for (wi in 0 until WIDTHS) {
            val b = ci * WIDTHS + wi
            if (bucketN[b] == 0) continue
            val cc = (ci + 0.5f) / COLOURS
            bladePaint.color = mix(mix(gN, rgb(240, 200, 110), 0.6f * cc), rgb(50, 36, 30), silh * 0.6f)
            val d = 0.15f + (wi + 0.5f) / WIDTHS * 0.85f
            bladePaint.strokeWidth = (0.6f + d) * k
            c.drawLines(bucketPts[b], 0, bucketN[b], bladePaint)
        }
    }

    // ---- Frame ------------------------------------------------------------------------------

    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        if (abs(silh - bakedSilh) > 0.04f || land == null) bake()
        canvas.drawBitmap(land!!, 0f, 0f, bmpPaint)

        val trunk = mix(rgb(62, 48, 38), rgb(24, 20, 24), silh * 0.8f)
        val canopy = mix(rgb(72, 92, 42), rgb(26, 26, 26), silh * 0.75f)
        wildlife?.drawBand(canvas, 0f, SavannaLayout.SMALL_DEPTH, silh, haze)
        L.trees[0].draw(canvas, L.smallX * wf, L.smallY, L.smallSize, p, trunk, canopy)
        wildlife?.drawBand(canvas, SavannaLayout.SMALL_DEPTH, 2f, silh, haze)
        L.trees[1].draw(canvas, L.bigX * wf, L.bigY, L.bigSize, p, trunk, canopy)
        wildlife?.drawPerched(canvas, silh)
        drawBlades(canvas)
        wildlife?.drawFlying(canvas, silh)
    }

    private companion object {
        const val COLOURS = 4
        const val WIDTHS = 3
        const val BUCKETS = COLOURS * WIDTHS
        const val LayoutBlades = SavannaLayout.BLADES
    }
}
