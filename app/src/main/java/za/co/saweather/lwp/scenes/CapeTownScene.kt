package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Atmosphere
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Precip
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Weather
import za.co.saweather.lwp.engine.Wildlife
import za.co.saweather.lwp.engine.clamp01
import za.co.saweather.lwp.engine.hourWindow
import za.co.saweather.lwp.engine.lerp
import za.co.saweather.lwp.engine.lerpColor
import za.co.saweather.lwp.engine.multiply
import za.co.saweather.lwp.engine.smoothstep
import za.co.saweather.lwp.engine.withAlpha
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Scene 2 — Table Mountain over the Atlantic. Hero: the morning tablecloth cloud. */
class CapeTownScene : Scene(
    SceneId.CAPE_TOWN,
    SceneConfig(
        starCount = 650, lightPollution = 0.75f, milkyWayAllowed = false, precip = Precip.RAIN,
        stormStrength = 0.8f, precipStrength = 0.35f, windStrength = 1.9f, lightningStrength = 0.25f,
        morningMist = 0.25f, horizon = 0.56f, shootingStarRate = 0.5f,
    ),
) {
    private var mountain: LandLayer? = null
    private var rocks: LandLayer? = null
    private val cityLights = ArrayList<FloatArray>()  // x, y, colourIndex, phase
    private val harbourLights = ArrayList<FloatArray>()
    private lateinit var weather: Weather
    private var splashTimer = 0f
    private val rect = RectF()

    // Geometry (fractions of width) shared by the layer and the tablecloth.
    private val tableL = 0.22f
    private val tableR = 0.64f
    private val lighthouseX = 0.83f

    private fun tableTop(f: Frame) = f.horizonY - f.h * 0.165f
    private fun seaH(f: Frame) = f.h - f.horizonY

    override fun build(f: Frame) {
        val hy = f.horizonY
        val top = tableTop(f)
        mountain = layer(f, top - f.h * 0.03f, hy + 3f, 0.1f) { l, r ->
            val m = Path()
            m.moveTo(l, hy + 2f)
            // Devil's Peak, the table, Lion's Head and Signal Hill.
            val pts = floatArrayOf(
                -0.05f, 0.02f, 0.02f, 0.06f, 0.1f, 0.15f, 0.13f, 0.12f, 0.18f, 0.155f,
                tableL, 0.165f, 0.4f, 0.168f, tableR, 0.165f, 0.69f, 0.1f, 0.74f, 0.07f,
                0.8f, 0.125f, 0.835f, 0.11f, 0.87f, 0.05f, 0.95f, 0.035f, 1.05f, 0.015f,
            )
            var i = 0
            while (i < pts.size) {
                m.lineTo(pts[i] * f.w, hy - pts[i + 1] * f.h)
                i += 2
            }
            m.lineTo(r, hy + 2f)
            m.close()
            gradientFill(m, top, hy, Color.rgb(112, 122, 126), Color.rgb(76, 98, 92))
            // Sandstone cliff face with vertical gullies.
            val cliff = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            val r0 = Rng(3)
            save()
            clipPath(m)
            for (k in 0 until 90) {
                val x = f.w * r0.range(0.16f, 0.66f)
                cliff.color = if (r0.chance(0.5f)) Color.argb(60, 60, 70, 74) else Color.argb(50, 170, 170, 160)
                cliff.strokeWidth = r0.range(1f, 4f) * f.u
                drawLine(x, top + f.h * 0.004f, x + r0.range(-6f, 6f) * f.u, top + f.h * r0.range(0.03f, 0.09f), cliff)
            }
            // Green lower slopes.
            val slope = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, hy - f.h * 0.07f, 0f, hy, Color.argb(0, 70, 96, 70), Color.rgb(64, 92, 66), Shader.TileMode.CLAMP)
            }
            drawRect(l, hy - f.h * 0.07f, r, hy + 2f, slope)
            restore()
            // City strip at the base and harbour cranes to the far left.
            val b = Paint(Paint.ANTI_ALIAS_FLAG)
            var x = f.w * 0.0f
            while (x < f.w * 0.78f) {
                val bw = f.w * r0.range(0.004f, 0.012f)
                val bh = f.h * r0.range(0.003f, 0.012f) * (if (x < f.w * 0.15f) 1.6f else 1f)
                b.color = lerpColor(Color.rgb(200, 200, 196), Color.rgb(150, 156, 160), r0.float())
                drawRect(x, hy - bh, x + bw, hy + 1f, b)
                if (r0.chance(0.5f)) cityLights += floatArrayOf(x + bw / 2, hy - bh * r0.range(0.2f, 0.8f), r0.int(3).toFloat(), r0.float() * 6f)
                x += bw * r0.range(0.9f, 1.6f)
            }
            b.color = Color.rgb(90, 96, 104)
            b.strokeWidth = 2f * f.u
            for (k in 0 until 4) {
                val cx = f.w * (0.03f + k * 0.035f)
                drawLine(cx, hy, cx, hy - f.h * 0.03f, b)
                drawLine(cx - f.w * 0.01f, hy - f.h * 0.03f, cx + f.w * 0.025f, hy - f.h * 0.028f, b)
                harbourLights += floatArrayOf(cx, hy - f.h * 0.03f, 1f, k.toFloat())
            }
            for (k in 0 until 10) harbourLights += floatArrayOf(f.w * r0.range(0.0f, 0.17f), hy - f.h * 0.004f, 0f, r0.float() * 6f)
        }

        rocks = layer(f, f.h * 0.6f, f.h.toFloat(), 0.6f) { l, r ->
            val rp = Paint(Paint.ANTI_ALIAS_FLAG)
            val r1 = Rng(8)
            // Rounded Cape granite boulders: left cluster (seals) and right point (lighthouse, penguins).
            fun boulders(cx: Float, cy: Float, spread: Float, n: Int, size: Float) {
                repeat(n) {
                    val bx = cx + r1.range(-spread, spread)
                    val by = cy + r1.range(-0.2f, 1f) * size
                    val br = size * r1.range(0.5f, 1.1f)
                    rp.shader = LinearGradient(0f, by - br, 0f, by + br, Color.rgb(150, 140, 132), Color.rgb(70, 64, 62), Shader.TileMode.CLAMP)
                    drawOval(RectF(bx - br * 1.3f, by - br * 0.8f, bx + br * 1.3f, by + br), rp)
                }
                rp.shader = null
            }
            boulders(f.w * 0.12f, f.h * 0.86f, f.w * 0.2f, 14, f.w * 0.09f)
            boulders(f.w * 0.86f, f.h * 0.8f, f.w * 0.16f, 12, f.w * 0.08f)
            rp.color = Color.rgb(62, 58, 56)
            drawRect(l, f.h * 0.94f, r, f.h.toFloat(), rp)

            // Lighthouse with red diagonal bands (Green Point style).
            val lx = f.w * lighthouseX
            val base = f.h * 0.76f
            val lh = f.h * 0.12f
            val tw = f.w * 0.045f
            val tower = Path().apply {
                moveTo(lx - tw, base); lineTo(lx - tw * 0.7f, base - lh); lineTo(lx + tw * 0.7f, base - lh); lineTo(lx + tw, base); close()
            }
            rp.color = Color.rgb(240, 238, 232)
            drawPath(tower, rp)
            save()
            clipPath(tower)
            rp.color = Color.rgb(196, 44, 40)
            for (k in 0 until 5) {
                val y = base - lh * (k + 0.2f) / 5f
                drawPath(Path().apply { moveTo(lx - tw * 1.2f, y); lineTo(lx + tw * 1.2f, y - lh * 0.12f); lineTo(lx + tw * 1.2f, y - lh * 0.07f); lineTo(lx - tw * 1.2f, y + lh * 0.05f); close() }, rp)
            }
            restore()
            rp.color = Color.rgb(40, 40, 44)
            drawRect(lx - tw * 0.85f, base - lh - f.h * 0.006f, lx + tw * 0.85f, base - lh, rp)
            drawRect(lx - tw * 0.4f, base - lh - f.h * 0.022f, lx + tw * 0.4f, base - lh - f.h * 0.019f, rp)
            rp.color = Color.rgb(230, 220, 170)
            drawRect(lx - tw * 0.35f, base - lh - f.h * 0.019f, lx + tw * 0.35f, base - lh - f.h * 0.006f, rp)
        }

        // --- Wildlife
        actors += Whale()
        actors += Dolphins()
        actors += Seals()
        actors += Penguins()
        actors += Gannets()
        actors += Ship(container = true, 0.9f, 41)
        actors += Ship(container = false, 0.45f, 42)
    }

    override fun adjust(a: Atmosphere) {
        // The Cape Doctor is a dry wind: keep cloud thin and mist low during the "storm".
        a.cloudCover = a.cloudCover.coerceAtMost(0.55f)
    }

    override fun update(f: Frame, weather: Weather) {
        this.weather = weather
        super.update(f, weather)
        // Waves breaking on the rocks.
        splashTimer -= f.dt
        if (splashTimer <= 0f) {
            val wind = f.atmos.windAbs()
            splashTimer = rng.range(1.2f, 4f) / (0.6f + wind * 1.5f)
            val left = rng.chance(0.5f)
            val x = if (left) f.w * rng.range(0.02f, 0.3f) else f.w * rng.range(0.7f, 0.98f)
            val y = if (left) f.h * 0.8f else f.h * 0.74f
            weather.spray(x + f.shiftX(0.6f), y, 0.35f + wind * 0.6f, f)
        }
    }

    // ------------------------------------------------------------------------------------

    override fun drawWorld(c: Canvas, f: Frame) {
        mountain?.draw(c, f)
        drawTablecloth(c, f)
        drawSea(c, f)
        drawActors(c, f, 0f, 0.5f)
        rocks?.draw(c, f)
        drawActors(c, f, 0.5f, 1f)
    }

    private fun drawSea(c: Canvas, f: Frame) {
        val a = f.atmos
        val hy = f.horizonY
        val lit = a.lit()
        val far = lerpColor(a.skyHorizon, multiply(Color.rgb(60, 104, 130), lit), 0.55f)
        val near = multiply(lerpColor(Color.rgb(16, 62, 86), Color.rgb(40, 60, 70), a.storm), lit)
        paint.alpha = 255
        paint.shader = LinearGradient(0f, hy, 0f, f.h.toFloat(), far, near, Shader.TileMode.CLAMP)
        c.drawRect(0f, hy, f.w.toFloat(), f.h.toFloat(), paint)
        paint.shader = null

        // Moonpath / sun glitter shimmering on the water.
        val moonVis = a.moonLight
        if (moonVis > 0.03f) glitterPath(c, f, za.co.saweather.lwp.engine.Celestial.moonPos(f).first, Color.rgb(220, 230, 255), moonVis)
        val sunVis = a.golden * (1f - a.storm) * smoothstep(-0.02f, 0.05f, a.sunElev)
        if (sunVis > 0.03f) glitterPath(c, f, za.co.saweather.lwp.engine.Celestial.sunPos(f).first, Color.rgb(255, 190, 110), sunVis)

        // Swell lines with perspective spacing; whitecaps when the southeaster blows.
        val sea = seaH(f)
        val wind = a.windAbs()
        stroke.strokeCap = Paint.Cap.ROUND
        val rows = 26
        for (i in 0 until rows) {
            val t = (i + (f.t * 0.06f) % 1f) / rows
            val y = hy + sea * t * t
            val amp = (1f + t * 6f) * f.u
            stroke.strokeWidth = (0.8f + t * 2.6f) * f.u
            stroke.color = withAlpha(lerpColor(near, Color.WHITE, 0.25f), 0.25f + 0.2f * t)
            val seg = f.w * (0.05f + 0.1f * t)
            var x = -seg + ((i * 97f + f.t * 10f * (1f + t)) % seg)
            while (x < f.w + seg) {
                val yy = y + sin(x * 0.02f / (0.3f + t) + f.t * 1.5f + i) * amp
                c.drawLine(x, yy, x + seg * 0.5f, yy, stroke)
                x += seg * 1.1f
            }
            if (wind > 0.5f && i % 2 == 0) {
                stroke.color = withAlpha(multiply(Color.WHITE, lerpColor(lit, Color.WHITE, 0.3f)), (wind - 0.5f) * 0.7f * (0.4f + t))
                var wx = ((i * 211f + f.t * 40f * (0.5f + t)) % (f.w * 0.4f)) - f.w * 0.1f
                while (wx < f.w) {
                    val yy = y + sin(wx * 0.05f + i) * amp
                    c.drawLine(wx, yy, wx + seg * 0.25f, yy - amp * 0.4f, stroke)
                    wx += f.w * (0.18f + 0.07f * sin(i.toFloat()))
                }
            }
        }

        // Harbour lights and their reflections.
        val nightLights = smoothstep(0.25f, 0.7f, a.night) + a.storm * 0.4f * (1f - a.daylight * 0.5f)
        if (nightLights > 0.02f) {
            for (hl in harbourLights) {
                val x = hl[0] + f.shiftX(0.1f)
                val col = if (hl[2] > 0.5f) Color.rgb(255, 80, 70) else Color.rgb(255, 210, 140)
                for (k in 0 until 6) {
                    val ry = hy + 4f + k * 7f * f.u
                    val wob = sin(f.t * 3f + k + hl[3]) * 3f * f.u
                    paint.color = withAlpha(col, nightLights * 0.35f * (1f - k / 6f))
                    c.drawRect(x - 2f * f.u + wob, ry, x + 2f * f.u + wob, ry + 4f * f.u, paint)
                }
            }
        }
    }

    private fun glitterPath(c: Canvas, f: Frame, sx: Float, col: Int, vis: Float) {
        val hy = f.horizonY
        val sea = seaH(f)
        for (i in 0 until 60) {
            val t = ((i * 0.6180339f) % 1f)
            val y = hy + sea * t * t
            val width = f.w * (0.01f + 0.12f * t)
            val shimmer = 0.5f + 0.5f * sin(f.t * (2f + i % 5) + i * 3.7f)
            val x = sx + sin(i * 12.9898f) * width
            paint.color = withAlpha(col, vis * shimmer * 0.55f)
            rect.set(x - width * 0.3f, y - 1.5f * f.u, x + width * 0.3f, y + 1.5f * f.u * (1f + t * 2f))
            c.drawOval(rect, paint)
        }
    }

    /** Orographic cloud pouring over the table top, burning off by midday. */
    private fun drawTablecloth(c: Canvas, f: Frame) {
        val a = f.atmos
        val amount = max(hourWindow(5.2f, 6.8f, 10f, 12.3f, a.hour), a.windAbs() * 0.45f * (1f - a.night * 0.3f)) * (1f - a.precip)
        if (amount < 0.02f) return
        val top = tableTop(f) + f.shiftY(0.1f)
        val dx = f.shiftX(0.1f)
        val col = multiply(Color.rgb(246, 248, 250), lerpColor(a.lit(), Color.WHITE, 0.2f))
        val l = f.w * (tableL - 0.03f) + dx
        val r = f.w * (tableR + 0.04f) + dx
        // Billowing blanket on top.
        for (i in 0 until 22) {
            val t = i / 21f
            val x = lerp(l, r, t) + sin(f.t * 0.2f + i) * 8f * f.u
            val y = top - f.h * 0.006f + sin(i * 2.1f + f.t * 0.15f) * f.h * 0.004f
            val rad = f.w * (0.045f + 0.02f * sin(i * 1.3f))
            glow(c, x, y, rad, col, 0.75f * amount)
        }
        // Streamers pouring down the face and evaporating.
        for (i in 0 until 16) {
            val t = (i + 0.5f) / 16f
            val x = lerp(l + f.w * 0.02f, r - f.w * 0.02f, t)
            val flow = (f.t * 0.12f + i * 0.37f) % 1f
            val len = f.h * (0.04f + 0.05f * flow)
            paint.alpha = 255
            paint.shader = LinearGradient(0f, top, 0f, top + len, withAlpha(col, 0.7f * amount), 0, Shader.TileMode.CLAMP)
            rect.set(x - f.w * 0.025f, top - f.h * 0.004f, x + f.w * 0.025f, top + len)
            c.drawOval(rect, paint)
        }
        paint.shader = null
    }

    override fun drawForeground(c: Canvas, f: Frame) {
        // Foam washing around the rocks — bioluminescent blue at night.
        val a = f.atmos
        val bio = smoothstep(0.5f, 0.9f, a.night)
        val foam = multiply(Color.WHITE, lerpColor(a.lit(), Color.WHITE, 0.3f))
        stroke.strokeWidth = 4f * f.u
        val dx = f.shiftX(0.7f)
        for (k in 0 until 3) {
            val y0 = f.h * (0.9f + k * 0.03f)
            val surge = sin(f.t * 0.8f + k * 2f)
            path.reset()
            var x = -20f
            path.moveTo(x + dx, y0)
            while (x < f.w + 20f) {
                x += f.w / 24f
                path.lineTo(x + dx, y0 + sin(x * 0.03f + f.t * 1.2f + k) * 6f * f.u - surge * 8f * f.u)
            }
            stroke.color = withAlpha(foam, 0.45f - k * 0.1f)
            c.drawPath(path, stroke)
            if (bio > 0.02f) {
                stroke.color = withAlpha(Color.rgb(60, 220, 255), bio * (0.45f + 0.35f * surge) * f.theme.bloom)
                stroke.strokeWidth = 7f * f.u
                c.drawPath(path, stroke)
                stroke.strokeWidth = 4f * f.u
            }
        }
    }

    override fun drawEmissive(c: Canvas, f: Frame) {
        val a = f.atmos
        val on = clamp01(smoothstep(0.2f, 0.6f, a.night) + a.storm * 0.6f)
        if (on < 0.02f) return
        // City lights at the foot of the mountain.
        val dx = f.shiftX(0.1f)
        val cols = intArrayOf(Color.rgb(255, 214, 150), Color.rgb(255, 240, 210), Color.rgb(255, 180, 110))
        for (cl in cityLights) {
            val tw = 0.75f + 0.25f * sin(f.t * 1.3f + cl[3])
            paint.color = withAlpha(cols[cl[2].toInt()], on * tw)
            c.drawCircle(cl[0] + dx, cl[1], 1.6f * f.u, paint)
        }
        for (hl in harbourLights) {
            val col = if (hl[2] > 0.5f) Color.rgb(255, 70, 60) else Color.rgb(255, 214, 150)
            val blink = if (hl[2] > 0.5f) (if (sin(f.t * 2f + hl[3]) > 0f) 1f else 0.2f) else 1f
            glow(c, hl[0] + dx, hl[1], 7f * f.u, col, on * blink * 0.8f)
        }
        // Sweeping lighthouse beam.
        val lx = f.w * lighthouseX + f.shiftX(0.6f)
        val ly = f.h * 0.76f - f.h * 0.12f - f.h * 0.0125f + f.shiftY(0.6f)
        val ang = f.t * 0.9f
        val toward = cos(ang)
        val reach = sin(ang) * f.w * 1.1f
        val spread = f.h * 0.03f
        paint.alpha = 255
        paint.shader = LinearGradient(lx, ly, lx + reach, ly, withAlpha(Color.rgb(255, 245, 200), 0.5f * on * f.theme.bloom), 0, Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(lx, ly - 2f)
        path.lineTo(lx + reach, ly - spread * (0.4f + abs(toward)))
        path.lineTo(lx + reach, ly + spread * (0.4f + abs(toward)))
        path.lineTo(lx, ly + 2f)
        path.close()
        c.drawPath(path, paint)
        paint.shader = null
        val face = smoothstep(0.6f, 1f, toward)
        glow(c, lx, ly, f.w * (0.02f + 0.08f * face), Color.rgb(255, 240, 200), on * (0.5f + 0.5f * face))
    }

    // ---- Wildlife ---------------------------------------------------------------------

    /** Southern right whale: spouts, then an occasional slow, majestic breach. */
    private inner class Whale : Actor(Activity.ANY, 0.25f) {
        private var timer = 8f
        private var t = -1f
        private var x = 0f
        private val dur = 7f
        override val fadeRate = 1f
        override fun update(f: Frame, active: Boolean) {
            if (t < 0f) {
                timer -= f.dt
                if (timer <= 0f && active) { t = 0f; x = rng.range(0.25f, 0.7f) }
            } else {
                t += f.dt
                if (t > dur) { t = -1f; timer = rng.range(35f, 80f) }
            }
        }
        override fun draw(c: Canvas, f: Frame) {
            if (t < 0f) return
            val sea = seaH(f)
            val wx = f.w * x + f.shiftX(depth)
            val wy = f.horizonY + sea * 0.06f
            val s = f.w * 0.22f
            val col = f.animal(Color.rgb(40, 44, 52))
            val p = Scenes.paint
            val k = t / dur
            // 0-0.25 spout, 0.3-0.7 breach arc, then splash.
            if (k < 0.25f) {
                val spout = sin(k / 0.25f * Math.PI.toFloat())
                glow(c, wx, wy - s * 0.25f * spout, s * 0.12f, multiply(Color.WHITE, f.atmos.lit()), 0.7f * spout)
                p.color = col
                rect.set(wx - s * 0.25f, wy - s * 0.03f, wx + s * 0.25f, wy + s * 0.02f)
                c.drawOval(rect, p)
            } else if (k < 0.72f) {
                val b = (k - 0.25f) / 0.47f
                val rise = sin(b * Math.PI.toFloat())
                c.save()
                c.clipRect(0f, 0f, f.w.toFloat(), wy + 2f)
                c.translate(wx + (b - 0.5f) * s * 0.4f, wy + s * 0.55f - rise * s * 0.85f)
                c.rotate(-80f + b * 70f)
                p.color = col
                rect.set(-s * 0.5f, -s * 0.12f, s * 0.5f, s * 0.12f)
                c.drawOval(rect, p)
                // Long pectoral fin and fluke.
                path.reset(); path.moveTo(s * 0.15f, s * 0.05f); path.quadTo(s * 0.05f, s * 0.3f, -s * 0.1f, s * 0.32f); path.lineTo(s * 0.05f, s * 0.08f); path.close()
                c.drawPath(path, p)
                path.reset(); path.moveTo(-s * 0.48f, 0f); path.lineTo(-s * 0.65f, -s * 0.12f); path.lineTo(-s * 0.6f, 0f); path.lineTo(-s * 0.65f, s * 0.12f); path.close()
                c.drawPath(path, p)
                // Callosities on the head.
                p.color = f.animal(Color.rgb(200, 196, 180))
                c.drawCircle(s * 0.42f, -s * 0.06f, s * 0.018f, p)
                c.drawCircle(s * 0.36f, -s * 0.09f, s * 0.014f, p)
                c.restore()
            }
            if (k > 0.6f) {
                val sp = (k - 0.6f) / 0.4f
                val foam = multiply(Color.WHITE, lerpColor(f.atmos.lit(), Color.WHITE, 0.3f))
                for (i in 0 until 9) {
                    val ang = -Math.PI.toFloat() * (0.1f + 0.8f * i / 8f)
                    val r = s * 0.35f * sp
                    glow(c, wx + cos(ang) * r, wy + sin(ang) * r * 0.8f, s * 0.08f, foam, 0.8f * (1f - sp))
                }
            }
        }
    }

    /** A pod leaping in arcs near the rocks. */
    private inner class Dolphins : Actor(Activity.DAY, 0.45f) {
        private var timer = 4f
        private var t = -1f
        private var dir = 1f
        override fun update(f: Frame, active: Boolean) {
            if (t < 0f) { timer -= f.dt; if (timer <= 0f && active) { t = 0f; dir = rng.sign() } }
            else { t += f.dt; if (t > 9f) { t = -1f; timer = rng.range(15f, 40f) } }
        }
        override fun draw(c: Canvas, f: Frame) {
            if (t < 0f) return
            val baseY = f.horizonY + seaH(f) * 0.32f
            val s = f.w * 0.06f
            val col = f.animal(Color.rgb(90, 100, 112))
            for (i in 0 until 3) {
                val lt = t - i * 0.35f
                if (lt < 0f) continue
                val cycle = (lt % 1.6f) / 1.6f
                if (cycle > 0.6f) continue
                val jump = cycle / 0.6f
                val travel = (lt / 9f)
                val x = if (dir > 0) lerp(-0.1f, 1.1f, travel) else lerp(1.1f, -0.1f, travel)
                val px = x * f.w + jump * s * 2f * dir + f.shiftX(depth) + i * s * 0.6f
                val py = baseY + i * 6f * f.u - sin(jump * Math.PI.toFloat()) * s * 1.2f
                c.save()
                c.clipRect(0f, 0f, f.w.toFloat(), baseY + i * 6f * f.u)
                c.translate(px, py)
                c.scale(dir, 1f)
                c.rotate(-50f + jump * 100f)
                Scenes.paint.color = col
                rect.set(-s * 0.5f, -s * 0.1f, s * 0.5f, s * 0.1f)
                c.drawOval(rect, Scenes.paint)
                path.reset(); path.moveTo(0f, -s * 0.08f); path.lineTo(-s * 0.12f, -s * 0.25f); path.lineTo(-s * 0.15f, -s * 0.06f); path.close()
                c.drawPath(path, Scenes.paint)
                path.reset(); path.moveTo(s * 0.5f, 0f); path.lineTo(s * 0.68f, s * 0.02f); path.lineTo(s * 0.48f, s * 0.05f); path.close()
                c.drawPath(path, Scenes.paint)
                path.reset(); path.moveTo(-s * 0.48f, 0f); path.lineTo(-s * 0.62f, -s * 0.1f); path.lineTo(-s * 0.58f, 0f); path.lineTo(-s * 0.62f, s * 0.1f); path.close()
                c.drawPath(path, Scenes.paint)
                c.restore()
            }
        }
    }

    /** Cape fur seals basking; now and then one flops into the water and later hauls out again. */
    private inner class Seals : Actor(Activity.ANY, 0.65f) {
        private val pos = floatArrayOf(0.05f, 0.828f, 0.15f, 0.822f, 0.25f, 0.834f, 0.1f, 0.842f)
        private val state = FloatArray(4) // 0 on rock; >0 sliding progress; <0 in water timer
        private var timer = 10f
        override fun update(f: Frame, active: Boolean) {
            timer -= f.dt
            if (timer <= 0f) {
                timer = rng.range(8f, 20f)
                val i = rng.int(4)
                if (state[i] == 0f) state[i] = 0.001f
            }
            for (i in 0 until 4) {
                if (state[i] > 0f) {
                    state[i] += f.dt * 0.6f
                    if (state[i] >= 1f) {
                        state[i] = -rng.range(12f, 30f)
                        weather.spray(f.w * pos[i * 2] + f.shiftX(depth), f.h * 0.9f, 0.6f, f)
                    }
                } else if (state[i] < 0f) {
                    state[i] += f.dt
                    if (state[i] >= 0f) state[i] = 0f
                }
            }
        }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val col = f.animal(Color.rgb(84, 66, 52), 0.5f)
            for (i in 0 until 4) {
                if (state[i] < 0f) continue
                val slide = state[i]
                val x = f.w * pos[i * 2] + f.shiftX(depth) + slide * f.w * 0.04f
                val y = f.h * pos[i * 2 + 1] + slide * slide * f.h * 0.1f
                val s = f.w * 0.07f
                val breathe = sin(f.t * 0.8f + i) * 0.03f
                c.save(); c.translate(x, y); c.rotate(slide * 50f + (if (i % 2 == 0) -6f else 8f))
                p.color = col
                rect.set(-s * 0.5f, -s * (0.16f + breathe), s * 0.4f, s * 0.12f)
                c.drawOval(rect, p)
                // Head raised, flippers.
                val head = 0.5f + 0.5f * sin(f.t * 0.3f + i * 2f)
                c.drawCircle(s * 0.42f, -s * (0.12f + head * 0.12f), s * 0.11f, p)
                path.reset(); path.moveTo(-s * 0.45f, 0f); path.lineTo(-s * 0.68f, -s * 0.08f); path.lineTo(-s * 0.66f, s * 0.08f); path.close()
                c.drawPath(path, p)
                c.restore()
            }
        }
    }

    /** African penguins waddling about on the lighthouse rocks. */
    private inner class Penguins : Actor(Activity.DAY, 0.62f) {
        private val xs = floatArrayOf(0.74f, 0.79f, 0.92f)
        private val dirs = floatArrayOf(1f, -1f, 1f)
        override fun update(f: Frame, active: Boolean) {
            for (i in xs.indices) {
                xs[i] += dirs[i] * 0.006f * f.dt
                if (xs[i] > 0.97f || xs[i] < 0.7f || rng.float() < 0.08f * f.dt) dirs[i] = -dirs[i]
            }
        }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            for (i in xs.indices) {
                val x = f.w * xs[i] + f.shiftX(depth)
                val y = f.h * (0.775f + i * 0.008f)
                val h = f.w * 0.035f
                val wob = sin(f.t * 7f + i) * 8f
                c.save(); c.translate(x, y); c.rotate(wob); c.scale(dirs[i], 1f)
                p.color = f.animal(Color.rgb(28, 28, 32))
                rect.set(-h * 0.22f, -h, h * 0.22f, 0f); c.drawOval(rect, p)
                c.drawCircle(h * 0.04f, -h * 0.95f, h * 0.15f, p)
                p.color = f.animal(Color.rgb(236, 236, 230))
                rect.set(-h * 0.08f, -h * 0.75f, h * 0.2f, -h * 0.05f); c.drawOval(rect, p)
                p.color = f.animal(Color.rgb(240, 150, 140))
                c.drawCircle(h * 0.1f, -h * 0.98f, h * 0.035f, p)
                c.restore()
            }
        }
    }

    /** Cape gannets circling, folding and plunge-diving. */
    private inner class Gannets : Actor(Activity.DAY_CALM, 0.3f) {
        private val n = 3
        private val st = FloatArray(n) { -it * 4f }
        private val gx = FloatArray(n) { 0.3f + it * 0.2f }
        override fun update(f: Frame, active: Boolean) {
            for (i in 0 until n) {
                val prev = st[i]
                st[i] += f.dt
                if (st[i] > 10f) { st[i] = 0f; gx[i] = rng.range(0.15f, 0.85f) }
                if (prev < 9f && st[i] >= 9f) weather.spray(f.w * gx[i] + f.shiftX(depth), f.horizonY + seaH(f) * 0.15f, 0.3f, f)
            }
        }
        override fun draw(c: Canvas, f: Frame) {
            val col = f.animal(Color.rgb(240, 240, 236), 0.8f)
            val seaY = f.horizonY + seaH(f) * 0.15f
            for (i in 0 until n) {
                val s = st[i]
                if (s < 0f) continue
                val x0 = f.w * gx[i] + f.shiftX(depth)
                if (s < 7f) {
                    val ang = s * 0.9f
                    Wildlife.bird(c, Scenes.paint, x0 + cos(ang) * f.w * 0.06f, f.horizonY * 0.7f + sin(ang) * f.h * 0.015f, f.w * 0.05f,
                        sin(f.t * 5f + i) * 0.4f, if (sin(ang) > 0) -1f else 1f, col)
                } else if (s < 9f) {
                    val d = (s - 7f) / 2f
                    val y = lerp(f.horizonY * 0.7f, seaY, d * d)
                    Scenes.paint.color = col
                    path.reset(); path.moveTo(x0, y + f.w * 0.02f); path.lineTo(x0 - f.w * 0.008f, y - f.w * 0.02f); path.lineTo(x0 + f.w * 0.008f, y - f.w * 0.02f); path.close()
                    c.drawPath(path, Scenes.paint)
                }
            }
        }
    }

    /** Fishing boats and container ships creeping along the horizon. */
    private inner class Ship(private val container: Boolean, private val scale: Float, seed: Long) : Actor(Activity.ANY, 0.05f) {
        private val r = Rng(seed)
        private var x = r.range(0f, 1f)
        private val speed = if (container) 0.002f else 0.004f
        private val dir = if (container) -1f else 1f
        override val fadeRate = 1f
        override fun update(f: Frame, active: Boolean) {
            x += speed * dir * f.dt
            if (x < -0.2f) x = 1.2f
            if (x > 1.2f) x = -0.2f
        }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val sx = f.w * x + f.shiftX(depth)
            val y = f.horizonY + (if (container) 1f else seaH(f) * 0.05f)
            val s = f.w * 0.12f * scale
            val bob = sin(f.t * 1.5f + seedOff) * 1.5f * f.u
            p.color = f.tint(Color.rgb(46, 48, 56))
            if (container) {
                rect.set(sx - s, y - s * 0.08f + bob, sx + s, y + bob); c.drawRect(rect, p)
                val cc = intArrayOf(Color.rgb(170, 60, 50), Color.rgb(60, 100, 150), Color.rgb(200, 160, 60), Color.rgb(80, 130, 90))
                for (i in 0 until 8) {
                    p.color = f.tint(cc[i % 4])
                    rect.set(sx - s * 0.8f + i * s * 0.18f, y - s * 0.16f + bob, sx - s * 0.64f + i * s * 0.18f, y - s * 0.08f + bob); c.drawRect(rect, p)
                }
                p.color = f.tint(Color.rgb(220, 220, 214))
                rect.set(sx + s * 0.65f, y - s * 0.26f + bob, sx + s * 0.85f, y - s * 0.08f + bob); c.drawRect(rect, p)
            } else {
                path.reset(); path.moveTo(sx - s * 0.5f, y - s * 0.12f + bob); path.lineTo(sx + s * 0.6f, y - s * 0.12f + bob); path.lineTo(sx + s * 0.4f, y + bob); path.lineTo(sx - s * 0.45f, y + bob); path.close()
                p.color = f.tint(Color.rgb(36, 80, 130)); c.drawPath(path, p)
                p.color = f.tint(Color.rgb(230, 230, 226))
                rect.set(sx - s * 0.1f, y - s * 0.3f + bob, sx + s * 0.2f, y - s * 0.12f + bob); c.drawRect(rect, p)
                stroke.color = f.tint(Color.rgb(60, 60, 64)); stroke.strokeWidth = 1.5f * f.u
                c.drawLine(sx - s * 0.25f, y - s * 0.12f + bob, sx - s * 0.25f, y - s * 0.55f + bob, stroke)
            }
            val on = smoothstep(0.3f, 0.7f, f.atmos.night)
            if (on > 0.02f) {
                glow(c, sx + s * 0.3f * dir, y - s * 0.3f + bob, 6f * f.u, Color.rgb(255, 230, 170), on)
                glow(c, sx - s * 0.4f * dir, y - s * 0.12f + bob, 5f * f.u, if (dir > 0) Color.rgb(80, 255, 120) else Color.rgb(255, 70, 60), on)
            }
        }
        private val seedOff = r.float() * 6f
    }
}
