package za.co.saweather.lwp.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Layers 3 and 7: clouds, precipitation, lightning, dust, mist, fireflies, spray, leaves,
 * heat shimmer and rainbows. Particle state lives in flat arrays to keep the frame loop
 * allocation-free.
 */
class Weather {
    private val rng = Rng(9001)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private var builtFor = ""

    // ---- Clouds
    private val cloudSprites = ArrayList<Bitmap>()
    private var fogSprite: Bitmap? = null
    private class Cloud(var x: Float, val y: Float, val scale: Float, val speed: Float, val sprite: Int, val order: Float)
    private val clouds = ArrayList<Cloud>()
    private var lastCloudColor = 0
    private var cloudFilter = LightingColorFilter(Color.WHITE, 0)

    // ---- Precipitation: x, y, z(depth), seed
    private val maxDrops = 1400
    private val drops = FloatArray(maxDrops * 4)
    private val lineBuf = FloatArray(maxDrops * 4)

    // ---- Dust / spray / leaves / fireflies share a generic particle pool.
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val kind: Int, var rot: Float = 0f)
    private val particles = ArrayList<P>()
    private val fireflies = ArrayList<P>()

    // ---- Lightning
    private class Bolt(val segs: FloatArray, val count: Int, var life: Float, val maxLife: Float, val power: Float, val distant: Boolean, val sheet: Boolean)
    private val bolts = ArrayList<Bolt>()
    private var flash = 0f

    fun build(f: Frame, cfg: SceneConfig) {
        val key = "${f.w}x${f.h}:${f.scene}"
        if (key == builtFor) return
        builtFor = key
        if (cloudSprites.isEmpty()) {
            for (i in 0 until 5) cloudSprites.add(buildCloud(Rng(31L + i * 17)))
            fogSprite = buildFog()
        }
        clouds.clear()
        val n = 16
        for (i in 0 until n) {
            clouds.add(Cloud(
                x = rng.range(-0.3f, 1.3f) * f.w,
                y = rng.range(0.02f, 0.62f) * f.horizonY,
                scale = rng.range(0.55f, 1.25f) * f.w / 900f,
                speed = rng.range(6f, 16f) * f.u,
                sprite = rng.int(cloudSprites.size),
                order = i / n.toFloat(),
            ))
        }
        clouds.sortBy { it.scale }
        for (i in 0 until maxDrops) resetDrop(i, f, true)
        particles.clear()
        fireflies.clear()
        for (i in 0 until cfg.fireflies) {
            fireflies.add(P(rng.float() * f.w, f.horizonY + rng.float() * (f.h - f.horizonY), 0f, 0f, rng.float() * 10f, 10f, rng.range(2f, 4f) * f.u, 0))
        }
    }

    // =====================================================================================
    // Update
    // =====================================================================================

    fun update(f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        val dt = f.dt

        // Lightning: Poisson-distributed strikes during the storm, heat lightning at night.
        var rate = a.lightningRate
        if (cfg.distantLightning) rate += a.night * 0.12f * (1f - a.cloudCover)
        if (rate > 0f && rng.float() < rate * dt) spawnBolt(f, a.lightningRate < 0.05f)
        flash = max(0f, flash - dt * 4f)
        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.life -= dt
            if (b.life <= 0f) { bi.remove(); continue }
            val age = 1f - b.life / b.maxLife
            // Return-stroke flicker: bright, dim, bright again.
            val strobe = if (age < 0.15f || (age > 0.3f && age < 0.45f)) 1f else 0.35f
            flash = max(flash, b.power * strobe * (if (b.distant) 0.18f else if (b.sheet) 0.55f else 0.8f))
        }
        a.flash = flash

        // Dust devils ahead of the storm.
        if (cfg.dust) {
            val dust = hourWindow(13.4f, 14.6f, 15.5f, 16.0f, a.hour) * f.particleScale
            if (rng.float() < dust * 45f * dt) {
                particles.add(P(-0.05f * f.w, f.horizonY + rng.float() * (f.h - f.horizonY) * 0.8f,
                    rng.range(120f, 320f) * f.u * (0.4f + a.windAbs()), rng.range(-30f, 10f) * f.u,
                    0f, rng.range(3f, 6f), rng.range(2f, 6f) * f.u, KIND_DUST))
            }
        }
        if (cfg.fallingLeaves) {
            val rateL = (0.4f + a.windAbs() * 2.5f) * f.particleScale
            if (rng.float() < rateL * dt) {
                particles.add(P(rng.float() * f.w, -10f, 0f, rng.range(30f, 60f) * f.u, 0f, 14f, rng.range(5f, 9f) * f.u, KIND_LEAF, rng.float() * 360f))
            }
        }
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life += dt
            if (p.life > p.maxLife || p.y > f.h + 40f || p.x > f.w + 60f || p.x < -80f) { pi.remove(); continue }
            when (p.kind) {
                KIND_DUST -> { p.vy += sin(p.life * 3f + p.x * 0.01f) * 40f * f.u * dt; p.rot += dt * 200f }
                KIND_SPRAY -> p.vy += 900f * f.u * dt
                KIND_LEAF -> { p.vx = (sin(p.life * 2.2f + p.size) * 40f + a.wind * 120f) * f.u; p.rot += dt * 90f }
            }
            p.x += p.vx * dt; p.y += p.vy * dt
        }
        for (ff in fireflies) {
            ff.life += dt
            ff.x += noise1(ff.life * 0.4f, ff.size.toInt() + 3) * 30f * f.u * dt
            ff.y += noise1(ff.life * 0.4f + 50f, ff.size.toInt() + 7) * 20f * f.u * dt
            if (ff.x < 0) ff.x += f.w; if (ff.x > f.w) ff.x -= f.w
            ff.y = clamp(ff.y, f.horizonY, f.h.toFloat())
        }
    }

    /** Called by coastal scenes when a wave breaks on rocks. */
    fun spray(x: Float, y: Float, strength: Float, f: Frame) {
        val n = (14 * strength * f.particleScale).toInt().coerceAtLeast(2)
        repeat(n) {
            particles.add(P(x + rng.range(-20f, 20f) * f.u, y, rng.range(-90f, 90f) * f.u * (0.5f + strength),
                -rng.range(240f, 620f) * f.u * (0.4f + strength), 0f, 1.4f, rng.range(1.5f, 4f) * f.u, KIND_SPRAY))
        }
    }

    private fun spawnBolt(f: Frame, distant: Boolean) {
        val sheet = !distant && rng.chance(0.35f)
        val x0 = rng.range(0.1f, 0.9f) * f.w
        val y0 = if (distant) f.horizonY - rng.range(0.04f, 0.12f) * f.h else rng.range(0.05f, 0.2f) * f.horizonY
        val y1 = if (distant) f.horizonY + 2f else f.horizonY + rng.range(-0.02f, 0.08f) * f.h
        val x1 = x0 + rng.range(-0.15f, 0.15f) * f.w * (if (distant) 0.3f else 1f)
        val segs = FloatArray(400)
        var n = 0
        fun add(ax: Float, ay: Float, bx: Float, by: Float) {
            if (n + 4 <= segs.size) { segs[n++] = ax; segs[n++] = ay; segs[n++] = bx; segs[n++] = by }
        }
        fun fork(ax: Float, ay: Float, bx: Float, by: Float, disp: Float, depth: Int, branch: Boolean) {
            if (depth == 0) { add(ax, ay, bx, by); return }
            val mx = (ax + bx) / 2 + rng.range(-disp, disp)
            val my = (ay + by) / 2 + rng.range(-disp, disp) * 0.3f
            fork(ax, ay, mx, my, disp / 2, depth - 1, branch)
            fork(mx, my, bx, by, disp / 2, depth - 1, branch)
            if (branch && depth >= 3 && rng.chance(0.35f)) {
                val len = abs(by - ay) * rng.range(0.3f, 0.6f)
                fork(mx, my, mx + rng.range(-len, len) * 0.6f, my + len, disp / 2, depth - 2, false)
            }
        }
        if (!sheet) fork(x0, y0, x1, y1, (y1 - y0) * 0.22f, if (distant) 5 else 7, true)
        val life = rng.range(0.35f, 0.6f)
        bolts.add(Bolt(segs, n, life, life, rng.range(0.6f, 1f), distant, sheet))
    }

    private fun resetDrop(i: Int, f: Frame, anywhere: Boolean) {
        val o = i * 4
        drops[o] = rng.range(-0.2f, 1.2f) * f.w
        drops[o + 1] = if (anywhere) rng.float() * f.h else -rng.float() * f.h * 0.2f
        drops[o + 2] = rng.float()
        drops[o + 3] = rng.float()
    }

    // =====================================================================================
    // Drawing
    // =====================================================================================

    fun drawClouds(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        val lit = a.lit()
        // Cloud colour: white by day, sunset-tinted at golden hour, slate in storms, blue-grey at night.
        var base = lerpColor(Color.WHITE, lerpColor(Color.WHITE, a.skyHorizon, 0.75f), a.golden)
        base = lerpColor(base, Color.rgb(86, 90, 104), a.storm * 0.9f)
        val col = multiply(base, lerpColor(lit, Color.WHITE, 0.25f * a.daylight))
        if (col != lastCloudColor) {
            lastCloudColor = col
            cloudFilter = LightingColorFilter(col and 0xFFFFFF, 0)
        }
        bmpPaint.colorFilter = cloudFilter
        val windPush = 1f + a.windAbs() * 5f
        for (cl in clouds) {
            cl.x += cl.speed * windPush * a.windSign() * f.dt
            val spr = cloudSprites[cl.sprite]
            val cw = spr.width * cl.scale
            if (cl.x > f.w + cw * 0.6f) cl.x = -cw * 0.6f
            if (cl.x < -cw * 0.6f) cl.x = f.w + cw * 0.6f
            val vis = smoothstep(cl.order - 0.08f, cl.order + 0.05f, a.cloudCover * 1.15f)
            if (vis < 0.02f) continue
            val x = cl.x + f.shiftX(0.08f + cl.scale * 0.05f)
            dst.set(x - cw / 2, cl.y - spr.height * cl.scale / 2, x + cw / 2, cl.y + spr.height * cl.scale / 2)
            bmpPaint.alpha = (vis * 235).toInt()
            c.drawBitmap(spr, null, dst, bmpPaint)
        }
        drawStormFront(c, f, cfg)
        bmpPaint.colorFilter = null
    }

    /** A dark anvil deck that rolls in from the east and exits west. */
    private fun drawStormFront(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        if (a.storm < 0.02f) return
        val p = clamp01((a.hour - 13.4f) / (18.4f - 13.4f))
        val edge = lerp(f.w * 1.15f, -f.w * 2.5f, p)
        val deckW = f.w * 2.3f
        val bottom = f.horizonY * 0.62f
        val darkness = a.storm
        val deckCol = multiply(Color.rgb(70, 74, 86), lerpColor(a.lit(), Color.WHITE, 0.2f))
        val vertical = LinearGradient(0f, 0f, 0f, bottom, withAlpha(deckCol, 0.92f * darkness), withAlpha(deckCol, 0.0f), Shader.TileMode.CLAMP)
        val feather = LinearGradient(edge, 0f, edge + deckW, 0f, intArrayOf(0, Color.BLACK, Color.BLACK, 0), floatArrayOf(0f, 0.18f, 0.82f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = 255
        paint.shader = ComposeShader(vertical, feather, PorterDuff.Mode.DST_IN)
        c.drawRect(max(0f, edge), 0f, min(f.w.toFloat(), edge + deckW), bottom, paint)
        paint.shader = null
        // Billowing leading and trailing edges.
        val cw = cloudSprites[0].width
        var x = edge
        var i = 0
        while (x < edge + deckW) {
            if (x > -cw && x < f.w + cw) {
                val spr = cloudSprites[i % cloudSprites.size]
                val sc = f.w / 520f * (0.9f + 0.3f * sin(i * 1.7f))
                val y = bottom * (0.35f + 0.25f * sin(i * 2.3f))
                dst.set(x - spr.width * sc / 2, y - spr.height * sc / 2, x + spr.width * sc / 2, y + spr.height * sc / 2)
                bmpPaint.alpha = (darkness * 245).toInt()
                c.drawBitmap(spr, null, dst, bmpPaint)
            }
            x += f.w * 0.28f
            i++
        }
    }

    /** Bolts are drawn behind the land so strikes appear to hit the ground in the distance. */
    fun drawLightning(c: Canvas, f: Frame) {
        if (bolts.isEmpty()) return
        val col = f.theme.lightningColor
        for (b in bolts) {
            val age = 1f - b.life / b.maxLife
            val strobe = if (age < 0.15f || (age > 0.3f && age < 0.45f)) 1f else 0.25f
            if (b.sheet) continue
            val w = if (b.distant) 1.6f else 3.2f
            stroke.color = withAlpha(col, 0.22f * strobe * f.theme.bloom)
            stroke.strokeWidth = w * 5f * f.u
            c.drawLines(b.segs, 0, b.count, stroke)
            stroke.color = withAlpha(Color.WHITE, strobe)
            stroke.strokeWidth = w * f.u
            c.drawLines(b.segs, 0, b.count, stroke)
        }
    }

    fun drawPrecipitation(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        val intensity = a.precip
        if (intensity < 0.01f) return
        val kind = cfg.precip
        val count = (maxDrops * intensity * f.particleScale * (if (kind == Precip.SNOW) 0.6f else 1f)).toInt().coerceAtMost(maxDrops)
        val dt = f.dt
        val wind = a.wind * f.w * 0.35f
        when (kind) {
            Precip.RAIN, Precip.DRIZZLE -> {
                val drizzle = kind == Precip.DRIZZLE
                val baseSpeed = (if (drizzle) 700f else 1500f) * f.u
                val baseLen = (if (drizzle) 14f else 34f) * f.u
                var n = 0
                for (i in 0 until count) {
                    val o = i * 4
                    val z = drops[o + 2]
                    val sp = baseSpeed * (0.55f + 0.7f * z)
                    drops[o] += wind * (0.6f + 0.6f * z) * dt
                    drops[o + 1] += sp * dt
                    if (drops[o + 1] > f.h || drops[o] > f.w * 1.25f || drops[o] < -f.w * 0.25f) resetDrop(i, f, false)
                    val len = baseLen * (0.5f + z)
                    val vx = wind * (0.6f + 0.6f * z) / sp
                    lineBuf[n++] = drops[o]; lineBuf[n++] = drops[o + 1]
                    lineBuf[n++] = drops[o] - vx * len; lineBuf[n++] = drops[o + 1] - len
                }
                stroke.color = withAlpha(multiply(f.theme.rainTint, lerpColor(a.lit(), Color.WHITE, 0.35f)), if (drizzle) 0.35f else 0.42f)
                stroke.strokeWidth = (if (drizzle) 1.1f else 1.6f) * f.u
                c.drawLines(lineBuf, 0, n, stroke)
            }
            Precip.SNOW -> {
                paint.color = multiply(Color.rgb(250, 252, 255), lerpColor(a.lit(), Color.WHITE, 0.4f))
                val fallBase = 110f * f.u
                for (i in 0 until count) {
                    val o = i * 4
                    val z = drops[o + 2]
                    drops[o] += (wind * (0.8f + z) + sin(f.t * 1.3f + drops[o + 3] * 20f) * 40f * f.u) * dt
                    drops[o + 1] += fallBase * (0.5f + z) * (1f + a.windAbs()) * dt
                    if (drops[o + 1] > f.h || drops[o] > f.w * 1.25f || drops[o] < -f.w * 0.25f) resetDrop(i, f, false)
                    paint.alpha = (150 + 100 * z).toInt()
                    c.drawCircle(drops[o], drops[o + 1], (1.2f + 3.2f * z) * f.u, paint)
                }
                // Whiteout: visibility drops at the storm's height.
                val white = (a.precip * a.windAbs() * 0.15f).coerceAtMost(0.18f)
                if (white > 0.01f) c.drawColor(withAlpha(multiply(Color.rgb(225, 230, 240), a.lit()), white))
            }
        }
    }

    fun drawParticles(c: Canvas, f: Frame) {
        val a = f.atmos
        for (p in particles) {
            val t = p.life / p.maxLife
            when (p.kind) {
                KIND_DUST -> {
                    paint.color = withAlpha(f.tint(Color.rgb(170, 110, 70)), 0.5f * sin(t * PI_F))
                    c.drawCircle(p.x, p.y, p.size * (1f + t * 2f), paint)
                }
                KIND_SPRAY -> {
                    paint.color = withAlpha(f.tint(Color.rgb(240, 248, 255)), 0.75f * (1f - t))
                    c.drawCircle(p.x, p.y, p.size, paint)
                }
                KIND_LEAF -> {
                    paint.color = f.tint(if (p.size > 7f * f.u) Color.rgb(150, 110, 40) else Color.rgb(90, 130, 50))
                    c.save(); c.rotate(p.rot, p.x, p.y)
                    dst.set(p.x - p.size, p.y - p.size * 0.45f, p.x + p.size, p.y + p.size * 0.45f)
                    c.drawOval(dst, paint)
                    c.restore()
                }
            }
        }
        // Fireflies / lightning bugs.
        val ffVis = if (f.settings.allAnimalsAlways) max(a.night, 0.6f) else a.night * (1f - a.precip)
        if (ffVis > 0.02f) {
            for ((i, ff) in fireflies.withIndex()) {
                val blink = smoothstep(0.55f, 0.95f, sin(ff.life * (0.8f + (i % 5) * 0.23f) + i * 1.9f))
                if (blink < 0.02f) continue
                val al = blink * ffVis
                val r = ff.size * 4f
                paint.alpha = 255
                paint.shader = RadialGradient(ff.x, ff.y, r, withAlpha(Color.rgb(210, 255, 120), 0.5f * al * f.theme.bloom), 0, Shader.TileMode.CLAMP)
                c.drawCircle(ff.x, ff.y, r, paint)
                paint.shader = null
                paint.color = withAlpha(Color.rgb(240, 255, 190), al)
                c.drawCircle(ff.x, ff.y, ff.size * 0.6f, paint)
            }
        }
    }

    /** Layer 7: fog, dust haze, heat shimmer, rainbow and the lightning flash itself. */
    fun drawAtmosphere(c: Canvas, f: Frame, cfg: SceneConfig) {
        val a = f.atmos
        // Rainbow opposite the sun after the storm.
        val bow = a.clearing * smoothstep(0.05f, 0.25f, a.sunElev) * (1f - a.precip) * (1f - a.cloudCover * 0.5f)
        if (bow > 0.02f) drawRainbow(c, f, bow)

        if (a.heat > 0.02f) {
            stroke.strokeWidth = 2f * f.u
            for (i in 0 until 7) {
                val y = f.horizonY + (i * 9f - ((f.t * 14f) % 9f)) * f.u
                stroke.color = withAlpha(Color.WHITE, 0.05f * a.heat)
                val wob = sin(f.t * 3f + i) * 6f * f.u
                c.drawLine(0f, y + wob, f.w.toFloat(), y - wob, stroke)
            }
        }

        val fog = a.mist * f.theme.fog
        val fogSpr = fogSprite
        if (fog > 0.02f && fogSpr != null) {
            bmpPaint.colorFilter = null
            val fogCol = f.tint(Color.rgb(232, 236, 240))
            bmpPaint.colorFilter = LightingColorFilter(fogCol and 0xFFFFFF, 0)
            for (i in 0 until 4) {
                val y = f.horizonY + (i - 1.2f) * f.h * 0.07f
                val speed = (8f + i * 5f) * f.u
                val bw = f.w * 1.6f
                val x = ((f.t * speed + i * 377f) % bw) - bw + f.shiftX(0.3f + i * 0.15f)
                bmpPaint.alpha = (clamp01(fog * (0.55f - i * 0.06f)) * 255).toInt()
                dst.set(x, y - f.h * 0.09f, x + bw, y + f.h * 0.09f)
                c.drawBitmap(fogSpr, null, dst, bmpPaint)
                dst.offset(bw, 0f)
                c.drawBitmap(fogSpr, null, dst, bmpPaint)
            }
            bmpPaint.colorFilter = null
            // Overall veil reduces visibility.
            c.drawColor(withAlpha(fogCol, fog * 0.18f))
        }

        if (cfg.dust) {
            val dust = hourWindow(13.4f, 14.6f, 15.5f, 16.0f, a.hour) * 0.22f + a.golden * 0.08f
            if (dust > 0.01f) {
                paint.alpha = 255
                paint.shader = LinearGradient(0f, f.horizonY - f.h * 0.15f, 0f, f.h.toFloat(), 0, withAlpha(f.tint(Color.rgb(196, 120, 70)), dust), Shader.TileMode.CLAMP)
                c.drawRect(0f, f.horizonY - f.h * 0.15f, f.w.toFloat(), f.h.toFloat(), paint)
                paint.shader = null
            }
        }

        if (flash > 0.02f) c.drawColor(withAlpha(f.theme.lightningColor, flash * 0.28f))
    }

    private fun drawRainbow(c: Canvas, f: Frame, vis: Float) {
        val (sx, _) = Celestial.sunPos(f)
        val cx = f.w - sx * 0.6f
        val cy = f.horizonY + f.h * 0.12f
        val r = f.w * 0.62f
        val colors = intArrayOf(Color.rgb(255, 60, 60), Color.rgb(255, 160, 40), Color.rgb(255, 240, 80), Color.rgb(80, 220, 90), Color.rgb(60, 140, 255), Color.rgb(140, 80, 220))
        stroke.strokeWidth = 7f * f.u
        c.save()
        c.clipRect(0f, 0f, f.w.toFloat(), f.horizonY + f.h * 0.04f)
        for ((i, col) in colors.withIndex()) {
            stroke.color = withAlpha(col, 0.16f * vis)
            dst.set(cx - r + i * 7f * f.u, cy - r + i * 7f * f.u, cx + r - i * 7f * f.u, cy + r - i * 7f * f.u)
            c.drawArc(dst, 180f, 180f, false, stroke)
        }
        c.restore()
    }

    // ---- Sprite generation

    private fun buildCloud(r: Rng): Bitmap {
        val w = 520
        val h = 220
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        val puffs = 14
        for (i in 0 until puffs) {
            val t = i / (puffs - 1f)
            val x = 70f + t * (w - 140f) + r.range(-20f, 20f)
            val bulge = sin(t * PI_F)
            val rad = r.range(34f, 52f) + bulge * 38f
            val y = h * 0.66f - bulge * 48f + r.range(-10f, 10f)
            // Shaded underside, bright crown.
            p.shader = LinearGradient(0f, y - rad, 0f, y + rad, Color.WHITE, Color.rgb(176, 182, 196), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rad, p)
        }
        return bmp
    }

    private fun buildFog(): Bitmap {
        val w = 640
        val h = 120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        val r = Rng(55)
        for (i in 0 until 40) {
            // Wrap horizontally so the strip tiles seamlessly.
            val x = r.float() * w
            val y = h / 2f + r.range(-18f, 18f)
            val rad = r.range(20f, 42f)
            p.color = withAlpha(Color.WHITE, r.range(0.25f, 0.55f))
            c.drawCircle(x, y, rad, p)
            if (x < rad * 2) c.drawCircle(x + w, y, rad, p)
            if (x > w - rad * 2) c.drawCircle(x - w, y, rad, p)
        }
        return bmp
    }

    companion object {
        private const val KIND_DUST = 1
        private const val KIND_SPRAY = 2
        private const val KIND_LEAF = 3
    }
}
