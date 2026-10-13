package za.co.saweather.lwp.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stars, Milky Way, constellations, planets, shooting stars, moon (with accurate phase) and sun.
 * The star field is generated once per size and only its opacity is animated.
 */
class Celestial {
    private var stars = FloatArray(0) // x, y, brightness, twinkleSpeed, twinklePhase, warmth per star
    private var starCount = 0
    private var builtFor = ""
    private var milkyWay: Bitmap? = null
    private var radius = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val moonPath = Path()
    private val src = Rect()
    private val dst = RectF()

    private val shooting = ArrayList<Meteor>()
    private class Meteor(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val maxLife: Float, val len: Float)

    private val rng = Rng(42)

    fun build(f: Frame, cfg: SceneConfig) {
        val key = "${f.w}x${f.h}:${cfg.starCount}:${f.particleScale}"
        if (key == builtFor) return
        builtFor = key
        radius = hypot(f.w.toFloat(), f.h.toFloat()) * 0.75f
        val r = Rng(1234)
        starCount = (cfg.starCount * (0.55f + 0.45f * f.particleScale)).toInt()
        stars = FloatArray(starCount * 6)
        for (i in 0 until starCount) {
            // Uniform over a disc around the pivot so rotation never reveals gaps.
            val ang = r.float() * TAU
            val d = sqrt(r.float()) * radius
            val mag = r.float()
            stars[i * 6] = cos(ang) * d
            stars[i * 6 + 1] = sin(ang) * d
            stars[i * 6 + 2] = 0.15f + 0.85f * mag * mag * mag // many faint, few bright
            stars[i * 6 + 3] = r.range(0.6f, 3.2f)
            stars[i * 6 + 4] = r.float() * TAU
            stars[i * 6 + 5] = r.range(-1f, 1f)
        }
        if (cfg.milkyWayAllowed && milkyWay == null) milkyWay = buildMilkyWay()
    }

    private fun pivotX(f: Frame) = f.w * 0.5f + f.shiftX(0.04f)
    private fun pivotY(f: Frame) = f.horizonY * 0.5f + f.shiftY(0.04f)

    /** Slow rotation of the sky over the night. */
    private fun skyAngle(f: Frame) = (f.atmos.hour * 15f * 0.3f) % 360f

    fun draw(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        val s = f.settings
        val starVis = a.night * (1f - a.cloudCover * 0.92f) * (1f - a.flash * 0.6f)

        // Milky Way: only on dark, moon-poor nights at dark-sky sites.
        val moonWash = a.moonIllum * smoothstep(0f, 0.25f, a.moonAlt)
        val mwVis = if (s.milkyWay && cfg.milkyWayAllowed) {
            smoothstep(0.55f, 0.95f, a.night) * (1f - smoothstep(0.25f, 0.7f, moonWash)) * (1f - a.cloudCover)
        } else 0f

        if (starVis > 0.01f) {
            c.save()
            c.clipRect(0f, 0f, f.w.toFloat(), f.horizonY + 2f)
            c.translate(pivotX(f), pivotY(f))
            c.rotate(skyAngle(f))
            if (mwVis > 0.01f) drawMilkyWay(c, f, mwVis)
            drawStars(c, f, cfg, starVis)
            if (s.constellations) drawConstellations(c, f, starVis)
            if (s.planets) drawOuterPlanets(c, f, starVis)
            c.restore()
        }
        if (s.planets) drawVenus(c, f)
        if (s.shootingStars) updateShootingStars(c, f, cfg, starVis, mwVis)
        drawMoon(c, f)
        drawSun(c, f)
    }

    private fun drawStars(c: Canvas, f: Frame, cfg: SceneConfig, vis: Float) {
        val px = pivotX(f); val py = pivotY(f)
        val ang = Math.toRadians(skyAngle(f).toDouble())
        val ca = cos(ang).toFloat(); val sa = sin(ang).toFloat()
        val hy = f.horizonY
        val base = 1.3f * f.u
        for (i in 0 until starCount) {
            val x = stars[i * 6]; val y = stars[i * 6 + 1]
            // World position after rotation, used for culling and horizon extinction.
            val wy = py + x * sa + y * ca
            if (wy > hy || wy < -4f) continue
            val wx = px + x * ca - y * sa
            if (wx < -4f || wx > f.w + 4f) continue
            val b = stars[i * 6 + 2]
            val tw = 0.7f + 0.3f * sin(f.t * stars[i * 6 + 3] + stars[i * 6 + 4])
            val nearHorizon = smoothstep(0.45f, 1f, wy / hy)
            val extinction = 1f - nearHorizon * (0.55f + 0.45f * cfg.lightPollution)
            val al = vis * b * tw * extinction
            if (al < 0.03f) continue
            val warm = stars[i * 6 + 5]
            paint.color = when {
                warm > 0.7f -> Color.rgb(255, 222, 190)
                warm < -0.7f -> Color.rgb(196, 214, 255)
                else -> Color.WHITE
            }
            paint.alpha = (al * 255).toInt()
            c.drawCircle(x, y, base * (0.5f + b * 1.3f), paint)
        }
    }

    private fun drawMilkyWay(c: Canvas, f: Frame, vis: Float) {
        val mw = milkyWay ?: return
        // The band has its own slow drift on top of the sky rotation.
        val drift = -38f + (f.atmos.hour - 22f) * 4f
        c.save()
        c.rotate(drift)
        val len = radius * 2.1f
        val thick = len * 0.24f
        src.set(0, 0, mw.width, mw.height)
        dst.set(-len / 2, -thick / 2 - radius * 0.1f, len / 2, thick / 2 - radius * 0.1f)
        bmpPaint.alpha = (vis * 0.6f * 255).toInt().coerceIn(0, 255)
        c.drawBitmap(mw, src, dst, bmpPaint)
        c.restore()
    }

    // ---- Constellations (hardcoded shapes, placed in the rotating sky frame).

    private class Constellation(val ax: Float, val ay: Float, val scale: Float, val pts: FloatArray, val mags: FloatArray, val lines: IntArray, val colors: IntArray? = null)

    private val constellations = listOf(
        // Southern Cross (Crux) + the Pointers (Alpha & Beta Centauri).
        Constellation(
            -0.32f, -0.42f, 0.16f,
            floatArrayOf(0f, 0f, 0.08f, 1f, -0.42f, 0.42f, 0.38f, 0.32f, 0.22f, 0.62f, -1.3f, 0.95f, -2.05f, 1.15f),
            floatArrayOf(0.75f, 1f, 0.9f, 0.55f, 0.35f, 0.95f, 1f),
            intArrayOf(0, 1, 2, 3, 5, 6),
            intArrayOf(Color.rgb(255, 214, 180), Color.WHITE, Color.rgb(205, 220, 255), Color.WHITE, Color.WHITE, Color.WHITE, Color.rgb(255, 245, 220)),
        ),
        // Scorpius, with red Antares.
        Constellation(
            0.32f, -0.28f, 0.14f,
            floatArrayOf(0f, -0.35f, 0.05f, 0f, 0.12f, 0.32f, 0.6f, 0.25f, 0.9f, 0.45f, 1.1f, 0.8f, 1.15f, 1.2f, 1.0f, 1.5f, 0.75f, 1.62f, 0.58f, 1.45f, 0.52f, 1.3f),
            floatArrayOf(0.5f, 0.65f, 0.5f, 1f, 0.5f, 0.45f, 0.5f, 0.55f, 0.6f, 0.7f, 0.8f),
            intArrayOf(0, 1, 1, 2, 1, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10),
            IntArray(11) { if (it == 3) Color.rgb(255, 120, 80) else Color.rgb(215, 225, 255) },
        ),
        // Orion as seen from the south: upside down, Rigel on top.
        Constellation(
            -0.05f, 0.2f, 0.13f,
            floatArrayOf(-0.15f, 0f, 0f, 0.05f, 0.15f, 0.1f, 0.38f, -0.62f, -0.3f, -0.55f, -0.4f, 0.72f, 0.42f, 0.62f),
            floatArrayOf(0.7f, 0.7f, 0.7f, 1f, 0.6f, 1f, 0.7f),
            intArrayOf(0, 1, 1, 2, 2, 3, 0, 4, 0, 5, 2, 6),
            intArrayOf(Color.WHITE, Color.WHITE, Color.WHITE, Color.rgb(200, 215, 255), Color.WHITE, Color.rgb(255, 150, 90), Color.WHITE),
        ),
    )

    private fun drawConstellations(c: Canvas, f: Frame, vis: Float) {
        val r = minOf(f.w, f.h).toFloat()
        linePaint.strokeWidth = 1.4f * f.u
        for (k in constellations) {
            val ox = k.ax * f.w
            val oy = k.ay * f.h
            val sc = k.scale * r
            linePaint.color = withAlpha(Color.rgb(150, 180, 255), 0.28f * vis)
            var i = 0
            while (i < k.lines.size) {
                val p = k.lines[i]; val q = k.lines[i + 1]
                c.drawLine(ox + k.pts[p * 2] * sc, oy + k.pts[p * 2 + 1] * sc, ox + k.pts[q * 2] * sc, oy + k.pts[q * 2 + 1] * sc, linePaint)
                i += 2
            }
            for (j in k.mags.indices) {
                val x = ox + k.pts[j * 2] * sc
                val y = oy + k.pts[j * 2 + 1] * sc
                val tw = 0.85f + 0.15f * sin(f.t * 2.1f + j * 1.7f)
                paint.color = k.colors?.get(j) ?: Color.WHITE
                paint.alpha = (vis * tw * 255).toInt().coerceIn(0, 255)
                c.drawCircle(x, y, (1.6f + 2.6f * k.mags[j]) * f.u, paint)
            }
        }
    }

    private fun drawOuterPlanets(c: Canvas, f: Frame, vis: Float) {
        // Decorative placement along the ecliptic; planets do not twinkle.
        drawPlanet(c, f.w * 0.18f, -f.h * 0.12f, 3.6f * f.u, Color.rgb(255, 244, 222), vis) // Jupiter
        drawPlanet(c, f.w * 0.36f, -f.h * 0.02f, 2.8f * f.u, Color.rgb(240, 222, 170), vis) // Saturn
    }

    private fun drawVenus(c: Canvas, f: Frame) {
        val a = f.atmos
        // Evening star low in the west after sunset, morning star in the east before dawn.
        val evening = hourWindow(18.7f, 19.3f, 21.0f, 21.8f, a.hour)
        val morning = hourWindow(3.6f, 4.3f, 5.8f, 6.4f, a.hour)
        val v = maxOf(evening, morning) * (1f - a.cloudCover) * (1f - a.daylight * 0.7f)
        if (v < 0.02f) return
        val x = if (evening > morning) f.w * 0.16f else f.w * 0.84f
        val y = f.horizonY * (0.72f - 0.12f * (if (evening > morning) 1f - smoothstep(19f, 21.5f, a.hour) else smoothstep(3.8f, 6f, a.hour)))
        drawPlanet(c, x + f.shiftX(0.05f), y, 4.6f * f.u, Color.rgb(255, 252, 236), v)
    }

    private fun drawPlanet(c: Canvas, x: Float, y: Float, r: Float, color: Int, vis: Float) {
        glowPaint.shader = RadialGradient(x, y, r * 5f, withAlpha(color, 0.35f * vis), 0, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 5f, glowPaint)
        glowPaint.shader = null
        paint.color = withAlpha(color, vis)
        c.drawCircle(x, y, r, paint)
    }

    private fun updateShootingStars(c: Canvas, f: Frame, cfg: SceneConfig, starVis: Float, mwVis: Float) {
        val rate = 0.045f * cfg.shootingStarRate * (1f + 2f * mwVis) * starVis
        if (starVis > 0.3f && rng.float() < rate * f.dt) {
            val dir = rng.sign()
            val speed = f.w * rng.range(0.9f, 1.6f)
            val ang = rng.range(0.35f, 0.75f)
            val life = rng.range(0.45f, 1.0f)
            shooting.add(Meteor(rng.range(0.1f, 0.9f) * f.w, rng.range(0.03f, 0.45f) * f.horizonY,
                dir * cos(ang) * speed, sin(ang) * speed, life, life, rng.range(0.08f, 0.18f) * f.w))
        }
        val it = shooting.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.life -= f.dt
            if (m.life <= 0f) { it.remove(); continue }
            m.x += m.vx * f.dt; m.y += m.vy * f.dt
            val p = m.life / m.maxLife
            val sp = hypot(m.vx, m.vy)
            val tx = m.x - m.vx / sp * m.len * (1.2f - p)
            val ty = m.y - m.vy / sp * m.len * (1.2f - p)
            val al = sin(p * PI_F) * starVis
            linePaint.alpha = 255
            linePaint.shader = LinearGradient(m.x, m.y, tx, ty, withAlpha(Color.WHITE, al), 0, Shader.TileMode.CLAMP)
            linePaint.strokeWidth = 2.2f * f.u
            c.drawLine(m.x, m.y, tx, ty, linePaint)
            linePaint.shader = null
        }
    }

    // ---- Moon: two-curve construction (limb semicircle + elliptical terminator) for any phase.

    private fun drawMoon(c: Canvas, f: Frame) {
        val a = f.atmos
        if (a.moonAlt <= 0.01f) return
        val (x, y) = moonPos(f)
        val r = 26f * f.u
        val dayFade = 1f - a.daylight * 0.65f
        val cloudFade = 1f - a.cloudCover * 0.85f
        val vis = dayFade * cloudFade * smoothstep(0f, 0.08f, a.moonAlt)
        if (vis < 0.02f) return

        // Halo, brighter for a fuller moon; washes out with daylight.
        if (f.settings.moonGlow) {
            val g = a.moonIllum * vis * (1f - a.daylight) * f.theme.bloom
            if (g > 0.02f) {
                glowPaint.shader = RadialGradient(x, y, r * 7f, intArrayOf(withAlpha(Color.rgb(200, 215, 255), 0.42f * g), withAlpha(Color.rgb(170, 190, 255), 0.1f * g), 0), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, r * 7f, glowPaint)
                glowPaint.shader = null
            }
        }
        // Earthshine on the dark side.
        paint.color = withAlpha(Color.rgb(60, 70, 96), 0.55f * vis * (1f - a.daylight))
        c.drawCircle(x, y, r, paint)

        val phase = if (f.settings.realLunar) a.moonPhase else 0.2f
        buildMoonPath(moonPath, x, y, r, phase)
        paint.color = withAlpha(Color.rgb(246, 244, 232), vis)
        c.drawPath(moonPath, paint)
        // A few maria for texture.
        c.save()
        c.clipPath(moonPath)
        paint.color = withAlpha(Color.rgb(170, 172, 168), 0.45f * vis)
        c.drawCircle(x - r * 0.25f, y - r * 0.2f, r * 0.28f, paint)
        c.drawCircle(x + r * 0.2f, y + r * 0.15f, r * 0.2f, paint)
        c.drawCircle(x - r * 0.05f, y + r * 0.42f, r * 0.14f, paint)
        c.restore()
    }

    private fun drawSun(c: Canvas, f: Frame) {
        val a = f.atmos
        if (a.sunElev < -0.05f) return
        val (x, y) = sunPos(f)
        val r = 30f * f.u
        val vis = (1f - a.storm * 0.95f) * (1f - a.cloudCover * 0.5f) * smoothstep(-0.05f, 0.02f, a.sunElev)
        if (vis < 0.02f) return
        val col = lerpColor(Color.rgb(255, 252, 240), Color.rgb(255, 160, 70), a.golden)
        glowPaint.shader = RadialGradient(x, y, r * 4f, intArrayOf(withAlpha(col, 0.7f * vis * f.theme.bloom.coerceAtMost(1.2f)), withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 4f, glowPaint)
        glowPaint.shader = null
        paint.color = withAlpha(col, vis)
        c.drawCircle(x, y, r, paint)
    }

    private fun buildMilkyWay(): Bitmap {
        val w = 1400
        val h = 340
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = Rng(777)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun gauss(): Float = (r.float() + r.float() + r.float() + r.float() - 2f) / 2f
        // Soft nebulous glow: overlapping blurred blobs, tinted purple / blue / gold.
        p.maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
        val tints = intArrayOf(Color.rgb(170, 150, 220), Color.rgb(140, 170, 235), Color.rgb(240, 210, 170), Color.rgb(210, 190, 230))
        for (i in 0 until 260) {
            val x = r.float() * w
            val density = 0.5f + 0.5f * fbm(x / w * 6f, 4, 3)
            val y = h / 2f + gauss() * h * 0.22f
            p.color = withAlpha(tints[r.int(tints.size)], 0.05f + 0.09f * density)
            c.drawCircle(x, y, r.range(18f, 46f) * (0.6f + density), p)
        }
        // Brighter galactic core region.
        for (i in 0 until 50) {
            p.color = withAlpha(Color.rgb(250, 225, 190), 0.045f)
            c.drawCircle(w * 0.62f + gauss() * w * 0.08f, h / 2f + gauss() * h * 0.12f, r.range(20f, 40f), p)
        }
        // Dust lanes cut through the middle.
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        p.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        for (i in 0 until 70) {
            val x = r.float() * w
            p.color = withAlpha(Color.BLACK, 0.18f + 0.2f * r.float())
            c.drawCircle(x, h / 2f + fbm(x / w * 9f, 3, 9) * h * 0.1f, r.range(6f, 18f), p)
        }
        p.xfermode = null
        p.maskFilter = null
        // Dense unresolved stars.
        for (i in 0 until 3600) {
            val x = r.float() * w
            val y = h / 2f + gauss() * h * 0.26f
            p.color = withAlpha(Color.WHITE, r.range(0.06f, 0.4f))
            c.drawCircle(x, y, r.range(0.5f, 1.3f), p)
        }
        // Fade the band's ends.
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(Color.BLACK, 0, 0, Color.BLACK), floatArrayOf(0f, 0.2f, 0.8f, 1f), Shader.TileMode.CLAMP)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    companion object {
        fun sunPos(f: Frame): Pair<Float, Float> {
            val a = f.atmos
            return Pair(
                lerp(f.w * 1.06f, -f.w * 0.06f, a.sunU) + f.shiftX(0.05f),
                f.horizonY - a.sunElev * f.horizonY * 0.82f,
            )
        }

        fun moonPos(f: Frame): Pair<Float, Float> {
            val a = f.atmos
            // Lower, flatter arc than the sun.
            return Pair(
                lerp(f.w * 1.04f, -f.w * 0.04f, a.moonU) + f.shiftX(0.05f),
                f.horizonY - a.moonAlt * f.horizonY * 0.7f,
            )
        }

        /**
         * Lit region of the moon. Viewed from the Southern Hemisphere a waxing moon is lit on the
         * left. The terminator is an ellipse whose x-radius is r·cos(2π·phase).
         */
        fun buildMoonPath(path: Path, cx: Float, cy: Float, r: Float, phase: Float) {
            path.reset()
            val waxing = phase < 0.5f
            val side = if (waxing) -1f else 1f
            val k = cos(TAU * phase)
            val steps = 32
            for (i in 0..steps) {
                val th = -PI_F / 2 + PI_F * i / steps
                val x = cx + side * r * cos(th)
                val y = cy + r * sin(th)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in steps downTo 0) {
                val th = -PI_F / 2 + PI_F * i / steps
                path.lineTo(cx + side * r * cos(th) * k, cy + r * sin(th))
            }
            path.close()
        }
    }
}
