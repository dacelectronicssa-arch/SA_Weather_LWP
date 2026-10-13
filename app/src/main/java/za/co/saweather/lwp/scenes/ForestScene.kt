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
import za.co.saweather.lwp.engine.Horns
import za.co.saweather.lwp.engine.Precip
import za.co.saweather.lwp.engine.Quad
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Weather
import za.co.saweather.lwp.engine.Wildlife
import za.co.saweather.lwp.engine.hourWindow
import za.co.saweather.lwp.engine.lerp
import za.co.saweather.lwp.engine.lerpColor
import za.co.saweather.lwp.engine.multiply
import za.co.saweather.lwp.engine.smoothstep
import za.co.saweather.lwp.engine.withAlpha
import kotlin.math.max
import kotlin.math.sin

/** Scene 4 — ancient yellowwood forest above a river gorge. Hero: soft misty rain. */
class ForestScene : Scene(
    SceneId.FOREST,
    SceneConfig(
        starCount = 350, lightPollution = 0f, milkyWayAllowed = false, precip = Precip.DRIZZLE,
        stormStrength = 0.55f, precipStrength = 0.85f, windStrength = 0.45f, lightningStrength = 0f,
        morningMist = 0.75f, baseMist = 0.1f, baseCloud = 0.3f, horizon = 0.44f,
        fireflies = 45, fallingLeaves = true,
    ),
) {
    private var ridges: LandLayer? = null
    private var gorge: LandLayer? = null
    private var banks: LandLayer? = null
    private var trunks: LandLayer? = null
    private val fungi = ArrayList<FloatArray>()
    private val drips = FloatArray(40 * 3) // x, y, speed
    private val dripSources = ArrayList<FloatArray>()
    private var riverLevel = 0f
    private val rect = RectF()

    override fun adjust(a: Atmosphere) {
        // Soft, constant drizzle instead of a thunderstorm; light drizzle some mornings too.
        a.precip = max(a.precip, max(a.storm * 0.9f, hourWindow(5f, 6.5f, 8.2f, 9.6f, a.hour) * 0.4f))
        a.mist = max(a.mist, a.precip * 0.65f)
        // Under the canopy nights are very dark.
        a.ambient = multiply(a.ambient, lerpColor(Color.WHITE, Color.rgb(150, 160, 170), a.night))
    }

    private fun riverHalf(f: Frame, t: Float) = f.w * (0.02f + 0.2f * t) * (1f + riverLevel * 0.25f)
    private fun riverX(f: Frame, t: Float) = f.w * (0.5f + 0.06f * sin(t * 4f) - 0.05f * t)

    override fun build(f: Frame) {
        val hy = f.horizonY
        ridges = layer(f, hy - f.h * 0.08f, hy + f.h * 0.02f, 0.08f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            pa.color = Color.rgb(110, 136, 132)
            drawPath(ridge(l, r, hy - f.h * 0.035f, f.h * 0.03f, 3f, 41, hy + f.h * 0.02f), pa)
            pa.color = Color.rgb(84, 116, 98)
            drawPath(ridge(l, r, hy - f.h * 0.012f, f.h * 0.025f, 5f, 42, hy + f.h * 0.02f), pa)
        }
        gorge = layer(f, hy - f.h * 0.06f, f.h.toFloat(), 0.25f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            val ground = Path().apply { addRect(l, hy, r, f.h.toFloat(), Path.Direction.CW) }
            gradientFill(ground, hy, f.h.toFloat(), Color.rgb(66, 96, 64), Color.rgb(40, 62, 38))
            // Gorge walls: forested slopes falling toward the river.
            val r0 = Rng(17)
            for (side in floatArrayOf(-1f, 1f)) {
                repeat(80) {
                    val t = r0.float()
                    val y = hy + (f.h - hy) * t * 0.7f
                    val edge = riverX(f, t) + side * (riverHalf(f, t) + f.w * (0.03f + r0.float() * 0.5f))
                    val s = f.w * (0.03f + 0.07f * t) * r0.range(0.7f, 1.3f)
                    pa.color = lerpColor(Color.rgb(46, 84, 54), Color.rgb(76, 110, 66), r0.float())
                    drawOval(RectF(edge - s, y - s * 1.4f, edge + s, y + s * 0.3f), pa)
                }
            }
        }
        banks = layer(f, f.h * 0.6f, f.h.toFloat(), 0.55f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            val r1 = Rng(23)
            // Moss-covered boulders scattered along both banks, each a lumpy cluster.
            val boulders = (0 until 16).map { r1.range(0.3f, 1f) }.sorted()
            for (t in boulders) {
                val side = r1.sign()
                val y = hy + (f.h - hy) * t
                val x = riverX(f, t) + side * (riverHalf(f, t) * 0.95f + f.w * r1.range(0f, 0.22f))
                val s = f.w * (0.02f + 0.07f * t) * r1.range(0.6f, 1.2f)
                pa.shader = LinearGradient(0f, y - s, 0f, y + s * 0.4f, Color.rgb(84, 92, 80), Color.rgb(44, 48, 44), Shader.TileMode.CLAMP)
                val dome = Path()
                val lumps = 2 + r1.int(3)
                repeat(lumps) {
                    val ox = r1.range(-0.6f, 0.6f) * s
                    val sw = s * r1.range(0.6f, 1f)
                    dome.addOval(RectF(x + ox - sw, y - sw * r1.range(0.8f, 1.1f), x + ox + sw, y + sw * 0.3f), Path.Direction.CW)
                }
                drawPath(dome, pa)
                // Moss blanket over the top of the rock.
                pa.shader = null
                save()
                clipPath(dome)
                pa.color = Color.rgb(92, 132, 56)
                drawOval(RectF(x - s * 2f, y - s * 1.6f, x + s * 1.6f, y - s * 0.35f), pa)
                pa.color = Color.argb(120, 150, 186, 90)
                drawOval(RectF(x - s * 1.2f, y - s * 1.4f, x + s * 0.6f, y - s * 0.7f), pa)
                restore()
            }
            pa.shader = null
            // A fallen log (the monitor lizard's basking spot).
            val lx = f.w * 0.12f; val ly = f.h * 0.79f
            pa.color = Color.rgb(96, 74, 56)
            drawRoundRect(RectF(lx - f.w * 0.16f, ly - f.h * 0.014f, lx + f.w * 0.2f, ly + f.h * 0.012f), 20f, 20f, pa)
            pa.color = Color.rgb(120, 150, 70)
            drawOval(RectF(lx - f.w * 0.1f, ly - f.h * 0.017f, lx + f.w * 0.05f, ly - f.h * 0.007f), pa)
            fungi += floatArrayOf(lx + f.w * 0.12f, ly - f.h * 0.012f, 0f)
            fungi += floatArrayOf(lx - f.w * 0.06f, ly + f.h * 0.008f, 1f)
            fungi += floatArrayOf(lx + f.w * 0.02f, ly + f.h * 0.01f, 2f)
            // Ferns.
            val fern = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            repeat(24) {
                val x = r1.range(l, r)
                val y = f.h * r1.range(0.72f, 0.98f)
                if (kotlin.math.abs(x - riverX(f, (y - hy) / (f.h - hy))) < riverHalf(f, (y - hy) / (f.h - hy))) return@repeat
                drawFern(this, x, y, f.w * r1.range(0.05f, 0.1f), fern, r1)
            }
        }
        trunks = layer(f, 0f, f.h.toFloat(), 0.85f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            val r2 = Rng(31)
            // Two great yellowwood trunks framing the view.
            for ((cx, w) in listOf(f.w * 0.05f to f.w * 0.16f, f.w * 0.97f to f.w * 0.13f)) {
                val trunk = Path().apply {
                    moveTo(cx - w, f.h.toFloat()); cubicTo(cx - w * 0.6f, f.h * 0.6f, cx - w * 0.5f, f.h * 0.3f, cx - w * 0.4f, 0f)
                    lineTo(cx + w * 0.4f, 0f); cubicTo(cx + w * 0.5f, f.h * 0.3f, cx + w * 0.6f, f.h * 0.6f, cx + w, f.h.toFloat()); close()
                }
                pa.shader = LinearGradient(cx - w, 0f, cx + w, 0f, intArrayOf(Color.rgb(60, 52, 44), Color.rgb(120, 104, 84), Color.rgb(70, 60, 50)), null, Shader.TileMode.CLAMP)
                drawPath(trunk, pa)
                pa.shader = null
                // Flaky bark and moss streaks.
                val bark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * f.u }
                repeat(60) {
                    val y = r2.range(0f, f.h.toFloat())
                    val x = cx + r2.range(-0.5f, 0.5f) * w * (0.5f + y / f.h * 0.5f)
                    bark.color = if (r2.chance(0.6f)) Color.argb(90, 40, 34, 28) else Color.argb(110, 110, 150, 70)
                    drawLine(x, y, x + r2.range(-3f, 3f) * f.u, y + r2.range(20f, 80f) * f.u, bark)
                }
                repeat(4) { fungi += floatArrayOf(cx + r2.range(-0.3f, 0.3f) * w, f.h * r2.range(0.6f, 0.9f), r2.int(3).toFloat()) }
            }
            // Canopy mass across the top, with an opening over the gorge: a dark backing,
            // then many small leaf clusters from dark (inside) to light (sunlit edges).
            fun canopyDepth(x: Float): Float {
                val gap = 1f - smoothstep(0.1f, 0.3f, kotlin.math.abs(x / f.w - 0.52f))
                return f.h * (0.24f - 0.2f * gap)
            }
            val backing = Path().apply {
                moveTo(l, 0f)
                var x = l
                while (x <= r) { lineTo(x, canopyDepth(x) * 0.8f); x += 10f }
                lineTo(r, 0f); close()
            }
            pa.color = Color.rgb(22, 44, 26)
            drawPath(backing, pa)
            for (pass in 0 until 3) {
                repeat(260) {
                    val x = r2.range(l, r)
                    val d = canopyDepth(x)
                    val y = r2.range(-0.02f * f.h, d)
                    val s = f.w * r2.range(0.02f, 0.05f) * (1.2f - pass * 0.2f)
                    val light = (y / d.coerceAtLeast(1f)) * 0.5f + pass * 0.25f
                    pa.color = lerpColor(Color.rgb(26, 54, 30), Color.rgb(96, 140, 64), light * r2.range(0.6f, 1f))
                    drawOval(RectF(x - s, y - s * 0.55f, x + s, y + s * 0.55f), pa)
                    if (pass == 2 && r2.chance(0.12f)) dripSources += floatArrayOf(x, y + s * 0.5f)
                }
            }
            // Hanging old-man's-beard lichen.
            val lichen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * f.u; color = Color.argb(160, 170, 190, 150) }
            repeat(30) {
                val x = r2.range(l, r)
                val y = f.h * r2.range(0.1f, 0.26f)
                drawLine(x, y, x + r2.range(-4f, 4f) * f.u, y + f.h * r2.range(0.02f, 0.06f), lichen)
            }
        }
        for (i in 0 until drips.size / 3) { drips[i * 3 + 1] = -1f }

        val bushbuck = Quad(Color.rgb(150, 90, 50), bodyH = 0.42f, legLen = 0.55f, legW = 0.065f, neckLen = 0.3f, neckAngle = 40f, neckW = 0.13f,
            headLen = 0.24f, headH = 0.13f, ear = 0.08f, tailLen = 0.18f, horns = Horns.TWISTED, hornLen = 0.2f, belly = Color.rgb(200, 170, 140))
        actors += object : Walker(Activity.DAY_CALM, 0.5f, 61) {
            override val walkSpeed = 0.006f
            override val pauseChance = 0.25f
            override val restRange = 20f..60f
            override fun extent(f: Frame) = f.w * 0.15f
            override fun draw(c: Canvas, f: Frame) {
                val t = 0.55f
                val y = f.horizonY + (f.h - f.horizonY) * t
                val s = f.w * 0.11f
                val graze = if (paused > 0f) 1f else 0f
                Wildlife.quad(c, Scenes.paint, bushbuck, x + f.shiftX(depth), y, s, dir, gait, stride, f.animal(bushbuck.color), graze,
                    darker = f.animal(lerpColor(bushbuck.color, Color.BLACK, 0.3f)))
            }
        }
        actors += Boomslang()
        actors += Loerie()
        actors += Monitor()
        actors += Frogs()
    }

    private fun drawFern(c: Canvas, x: Float, y: Float, s: Float, p: Paint, r: Rng) {
        for (k in 0 until 6) {
            val ang = -150f + k * 24f + r.range(-8f, 8f)
            val rad = Math.toRadians(ang.toDouble())
            val ex = x + (kotlin.math.cos(rad) * s).toFloat()
            val ey = y + (kotlin.math.sin(rad) * s).toFloat()
            p.color = lerpColor(Color.rgb(60, 120, 50), Color.rgb(100, 150, 60), r.float())
            p.strokeWidth = s * 0.04f
            val fp = Path().apply { moveTo(x, y); quadTo((x + ex) / 2, ey - s * 0.2f, ex, ey + s * 0.15f) }
            c.drawPath(fp, p)
            p.strokeWidth = s * 0.025f
            for (j in 1..8) {
                val t = j / 9f
                val px = lerp(x, ex, t); val py = lerp(y, ey, t) - s * 0.1f * sin(t * 3.14f)
                c.drawLine(px, py, px - s * 0.08f * (1 - t), py - s * 0.1f * (1 - t), p)
                c.drawLine(px, py, px + s * 0.08f * (1 - t), py + s * 0.04f * (1 - t), p)
            }
        }
    }

    override fun update(f: Frame, weather: Weather) {
        super.update(f, weather)
        val a = f.atmos
        // The river rises during rain and slowly drains afterwards.
        val target = a.precip
        riverLevel += (target - riverLevel) * f.dt * (if (target > riverLevel) 0.08f else 0.02f)
        // Drips keep falling from the leaves for a while after the rain.
        val wet = max(a.precip, a.clearing * 0.6f + a.mist * 0.2f)
        for (i in 0 until drips.size / 3) {
            val o = i * 3
            if (drips[o + 1] < 0f) {
                if (dripSources.isNotEmpty() && rng.float() < wet * 0.6f * f.dt) {
                    val s = dripSources[rng.int(dripSources.size)]
                    drips[o] = s[0]; drips[o + 1] = s[1]; drips[o + 2] = 0f
                }
            } else {
                drips[o + 2] += 1600f * f.u * f.dt
                drips[o + 1] += drips[o + 2] * f.dt
                if (drips[o + 1] > f.h) drips[o + 1] = -1f
            }
        }
    }

    override fun drawWorld(c: Canvas, f: Frame) {
        ridges?.draw(c, f)
        gorge?.draw(c, f)
        drawRiver(c, f)
        drawShafts(c, f)
        drawActors(c, f, 0f, 0.55f)
        banks?.draw(c, f)
        drawMoonDapples(c, f)
        drawActors(c, f, 0.55f, 0.8f)
    }

    private fun drawRiver(c: Canvas, f: Frame) {
        val a = f.atmos
        val hy = f.horizonY
        val dx = f.shiftX(0.35f)
        path.reset()
        val steps = 30
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = riverX(f, t) - riverHalf(f, t) + dx
            if (i == 0) path.moveTo(x, hy + (f.h - hy) * t) else path.lineTo(x, hy + (f.h - hy) * t)
        }
        for (i in steps downTo 0) {
            val t = i / steps.toFloat()
            path.lineTo(riverX(f, t) + riverHalf(f, t) + dx, hy + (f.h - hy) * t)
        }
        path.close()
        val top = lerpColor(a.skyHorizon, multiply(Color.rgb(90, 120, 110), a.lit()), 0.5f)
        val bottom = multiply(lerpColor(Color.rgb(40, 70, 60), Color.rgb(90, 76, 54), riverLevel * 0.6f), a.lit())
        paint.alpha = 255
        paint.shader = LinearGradient(0f, hy, 0f, f.h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
        // Flowing ripples, faster when in spate.
        c.save()
        c.clipPath(path)
        stroke.strokeWidth = 2f * f.u
        val speed = 0.12f + riverLevel * 0.25f
        for (i in 0 until 40) {
            val t = ((i * 0.61803f) + f.t * speed * (0.5f + (i % 3) * 0.2f)) % 1f
            val y = hy + (f.h - hy) * t
            val x = riverX(f, t) + dx + sin(i * 7.1f) * riverHalf(f, t) * 0.7f
            val len = riverHalf(f, t) * 0.25f
            stroke.color = withAlpha(lerpColor(a.lit(), Color.WHITE, 0.3f), 0.25f + riverLevel * 0.2f)
            c.drawLine(x - len, y, x + len, y + len * 0.1f, stroke)
        }
        c.restore()
    }

    /** Morning light shafts piercing the canopy. */
    private fun drawShafts(c: Canvas, f: Frame) {
        val a = f.atmos
        val k = hourWindow(6.3f, 7.5f, 10.5f, 12f, a.hour) * (1f - a.cloudCover * 0.7f) * (1f - a.precip)
        if (k < 0.02f) return
        val col = multiply(Color.rgb(255, 244, 200), a.lit())
        for (i in 0 until 5) {
            val x0 = f.w * (0.36f + i * 0.07f) + f.shiftX(0.5f)
            val pulse = 0.6f + 0.4f * sin(f.t * 0.3f + i * 1.7f)
            paint.alpha = 255
            paint.shader = LinearGradient(x0, f.h * 0.1f, x0 - f.w * 0.25f, f.h * 0.95f, withAlpha(col, 0.24f * k * pulse * f.theme.bloom), 0, Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo(x0, f.h * 0.12f)
            path.lineTo(x0 + f.w * 0.035f, f.h * 0.12f)
            path.lineTo(x0 - f.w * 0.15f, f.h.toFloat())
            path.lineTo(x0 - f.w * 0.28f, f.h.toFloat())
            path.close()
            c.drawPath(path, paint)
        }
        paint.shader = null
    }

    private fun drawMoonDapples(c: Canvas, f: Frame) {
        val m = f.atmos.moonLight
        if (m < 0.05f) return
        val r = Rng(88)
        for (i in 0 until 18) {
            val x = r.float() * f.w + f.shiftX(0.6f)
            val y = f.h * r.range(0.65f, 0.98f)
            val s = f.w * r.range(0.015f, 0.04f) * (0.8f + 0.2f * sin(f.t * 0.5f + i))
            paint.color = withAlpha(Color.rgb(200, 215, 255), m * 0.22f)
            rect.set(x - s * 1.6f, y - s * 0.5f, x + s * 1.6f, y + s * 0.5f)
            c.drawOval(rect, paint)
        }
    }

    override fun drawForeground(c: Canvas, f: Frame) {
        trunks?.draw(c, f)
        drawActors(c, f, 0.8f, 1f)
        // Drips from the leaves.
        stroke.strokeWidth = 2f * f.u
        stroke.color = withAlpha(lerpColor(f.atmos.lit(), Color.WHITE, 0.4f), 0.6f)
        val dx = f.shiftX(0.85f)
        for (i in 0 until drips.size / 3) {
            val o = i * 3
            if (drips[o + 1] < 0f) continue
            c.drawLine(drips[o] + dx, drips[o + 1], drips[o] + dx, drips[o + 1] - 10f * f.u, stroke)
        }
        drawLiveGrass(c, f, 9, 70, f.h * 0.94f, f.h * 0.05f, Color.rgb(60, 110, 50), Color.rgb(100, 150, 70), 0.95f)
    }

    override fun drawEmissive(c: Canvas, f: Frame) {
        val g = smoothstep(0.5f, 0.9f, f.atmos.night)
        if (g < 0.02f) return
        for ((i, fu) in fungi.withIndex()) {
            val depth = if (fu[1] > f.h * 0.75f && fu[0] < f.w * 0.4f && fu[0] > f.w * 0.0f) 0.55f else 0.85f
            val x = fu[0] + f.shiftX(depth)
            val pulse = 0.7f + 0.3f * sin(f.t * 0.6f + i)
            val col = if (fu[2] < 1f) Color.rgb(120, 255, 200) else if (fu[2] < 2f) Color.rgb(140, 220, 255) else Color.rgb(180, 255, 140)
            glow(c, x, fu[1], 22f * f.u, col, g * 0.5f * pulse * f.theme.bloom)
            paint.color = withAlpha(col, g * pulse)
            for (k in 0 until 3) c.drawCircle(x + (k - 1) * 5f * f.u, fu[1] - (k % 2) * 3f * f.u, 2.4f * f.u, paint)
        }
    }

    // ---- Wildlife ---------------------------------------------------------------------

    /** Boomslang gliding along a canopy branch. */
    private inner class Boomslang : Actor(Activity.DAY_CALM, 0.84f) {
        private var p = 0f
        private var dir = 1f
        override val fadeRate = 0.3f
        override fun update(f: Frame, active: Boolean) {
            p += dir * 0.025f * f.dt
            if (p > 1f || p < 0f) { dir = -dir; p = p.coerceIn(0f, 1f) }
        }
        override fun draw(c: Canvas, f: Frame) {
            val x = f.w * lerp(0.1f, 0.4f, p) + f.shiftX(depth)
            val y = f.h * lerp(0.22f, 0.18f, p)
            Wildlife.snake(c, Scenes.paint, x, y, f.w * 0.16f, f.t * 3f, dir, 5f * f.u, f.animal(Color.rgb(90, 170, 60)), f.animal(Color.rgb(60, 120, 40)))
        }
    }

    /** Knysna turaco crossing between trees — a flash of crimson flight feathers. */
    private inner class Loerie : Actor(Activity.DAY, 0.7f) {
        private var t = -1f
        private var timer = 6f
        private var dir = 1f
        override val fadeRate = 2f
        override fun update(f: Frame, active: Boolean) {
            if (t < 0f) { timer -= f.dt; if (timer < 0f && active) { t = 0f; dir = rng.sign() } }
            else { t += f.dt * 0.35f; if (t > 1f) { t = -1f; timer = rng.range(10f, 25f) } }
        }
        override fun draw(c: Canvas, f: Frame) {
            if (t < 0f) return
            val x = f.w * (if (dir > 0) lerp(0.1f, 0.92f, t) else lerp(0.92f, 0.1f, t)) + f.shiftX(depth)
            val y = f.h * (0.3f - sin(t * 3.14f) * 0.06f)
            val flap = sin(f.t * 14f)
            Wildlife.bird(c, Scenes.paint, x, y, f.w * 0.09f, flap, dir, f.animal(Color.rgb(40, 140, 60)), tailFan = true,
                accent = if (flap < 0f) f.animal(Color.rgb(220, 30, 40)) else null)
        }
    }

    /** Water monitor basking on the log, flicking its tongue. */
    private inner class Monitor : Actor(Activity.DAY_CALM, 0.56f) {
        override val hidesInStorm = true
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            val x = f.w * 0.13f + f.shiftX(depth)
            val y = f.h * 0.79f - f.h * 0.014f
            val s = f.w * 0.16f
            val col = f.animal(Color.rgb(70, 72, 50))
            p.color = col
            rect.set(x - s * 0.3f, y - s * 0.07f, x + s * 0.2f, y + s * 0.01f); c.drawOval(rect, p)
            rect.set(x + s * 0.15f, y - s * 0.06f, x + s * 0.35f, y - s * 0.01f); c.drawOval(rect, p)
            val leg = Wildlife.stroke(); leg.color = col
            leg.strokeWidth = s * 0.05f
            path.reset(); path.moveTo(x - s * 0.28f, y - s * 0.03f); path.quadTo(x - s * 0.5f, y, x - s * 0.65f, y + s * 0.03f + sin(f.t * 0.5f) * s * 0.02f)
            c.drawPath(path, leg)
            leg.strokeWidth = s * 0.03f
            c.drawLine(x - s * 0.15f, y - s * 0.02f, x - s * 0.2f, y + s * 0.04f, leg)
            c.drawLine(x + s * 0.1f, y - s * 0.02f, x + s * 0.15f, y + s * 0.04f, leg)
            p.color = f.animal(Color.rgb(200, 190, 110))
            for (i in 0 until 6) c.drawCircle(x - s * 0.22f + i * s * 0.07f, y - s * 0.04f, s * 0.012f, p)
            if (sin(f.t * 1.3f) > 0.85f) {
                leg.color = f.animal(Color.rgb(200, 60, 90)); leg.strokeWidth = s * 0.008f
                c.drawLine(x + s * 0.35f, y - s * 0.035f, x + s * 0.43f, y - s * 0.03f, leg)
            }
        }
    }

    /** Tree frogs on leaves at night, throats pulsing. */
    private inner class Frogs : Actor(Activity.NIGHT, 0.9f) {
        private val pos = floatArrayOf(0.3f, 0.93f, 0.72f, 0.9f, 0.86f, 0.96f)
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val p = Scenes.paint
            for (i in 0 until 3) {
                val x = f.w * pos[i * 2] + f.shiftX(depth)
                val y = f.h * pos[i * 2 + 1]
                val s = f.w * 0.03f
                p.color = f.tint(Color.rgb(110, 190, 90))
                rect.set(x - s * 0.5f, y - s * 0.35f, x + s * 0.5f, y + s * 0.2f); c.drawOval(rect, p)
                c.drawCircle(x - s * 0.2f, y - s * 0.38f, s * 0.14f, p)
                c.drawCircle(x + s * 0.2f, y - s * 0.38f, s * 0.14f, p)
                val croak = smoothstep(0.6f, 1f, sin(f.t * 3f + i * 2.1f))
                p.color = f.tint(Color.rgb(230, 220, 150))
                c.drawCircle(x, y + s * 0.05f, s * (0.08f + 0.18f * croak), p)
                p.color = Color.rgb(20, 20, 20)
                c.drawCircle(x - s * 0.2f, y - s * 0.4f, s * 0.06f, p)
                c.drawCircle(x + s * 0.2f, y - s * 0.4f, s * 0.06f, p)
            }
        }
    }
}
