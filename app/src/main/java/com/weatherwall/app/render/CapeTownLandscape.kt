package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cape Town seen across Table Bay from Bloubergstrand: Devil's Peak, Table Mountain,
 * Lion's Head and Signal Hill; the city and Cape Town Stadium on the far shore;
 * the bay in between and the beach in the foreground.
 *
 * Colours are derived from the scene's sky so the landscape always sits in the same light.
 */
class CapeTownLandscape(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
    private val dp: Float,
) : Landscape {
    /** Where the sun or moon sits horizontally, and how bright its reflection on the water is. */
    private var glintX = 0f
    private var glintStrength = 0f

    override fun setGlint(x: Float, strength: Float) {
        glintX = x
        glintStrength = strength
    }

    private val wf = width.toFloat()
    private val hf = height.toFloat()
    private var time = 0f

    // Fixed seed: the skyline and mountain detail stay identical between scenes,
    // so a weather crossfade never shuffles buildings around.
    private val rnd = Random(7)
    private val anim = Random(System.nanoTime())

    private val shoreY = hf * 0.72f
    private val beachY = hf * 0.935f
    private val unit = min(wf, hf * 0.8f) * 1.35f * MountainDesign.HEIGHT_SCALE // vertical scale of the mountains

    private fun mx(f: Float) = f * wf
    private fun my(f: Float) = shoreY - f * unit

    // ---- Palette -----------------------------------------------------------
    private val night = cfg.night
    private val haze = cfg.skyBottom
    private val mountainTop = mix(haze, if (night) 0xFF0B1019.toInt() else 0xFF4A5864.toInt(), if (night) 0.72f else 0.6f)
    private val mountainBase = mix(haze, if (night) 0xFF06090F.toInt() else 0xFF2F3B3A.toInt(), 0.8f)
    private val slopeColor = mix(haze, if (night) 0xFF070A10.toInt() else 0xFF33453A.toInt(), 0.8f)
    private val cityColor = if (night) mix(haze, 0xFF080B12.toInt(), 0.85f) else mix(haze, 0xFFC4CACF.toInt(), 0.6f)
    private val seaTop = mix(haze, if (night) 0xFF07111D.toInt() else 0xFF2A6C96.toInt(), 0.55f)
    private val seaBottom = mix(haze, if (night) 0xFF02060B.toInt() else 0xFF0F3A5A.toInt(), 0.75f)
    private val sandColor = mix(haze, if (night) 0xFF1E1E22.toInt() else 0xFFE2D1A8.toInt(), 0.75f)
    private val lightColor = 0xFFFFD98A.toInt()

    // ---- Mountains ---------------------------------------------------------
    // Two layers, shaped in the mountain designer (see MountainDesign): the back ridge with
    // Table Mountain, Lion's Head and Signal Hill, and Devil's Peak standing in front of the table.
    private val ridge = MountainDesign.expand(MountainDesign.backRidge)
    private val frontRidge = MountainDesign.expand(MountainDesign.devilsPeak)
    private val mountainPath = smoothPath(ridge)
    private val frontPath = smoothPath(frontRidge)
    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, my(0.23f), 0f, shoreY, mountainTop, mountainBase, Shader.TileMode.CLAMP)
    }
    private val frontTop = scale(mountainTop, MountainDesign.DEVILS_PEAK_DEPTH)
    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, my(0.23f), 0f, shoreY,
            frontTop, scale(mountainBase, MountainDesign.DEVILS_PEAK_DEPTH), Shader.TileMode.CLAMP,
        )
    }

    private val slopes = floatArrayOf(
        -0.02f, 0.02f, 0.06f, 0.06f, 0.16f, 0.085f, 0.30f, 0.10f, 0.45f, 0.095f,
        0.60f, 0.10f, 0.69f, 0.085f, 0.76f, 0.05f, 0.85f, 0.03f, 1.02f, 0.02f,
    )
    private val slopePath = smoothPath(slopes)
    private val slopePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, my(0.1f), 0f, shoreY, scale(slopeColor, 1.08f), slopeColor, Shader.TileMode.CLAMP)
    }
    private val ridgeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
        color = withAlpha(scale(mountainTop, if (night) 1.6f else 1.25f), 110)
    }
    private val ridgeLine = smoothPath(ridge, close = false)
    // Where Devil's Peak overlaps the table, its edge catches the light.
    private val frontLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * dp
        color = withAlpha(scale(frontTop, if (night) 1.7f else 1.3f), 140)
    }
    private val frontLine = smoothPath(frontRidge, close = false)

    /** Screen y of the top of the table, and the mountains' vertical scale (for the storm ceiling). */
    override val tableTopY get() = my(tableTop)
    override val mountainUnit get() = unit

    // Landmarks, found from the ridges so they follow whatever shape was designed.
    private val tableTop: Float
    private val tableStart: Float
    private val tableEnd: Float
    private val lionsX: Float
    private val lionsH: Float
    private val nekX: Float
    private val nekH: Float
    private val devilsX: Float
    private val devilsH: Float

    init {
        val b = MountainDesign.backRidge
        val n = b.size / 3
        fun x(i: Int) = b[i * 3]
        fun h(i: Int) = b[i * 3 + 1]
        tableTop = (0 until n).maxOf { h(it) }
        val table = (0 until n).filter { h(it) >= tableTop * 0.97f }
        tableStart = table.minOf { x(it) }
        tableEnd = table.maxOf { x(it) }
        val lions = (0 until n).filter { x(it) > tableEnd + 0.05f }.maxByOrNull { h(it) }
        lionsX = lions?.let { x(it) } ?: 0.87f
        lionsH = lions?.let { h(it) } ?: 0.16f
        val nek = (0 until n).filter { x(it) > tableEnd && x(it) < lionsX }.minByOrNull { h(it) }
        nekX = nek?.let { x(it) } ?: 0.775f
        nekH = nek?.let { h(it) } ?: 0.076f
        val f = MountainDesign.devilsPeak
        val peak = (0 until f.size / 3).maxBy { f[it * 3 + 1] }
        devilsX = f[peak * 3]
        devilsH = f[peak * 3 + 1]
    }

    // Rock faces: the table's front cliffs are a row of buttresses, each with a sunlit and a
    // shaded side (the gaps between them read as gorges), plus the flanks of the peaks.
    // Which side is lit follows the sun: from the left in the morning, the right in the afternoon.
    private class Facet(val path: Path, val facesRight: Boolean, val weight: Float)

    private val facets = ArrayList<Facet>()
    private val frontFacets = ArrayList<Facet>()
    private var lightDir = 0f
    private var lightIntensity = 0f
    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scale(mountainTop, if (night) 1.4f else 1.2f) }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scale(mountainTop, 0.74f) }
    private val frontLitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scale(frontTop, if (night) 1.4f else 1.2f) }
    private val frontShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scale(frontTop, 0.74f) }

    private fun polygon(vararg pts: Float) = Path().apply {
        moveTo(mx(pts[0]), my(pts[1]))
        for (i in 2 until pts.size step 2) lineTo(mx(pts[i]), my(pts[i + 1]))
        close()
    }

    init {
        val d = MountainDesign
        // Buttresses along the front of the table (same generator and order as the designer).
        val r = Mulberry32(d.ROCK_PATTERN)
        var x = tableStart + 0.002f
        val end = tableEnd - 0.008f
        while (x < end - 0.012f) {
            val bw = min(d.BUTTRESS_WIDTH + r.next() * d.WIDTH_VARIATION, end - x)
            val top = tableTop - 0.004f
            val bottom = 0.06f
            val ridgeTop = x + bw * (0.3f + r.next() * 0.4f)
            val ridgeBottom = ridgeTop + (r.next() - 0.5f) * bw * 1.1f
            val ridgeMid = (ridgeTop + ridgeBottom) / 2f + (r.next() - 0.5f) * bw * 0.6f
            val weight = 0.6f + r.next() * 0.4f
            val midH = top * 0.69f + r.next() * 0.03f
            facets += Facet(polygon(x, top, ridgeTop, top, ridgeMid, midH, ridgeBottom, bottom, x + bw * 0.06f, bottom), false, weight)
            facets += Facet(polygon(ridgeTop, top, x + bw, top, x + bw * 0.94f, bottom, ridgeBottom, bottom, ridgeMid, midH), true, weight)
            x += bw + d.GORGE_WIDTH + r.next() * d.GORGE_WIDTH * 2.5f
        }
        // The table's western end and the flanks of Lion's Head; edges follow natural ridgelines
        // and anything outside the silhouette is clipped away.
        facets += Facet(polygon(tableEnd - 0.012f, 0.3f, tableEnd - 0.008f, tableTop + 0.001f, tableEnd + 0.014f, -0.02f,
            nekX + 0.025f, -0.02f, nekX, nekH, nekX, 0.3f), true, 0.7f)
        facets += Facet(polygon(lionsX, lionsH, lionsX, 0.3f, nekX, 0.3f, nekX, nekH, nekX + 0.025f, -0.02f,
            lionsX - 0.017f, -0.02f), false, 0.6f)
        facets += Facet(polygon(lionsX, lionsH, lionsX - 0.017f, -0.02f, 1.05f, -0.02f, 1.05f, 0.3f, lionsX, 0.3f), true, 0.6f)

        // Devil's Peak: its two flanks, plus gullies down the shoulder that crosses the table.
        val rf = Mulberry32(d.ROCK_PATTERN + 101)
        frontFacets += Facet(polygon(devilsX, devilsH, devilsX + 0.03f, -0.02f, -0.05f, -0.02f, -0.05f, 0.35f, devilsX, 0.35f), false, 0.75f)
        frontFacets += Facet(polygon(devilsX, devilsH, devilsX, 0.35f, 0.7f, 0.35f, 0.7f, -0.02f, devilsX + 0.03f, -0.02f), true, 0.75f)
        var gx = devilsX + 0.035f
        while (gx < 0.4f) {
            val gap = 0.012f + rf.next() * 0.02f
            val h0 = 0.2f - (gx - devilsX) * 0.45f
            if (h0 < 0.03f) break
            frontFacets += Facet(polygon(gx, h0 + 0.02f, gx + 0.006f, h0 + 0.02f, gx + 0.03f + rf.next() * 0.02f, 0f, gx + 0.02f, 0f),
                false, 0.5f + rf.next() * 0.3f)
            gx += gap + 0.012f
        }
    }

    /**
     * @param direction -1 (light from the far left) .. 1 (far right); 0 is light from behind the viewer.
     * @param intensity 0..1 contrast of the lit and shaded faces.
     */
    // Alpenglow: the mountain turns pink around sunrise and sunset, and the bay warms with it.
    private var twilight = 0f
    private val alpenglowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, my(0.23f), 0f, my(0.03f), 0xFFE8737A.toInt(), 0x00E8737A, Shader.TileMode.CLAMP)
    }
    private val warmSeaPaint = Paint().apply {
        shader = LinearGradient(0f, shoreY, 0f, beachY, 0xFFF4A08C.toInt(), 0x00F4A08C, Shader.TileMode.CLAMP)
    }

    override fun setTwilight(amount: Float) {
        twilight = amount.coerceIn(0f, 1f)
    }

    override fun setLight(direction: Float, intensity: Float) {
        lightDir = direction.coerceIn(-1f, 1f)
        lightIntensity = intensity.coerceIn(0f, 1f)
    }

    // ---- Tablecloth: cloud pouring over the table like a waterfall ---------------------
    // A billowing blanket on the plateau, and streams of soft cloud that spill over the edge,
    // speed up as they fall, spread, and evaporate partway down the cliffs. Shaped in the
    // tablecloth preview; values in TableclothDesign. Sizes there are in preview pixels.
    private val clothScale = wf / 680f
    private val clothDay = 0xFFF6F8FB.toInt()
    private val clothDusk = 0xFFFAD6CE.toInt()
    private val clothNight = 0xFF8E97A8.toInt()
    // In rain the cloth is a heavier, darker grey to sit under the storm sky.
    private val clothRainShade = if (cfg.rain > 0) 0.72f else 1f
    private val clothRainTint = 0xFFB8C0CA.toInt()
    private var clothColor = rainCloth(if (night) clothNight else clothDay)

    private fun rainCloth(c: Int) = if (clothRainShade < 1f) scale(mix(c, clothRainTint, 0.5f), clothRainShade) else c
    private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blanketPaint = Paint()

    private class Stream(val x: Float, val fall: Float, val speed: Float, val seed: Float, val spread: Float)
    private val streams: List<Stream> = run {
        val r = Mulberry32(23)
        val n = TableclothDesign.STREAMS
        List(n) { i ->
            val u = (i + 0.5f) / n
            Stream(
                x = tableStart - 0.006f + (tableEnd - tableStart + 0.02f) * u + (r.next() - 0.5f) * 0.6f * (tableEnd - tableStart) / n,
                fall = 0.6f + 0.8f * r.next(),
                speed = 0.8f + 0.4f * r.next(),
                seed = r.next() * 6.28f,
                spread = r.next() - 0.5f,
            )
        }
    }
    private val puffX: FloatArray
    private val puffR: FloatArray

    init {
        val r = Mulberry32(5)
        val xs = ArrayList<Float>()
        val rs = ArrayList<Float>()
        var x = tableStart - 0.012f
        while (x < tableEnd + 0.016f) {
            xs += x
            rs += 7f + r.next() * 8f
            x += 0.011f + r.next() * 0.012f
        }
        puffX = xs.toFloatArray()
        puffR = rs.toFloatArray()
        setClothColor(clothColor)
    }

    private fun setClothColor(c: Int) {
        clothColor = c
        puffPaint.shader = RadialGradient(
            0f, 0f, 100f,
            intArrayOf(c, withAlpha(c, 140), withAlpha(c, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        val th = TableclothDesign.THICKNESS * clothScale
        val top = my(tableTop)
        blanketPaint.shader = LinearGradient(
            0f, top - 8f * th, 0f, top + 10f * th,
            // Fades in at the top too, so the billowing puffs, not a straight edge, form its outline.
            intArrayOf(withAlpha(c, 0), c, c, withAlpha(c, 0)), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** A soft round puff of cloud; [a] is its centre opacity, 0..1. */
    private fun puff(c: Canvas, x: Float, y: Float, radius: Float, a: Float) {
        puffPaint.alpha = (255 * a).toInt().coerceIn(0, 255)
        c.save()
        c.translate(x, y)
        c.scale(radius / 100f, radius / 100f)
        c.drawCircle(0f, 0f, 100f, puffPaint)
        c.restore()
    }

    // ---- City --------------------------------------------------------------
    // Shaped in the shoreline designer; see ShorelineCity.
    private val city = ShorelineCity(wf, shoreY, unit, night, haze)
    private val beaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() }

    // ---- Cape Town Stadium -------------------------------------------------
    // Seen almost side-on from across the bay: a long, low, rounded bowl clad in a pleated
    // membrane. The pleats crowd together towards the ends as the wall curves away, and a
    // sliver of the pale roof ring shows above. At night the whole facade glows.
    private val stCx = mx(0.865f)
    private val stW = mx(0.2f)
    private val stH = stW * 0.19f
    private val stX0 = stCx - stW / 2f
    private val stX1 = stCx + stW / 2f
    private val stBase = shoreY + 1f
    private val stTop = stBase - stH
    private val facadeRect = RectF(stX0, stTop, stX1, stBase)
    private val facadePath = Path().apply { addRoundRect(facadeRect, stH * 0.8f, stH * 0.5f, Path.Direction.CW) }
    private val roofRect = RectF(stX0 + stW * 0.012f, stTop - stH * 0.14f, stX1 - stW * 0.012f, stTop + stH * 0.14f)
    private val facadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val edge = if (night) 0xFFB8925A.toInt() else mix(0xFFBFC6CD.toInt(), haze, 0.12f)
        val mid = if (night) 0xFFF2DDB0.toInt() else 0xFFEEF1F4.toInt()
        val high = if (night) 0xFFFFF3D9.toInt() else 0xFFFFFFFF.toInt()
        shader = LinearGradient(
            stX0, 0f, stX1, 0f,
            intArrayOf(edge, mid, high, mid, edge), floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    // Top lighter, bottom darker, over the horizontal shading: reads as a rounded wall.
    private val facadeShadePaint = Paint().apply {
        shader = LinearGradient(
            0f, stTop, 0f, stBase,
            intArrayOf(0x40FFFFFF, 0x00000000, if (night) 0x00000000 else 0x26000000),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pleatPts = FloatArray(76 * 4).also { pts ->
        for (i in 0 until 76) {
            val theta = (-0.5 + (i + 0.5) / 76.0) * PI
            val x = stCx + stW / 2f * 0.985f * sin(theta).toFloat()
            floatArrayOf(x, stTop, x, stBase).copyInto(pts, i * 4)
        }
    }
    private val pleatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0x38906A30 else 0x2E3C4650
        strokeWidth = 0.7f * dp
    }
    private val podiumPaint = Paint().apply { color = if (night) 0xFF4A4034.toInt() else scale(cityColor, 0.7f) }
    private val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0xFFE8D9BA.toInt() else mix(0xFFDDE2E6.toInt(), haze, 0.15f)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * dp
        color = if (night) 0xFFFFF6E0.toInt() else 0xFFFAFBFC.toInt()
    }
    private val baseLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE6B0.toInt()
        strokeWidth = 1.6f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val stadiumGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 100f, intArrayOf(0x70FFF0D0, 0x00FFF0D0), null, Shader.TileMode.CLAMP)
    }

    // ---- Sea ---------------------------------------------------------------
    private val seaPaint = Paint().apply {
        shader = LinearGradient(0f, shoreY, 0f, beachY, seaTop, seaBottom, Shader.TileMode.CLAMP)
    }
    private val shoreLinePaint = Paint().apply {
        color = withAlpha(mix(seaTop, Color.WHITE, 0.35f), 130)
        strokeWidth = 1f * dp
    }

    private val waveN = 70
    private val waveT = FloatArray(waveN) { anim.nextFloat().pow(1.5f) } // denser towards the far shore
    private val waveY = FloatArray(waveN) { shoreY + 4f * dp + waveT[it] * (beachY - shoreY - 6f * dp) }
    private val waveX = FloatArray(waveN) { anim.nextFloat() * wf }
    private val waveLen = FloatArray(waveN) { (6f + 50f * waveT[it]) * dp }
    private val waveSpeed = FloatArray(waveN) { (4f + 10f * waveT[it]) * dp * (1f + cfg.wind * 2f) }
    private val wavePhase = FloatArray(waveN) { anim.nextFloat() * TWO_PI }
    private val waveRate = FloatArray(waveN) { 0.5f + anim.nextFloat() * 1.5f }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val waveMaxAlpha = if (night) 45f else 90f

    private val hasBody = cfg.sun || cfg.moon
    private val glintN = if (hasBody) 50 else 0
    private val glintT = FloatArray(glintN) { anim.nextFloat().pow(1.3f) }
    private val glintY = FloatArray(glintN) { shoreY + 3f * dp + glintT[it] * (beachY - shoreY - 5f * dp) }
    private val glintOff = FloatArray(glintN) {
        val g = (anim.nextFloat() + anim.nextFloat() + anim.nextFloat() - 1.5f) // ~gaussian
        g * (8f + 70f * glintT[it]) * dp
    }
    private val glintLen = FloatArray(glintN) { (4f + 26f * glintT[it]) * dp }
    private val glintPhase = FloatArray(glintN) { anim.nextFloat() * TWO_PI }
    private val glintRate = FloatArray(glintN) { 1.5f + anim.nextFloat() * 2.5f }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (cfg.sun) 0xFFFFF4CF.toInt() else 0xFFE3E8FF.toInt()
        strokeWidth = 1.6f * dp
        strokeCap = Paint.Cap.ROUND
    }

    // City lights reflected in the bay at night.
    private val reflCenter: FloatArray
    private val reflHalf: FloatArray
    private val reflPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lightColor
        strokeWidth = 1.4f * dp
        strokeCap = Paint.Cap.ROUND
    }

    init {
        val lit = if (night) city.frontRow.filter { it.h >= 0.012 } else emptyList()
        val n = lit.size + if (night) 1 else 0
        reflCenter = FloatArray(n) { i -> if (i < lit.size) ((lit[i].x + lit[i].w / 2) * wf).toFloat() else mx(0.865f) }
        reflHalf = FloatArray(n) { i -> if (i < lit.size) (lit[i].w * wf / 2).toFloat() else stW * 0.35f }
    }

    // ---- Beach -------------------------------------------------------------
    private val sandPaint = Paint().apply {
        shader = LinearGradient(0f, beachY, 0f, hf, mix(sandColor, seaBottom, 0.2f), sandColor, Shader.TileMode.CLAMP)
    }
    private val wetSandPaint = Paint().apply { color = withAlpha(scale(sandColor, 0.78f), 150) }
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(mix(seaBottom, seaTop, 0.4f), 225) }
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeJoin = Paint.Join.ROUND
        color = if (night) 0x99B8C2CC.toInt() else 0xE6FFFFFF.toInt()
    }
    private val washPath = Path()
    private val foamPath = Path()

    // ========================================================================

    override fun update(dt: Float) {
        time += dt
        for (i in 0 until waveN) {
            waveX[i] += waveSpeed[i] * dt
            if (waveX[i] > wf + waveLen[i]) waveX[i] = -waveLen[i]
        }
    }

    /** @param onWater drawn on the bay in front of the city, behind the beach (e.g. a passing ship). */
    override fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)?) {
        drawMountains(canvas)
        if (cfg.tablecloth) drawTablecloth(canvas)
        drawDevilsPeak(canvas)
        canvas.drawPath(slopePath, slopePaint)
        drawSea(canvas)
        drawCity(canvas)
        drawStadium(canvas)
        onWater?.invoke(canvas)
        drawBeach(canvas)
    }

    private fun drawMountains(canvas: Canvas) {
        canvas.drawPath(mountainPath, mountainPaint)
        drawFacets(canvas, mountainPath, facets, litPaint, shadePaint)
        drawAlpenglow(canvas, mountainPath)
        canvas.drawPath(ridgeLine, ridgeLinePaint)
    }

    private fun drawDevilsPeak(canvas: Canvas) {
        canvas.drawPath(frontPath, frontPaint)
        drawFacets(canvas, frontPath, frontFacets, frontLitPaint, frontShadePaint)
        drawAlpenglow(canvas, frontPath)
        canvas.drawPath(frontLine, frontLinePaint)
    }

    private fun drawFacets(canvas: Canvas, clip: Path, list: List<Facet>, lit: Paint, shade: Paint) {
        if (lightIntensity <= 0.01f) return
        canvas.save()
        canvas.clipPath(clip)
        for (f in list) {
            val facing = if (f.facesRight) lightDir else -lightDir // > 0: turned towards the light
            val paint = if (facing >= 0f) lit else shade
            paint.alpha = (220 * lightIntensity * f.weight * (0.35f + 0.65f * kotlin.math.abs(facing)) * MountainDesign.CONTRAST)
                .toInt().coerceIn(0, 255)
            canvas.drawPath(f.path, paint)
        }
        canvas.restore()
    }

    private fun drawAlpenglow(canvas: Canvas, path: Path) {
        if (twilight <= 0.01f) return
        alpenglowPaint.alpha = (150 * twilight).toInt()
        canvas.drawPath(path, alpenglowPaint)
    }

    private fun drawTablecloth(canvas: Canvas) {
        // Pink at sunrise and sunset, like the rest of the mountain.
        val wanted = rainCloth(if (night) clothNight else mix(clothDay, clothDusk, twilight))
        if (wanted != clothColor) setClothColor(wanted)

        val d = TableclothDesign
        val k = clothScale
        val top = my(tableTop)
        val cliffH = (tableTop - 0.06f) * unit
        val op = d.OPACITY
        val size = 7f * d.PUFF_SIZE * k

        // The cascade, clipped to the mountain so it pours down the cliffs (and behind Devil's Peak).
        canvas.save()
        canvas.clipPath(mountainPath)
        for (s in streams) {
            val fall = d.FALL_LENGTH * s.fall * cliffH
            val rate = time * d.FLOW_SPEED * s.speed * 0.35f / max(0.25f, d.FALL_LENGTH * s.fall)
            for (i in 0 until PUFFS_PER_STREAM) {
                val p = (rate + i / PUFFS_PER_STREAM.toFloat()) % 1f
                val e = p.pow(1.35f)
                val x = mx(s.x) + sin(p * 3f + s.seed + time * 0.5f) * 2.2f * k + s.spread * e * 14f * k
                val y = top + e * fall
                val radius = size * (0.7f + 1.1f * p) * (0.9f + 0.2f * sin(s.seed + i))
                val a = op * (1f - p).pow(1.25f) * min(1f, p / 0.06f)
                if (a > 0.01f) puff(canvas, x, y, radius, a * 0.75f)
            }
        }
        canvas.restore()

        // The blanket lying on the plateau.
        val th = d.THICKNESS * k
        blanketPaint.alpha = (255 * op).toInt()
        canvas.drawRect(mx(tableStart - 0.012f), top - 8f * th, mx(tableEnd + 0.018f), top + 10f * th, blanketPaint)
        for (i in puffX.indices) {
            val radius = puffR[i] * th * (1f + 0.07f * sin(time * 0.5f + puffX[i] * 90f))
            puff(canvas, mx(puffX[i]), top - radius * 0.15f, radius * 1.6f, op)
        }
    }

    private fun drawSea(canvas: Canvas) {
        canvas.drawRect(0f, shoreY, wf, beachY, seaPaint)
        if (twilight > 0.01f) {
            warmSeaPaint.alpha = (110 * twilight).toInt()
            canvas.drawRect(0f, shoreY, wf, beachY, warmSeaPaint)
        }

        for (i in reflCenter.indices) {
            for (k in 1..4) {
                val flicker = 0.6f + 0.4f * sin(time * 2f + i * 1.7f + k)
                reflPaint.alpha = (95f * (1f - k * 0.22f) * flicker).toInt()
                val half = reflHalf[i] * (1f - k * 0.15f)
                val y = shoreY + k * 5f * dp
                canvas.drawLine(reflCenter[i] - half, y, reflCenter[i] + half, y, reflPaint)
            }
        }

        for (i in 0 until waveN) {
            val a = 0.5f + 0.5f * sin(time * waveRate[i] + wavePhase[i])
            wavePaint.alpha = (waveMaxAlpha * a).toInt()
            wavePaint.strokeWidth = (0.8f + 1.4f * waveT[i]) * dp
            canvas.drawLine(waveX[i] - waveLen[i], waveY[i], waveX[i], waveY[i], wavePaint)
        }

        if (glintStrength > 0.02f) for (i in 0 until glintN) {
            val a = sin(time * glintRate[i] + glintPhase[i])
            if (a <= 0f) continue
            glintPaint.alpha = (230 * a * glintStrength).toInt()
            val x = glintX + glintOff[i] + sin(time * 0.7f + glintPhase[i]) * 3f * dp
            canvas.drawLine(x - glintLen[i] / 2f, glintY[i], x + glintLen[i] / 2f, glintY[i], glintPaint)
        }

        canvas.drawLine(0f, shoreY, wf, shoreY, shoreLinePaint)
    }

    private fun drawCity(canvas: Canvas) {
        canvas.drawBitmap(city.bitmap(lightFromRight = lightDir >= 0f), 0f, city.top, null)
        if (night && time % 1.6f < 0.18f) {
            val b = city.beacons
            for (i in b.indices step 2) canvas.drawCircle(b[i], b[i + 1], 1.6f * wf / 720f, beaconPaint)
        }
    }

    private fun drawStadium(canvas: Canvas) {
        if (night) {
            canvas.save()
            canvas.translate(stCx, stTop + stH * 0.3f)
            canvas.scale(stW * 0.8f / 100f, stW * 0.4f / 100f)
            canvas.drawCircle(0f, 0f, 100f, stadiumGlowPaint)
            canvas.restore()
        }
        // Roof ring first; the facade covers its near half.
        canvas.drawOval(roofRect, roofPaint)
        canvas.drawArc(roofRect, 180f, 180f, false, rimPaint)
        canvas.drawPath(facadePath, facadePaint)
        canvas.save()
        canvas.clipPath(facadePath)
        canvas.drawLines(pleatPts, pleatPaint)
        canvas.drawRect(facadeRect, facadeShadePaint)
        canvas.drawRect(stX0, stBase - stH * 0.1f, stX1, stBase, podiumPaint)
        canvas.restore()
        canvas.drawLine(stX0 + stH * 0.7f, stTop + 0.6f * dp, stX1 - stH * 0.7f, stTop + 0.6f * dp, rimPaint)
        if (night) {
            val y = stBase - stH * 0.1f
            canvas.drawLine(stX0 + stH * 0.5f, y, stX1 - stH * 0.5f, y, baseLightPaint)
        }
    }

    private fun drawBeach(canvas: Canvas) {
        canvas.drawRect(0f, beachY, wf, hf, sandPaint)
        val depth = hf - beachY
        canvas.drawRect(0f, beachY, wf, beachY + depth * 0.62f, wetSandPaint)

        // Waves run up the sand and slide back.
        val reach = 0.5f - 0.5f * cos(time * 0.55f)
        val edge = beachY + depth * (0.12f + 0.42f * reach)
        washPath.reset()
        foamPath.reset()
        washPath.moveTo(0f, beachY - 1f)
        washPath.lineTo(wf, beachY - 1f)
        val steps = 28
        for (s in steps downTo 0) {
            val x = wf * s / steps
            val u = s / steps.toFloat() * TWO_PI
            val y = edge + sin(u * 3f + time * 0.8f) * 3f * dp + sin(u * 7f - time * 1.3f) * 1.5f * dp
            washPath.lineTo(x, y)
            if (s == steps) foamPath.moveTo(x, y) else foamPath.lineTo(x, y)
        }
        washPath.close()
        canvas.drawPath(washPath, washPaint)
        foamPaint.alpha = (if (night) 150 else 230) - (reach * 60).toInt()
        canvas.drawPath(foamPath, foamPaint)
    }

    /** Builds a smooth silhouette through (x, height) pairs; closed down to the shore by default. */
    private fun smoothPath(pts: FloatArray, close: Boolean = true): Path {
        val p = Path()
        val n = pts.size / 2
        fun px(i: Int) = mx(pts[i * 2])
        fun py(i: Int) = my(pts[i * 2 + 1])
        p.moveTo(px(0), py(0))
        for (i in 1 until n - 1) {
            p.quadTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
        }
        p.lineTo(px(n - 1), py(n - 1))
        if (close) {
            p.lineTo(px(n - 1), shoreY + 1f)
            p.lineTo(px(0), shoreY + 1f)
            p.close()
        }
        return p
    }

    private companion object {
        const val PUFFS_PER_STREAM = 9
    }
}
