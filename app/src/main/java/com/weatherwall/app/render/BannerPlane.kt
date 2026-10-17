package com.weatherwall.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Mavericks banner plane that flies over Cape Town: a small high-wing taildragger towing
 * a fluttering black banner. Drawn in code so it stays crisp at any size.
 */
class BannerPlane(private val dp: Float) {

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f

    var active = false
        private set
    private var t = 0f
    private var x = 0f        // nose position
    private var baseY = 0f
    private var dir = 1f      // +1 flying right, -1 flying left
    private var speed = 0f
    private var alpha = 255

    // Sizes, set from the screen width.
    private var planeLen = 0f
    private var towLen = 0f
    private var bannerLen = 0f
    private var bannerH = 0f
    private var banner: Bitmap? = null

    fun setSize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat()
        h = height.toFloat()
        planeLen = w * 0.11f
        towLen = w * 0.07f
        bannerLen = w * 0.3f
        bannerH = bannerLen * 0.19f
        banner = renderBanner(bannerLen.toInt(), bannerH.toInt())
        buildPlanePaths(planeLen)
        active = false
    }

    /**
     * Starts a pass across the sky. [haze] 0..1 fades it in fog or heavy cloud; it flies at a height
     * between [top] and [top] + [band] (fractions of the screen height).
     */
    fun launch(haze: Float = 1f, top: Float = 0.38f, band: Float = 0.16f) {
        if (w == 0f) return
        dir = if (rnd.nextBoolean()) 1f else -1f
        baseY = h * (top + rnd.nextFloat() * band) // between the top widgets and the mountain (or skyline)
        val travel = w + planeLen + towLen + bannerLen
        speed = travel / (25f + rnd.nextFloat() * 10f)
        x = if (dir > 0) -0.02f * w else w * 1.02f // nose just off-screen; the banner follows
        alpha = (255 * haze).toInt().coerceIn(0, 255)
        t = 0f
        active = true
    }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        x += dir * speed * dt
        val tailEnd = x - dir * (planeLen + towLen + bannerLen)
        if ((dir > 0 && tailEnd > w + 10f) || (dir < 0 && tailEnd < -10f)) active = false
    }

    fun draw(canvas: Canvas) {
        if (!active) return
        val y = baseY + sin(t * 0.5f) * 5f * dp
        val pitch = cos(t * 0.5f) * 2.5f // degrees, nose follows the bob
        drawBanner(canvas, y)
        drawTowLine(canvas, y)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(-pitch * dir)
        if (dir < 0) canvas.scale(-1f, 1f)
        drawPlane(canvas)
        canvas.restore()
    }

    // ---- Banner ------------------------------------------------------------

    private val meshCols = 20
    private val meshVerts = FloatArray((meshCols + 1) * 2 * 2)
    private val bannerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun bannerFront() = x - dir * (planeLen + towLen)

    private fun drawBanner(canvas: Canvas, y: Float) {
        val bmp = banner ?: return
        val front = bannerFront()
        // Banner text always reads left to right; flutter grows towards the trailing end.
        for (i in 0..meshCols) {
            val u = i / meshCols.toFloat()                    // 0 = left edge of the banner
            val fromFront = if (dir > 0) 1f - u else u        // 0 at the towed edge
            val px = if (dir > 0) front - bannerLen + u * bannerLen else front + u * bannerLen
            val wave = sin(fromFront * 3f * PI.toFloat() - t * 7f) * bannerH * 0.09f * (0.2f + fromFront)
            val sag = fromFront * fromFront * bannerH * 0.12f
            val top = y - bannerH / 2f + wave + sag
            meshVerts[i * 2] = px
            meshVerts[i * 2 + 1] = top
            val j = (meshCols + 1 + i) * 2
            meshVerts[j] = px
            meshVerts[j + 1] = top + bannerH * (1f - 0.08f * fromFront) // trailing end narrows a touch
        }
        bannerPaint.alpha = alpha
        canvas.drawBitmapMesh(bmp, meshCols, 1, meshVerts, 0, null, 0, bannerPaint)
        // The weighted pole on the towed edge.
        val poleX = front
        polePaint.alpha = alpha
        canvas.drawLine(poleX, y - bannerH * 0.55f, poleX, y + bannerH * 0.6f, polePaint)
    }

    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2A2A.toInt()
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val towPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3A3A.toInt()
        strokeWidth = 0.9f * dp
    }

    private fun drawTowLine(canvas: Canvas, y: Float) {
        val tailX = x - dir * planeLen
        towPaint.alpha = (alpha * 0.8f).toInt()
        val front = bannerFront()
        // A single tow line, splitting into a short bridle to both ends of the banner's pole.
        val split = front + dir * towLen * 0.3f
        canvas.drawLine(tailX, y, split, y, towPaint)
        canvas.drawLine(split, y, front, y - bannerH * 0.5f, towPaint)
        canvas.drawLine(split, y, front, y + bannerH * 0.5f, towPaint)
    }

    // What the banner says: the usual advert with its two stars, or a holiday greeting with that holiday's symbol.
    private var label = LABEL
    private var symbol: BannerSymbol? = null
    private var year = 0
    private val symbolArt = BannerSymbolArt()

    /** Sets the banner's message and its holiday [symbol] (null message = the usual advert). */
    fun setMessage(message: String?, symbol: BannerSymbol?, year: Int) {
        val newLabel = message ?: LABEL
        val newSymbol = if (message != null) symbol else null
        if (newLabel == label && newSymbol == this.symbol && year == this.year) return
        label = newLabel
        this.symbol = newSymbol
        this.year = year
        if (bannerLen > 0f) banner = renderBanner(bannerLen.toInt(), bannerH.toInt())
    }

    private fun renderBanner(bw: Int, bh: Int): Bitmap {
        val bmp = Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = bmp.width.toFloat()
        val fh = bmp.height.toFloat()
        c.drawColor(0xFF1E1C1C.toInt())

        val sym = symbol
        if (sym != null) {
            symbolArt.draw(c, sym, fw * 0.13f, fh * 0.5f, fh * 0.9f, year)
        } else {
            val star = Paint(Paint.ANTI_ALIAS_FLAG)
            star.color = 0xFFB08A4E.toInt()
            c.drawPath(starPath(fw * 0.075f, fh * 0.5f, fh * 0.36f, -14f), star)
            star.color = 0xFFC8202F.toInt()
            c.drawPath(starPath(fw * 0.185f, fh * 0.5f, fh * 0.36f, 16f), star)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textScaleX = 1.12f
            letterSpacing = 0.04f
            textAlign = Paint.Align.CENTER
        }
        val left = fw * 0.265f
        val right = fw * 0.975f
        text.textSize = fh * 0.62f
        val measured = text.measureText(label)
        if (measured > right - left) text.textSize *= (right - left) / measured
        val bounds = android.graphics.Rect()
        text.getTextBounds(label, 0, label.length, bounds)
        c.drawText(label, (left + right) / 2f, fh / 2f - bounds.exactCenterY(), text)
        return bmp
    }

    /** A five-pointed star, slightly irregular like the logo's. */
    private fun starPath(cx: Float, cy: Float, r: Float, rotationDeg: Float): Path {
        val p = Path()
        val inner = r * 0.42f
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else inner
            val a = Math.toRadians((rotationDeg - 90f + i * 36f).toDouble())
            val px = cx + rr * cos(a).toFloat()
            val py = cy + rr * sin(a).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        return p
    }

    // ---- Plane (drawn facing right, nose at the origin, length planeLen) ----

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9DDE1.toInt() }
    private val bodyShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA1A9.toInt() }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B4048.toInt() }
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5D7286.toInt() }
    private val strutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6E757D.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val propPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553B4048 }

    private val finPath = Path()
    private val bodyPath = Path()

    private fun buildPlanePaths(l: Float) {
        finPath.reset()
        finPath.moveTo(-0.80f * l, -0.035f * l)
        finPath.lineTo(-0.92f * l, -0.21f * l)
        finPath.lineTo(-1.0f * l, -0.21f * l)
        finPath.lineTo(-1.0f * l, -0.015f * l)
        finPath.close()
        // Fuselage: blunt nose, cabin, tapering to the tail.
        bodyPath.reset()
        bodyPath.moveTo(0f, -0.035f * l)
        bodyPath.cubicTo(-0.05f * l, -0.08f * l, -0.12f * l, -0.1f * l, -0.2f * l, -0.105f * l)
        bodyPath.lineTo(-0.45f * l, -0.1f * l)
        bodyPath.cubicTo(-0.65f * l, -0.07f * l, -0.85f * l, -0.04f * l, -1.0f * l, -0.025f * l)
        bodyPath.lineTo(-1.0f * l, 0.01f * l)
        bodyPath.cubicTo(-0.75f * l, 0.03f * l, -0.5f * l, 0.065f * l, -0.25f * l, 0.065f * l)
        bodyPath.cubicTo(-0.1f * l, 0.065f * l, -0.02f * l, 0.05f * l, 0f, 0.035f * l)
        bodyPath.close()
    }

    private fun drawPlane(c: Canvas) {
        val l = planeLen
        val a = alpha
        for (p in listOf(bodyPaint, bodyShadePaint, darkPaint, windowPaint, strutPaint)) p.alpha = a
        propPaint.alpha = (0x55 * a / 255)
        strutPaint.strokeWidth = l * 0.018f

        // Tail fin and stabiliser.
        c.drawPath(finPath, bodyShadePaint)
        c.drawRect(-1.0f * l, -0.02f * l, -0.78f * l, 0.005f * l, darkPaint)

        c.drawPath(bodyPath, bodyPaint)
        c.drawRect(-0.9f * l, -0.005f * l, -0.12f * l, 0.012f * l, bodyShadePaint) // trim line

        // Cabin window.
        c.drawRoundRect(RectF(-0.37f * l, -0.088f * l, -0.19f * l, -0.035f * l), 0.01f * l, 0.01f * l, windowPaint)

        // High wing seen edge-on, with its strut.
        c.drawRoundRect(RectF(-0.5f * l, -0.15f * l, -0.15f * l, -0.098f * l), 0.026f * l, 0.026f * l, bodyShadePaint)
        c.drawRoundRect(RectF(-0.5f * l, -0.155f * l, -0.15f * l, -0.112f * l), 0.022f * l, 0.022f * l, bodyPaint)
        c.drawLine(-0.3f * l, -0.1f * l, -0.34f * l, 0.05f * l, strutPaint)

        // Landing gear: main wheels and tail wheel.
        c.drawLine(-0.2f * l, 0.05f * l, -0.15f * l, 0.13f * l, strutPaint)
        c.drawCircle(-0.15f * l, 0.14f * l, 0.04f * l, darkPaint)
        c.drawCircle(-0.97f * l, 0.03f * l, 0.015f * l, darkPaint)

        // Spinner and a blurred, spinning propeller.
        c.drawCircle(0.005f * l, 0f, 0.03f * l, darkPaint)
        val blade = 0.13f * l * (0.85f + 0.15f * sin(t * 40f))
        c.drawOval(RectF(0.0f, -blade, 0.025f * l, blade), propPaint)
    }

    private companion object {
        const val LABEL = "MAVERICKS"
    }
}
