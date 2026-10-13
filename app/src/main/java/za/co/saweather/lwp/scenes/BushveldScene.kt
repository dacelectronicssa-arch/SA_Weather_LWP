package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Horns
import za.co.saweather.lwp.engine.Quad
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Wildlife
import za.co.saweather.lwp.engine.lerp
import za.co.saweather.lwp.engine.lerpColor
import za.co.saweather.lwp.engine.multiply
import za.co.saweather.lwp.engine.smoothstep
import za.co.saweather.lwp.engine.withAlpha
import kotlin.math.abs
import kotlin.math.sin

/** Scene 3 — acacia savanna with a dry riverbed. Hero: golden-hour sunset in silhouette. */
class BushveldScene : Scene(
    SceneId.BUSHVELD,
    SceneConfig(
        starCount = 1700, lightPollution = 0.03f, milkyWayAllowed = true,
        stormStrength = 1f, precipStrength = 1f, windStrength = 1f, lightningStrength = 1f,
        morningMist = 0.28f, heatShimmer = true, dust = true, horizon = 0.6f, shootingStarRate = 1.3f,
    ),
) {
    private var hills: LandLayer? = null
    private var plain: LandLayer? = null
    private var river: LandLayer? = null
    private var tree: LandLayer? = null
    private val rect = RectF()

    private fun landT(f: Frame, y: Float) = (y - f.horizonY) / (f.h - f.horizonY)

    // Key positions (fractions of screen).
    private val poolX = 0.3f
    private val poolY = 0.775f
    private val branchY = 0.505f
    private val branchL = 0.56f
    private val branchR = 0.74f

    override fun build(f: Frame) {
        val hy = f.horizonY
        hills = layer(f, hy - f.h * 0.07f, hy + 4f, 0.1f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            pa.color = Color.rgb(140, 140, 160)
            drawPath(ridge(l, r, hy - f.h * 0.03f, f.h * 0.03f, 2.5f, 21, hy + 4f), pa)
            pa.color = Color.rgb(120, 124, 128)
            drawPath(ridge(l, r, hy - f.h * 0.01f, f.h * 0.018f, 4f, 22, hy + 4f), pa)
        }
        plain = layer(f, hy - f.h * 0.04f, f.h.toFloat(), 0.25f) { l, r ->
            val g = Path().apply { addRect(l, hy, r, f.h.toFloat(), Path.Direction.CW) }
            gradientFill(g, hy, f.h.toFloat(), Color.rgb(176, 160, 110), Color.rgb(150, 122, 70))
            val r0 = Rng(9)
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            // Distant umbrella thorns and bush clumps, smaller near the horizon.
            repeat(46) {
                val t = r0.float() * r0.float()
                val y = hy + (f.h - hy) * t * 0.6f + 2f
                val x = r0.range(l, r)
                val s = f.w * (0.015f + 0.09f * t)
                if (r0.chance(0.45f)) acacia(this, x, y, s, r0, Color.rgb(88, 92, 54), Color.rgb(70, 56, 44), pa)
                else {
                    pa.color = lerpColor(Color.rgb(110, 112, 66), Color.rgb(90, 96, 52), r0.float())
                    drawOval(RectF(x - s * 0.5f, y - s * 0.35f, x + s * 0.5f, y + s * 0.05f), pa)
                }
            }
            // Termite mounds.
            repeat(5) {
                val t = r0.range(0.15f, 0.55f)
                val y = hy + (f.h - hy) * t
                val x = r0.range(l, r)
                val mh = f.h * (0.02f + 0.08f * t)
                val mw = mh * 0.45f
                pa.shader = LinearGradient(x - mw, 0f, x + mw, 0f, Color.rgb(190, 110, 70), Color.rgb(140, 76, 50), Shader.TileMode.CLAMP)
                drawPath(Path().apply {
                    moveTo(x - mw, y); quadTo(x - mw * 0.6f, y - mh * 0.6f, x - mw * 0.12f, y - mh)
                    lineTo(x + mw * 0.1f, y - mh * 0.92f); quadTo(x + mw * 0.6f, y - mh * 0.5f, x + mw, y); close()
                }, pa)
                pa.shader = null
            }
            grassTufts(r0, l, r, hy + (f.h - hy) * 0.05f, f.h.toFloat(), 700, f.h * 0.025f,
                intArrayOf(Color.rgb(196, 172, 108), Color.rgb(160, 140, 80), Color.rgb(140, 120, 70)), 2f * f.u)
        }
        river = layer(f, f.h * 0.7f, f.h * 0.86f, 0.45f) { l, r ->
            val bed = Path()
            val top = { x: Float -> f.h * 0.735f + sin(x / f.w * 5f) * f.h * 0.012f }
            val bot = { x: Float -> f.h * 0.815f + sin(x / f.w * 4f + 1f) * f.h * 0.015f }
            bed.moveTo(l, top(l))
            var x = l
            while (x <= r) { bed.lineTo(x, top(x)); x += 8f }
            while (x >= l) { bed.lineTo(x, bot(x)); x -= 8f }
            bed.close()
            // Banks
            val bank = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f * f.u; color = Color.rgb(120, 86, 60) }
            drawPath(bed, bank)
            gradientFill(bed, f.h * 0.72f, f.h * 0.83f, Color.rgb(222, 200, 160), Color.rgb(206, 180, 140))
            val pebbles = Paint(Paint.ANTI_ALIAS_FLAG)
            val r1 = Rng(12)
            repeat(160) {
                val px = r1.range(l, r)
                val py = r1.range(top(px) + 6f, bot(px) - 4f)
                pebbles.color = lerpColor(Color.rgb(170, 150, 120), Color.rgb(236, 220, 190), r1.float())
                drawCircle(px, py, r1.range(1f, 3.5f) * f.u, pebbles)
            }
        }
        tree = layer(f, f.h * 0.36f, f.h.toFloat(), 0.75f) { _, _ ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            val bark = Color.rgb(70, 54, 44)
            pa.color = bark
            pa.style = Paint.Style.STROKE
            pa.strokeCap = Paint.Cap.ROUND
            fun limb(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) { pa.strokeWidth = w * f.u; drawLine(x0 * f.w, y0 * f.h, x1 * f.w, y1 * f.h, pa) }
            limb(0.87f, 1.02f, 0.8f, 0.6f, 74f)
            limb(0.8f, 0.62f, 0.7f, branchY + 0.005f, 34f)
            limb(0.72f, branchY + 0.006f, branchL, branchY - 0.004f, 24f)
            limb(0.8f, 0.62f, 0.95f, 0.47f, 32f)
            limb(0.8f, 0.6f, 0.82f, 0.44f, 26f)
            limb(0.7f, branchY, 0.6f, 0.43f, 16f)
            limb(0.56f, branchY - 0.004f, 0.46f, 0.435f, 12f)
            limb(0.95f, 0.47f, 1.06f, 0.43f, 18f)
            pa.style = Paint.Style.FILL
            // Flat-topped canopy built from layered leaf clusters.
            val r2 = Rng(4)
            for (layerI in 0 until 3) {
                repeat(120) {
                    val x = f.w * r2.range(0.36f, 1.16f)
                    val edge = 1f - kotlin.math.abs(x / f.w - 0.76f) / 0.42f
                    val y = f.h * (0.418f + layerI * 0.011f + r2.range(-0.006f, 0.01f) - edge * 0.012f)
                    pa.color = lerpColor(Color.rgb(54, 74, 34), Color.rgb(100, 116, 54), r2.float() * 0.45f + layerI * 0.22f)
                    drawOval(RectF(x - f.w * 0.055f, y - f.h * 0.011f, x + f.w * 0.055f, y + f.h * 0.011f), pa)
                }
            }
        }

        // --- Wildlife
        val elephant = Quad(Color.rgb(128, 120, 116), bodyH = 0.62f, legLen = 0.62f, legW = 0.2f, neckLen = 0.12f, neckAngle = 10f,
            neckW = 0.32f, headLen = 0.34f, headH = 0.34f, ear = 0.1f, tailLen = 0.4f, tailDroop = 80f, trunk = true, shoulderHump = 0.4f)
        actors += Herd(Activity.DAWN, 0.45f, 51, elephant, landT(f, f.h * 0.79f), 0.15f, listOf(
            Herd.Member(0f, 0f, 1.1f, 0f), Herd.Member(1.3f, 0.01f, 1f, 1.2f), Herd.Member(0.75f, 0.03f, 0.5f, 2.4f), Herd.Member(2.5f, -0.005f, 0.95f, 3.1f),
        ), 0.008f, grazes = false)

        val giraffe = Quad(Color.rgb(210, 160, 90), bodyH = 0.5f, legLen = 1.0f, legW = 0.07f, neckLen = 1.15f, neckAngle = 68f,
            neckW = 0.12f, headLen = 0.24f, headH = 0.12f, ear = 0.05f, tailLen = 0.5f, tailDroop = 75f, horns = Horns.SHORT, hornLen = 0.08f,
            spots = Color.rgb(130, 80, 40))
        actors += Herd(Activity.DAY, 0.25f, 52, giraffe, 0.07f, 0.04f, listOf(Herd.Member(0f, 0f, 1f, 0f), Herd.Member(1.6f, 0.01f, 0.85f, 1f)),
            0.004f, grazes = false)

        val impala = Quad(Color.rgb(184, 120, 70), bodyH = 0.38f, legLen = 0.6f, legW = 0.06f, neckLen = 0.32f, neckAngle = 48f, neckW = 0.12f,
            headLen = 0.22f, headH = 0.12f, ear = 0.07f, tailLen = 0.2f, horns = Horns.LYRE, hornLen = 0.26f, belly = Color.rgb(236, 226, 214))
        actors += object : Herd(Activity.DAY_CALM, 0.3f, 53, impala, 0.2f, 0.04f,
            (0 until 6).map { Herd.Member(it * 1.2f + rng.range(-0.3f, 0.3f), rng.range(-0.02f, 0.04f), rng.range(0.85f, 1.05f), rng.range(0f, 6f)) },
            0.006f, pronk = true) { override val pauseChance = 0.2f }

        val kudu = Quad(Color.rgb(140, 120, 104), bodyH = 0.44f, legLen = 0.62f, legW = 0.07f, neckLen = 0.36f, neckAngle = 45f, neckW = 0.15f,
            headLen = 0.26f, headH = 0.13f, ear = 0.09f, tailLen = 0.25f, horns = Horns.SPIRAL, hornLen = 0.45f)
        actors += object : Herd(Activity.DAY_CALM, 0.35f, 54, kudu, 0.3f, 0.055f,
            listOf(Herd.Member(0f, 0f, 1f, 0f), Herd.Member(1.5f, 0.02f, 0.9f, 2f)), 0.005f) {
            override val pauseChance = 0.25f
            override val restRange = 30f..70f
        }

        // Cheetah: a rare, fast sprint across open ground.
        val cheetah = Quad(Color.rgb(214, 170, 96), bodyH = 0.3f, legLen = 0.6f, legW = 0.055f, neckLen = 0.2f, neckAngle = 10f, neckW = 0.12f,
            headLen = 0.18f, headH = 0.12f, ear = 0.03f, tailLen = 0.7f, tailDroop = 20f, spots = Color.rgb(50, 36, 26))
        actors += object : Walker(Activity.DAY_CALM, 0.55f, 55) {
            override val walkSpeed = 0.35f
            override val restRange = 90f..240f
            override val pauseChance = 0f
            override fun extent(f: Frame) = f.w * 0.15f
            override fun update(f: Frame, active: Boolean) {
                super.update(f, active)
                if (onScreen) { stride = 1f; gait += f.dt * 10f }
            }
            override fun draw(c: Canvas, f: Frame) {
                val y = f.h * 0.86f
                val s = f.w * 0.13f
                val xx = x + f.shiftX(depth)
                Scenes.shadow(c, f, xx, y, s)
                Wildlife.quad(c, Scenes.paint, cheetah, xx, y - abs(sin(gait)) * s * 0.08f, s, dir, gait, 1f, f.animal(cheetah.color, 1f),
                    tail = 0.6f, darker = f.animal(lerpColor(cheetah.color, Color.BLACK, 0.3f), 1f))
            }
        }

        actors += Leopard()
        actors += Crocodile()
        actors += LionPride()
        actors += Hornbills()
        actors += Eyes()
    }

    private fun acacia(c: Canvas, x: Float, y: Float, s: Float, r: Rng, leaf: Int, bark: Int, pa: Paint) {
        pa.color = bark
        pa.strokeWidth = s * 0.06f
        pa.style = Paint.Style.STROKE
        c.drawLine(x, y, x - s * 0.05f, y - s * 0.55f, pa)
        c.drawLine(x - s * 0.05f, y - s * 0.5f, x - s * 0.3f, y - s * 0.7f, pa)
        c.drawLine(x - s * 0.05f, y - s * 0.5f, x + s * 0.25f, y - s * 0.72f, pa)
        pa.style = Paint.Style.FILL
        pa.color = leaf
        c.drawOval(RectF(x - s * 0.6f, y - s * 0.86f, x + s * 0.55f, y - s * 0.66f), pa)
        c.drawOval(RectF(x - s * 0.4f, y - s * 0.94f, x + s * 0.35f, y - s * 0.78f), pa)
    }

    override fun drawWorld(c: Canvas, f: Frame) {
        hills?.draw(c, f)
        plain?.draw(c, f)
        drawActors(c, f, 0f, 0.4f)
        river?.draw(c, f)
        drawPool(c, f)
        drawActors(c, f, 0.4f, 0.7f)
        tree?.draw(c, f)
    }

    override fun drawForeground(c: Canvas, f: Frame) {
        drawActors(c, f, 0.7f, 1f)
        drawLiveGrass(c, f, 6, 150, f.h * 0.9f, f.h * 0.06f, Color.rgb(170, 146, 84), Color.rgb(210, 186, 120), 0.9f)
    }

    private fun drawPool(c: Canvas, f: Frame) {
        val a = f.atmos
        val x = f.w * poolX + f.shiftX(0.45f)
        val y = f.h * poolY
        val grow = 1f + a.clearing * 0.4f + a.precip * 0.3f
        rect.set(x - f.w * 0.09f * grow, y - f.h * 0.008f * grow, x + f.w * 0.09f * grow, y + f.h * 0.012f * grow)
        paint.alpha = 255
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, lerpColor(a.skyHorizon, Color.rgb(60, 70, 60), 0.4f),
            multiply(Color.rgb(60, 74, 66), a.lit()), Shader.TileMode.CLAMP)
        c.drawOval(rect, paint)
        paint.shader = null
    }

    // ---- Wildlife ---------------------------------------------------------------------

    /** Leopard draped over the acacia branch with a slow tail flick. */
    private inner class Leopard : Actor(Activity.ANY, 0.75f) {
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val dx = f.shiftX(depth)
            val dy = f.shiftY(depth)
            val bx = f.w * (branchL + branchR) / 2 + dx
            val by = f.h * branchY + dy
            val s = f.w * 0.16f
            val col = f.animal(Color.rgb(212, 166, 92), 1f)
            p.color = col
            // Body lies along the branch, legs dangle either side.
            rect.set(bx - s * 0.5f, by - s * 0.2f, bx + s * 0.5f, by + s * 0.04f)
            c.drawOval(rect, p)
            val leg = Wildlife.stroke()
            leg.color = col
            leg.strokeWidth = s * 0.07f
            val sway = sin(f.t * 0.4f) * s * 0.02f
            c.drawLine(bx - s * 0.3f, by, bx - s * 0.32f + sway, by + s * 0.32f, leg)
            c.drawLine(bx + s * 0.32f, by, bx + s * 0.34f - sway, by + s * 0.3f, leg)
            c.drawLine(bx + s * 0.2f, by, bx + s * 0.22f, by + s * 0.22f, leg)
            // Head resting, occasional look-up.
            val look = smoothstep(0.7f, 1f, sin(f.t * 0.15f))
            c.drawCircle(bx + s * 0.55f, by - s * 0.1f - look * s * 0.08f, s * 0.11f, p)
            // Tail hangs and flicks.
            leg.strokeWidth = s * 0.045f
            path.reset()
            val flick = sin(f.t * 1.6f) * smoothstep(0.3f, 1f, sin(f.t * 0.37f))
            path.moveTo(bx - s * 0.48f, by - s * 0.05f)
            path.quadTo(bx - s * 0.6f, by + s * 0.3f, bx - s * 0.55f + flick * s * 0.15f, by + s * 0.6f)
            c.drawPath(path, leg)
            if (!f.theme.silhouettes && f.atmos.golden < 0.5f) {
                p.color = f.tint(Color.rgb(60, 40, 26))
                for (i in 0 until 12) c.drawCircle(bx - s * 0.4f + i * s * 0.07f, by - s * (0.06f + (i % 3) * 0.04f), s * 0.018f, p)
            }
            // Eyes catch the moonlight.
            if (f.atmos.isNight) glow(c, bx + s * 0.6f, by - s * 0.12f - look * s * 0.08f, s * 0.04f, Color.rgb(200, 255, 160), 0.8f)
        }
    }

    /** Crocodile basking on the bank that slides into the pool, leaving only its eyes above water. */
    private inner class Crocodile : Actor(Activity.DAY, 0.45f) {
        private var t = 0f
        private val cycle = 50f
        override fun update(f: Frame, active: Boolean) { t = (t + f.dt) % cycle }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val x0 = f.w * (poolX + 0.1f) + f.shiftX(depth)
            val y0 = f.h * (poolY - 0.012f)
            val s = f.w * 0.18f
            // 0-20 bask, 20-24 slide, 24-46 submerged, 46-50 haul out.
            val slide = when {
                t < 20f -> 0f
                t < 24f -> smoothstep(20f, 24f, t)
                t < 46f -> 1f
                else -> 1f - smoothstep(46f, 50f, t)
            }
            val x = x0 - slide * s * 0.6f
            val y = y0 + slide * f.h * 0.012f
            val col = f.animal(Color.rgb(84, 90, 60))
            if (slide < 0.95f) {
                c.save()
                c.clipRect(0f, 0f, f.w.toFloat(), y0 + f.h * 0.01f + (1f - slide) * f.h)
                p.color = col
                path.reset()
                path.moveTo(x - s * 0.5f, y)
                path.quadTo(x - s * 0.2f, y - s * 0.1f, x + s * 0.15f, y - s * 0.07f)
                path.quadTo(x + s * 0.35f, y - s * 0.05f, x + s * 0.55f, y)
                path.close()
                c.drawPath(path, p)
                // Scutes along the back.
                for (i in 0 until 10) c.drawCircle(x - s * 0.3f + i * s * 0.07f, y - s * 0.075f + abs(i - 5) * s * 0.004f, s * 0.012f, p)
                val leg = Wildlife.stroke(); leg.color = col; leg.strokeWidth = s * 0.03f
                c.drawLine(x - s * 0.15f, y - s * 0.02f, x - s * 0.22f, y + s * 0.03f, leg)
                c.drawLine(x + s * 0.2f, y - s * 0.02f, x + s * 0.26f, y + s * 0.03f, leg)
                c.restore()
            } else {
                p.color = col
                val ex = f.w * poolX + f.shiftX(depth) + sin(t * 0.1f) * f.w * 0.03f
                c.drawCircle(ex, f.h * poolY - 1f, s * 0.018f, p)
                c.drawCircle(ex + s * 0.05f, f.h * poolY - 1f, s * 0.018f, p)
                c.drawCircle(ex + s * 0.15f, f.h * poolY, s * 0.012f, p)
            }
        }
    }

    /** Pride resting in the acacia's shade; one stands up and yawns now and then. */
    private inner class LionPride : Actor(Activity.ANY, 0.8f) {
        private var t = 0f
        override val hidesInStorm = true
        override fun update(f: Frame, active: Boolean) { t += f.dt }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val dx = f.shiftX(depth)
            val gy = f.h * 0.93f
            val a = f.atmos
            // Shade pool under the canopy.
            val shade = a.daylight * (1f - a.cloudCover * 0.6f)
            if (shade > 0.05f) {
                p.color = withAlpha(Color.rgb(30, 24, 16), 0.25f * shade)
                rect.set(f.w * 0.52f + dx, gy - f.h * 0.03f, f.w * 1.1f + dx, gy + f.h * 0.03f)
                c.drawOval(rect, p)
            }
            val col = f.animal(Color.rgb(196, 152, 92), 1f)
            val mane = f.animal(Color.rgb(116, 76, 40), 1f)
            // Night with a bright moon: the pride is up and alert.
            val alert = if (a.isNight) a.moonIllum else 0f
            lying(c, f.w * 0.62f + dx, gy, f.w * 0.17f, col, null, 0.2f + alert)
            lying(c, f.w * 0.9f + dx, gy + f.h * 0.012f, f.w * 0.15f, col, null, alert)
            // The male: stands, yawns, lies back down every ~40 s.
            val cyc = t % 40f
            val stand = smoothstep(30f, 32f, cyc) * (1f - smoothstep(37f, 39f, cyc))
            val mx = f.w * 0.76f + dx
            if (stand > 0.5f) {
                val lion = Quad(col, bodyH = 0.36f, legLen = 0.42f, legW = 0.1f, neckLen = 0.2f, neckAngle = 25f + (cyc - 33f).coerceIn(0f, 2f) * 15f,
                    neckW = 0.2f, headLen = 0.26f, headH = 0.2f, ear = 0.03f, tailLen = 0.6f, tailDroop = 60f, mane = mane)
                Wildlife.quad(c, p, lion, mx, gy - f.h * 0.005f, f.w * 0.2f, -1f, 0f, 0f, col, darker = lerpColor(col, Color.BLACK, 0.25f))
                // Yawning mouth.
                val yawn = smoothstep(33f, 34f, cyc) * (1f - smoothstep(35.5f, 36.5f, cyc))
                if (yawn > 0.05f) {
                    p.color = f.animal(Color.rgb(90, 30, 30), 1f)
                    c.drawCircle(mx - f.w * 0.2f * 0.62f, gy - f.w * 0.2f * 0.9f, f.w * 0.012f * yawn, p)
                }
            } else {
                lying(c, mx, gy + f.h * 0.004f, f.w * 0.19f, col, mane, alert)
            }
        }

        private fun lying(c: Canvas, x: Float, y: Float, s: Float, col: Int, mane: Int?, headUp: Float) {
            val p = Scenes.paint
            p.color = col
            rect.set(x - s * 0.5f, y - s * 0.22f, x + s * 0.4f, y)
            c.drawOval(rect, p)
            val leg = Wildlife.stroke(); leg.color = col; leg.strokeWidth = s * 0.07f
            c.drawLine(x + s * 0.3f, y - s * 0.03f, x + s * 0.6f, y - s * 0.01f, leg)
            val hy = y - s * (0.2f + 0.08f * headUp.coerceIn(0f, 1f))
            if (mane != null) { p.color = mane; c.drawCircle(x + s * 0.38f, hy, s * 0.17f, p); p.color = col }
            c.drawCircle(x + s * 0.42f, hy, s * 0.11f, p)
            rect.set(x + s * 0.44f, hy - s * 0.02f, x + s * 0.6f, hy + s * 0.08f)
            c.drawOval(rect, p)
            leg.strokeWidth = s * 0.03f
            val tailT = sin(t * 0.8f + x)
            c.drawLine(x - s * 0.5f, y - s * 0.05f, x - s * 0.72f, y - s * 0.02f - tailT * s * 0.04f, leg)
        }
    }

    /** Hornbills hopping between acacia branches. */
    private inner class Hornbills : Actor(Activity.DAY_CALM, 0.76f) {
        private val spots = floatArrayOf(0.6f, 0.497f, 0.68f, 0.5f, 0.88f, 0.462f, 0.96f, 0.455f, 0.8f, 0.455f)
        private val at = intArrayOf(0, 2)
        private val hop = floatArrayOf(-1f, -1f)
        private val from = intArrayOf(0, 2)
        override fun update(f: Frame, active: Boolean) {
            for (i in 0 until 2) {
                if (hop[i] < 0f) {
                    if (rng.float() < 0.25f * f.dt) { from[i] = at[i]; at[i] = rng.int(5); hop[i] = 0f }
                } else {
                    hop[i] += f.dt * 1.4f
                    if (hop[i] >= 1f) hop[i] = -1f
                }
            }
        }
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            for (i in 0 until 2) {
                val k = if (hop[i] < 0f) 1f else hop[i]
                val ax = spots[from[i] * 2]; val ay = spots[from[i] * 2 + 1]
                val bx = spots[at[i] * 2]; val by = spots[at[i] * 2 + 1]
                val x = f.w * lerp(ax, bx, k) + f.shiftX(depth)
                val y = f.h * lerp(ay, by, k) - sin(k * Math.PI.toFloat()) * f.h * 0.02f + f.shiftY(depth)
                val s = f.w * 0.03f
                val face = if (bx >= ax) 1f else -1f
                c.save(); c.translate(x, y); c.scale(face, 1f)
                p.color = f.animal(Color.rgb(60, 56, 54), 1f)
                rect.set(-s * 0.5f, -s * 0.5f, s * 0.3f, 0f); c.drawOval(rect, p)
                c.drawCircle(s * 0.3f, -s * 0.55f, s * 0.18f, p)
                path.reset(); path.moveTo(-s * 0.4f, -s * 0.3f); path.lineTo(-s * 0.9f, -s * 0.1f); path.lineTo(-s * 0.4f, -s * 0.15f); path.close()
                c.drawPath(path, p)
                p.color = f.animal(Color.rgb(230, 180, 40), 1f)
                path.reset(); path.moveTo(s * 0.42f, -s * 0.62f); path.quadTo(s * 0.8f, -s * 0.6f, s * 0.95f, -s * 0.38f); path.lineTo(s * 0.42f, -s * 0.48f); path.close()
                c.drawPath(path, p)
                c.restore()
            }
        }
    }

    /** Eyes glowing in the dark grass — more of them under a full moon. */
    private inner class Eyes : Actor(Activity.NIGHT, 0.5f) {
        private val r = Rng(66)
        private val pos = FloatArray(16) { r.float() }
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val n = 2 + (f.atmos.moonIllum * 6).toInt()
            for (i in 0 until n) {
                val blink = smoothstep(0.2f, 0.5f, sin(f.t * 0.3f + i * 2.3f))
                if (blink < 0.05f) continue
                val x = f.w * pos[i * 2] + f.shiftX(depth)
                val y = f.horizonY + (f.h - f.horizonY) * (0.15f + pos[i * 2 + 1] * 0.5f)
                val sep = (4f + pos[i * 2 + 1] * 8f) * f.u
                val col = if (i % 3 == 0) Color.rgb(255, 210, 120) else Color.rgb(170, 255, 140)
                glow(c, x, y, sep * 1.2f, col, blink * 0.9f)
                glow(c, x + sep, y, sep * 1.2f, col, blink * 0.9f)
            }
        }
    }
}
