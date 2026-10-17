package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random

/**
 * Whales and dolphins in Table Bay: a whale lifting its tail to dive, a humpback breaching,
 * or a pod of dolphins leaping across. One event at a time, plus its spray and ripples.
 */
class MarineLife(private val dp: Float) {

    enum class Kind { TAIL, BREACH, DOLPHINS }

    private val rnd = Random(System.nanoTime())
    private var w = 0f
    private var h = 0f
    private var shoreY = 0f
    private var beachY = 0f

    var active = false
        private set
    private var kind = Kind.TAIL
    private var t = 0f
    private var ex = 0f          // event x
    private var waterY = 0f      // event waterline
    private var depth = 1f       // size factor: bigger when closer to the beach
    private var dir = 1f
    private var alpha = 255

    fun setSize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat()
        h = height.toFloat()
        shoreY = h * 0.72f
        beachY = h * 0.935f
        active = false
        particleCount = 0
        rippleCount = 0
    }

    fun launch(kind: Kind? = null, haze: Float = 1f) {
        if (w == 0f) return
        this.kind = kind ?: when (rnd.nextInt(10)) {
            in 0..3 -> Kind.TAIL
            in 4..7 -> Kind.DOLPHINS
            else -> Kind.BREACH
        }
        alpha = (255 * haze).toInt().coerceIn(0, 255)
        dir = if (rnd.nextBoolean()) 1f else -1f
        depth = 0.7f + rnd.nextFloat() * 0.5f
        waterY = shoreY + (beachY - shoreY) * (0.25f + 0.55f * (depth - 0.7f) / 0.5f)
        ex = w * (0.15f + rnd.nextFloat() * 0.7f)
        t = 0f
        if (this.kind == Kind.DOLPHINS) startPod()
        if (this.kind == Kind.TAIL) addRipple(ex, waterY, w * 0.03f * depth)
        active = true
    }

    fun update(dt: Float) {
        updateParticles(dt)
        updateRipples(dt)
        if (!active) return
        t += dt
        when (kind) {
            Kind.TAIL -> updateTail()
            Kind.BREACH -> updateBreach()
            Kind.DOLPHINS -> updatePod(dt)
        }
    }

    fun draw(canvas: Canvas, night: Boolean) {
        val tint = if (night) 0.45f else 1f
        drawRipples(canvas, tint)
        if (active) {
            canvas.save()
            canvas.clipRect(0f, 0f, w, waterY) // the sea hides everything below the waterline
            when (kind) {
                Kind.TAIL -> drawTail(canvas)
                Kind.BREACH -> drawBreach(canvas)
                Kind.DOLPHINS -> drawPod(canvas)
            }
            canvas.restore()
        }
        drawParticles(canvas, tint)
    }

    // ==== Whale tail: the back arches, the flukes rise, hang dripping, and slide under ====

    private val whalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3038.toInt() }
    private val palePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD3D8DD.toInt() }
    private val flukePath = Path().apply {
        moveTo(-0.07f, 0f)
        lineTo(-0.05f, -0.45f)
        cubicTo(-0.2f, -0.5f, -0.42f, -0.58f, -0.53f, -0.8f)
        cubicTo(-0.38f, -0.73f, -0.18f, -0.76f, 0f, -0.67f)
        cubicTo(0.18f, -0.76f, 0.38f, -0.73f, 0.53f, -0.8f)
        cubicTo(0.42f, -0.58f, 0.2f, -0.5f, 0.05f, -0.45f)
        lineTo(0.07f, 0f)
        close()
    }
    private val flukePatches = listOf(RectF(-0.4f, -0.72f, -0.12f, -0.6f), RectF(0.1f, -0.7f, 0.33f, -0.6f))
    private var tailSank = false

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun tailEmergence() = when {
        t < 1.4f -> 0f
        t < 3.0f -> smooth((t - 1.4f) / 1.6f)
        t < 5.0f -> 1f
        t < 6.6f -> 1f - smooth((t - 5.0f) / 1.6f)
        else -> 0f
    }

    private fun updateTail() {
        val f = w * 0.13f * depth
        val e = tailEmergence()
        // Water streaming off the fluke tips while it is up.
        if (t in 2.6f..6.2f && rnd.nextFloat() < 0.5f) {
            val tip = if (rnd.nextBoolean()) -0.5f else 0.5f
            val tipY = waterY + (1f - e) * 0.9f * f - 0.78f * f
            spawn(ex + tip * f * dir, tipY, 1, upSpeed = 0f, spread = 8f * dp, size = 1.4f * dp * depth, floorY = waterY)
        }
        if (t > 6.6f && !tailSank) {
            tailSank = true
            addRipple(ex, waterY, w * 0.05f * depth)
            spawn(ex, waterY, 12, upSpeed = 60f * dp * depth, spread = 40f * dp * depth, size = 1.8f * dp * depth, floorY = waterY)
        }
        if (t > 7.5f) { active = false; tailSank = false }
    }

    private fun drawTail(c: Canvas) {
        val f = w * 0.13f * depth
        // The back rolling forward before the dive.
        if (t < 2.4f) {
            val rise = sin((t / 2.4f) * PI.toFloat()) * 0.14f * f
            c.drawOval(RectF(ex - dir * 0.9f * f - 0.6f * f, waterY - rise, ex - dir * 0.9f * f + 0.6f * f, waterY + rise), whalePaint)
        }
        val e = tailEmergence()
        if (e <= 0f) return
        c.save()
        c.translate(ex, waterY + (1f - e) * 0.9f * f)
        c.rotate(dir * (-8f + 12f * (t / 6.6f)))
        c.scale(f, f)
        whalePaint.alpha = alpha
        c.drawPath(flukePath, whalePaint)
        // Humpbacks show pale markings on the underside of the flukes.
        palePaint.alpha = (alpha * 0.75f).toInt()
        c.save()
        c.clipPath(flukePath)
        for (r in flukePatches) c.drawOval(r, palePaint)
        c.restore()
        c.restore()
    }

    // ==== Breach: a humpback launches out, twists, and falls back in a burst of spray ====

    private val bodyPath = Path().apply {
        moveTo(0.5f, 0f)
        cubicTo(0.46f, -0.1f, 0.25f, -0.14f, 0f, -0.13f)
        cubicTo(-0.2f, -0.12f, -0.35f, -0.07f, -0.44f, -0.03f)
        lineTo(-0.56f, -0.12f)
        lineTo(-0.51f, 0f)
        lineTo(-0.56f, 0.12f)
        lineTo(-0.44f, 0.03f)
        cubicTo(-0.3f, 0.1f, 0.1f, 0.14f, 0.3f, 0.1f)
        cubicTo(0.42f, 0.07f, 0.49f, 0.04f, 0.5f, 0f)
        close()
    }
    private val flipperPath = Path().apply {
        moveTo(0.24f, 0.07f); lineTo(0.17f, 0.1f); lineTo(-0.08f, 0.38f); lineTo(-0.02f, 0.39f); close()
    }
    private val bellyRect = RectF(-0.1f, 0.02f, 0.5f, 0.16f)
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553A424C; strokeWidth = 0.008f }
    private var breachSplashOut = false
    private var breachSplashIn = false

    private val breachUp = 1.25f
    private fun breachY(b: Float): Float {
        val apex = 0.75f * b
        val g = 2f * apex / (breachUp * breachUp)
        val v0 = -g * breachUp
        return 0.3f * b + v0 * t + 0.5f * g * t * t // centre of the body, relative to the waterline
    }

    private fun updateBreach() {
        val b = w * 0.21f * depth
        if (!breachSplashOut && t > 0.25f) {
            breachSplashOut = true
            spawn(ex, waterY, 35, upSpeed = 220f * dp * depth, spread = 90f * dp * depth, size = 2.2f * dp * depth, floorY = waterY)
            addRipple(ex, waterY, w * 0.05f * depth)
        }
        if (!breachSplashIn && t > 2.3f) {
            breachSplashIn = true
            val x = ex + dir * 0.35f * b
            spawn(x, waterY, 70, upSpeed = 300f * dp * depth, spread = 160f * dp * depth, size = 2.6f * dp * depth, floorY = waterY)
            addRipple(x, waterY, w * 0.08f * depth)
            addRipple(x, waterY, w * 0.04f * depth)
        }
        if (t > 3.2f) { active = false; breachSplashOut = false; breachSplashIn = false }
    }

    private fun drawBreach(c: Canvas) {
        val b = w * 0.21f * depth
        val y = waterY + breachY(b)
        val x = ex + dir * 0.35f * b * (t / (2 * breachUp)).coerceAtMost(1f)
        // Nose-up out of the water, rolling over to land on its side/back.
        val angle = -80f + 125f * smooth(t / (2.1f * breachUp))
        c.save()
        c.translate(x, y)
        if (dir < 0) c.scale(-1f, 1f)
        c.rotate(angle)
        c.scale(b, b)
        whalePaint.alpha = alpha
        c.drawPath(bodyPath, whalePaint)
        c.save()
        c.clipPath(bodyPath)
        palePaint.alpha = (alpha * 0.85f).toInt()
        c.drawOval(bellyRect, palePaint)
        groovePaint.alpha = (0x55 * alpha / 255)
        for (i in 0 until 5) {
            val gy = 0.06f + i * 0.018f
            c.drawLine(-0.05f, gy, 0.42f, gy - 0.01f, groovePaint)
        }
        c.restore()
        palePaint.alpha = alpha
        c.drawPath(flipperPath, palePaint)
        c.restore()
    }

    // ==== Dolphins: a pod leaping across the bay ====================================

    private val maxPod = 5
    private var podN = 0
    private val dX = FloatArray(maxPod)
    private val dPeriod = FloatArray(maxPod)
    private val dPhase = FloatArray(maxPod)
    private val dYOff = FloatArray(maxPod)
    private val dWasUp = BooleanArray(maxPod)
    private var podSpeed = 0f

    private val dolphinPath = Path().apply {
        moveTo(0.5f, 0f)
        quadTo(0.43f, -0.035f, 0.33f, -0.06f)
        cubicTo(0.15f, -0.13f, -0.15f, -0.11f, -0.38f, -0.03f)
        lineTo(-0.44f, -0.015f)
        lineTo(-0.57f, -0.085f)
        lineTo(-0.52f, 0f)
        lineTo(-0.57f, 0.085f)
        lineTo(-0.44f, 0.015f)
        cubicTo(-0.15f, 0.09f, 0.15f, 0.09f, 0.32f, 0.045f)
        quadTo(0.42f, 0.025f, 0.5f, 0f)
        close()
        // Dorsal fin.
        moveTo(-0.02f, -0.11f); lineTo(-0.14f, -0.25f); lineTo(-0.17f, -0.09f); close()
    }
    private val dolphinBelly = RectF(-0.3f, 0.005f, 0.42f, 0.09f)
    private val dolphinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF56636F.toInt() }
    private val dolphinBellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9D0D6.toInt() }
    private val finPath = Path().apply { moveTo(0f, 0f); lineTo(-0.1f, -0.13f); lineTo(-0.16f, 0f); close() }

    private fun dolphinLen() = w * 0.07f * depth
    private fun leapFraction() = 0.45f

    private fun startPod() {
        podN = 3 + rnd.nextInt(3)
        podSpeed = w * 1.4f / (11f + rnd.nextFloat() * 4f)
        val start = if (dir > 0) -w * 0.05f else w * 1.05f
        for (i in 0 until podN) {
            dX[i] = start - dir * (i * w * (0.05f + rnd.nextFloat() * 0.04f))
            dPeriod[i] = 1.7f + rnd.nextFloat() * 0.7f
            dPhase[i] = rnd.nextFloat() * dPeriod[i]
            dYOff[i] = (rnd.nextFloat() - 0.5f) * 8f * dp * depth
            dWasUp[i] = false
        }
    }

    private fun leapS(i: Int): Float {
        val p = (t + dPhase[i]) % dPeriod[i]
        return p / (dPeriod[i] * leapFraction()) // 0..1 during a leap, >1 swimming under
    }

    private fun updatePod(dt: Float) {
        var anyOnScreen = false
        for (i in 0 until podN) {
            dX[i] += dir * podSpeed * dt
            if (dX[i] > -w * 0.1f && dX[i] < w * 1.1f) anyOnScreen = true
            val up = leapS(i) <= 1f
            if (up != dWasUp[i] && dX[i] in 0f..w) {
                val y = waterY + dYOff[i]
                spawn(dX[i], y, 6, upSpeed = 70f * dp * depth, spread = 25f * dp * depth, size = 1.4f * dp * depth, floorY = y)
                addRipple(dX[i], y, w * 0.012f * depth)
            }
            dWasUp[i] = up
        }
        if (!anyOnScreen && t > 2f) active = false
    }

    private fun drawPod(c: Canvas) {
        val d = dolphinLen()
        val leapH = 0.42f * d
        val leapDist = podSpeed * dPeriod[0] * leapFraction()
        for (i in 0 until podN) {
            val s = leapS(i)
            val y0 = waterY + dYOff[i]
            if (s <= 1f) {
                val pi = PI.toFloat()
                val lift = sin(s * pi) * leapH
                val slope = (leapH * pi * kotlin.math.cos(s * pi)) / leapDist
                val angle = Math.toDegrees(atan2(-slope.toDouble(), 1.0)).toFloat()
                c.save()
                c.translate(dX[i], y0 - lift + 0.1f * d)
                if (dir < 0) c.scale(-1f, 1f)
                c.rotate(angle)
                c.scale(d, d)
                dolphinPaint.alpha = alpha
                c.drawPath(dolphinPath, dolphinPaint)
                c.save()
                c.clipPath(dolphinPath)
                dolphinBellyPaint.alpha = alpha
                c.drawOval(dolphinBelly, dolphinBellyPaint)
                c.restore()
                c.restore()
            } else {
                // Between leaps: just the dorsal fin cutting the surface.
                c.save()
                c.translate(dX[i], y0 + 0.02f * d)
                if (dir < 0) c.scale(-1f, 1f)
                c.scale(d, d)
                dolphinPaint.alpha = alpha
                c.drawPath(finPath, dolphinPaint)
                c.restore()
            }
        }
    }

    // ==== Spray and ripples =====================================================

    private val maxParticles = 220
    private var particleCount = 0
    private val pX = FloatArray(maxParticles)
    private val pY = FloatArray(maxParticles)
    private val pVX = FloatArray(maxParticles)
    private val pVY = FloatArray(maxParticles)
    private val pLife = FloatArray(maxParticles)
    private val pMax = FloatArray(maxParticles)
    private val pR = FloatArray(maxParticles)
    private val pFloor = FloatArray(maxParticles)
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    private fun spawn(x: Float, y: Float, n: Int, upSpeed: Float, spread: Float, size: Float, floorY: Float) {
        repeat(n) {
            if (particleCount >= maxParticles) return
            val i = particleCount++
            pX[i] = x + (rnd.nextFloat() - 0.5f) * spread * 0.4f
            pY[i] = y
            pVX[i] = (rnd.nextFloat() - 0.5f) * spread * 2f
            pVY[i] = -upSpeed * (0.4f + rnd.nextFloat() * 0.6f)
            pMax[i] = 0.8f + rnd.nextFloat() * 0.8f
            pLife[i] = pMax[i]
            pR[i] = size * (0.6f + rnd.nextFloat() * 0.8f)
            pFloor[i] = floorY + 2f * dp
        }
    }

    private fun updateParticles(dt: Float) {
        val gravity = 420f * dp
        var i = 0
        while (i < particleCount) {
            pVY[i] += gravity * dt
            pX[i] += pVX[i] * dt
            pY[i] += pVY[i] * dt
            pLife[i] -= dt
            if (pLife[i] <= 0f || (pVY[i] > 0f && pY[i] > pFloor[i])) {
                val last = --particleCount // swap-remove
                pX[i] = pX[last]; pY[i] = pY[last]; pVX[i] = pVX[last]; pVY[i] = pVY[last]
                pLife[i] = pLife[last]; pMax[i] = pMax[last]; pR[i] = pR[last]; pFloor[i] = pFloor[last]
            } else {
                i++
            }
        }
    }

    private fun drawParticles(c: Canvas, tint: Float) {
        for (i in 0 until particleCount) {
            sprayPaint.alpha = (alpha * tint * (pLife[i] / pMax[i]).coerceIn(0f, 1f)).toInt()
            c.drawCircle(pX[i], pY[i], pR[i], sprayPaint)
        }
    }

    private val maxRipples = 12
    private var rippleCount = 0
    private val rX = FloatArray(maxRipples)
    private val rY = FloatArray(maxRipples)
    private val rAge = FloatArray(maxRipples)
    private val rSize = FloatArray(maxRipples)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
    }
    private val rippleOval = RectF()

    private fun addRipple(x: Float, y: Float, size: Float) {
        if (rippleCount >= maxRipples) return
        val i = rippleCount++
        rX[i] = x; rY[i] = y; rAge[i] = 0f; rSize[i] = size
    }

    private fun updateRipples(dt: Float) {
        var i = 0
        while (i < rippleCount) {
            rAge[i] += dt
            if (rAge[i] > RIPPLE_SECONDS) {
                val last = --rippleCount
                rX[i] = rX[last]; rY[i] = rY[last]; rAge[i] = rAge[last]; rSize[i] = rSize[last]
            } else {
                i++
            }
        }
    }

    private fun drawRipples(c: Canvas, tint: Float) {
        for (i in 0 until rippleCount) {
            val k = rAge[i] / RIPPLE_SECONDS
            val rx = rSize[i] * (0.4f + 1.6f * k)
            ripplePaint.alpha = (alpha * tint * 0.6f * (1f - k)).toInt()
            ripplePaint.strokeWidth = 1.4f * dp
            rippleOval.set(rX[i] - rx, rY[i] - rx * 0.16f, rX[i] + rx, rY[i] + rx * 0.16f)
            c.drawOval(rippleOval, ripplePaint)
        }
    }

    private companion object {
        const val RIPPLE_SECONDS = 2.6f
    }
}
