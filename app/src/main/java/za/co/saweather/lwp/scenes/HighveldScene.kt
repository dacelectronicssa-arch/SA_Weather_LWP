package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Horns
import za.co.saweather.lwp.engine.Quad
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Wildlife
import za.co.saweather.lwp.engine.lerpColor
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.pow

/** Scene 1 — flat golden grassland, kopjes, a red dirt road and telephone poles. Hero: the afternoon storm. */
class HighveldScene : Scene(
    SceneId.HIGHVELD,
    SceneConfig(
        starCount = 1300, lightPollution = 0.35f, milkyWayAllowed = true,
        stormStrength = 1f, precipStrength = 1f, windStrength = 1.2f, lightningStrength = 1.4f,
        morningMist = 0.2f, heatShimmer = true, dust = true, horizon = 0.6f,
        distantLightning = true, fireflies = 34,
    ),
) {
    private var hills: LandLayer? = null
    private var plain: LandLayer? = null

    override fun build(f: Frame) {
        val hy = f.horizonY
        val land = f.h - hy
        hills = layer(f, hy - f.h * 0.09f, hy + 4f, 0.12f) { l, r ->
            val far = ridge(l, r, hy - f.h * 0.012f, f.h * 0.012f, 3f, 11, hy + 4f)
            drawPath(far, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 146, 150) })
            // Kopjes: rounded granite outcrops with boulders.
            val r0 = Rng(5)
            val pk = Paint(Paint.ANTI_ALIAS_FLAG)
            for (k in 0 until 4) {
                val cx = l + (r - l) * (0.12f + k * 0.26f + r0.range(-0.05f, 0.05f))
                val kw = f.w * r0.range(0.12f, 0.22f)
                val kh = f.h * r0.range(0.035f, 0.07f)
                pk.color = Color.rgb(140, 124, 112)
                drawOval(RectF(cx - kw / 2, hy - kh, cx + kw / 2, hy + kh * 0.6f), pk)
                pk.color = Color.rgb(160, 142, 124)
                repeat(9) {
                    val bx = cx + r0.range(-0.4f, 0.4f) * kw
                    val by = hy - kh * r0.range(0.1f, 0.85f) * (1f - kotlin.math.abs(bx - cx) / kw * 1.6f).coerceAtLeast(0.2f)
                    drawCircle(bx, by, kh * r0.range(0.12f, 0.24f), pk)
                }
                pk.color = Color.rgb(96, 110, 70)
                repeat(6) { drawCircle(cx + r0.range(-0.45f, 0.45f) * kw, hy - kh * r0.range(0f, 0.3f), kh * 0.12f, pk) }
            }
        }

        plain = layer(f, hy - f.h * 0.06f, f.h.toFloat(), 0.3f) { l, r ->
            val ground = Path().apply { addRect(l, hy, r, f.h.toFloat(), Path.Direction.CW) }
            gradientFill(ground, hy, f.h.toFloat(), Color.rgb(206, 184, 136), Color.rgb(188, 142, 62))
            // Texture bands of grass.
            val r1 = Rng(77)
            val pb = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(70) {
                val y = hy + land * r1.float().pow(1.6f)
                val t = (y - hy) / land
                pb.color = lerpColor(Color.rgb(196, 170, 112), Color.rgb(170, 120, 52), t)
                pb.alpha = 90
                drawOval(RectF(r1.range(l, r), y - 2f - t * 8f, r1.range(l, r) + f.w * (0.1f + t * 0.4f), y + 2f + t * 8f), pb)
            }
            grassTufts(r1, l, r, hy + land * 0.1f, f.h.toFloat(), 900, f.h * 0.03f,
                intArrayOf(Color.rgb(222, 188, 104), Color.rgb(190, 146, 66), Color.rgb(168, 126, 60)), 2f * f.u)

            // The red dirt road, vanishing toward the horizon.
            val road = Path()
            val steps = 40
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val y = hy + land * t
                val cx = roadX(f, t)
                val hw = roadHalf(f, t)
                if (i == 0) road.moveTo(cx - hw, y) else road.lineTo(cx - hw, y)
            }
            for (i in steps downTo 0) {
                val t = i / steps.toFloat()
                road.lineTo(roadX(f, t) + roadHalf(f, t), hy + land * t)
            }
            road.close()
            gradientFill(road, hy, f.h.toFloat(), Color.rgb(170, 104, 74), Color.rgb(160, 74, 44))
            // Tyre ruts.
            val rut = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(130, 58, 36); strokeCap = Paint.Cap.ROUND }
            for (side in floatArrayOf(-0.45f, 0.45f)) {
                val pth = Path()
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = roadX(f, t) + roadHalf(f, t) * side
                    if (i == 0) pth.moveTo(x, hy + land * t) else pth.lineTo(x, hy + land * t)
                }
                rut.strokeWidth = 5f * f.u
                drawPath(pth, rut)
            }

            // Telephone poles and sagging wires.
            val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(74, 58, 46); strokeCap = Paint.Cap.ROUND }
            val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(160, 40, 34, 30) }
            var prevX = 0f; var prevTop = 0f
            val ts = floatArrayOf(0.02f, 0.045f, 0.08f, 0.14f, 0.24f, 0.4f, 0.66f, 1.05f)
            for ((i, t) in ts.withIndex()) {
                val y = hy + land * t
                val x = roadX(f, t) - roadHalf(f, t) * 1.7f - f.w * 0.02f
                val ph = f.h * (0.012f + 0.32f * t)
                pole.strokeWidth = (1.5f + 16f * t) * f.u
                drawLine(x, y, x, y - ph, pole)
                drawLine(x - ph * 0.12f, y - ph * 0.92f, x + ph * 0.12f, y - ph * 0.92f, pole)
                if (i > 0) {
                    wire.strokeWidth = (0.8f + 2.5f * t) * f.u
                    val sag = (y - ph * 0.92f - prevTop) * 0.2f + f.h * 0.012f * t
                    val w = Path().apply { moveTo(prevX, prevTop); quadTo((prevX + x) / 2, (prevTop + y - ph * 0.92f) / 2 + sag, x, y - ph * 0.92f) }
                    drawPath(w, wire)
                }
                prevX = x; prevTop = y - ph * 0.92f
            }
        }

        val lion = Quad(Color.rgb(196, 150, 86), bodyH = 0.36f, legLen = 0.42f, legW = 0.1f, neckLen = 0.2f, neckAngle = 18f,
            neckW = 0.2f, headLen = 0.26f, headH = 0.2f, ear = 0.03f, tailLen = 0.6f, tailDroop = 70f, mane = Color.rgb(120, 78, 40))
        actors += object : Walker(Activity.ANY, 0.85f, 3) {
            override val walkSpeed = 0.018f
            override val restRange = 25f..60f
            override val pauseChance = 0.03f
            override val hidesInStorm = true
            override fun extent(f: Frame) = f.w * 0.4f
            override fun update(f: Frame, active: Boolean) {
                super.update(f, active)
                stride = stride.coerceAtMost(0.3f) // a slow prowl, never a run
            }
            override fun draw(c: Canvas, f: Frame) {
                val y = f.h * 0.95f
                val s = f.w * 0.36f
                val xx = x + f.shiftX(depth)
                Scenes.shadow(c, f, xx, y, s)
                val col = f.animal(lion.color, 0.6f)
                Wildlife.quad(c, Scenes.paint, lion, xx, y, s, dir, gait * 0.7f, stride, col, graze = 0.25f, tail = sin(f.t * 0.7f) * 0.5f,
                    darker = lerpColor(col, Color.BLACK, 0.25f))
            }
        }

        // Springbok herd, pronking away before the storm.
        val springbok = Quad(Color.rgb(176, 120, 70), bodyH = 0.38f, legLen = 0.62f, legW = 0.06f, neckLen = 0.32f, neckAngle = 50f,
            neckW = 0.12f, headLen = 0.24f, headH = 0.12f, ear = 0.06f, tailLen = 0.2f, horns = Horns.LYRE, hornLen = 0.22f,
            belly = Color.rgb(240, 232, 220))
        val herd = (0 until 8).map { Herd.Member(it * 1.3f + rng.range(-0.3f, 0.3f), rng.range(0f, 0.05f), rng.range(0.85f, 1.1f), rng.range(0f, 6f)) }
        actors += Herd(Activity.DAY, 0.3f, 11, springbok, 0.07f, 0.045f, herd, 0.012f, pronk = true)

        // Snake crossing the dirt road.
        actors += object : Walker(Activity.DAY_CALM, 0.3f, 21) {
            override val walkSpeed = 0.01f
            override val restRange = 30f..90f
            override val pauseChance = 0f
            override val hidesInStorm = true
            override fun extent(f: Frame) = f.w * 0.1f
            override fun draw(c: Canvas, f: Frame) {
                val t = 0.42f
                val y = f.horizonY + (f.h - f.horizonY) * t
                Wildlife.snake(c, Scenes.paint, x + f.shiftX(depth), y, f.w * 0.09f, f.t * 5f, dir, 7f * f.u,
                    f.animal(Color.rgb(96, 84, 52)), f.animal(Color.rgb(60, 50, 34)))
            }
        }

        // Secretary bird stalking the grass.
        actors += object : Walker(Activity.DAY_CALM, 0.4f, 31) {
            override val walkSpeed = 0.01f
            override val restRange = 20f..50f
            override val pauseChance = 0.1f
            override fun extent(f: Frame) = f.w * 0.06f
            override fun draw(c: Canvas, f: Frame) {
                val y = f.horizonY + (f.h - f.horizonY) * 0.28f
                Wildlife.walkingBird(c, Scenes.paint, x + f.shiftX(depth), y, f.w * 0.075f, dir, gait, f.animal(Color.rgb(196, 196, 190)),
                    f.animal(Color.rgb(230, 150, 70)), true)
            }
        }

        // Vultures circling in a thermal.
        actors += object : Actor(Activity.DAY_CALM, 0.1f) {
            override val fadeRate = 0.3f
            override fun update(f: Frame, active: Boolean) {}
            override fun draw(c: Canvas, f: Frame) {
                for (i in 0 until 4) {
                    val ang = f.t * (0.18f + i * 0.02f) + i * 1.57f
                    val r = f.w * (0.08f + i * 0.025f)
                    val x = f.w * 0.3f + cos(ang) * r + f.shiftX(0.1f)
                    val y = f.horizonY * (0.3f + i * 0.03f) + sin(ang) * r * 0.25f
                    val span = f.w * (0.05f + 0.01f * sin(ang))
                    Wildlife.bird(c, Scenes.paint, x, y, span, 0.15f + sin(f.t * 0.5f + i) * 0.05f, if (sin(ang) > 0) -1f else 1f,
                        f.animal(Color.rgb(50, 44, 40), 1f))
                }
            }
        }
    }

    private fun roadX(f: Frame, t: Float) = f.w * (0.6f - 0.28f * t + 0.06f * sin(t * 3.2f) * t)
    private fun roadHalf(f: Frame, t: Float) = f.w * (0.004f + 0.3f * t.pow(1.15f))

    override fun drawWorld(c: Canvas, f: Frame) {
        hills?.draw(c, f)
        drawActors(c, f, 0f, 0.15f)
        plain?.draw(c, f)
        // Moonlit grassland: silver sheen on the grass.
        val moon = f.atmos.moonLight
        if (moon > 0.05f) {
            paint.color = Color.argb((moon * 45).toInt(), 200, 210, 235)
            c.drawRect(0f, f.horizonY, f.w.toFloat(), f.h.toFloat(), paint)
        }
        drawActors(c, f, 0.15f, 0.7f)
    }

    override fun drawForeground(c: Canvas, f: Frame) {
        drawActors(c, f, 0.7f, 1f)
        val tip = lerpColor(Color.rgb(236, 206, 120), Color.rgb(220, 226, 240), f.atmos.moonLight * 1.5f)
        drawLiveGrass(c, f, 4, 180, f.h * 0.88f, f.h * 0.07f, Color.rgb(184, 140, 60), tip, 0.95f)
    }
}
