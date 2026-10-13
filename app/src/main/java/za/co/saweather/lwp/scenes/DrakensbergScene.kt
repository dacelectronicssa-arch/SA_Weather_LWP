package za.co.saweather.lwp.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import za.co.saweather.lwp.SceneId
import za.co.saweather.lwp.engine.Frame
import za.co.saweather.lwp.engine.Horns
import za.co.saweather.lwp.engine.Precip
import za.co.saweather.lwp.engine.Quad
import za.co.saweather.lwp.engine.Rng
import za.co.saweather.lwp.engine.SceneConfig
import za.co.saweather.lwp.engine.Wildlife
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

/** Scene 5 — snow-capped peaks, basalt cliffs and a waterfall. Hero: snow storm, then the clearest night sky. */
class DrakensbergScene : Scene(
    SceneId.DRAKENSBERG,
    SceneConfig(
        starCount = 2300, lightPollution = 0f, milkyWayAllowed = true, precip = Precip.SNOW,
        stormStrength = 1f, precipStrength = 1f, windStrength = 1.5f, lightningStrength = 0.5f,
        morningMist = 0.35f, horizon = 0.56f, shootingStarRate = 2.6f,
    ),
) {
    private var peaks: LandLayer? = null
    private var snow: LandLayer? = null
    private var cliffs: LandLayer? = null
    private var foothills: LandLayer? = null
    private var rocks: LandLayer? = null
    private val silverPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = LightingColorFilter(0xC8D4F4, 0x101420) }
    private val rect = RectF()

    private val fallX = 0.42f
    private fun cliffTop(f: Frame) = f.horizonY - f.h * 0.065f
    private fun cliffBase(f: Frame) = f.horizonY + f.h * 0.05f

    override fun build(f: Frame) {
        val hy = f.horizonY
        val top = hy - f.h * 0.24f
        val bottom = hy - f.h * 0.02f
        var peakPath: Path? = null
        peaks = layer(f, top, bottom, 0.07f) { l, r ->
            val p = ridge(l, r, hy - f.h * 0.12f, f.h * 0.1f, 3.2f, 51, bottom, octaves = 6, sharp = true)
            peakPath = p
            gradientFill(p, top, bottom, Color.rgb(112, 118, 140), Color.rgb(150, 156, 172))
            // Rock striations.
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * f.u; color = Color.argb(50, 40, 44, 56) }
            save(); clipPath(p)
            val r0 = Rng(3)
            repeat(80) {
                val x = r0.range(l, r); val y = r0.range(top, bottom)
                drawLine(x, y, x + r0.range(-20f, 20f) * f.u, y + r0.range(10f, 40f) * f.u, st)
            }
            restore()
        }
        snow = layer(f, top, bottom, 0.07f) { _, _ ->
            val p = peakPath ?: return@layer
            save(); clipPath(p)
            val sn = Paint(Paint.ANTI_ALIAS_FLAG)
            val snowline = hy - f.h * 0.15f
            sn.shader = LinearGradient(0f, top, 0f, snowline + f.h * 0.03f, intArrayOf(Color.WHITE, Color.rgb(236, 240, 250), Color.argb(0, 230, 236, 250)),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
            drawRect(-f.w.toFloat(), top, f.w * 2f, snowline + f.h * 0.03f, sn)
            restore()
        }
        cliffs = layer(f, cliffTop(f) - f.h * 0.02f, cliffBase(f) + f.h * 0.09f, 0.18f) { l, r ->
            val ct = cliffTop(f); val cb = cliffBase(f)
            val wall = Path()
            wall.moveTo(l, cb + f.h * 0.09f)
            var x = l
            while (x <= r) {
                val y = ct + abs(fbmLocal(x / f.w * 6f)) * f.h * 0.012f + (if (abs(x / f.w - 0.8f) < 0.12f) f.h * 0.03f * (1f - abs(x / f.w - 0.8f) / 0.12f) else 0f)
                wall.lineTo(x, y); x += 6f
            }
            wall.lineTo(r, cb + f.h * 0.09f); wall.close()
            gradientFill(wall, ct, cb, Color.rgb(96, 84, 78), Color.rgb(64, 58, 56))
            // Vertical basalt columns.
            val col = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            save(); clipPath(wall)
            val r2 = Rng(6)
            var cx = l
            while (cx < r) {
                col.strokeWidth = r2.range(1f, 3f) * f.u
                col.color = if (r2.chance(0.5f)) Color.argb(80, 30, 26, 26) else Color.argb(60, 150, 130, 116)
                drawLine(cx, ct, cx + r2.range(-3f, 3f) * f.u, cb, col)
                cx += r2.range(6f, 18f) * f.u
            }
            // Green ledge at the base.
            col.style = Paint.Style.FILL
            col.shader = LinearGradient(0f, cb - f.h * 0.03f, 0f, cb + f.h * 0.02f, Color.argb(0, 110, 120, 70), Color.rgb(110, 118, 70), Shader.TileMode.CLAMP)
            drawRect(l, cb - f.h * 0.03f, r, cb + f.h * 0.1f, col)
            restore()
        }
        foothills = layer(f, hy - f.h * 0.01f, f.h.toFloat(), 0.4f) { l, r ->
            val far = ridge(l, r, hy + f.h * 0.07f, f.h * 0.04f, 2.2f, 61, f.h.toFloat(), octaves = 3)
            gradientFill(far, hy, f.h * 0.8f, Color.rgb(196, 170, 104), Color.rgb(150, 134, 70))
            val near = ridge(l, r, hy + f.h * 0.17f, f.h * 0.05f, 1.6f, 62, f.h.toFloat(), octaves = 3)
            gradientFill(near, hy + f.h * 0.1f, f.h.toFloat(), Color.rgb(214, 180, 100), Color.rgb(140, 120, 60))
            val r3 = Rng(8)
            grassTufts(r3, l, r, hy + f.h * 0.16f, f.h.toFloat(), 500, f.h * 0.02f,
                intArrayOf(Color.rgb(220, 190, 110), Color.rgb(180, 150, 80), Color.rgb(150, 130, 70)), 2f * f.u)
            // Scattered sandstone boulders.
            val pb = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(18) {
                val x = r3.range(l, r); val y = f.h * r3.range(0.7f, 0.9f); val s = f.w * r3.range(0.01f, 0.03f)
                pb.color = lerpColor(Color.rgb(170, 140, 110), Color.rgb(130, 110, 90), r3.float())
                drawOval(RectF(x - s, y - s * 0.7f, x + s, y + s * 0.2f), pb)
            }
        }
        rocks = layer(f, f.h * 0.8f, f.h.toFloat(), 0.75f) { l, r ->
            val pa = Paint(Paint.ANTI_ALIAS_FLAG)
            pa.shader = LinearGradient(0f, f.h * 0.84f, 0f, f.h.toFloat(), Color.rgb(176, 150, 120), Color.rgb(100, 84, 70), Shader.TileMode.CLAMP)
            drawPath(Path().apply {
                moveTo(f.w * 0.5f, f.h.toFloat()); quadTo(f.w * 0.55f, f.h * 0.88f, f.w * 0.72f, f.h * 0.865f)
                quadTo(f.w * 0.95f, f.h * 0.85f, r, f.h * 0.87f); lineTo(r, f.h.toFloat()); close()
            }, pa)
            drawPath(Path().apply {
                moveTo(l, f.h * 0.92f); quadTo(f.w * 0.1f, f.h * 0.9f, f.w * 0.25f, f.h * 0.94f); lineTo(f.w * 0.3f, f.h.toFloat()); lineTo(l, f.h.toFloat()); close()
            }, pa)
            pa.shader = null
            pa.color = Color.argb(90, 120, 140, 90)
            drawOval(RectF(f.w * 0.6f, f.h * 0.87f, f.w * 0.78f, f.h * 0.885f), pa)
        }

        // --- Wildlife
        val eland = Quad(Color.rgb(176, 136, 96), bodyH = 0.52f, legLen = 0.55f, legW = 0.09f, neckLen = 0.28f, neckAngle = 30f, neckW = 0.2f,
            headLen = 0.26f, headH = 0.14f, ear = 0.06f, tailLen = 0.3f, horns = Horns.TWISTED, hornLen = 0.32f, shoulderHump = 0.5f)
        actors += Herd(Activity.DAY_CALM, 0.4f, 71, eland, 0.2f, 0.06f,
            (0 until 5).map { Herd.Member(it * 1.25f + rng.range(-0.3f, 0.3f), rng.range(-0.01f, 0.04f), rng.range(0.85f, 1.05f), rng.range(0f, 6f)) },
            0.005f, silhouetteBias = 0.5f)

        val leopard = Quad(Color.rgb(200, 160, 100), bodyH = 0.32f, legLen = 0.42f, legW = 0.07f, neckLen = 0.2f, neckAngle = 15f, neckW = 0.13f,
            headLen = 0.2f, headH = 0.14f, ear = 0.035f, tailLen = 0.75f, tailDroop = 40f, spots = Color.rgb(60, 40, 26))
        actors += object : Walker(Activity.TWILIGHT_AND_NIGHT, 0.42f, 72) {
            override val walkSpeed = 0.01f
            override val pauseChance = 0.12f
            override val restRange = 40f..100f
            override fun extent(f: Frame) = f.w * 0.12f
            override fun draw(c: Canvas, f: Frame) {
                // Picks its way along the foothill ridgeline.
                val xx = x + f.shiftX(depth)
                val y = ridgeY(xx + f.maxShift() * depth, f.w + 2 * f.maxShift() * depth, f.horizonY + f.h * 0.17f, f.h * 0.05f, 1.6f, 62, 3)
                val s = f.w * 0.09f
                Wildlife.quad(c, Scenes.paint, leopard, xx, y + 2f, s, dir, gait, stride.coerceAtMost(0.35f), f.animal(leopard.color),
                    graze = if (paused > 0f) 0.4f else 0.1f, tail = sin(f.t * 0.8f) * 0.4f, darker = f.animal(lerpColor(leopard.color, Color.BLACK, 0.3f)))
                if (f.atmos.isNight) {
                    val ex = xx + dir * s * 0.62f
                    glow(c, ex, y - s * 0.62f, s * 0.05f, Color.rgb(190, 255, 150), 0.8f)
                }
            }
        }
        actors += Lammergeier()
        actors += PuffAdder()
    }

    private fun fbmLocal(x: Float) = za.co.saweather.lwp.engine.fbm(x, 3, 77)

    override fun drawWorld(c: Canvas, f: Frame) {
        val a = f.atmos
        peaks?.draw(c, f)
        // Fresh snow after the storm lingers overnight and melts by the next afternoon.
        val snowAmt = 0.45f + 0.55f * max(hourWindow(15.8f, 17.2f, 32f, 37f, a.hour), a.precip)
        snow?.let { s ->
            val lp = f.layerPaint
            val old = lp.alpha
            lp.alpha = (snowAmt * 255).toInt()
            s.draw(c, f, lp)
            lp.alpha = old
            // Moonlight turns snow and rock faces silver.
            if (a.moonLight > 0.03f) {
                silverPaint.alpha = (a.moonLight * snowAmt * 150).toInt()
                s.draw(c, f, silverPaint)
            }
        }
        drawActors(c, f, 0f, 0.15f)
        cliffs?.draw(c, f)
        drawWaterfall(c, f)
        foothills?.draw(c, f)
        drawActors(c, f, 0.15f, 0.7f)
        rocks?.draw(c, f)
    }

    override fun drawForeground(c: Canvas, f: Frame) {
        drawActors(c, f, 0.7f, 1f)
        drawLiveGrass(c, f, 12, 90, f.h * 0.93f, f.h * 0.05f, Color.rgb(170, 140, 70), Color.rgb(214, 186, 110), 0.9f)
    }

    private fun drawWaterfall(c: Canvas, f: Frame) {
        val a = f.atmos
        val x = f.w * fallX + f.shiftX(0.18f)
        val top = cliffTop(f) + f.h * 0.006f
        val base = cliffBase(f)
        val flow = 0.45f + 0.8f * hourWindow(16.6f, 17.6f, 20f, 23.5f, a.hour) + a.precip * 0.2f
        val frozen = hourWindow(3.5f, 5.5f, 8f, 10.5f, a.hour) * 0.75f
        val w = f.w * 0.012f * (0.6f + flow * 0.6f)
        val water = multiply(lerpColor(Color.rgb(210, 226, 236), Color.rgb(236, 246, 255), frozen), a.lit())
        paint.alpha = 255
        paint.shader = LinearGradient(0f, top, 0f, base, withAlpha(water, 0.85f), withAlpha(water, 0.6f), Shader.TileMode.CLAMP)
        rect.set(x - w, top, x + w, base)
        c.drawRect(rect, paint)
        paint.shader = null
        // Falling streaks — slower when partly frozen.
        stroke.strokeWidth = 1.6f * f.u
        stroke.color = withAlpha(lerpColor(water, Color.WHITE, 0.4f), 0.8f)
        val speed = (1f - frozen * 0.8f) * (0.5f + flow * 0.5f)
        for (i in 0 until 10) {
            val t = ((f.t * speed * 0.6f + i * 0.137f) % 1f)
            val sx = x + (i - 4.5f) / 4.5f * w * 0.8f
            val y = top + (base - top) * t
            c.drawLine(sx, y, sx, y + (base - top) * 0.12f, stroke)
        }
        if (frozen > 0.05f) {
            // Icicles fringing the lip.
            paint.color = withAlpha(multiply(Color.rgb(230, 244, 255), a.lit()), frozen)
            for (i in -4..4) {
                val ix = x + i * w * 0.6f
                path.reset(); path.moveTo(ix - w * 0.25f, top); path.lineTo(ix + w * 0.25f, top); path.lineTo(ix, top + f.h * 0.012f * (1f + (i and 1))); path.close()
                c.drawPath(path, paint)
            }
        }
        // Spray cloud at the foot.
        glow(c, x, base, w * 6f * (0.6f + flow * 0.5f), multiply(Color.WHITE, a.lit()), 0.35f * (1f - frozen * 0.6f))
    }

    // ---- Wildlife ---------------------------------------------------------------------

    /** Bearded vulture: a huge wingspan riding the thermals along the escarpment. */
    private inner class Lammergeier : Actor(Activity.DAY_CALM, 0.12f) {
        override val fadeRate = 0.25f
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val t = f.t * 0.03f
            val x = f.w * (0.5f + 0.42f * sin(t * 1.3f)) + f.shiftX(depth)
            val y = f.horizonY * (0.42f + 0.1f * cos(t * 2.1f))
            val heading = cos(t * 1.3f)
            val span = f.w * (0.16f + 0.02f * sin(t * 5f))
            Wildlife.bird(c, Scenes.paint, x, y, span, 0.08f + 0.05f * sin(f.t * 0.7f), if (heading > 0) 1f else -1f,
                f.animal(Color.rgb(60, 50, 44), 1f), tailFan = true)
        }
    }

    /** Puff adder coiled on a sun-warmed rock. */
    private inner class PuffAdder : Actor(Activity.DAY, 0.75f) {
        override val hidesInStorm = true
        override val fadeRate = 0.3f
        override fun update(f: Frame, active: Boolean) {}
        override fun draw(c: Canvas, f: Frame) {
            val cx = f.w * 0.69f + f.shiftX(depth)
            val cy = f.h * 0.86f
            val s = f.w * 0.05f
            val body = f.animal(Color.rgb(150, 120, 70))
            val mark = f.animal(Color.rgb(60, 44, 30))
            val st = Wildlife.stroke()
            // Flattened coils (ellipses from outside in).
            for (k in 0 until 3) {
                val rr = s * (1f - k * 0.28f)
                st.color = body
                st.strokeWidth = s * 0.28f
                rect.set(cx - rr, cy - rr * 0.38f, cx + rr, cy + rr * 0.38f)
                c.drawOval(rect, st)
                // Chevron markings.
                st.color = mark
                st.strokeWidth = s * 0.06f
                for (i in 0 until 10) {
                    val ang = i / 10f * 6.283f + k
                    val mx = cx + cos(ang) * rr; val my = cy + sin(ang) * rr * 0.38f
                    c.drawLine(mx - s * 0.05f, my - s * 0.06f, mx, my, st)
                    c.drawLine(mx, my, mx + s * 0.05f, my - s * 0.06f, st)
                }
            }
            // Head raised, tongue flicking.
            Scenes.paint.color = body
            val hx = cx + s * 0.3f; val hy = cy - s * 0.32f
            rect.set(hx - s * 0.18f, hy - s * 0.12f, hx + s * 0.2f, hy + s * 0.08f)
            c.drawOval(rect, Scenes.paint)
            if (sin(f.t * 2.2f) > 0.8f) {
                st.color = f.animal(Color.rgb(40, 20, 30)); st.strokeWidth = s * 0.025f
                c.drawLine(hx + s * 0.2f, hy - s * 0.02f, hx + s * 0.34f, hy - s * 0.04f, st)
            }
        }
    }
}
