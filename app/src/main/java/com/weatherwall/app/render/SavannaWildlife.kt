package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * The savanna herd: zebra, wildebeest, springbok, elephants with a calf and giraffes grazing
 * calmly, until a pride of lions walks in and scatters them (springbok pronking first). The lions
 * rest, leave, and the herd drifts back. A vulture sits on the big acacia, takes off when the
 * lions arrive, circles while they rest and glides back down once they've gone.
 *
 * Owned by the renderer so the animals carry on through weather changes.
 */
class SavannaWildlife(private val dp: Float) {

    private enum class State { GRAZE, WALK, ALERT, FLEE, AWAY, RETURN, ENTER, REST, LEAVE, GONE }

    private enum class Phase { CALM, HUNT, REST, LEAVE, RETURN }

    private class Animal(val sp: Species, val len: Float, val walk: Float, val run: Float, var x: Float, val d: Float) {
        val pose = Pose()
        var dir = 1f
        var state = State.GRAZE
        var timer = 0f
        var tx = 0f
        var headTarget = 0f
        var headTimer = 0f
        var pronk = 0f
        var mother: Animal? = null
        val isLion get() = sp == Species.LION || sp == Species.LIONESS
    }

    private val rnd = Random(System.nanoTime())
    private val art = AnimalArt()
    private val animals = ArrayList<Animal>()
    private val lions = ArrayList<Animal>()
    private val sorted = ArrayList<Animal>()
    private var L: SavannaLayout? = null
    private var w = 0f
    private var h = 0f

    private var phase = Phase.CALM
    /** Demo: the whole lion visit about three times quicker, back to normal once the herd returns. */
    private var quick = false
    private val pace get() = if (quick) 3f else 1f
    private var phaseTimer = 0f

    // Per-animal tint, read by the art's colour transform.
    private var tintHaze = 0f
    private var tintSilh = 0f
    private var tintHazeColor = 0
    private val dark = 0xFF241C20.toInt()
    init {
        art.tint = { c -> withAlpha(mix(mix(c, tintHazeColor, tintHaze), dark, tintSilh), c ushr 24) }
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setSize(width: Int, height: Int) {
        if (width.toFloat() == w && height.toFloat() == h) return
        w = width.toFloat(); h = height.toFloat()
        L = SavannaLayout(w, h)
        populate()
    }

    private fun r() = rnd.nextFloat()

    private fun depth() = 0.015f + (SavannaDesign.NEAREST_ANIMALS - 0.015f) * r().pow(SavannaDesign.HERD_DEPTH)

    private fun populate() {
        animals.clear(); lions.clear()
        fun add(sp: Species, n: Int, len: Float, walk: Float, run: Float) {
            repeat(n) { animals += Animal(sp, len, walk, run, 0.04f + r() * 0.92f, depth()).also { it.dir = if (rnd.nextBoolean()) 1f else -1f } }
        }
        add(Species.ZEBRA, SavannaDesign.ZEBRA, 0.13f, 0.03f, 0.42f)
        add(Species.WILDEBEEST, SavannaDesign.WILDEBEEST, 0.12f, 0.03f, 0.4f)
        add(Species.SPRINGBOK, SavannaDesign.SPRINGBOK, 0.095f, 0.03f, 0.45f)
        add(Species.GIRAFFE, SavannaDesign.GIRAFFES, 0.14f, 0.024f, 0.22f)
        // Elephants keep together, the calf at its mother's side.
        val ex = 0.15f + r() * 0.7f
        // Elephants keep to the back of the plain, near the horizon, so they never crowd the front.
        val ed = SavannaDesign.ELEPHANT_BACK + 0.015f + r() * (SavannaDesign.ELEPHANT_FRONT - SavannaDesign.ELEPHANT_BACK - 0.03f)
        var mother: Animal? = null
        for (i in 0 until SavannaDesign.ELEPHANTS) {
            val e = Animal(Species.ELEPHANT, 0.24f, 0.02f, 0.13f, (ex + (i - 1) * 0.12f).coerceIn(0.05f, 0.95f), (ed + (r() - 0.5f) * 0.03f).coerceIn(SavannaDesign.ELEPHANT_BACK, SavannaDesign.ELEPHANT_FRONT))
            e.dir = if (i == 0 || rnd.nextBoolean()) 1f else -1f
            animals += e
            if (mother == null) mother = e
        }
        mother?.let { m ->
            val calf = Animal(Species.CALF, 0.13f, 0.02f, 0.13f, m.x + 0.05f, min(SavannaDesign.ELEPHANT_FRONT, m.d + 0.01f))
            calf.mother = m; calf.dir = m.dir
            animals += calf
        }
        for (a in animals) {
            a.timer = r() * 20f / SavannaDesign.ACTIVITY
            a.pose.head = if (rnd.nextBoolean()) 0f else 1f
            a.pose.t = r() * 100f
        }
        val nLions = max(2, SavannaDesign.LIONS + 1)
        for (i in 0 until nLions) {
            val lion = Animal(if (i == 0) Species.LION else Species.LIONESS, 0.14f, 0.04f, 0.4f, -0.3f, 0.12f + r() * 0.28f)
            lion.state = State.GONE
            lions += lion
        }
        phase = Phase.CALM
        phaseTimer = 90f + r() * 60f
        vultures.clear()
    }

    /**
     * How smoothly the herd needs drawing right now: 2 = something runs, pronks or flaps (full
     * frame rate), 1 = animals walking or lions padding about, 0 = grazing and resting.
     */
    val motion: Int get() {
        var m = 0
        for (v in vultures) if (v.t >= 0f && v.mode != VMode.SIT && v.mode != VMode.GONE) return 2
        for (a in animals) when (a.state) {
            State.FLEE, State.ALERT -> return 2
            State.WALK -> m = 1
            State.RETURN -> if (a.timer <= 0f) m = 1
            else -> Unit
        }
        for (l in lions) if (l.state == State.ENTER || (l.state == State.LEAVE && l.timer <= 0f)) m = 1
        return m
    }

    /** Debug: the lions come now. */
    fun lionsNow(fast: Boolean = false) {
        quick = fast
        if (phase == Phase.CALM) phaseTimer = 0f
    }

    // ---- Behaviour ----------------------------------------------------------------------

    fun update(dt: Float) {
        val lay = L ?: return
        phaseTimer -= dt
        when (phase) {
            Phase.CALM -> if (phaseTimer <= 0f) startHunt()
            Phase.HUNT -> {
                val lead = lions.firstOrNull { it.state != State.GONE } ?: lions[0]
                val arrived = lions.all { it.state == State.REST || it.state == State.GONE }
                for (a in animals) {
                    if (a.state == State.GRAZE || a.state == State.WALK) {
                        val near = abs(a.x - lead.x) < 0.45f && lead.x > -0.05f && lead.x < 1.05f
                        if (near || arrived) {
                            a.state = State.ALERT
                            a.timer = 0.8f + r() * 1.5f
                            a.dir = sign(lead.x - a.x).let { if (it == 0f) 1f else it }
                        }
                    }
                }
                if (arrived && animals.all { it.state == State.AWAY }) {
                    phase = Phase.REST; phaseTimer = if (quick) 10f else 30f
                }
            }
            Phase.REST -> if (phaseTimer <= 0f) {
                phase = Phase.LEAVE
                vulturesLeave()
                for (l in lions) if (l.state == State.REST) {
                    l.state = State.LEAVE
                    l.tx = if (l.x < 0.5f) -0.25f else 1.25f
                    l.dir = sign(l.tx - l.x)
                    l.timer = r() * 3f
                }
            }
            Phase.LEAVE -> if (lions.all { it.state == State.GONE }) {
                phase = Phase.RETURN
                for (a in animals) {
                    a.state = State.RETURN
                    a.timer = r() * 25f / pace
                    val fromLeft = rnd.nextBoolean()
                    a.x = if (fromLeft) -0.2f else 1.2f
                    a.dir = if (fromLeft) 1f else -1f
                    a.tx = 0.05f + r() * 0.9f
                }
                // Mother and calf come back together.
                for (a in animals) a.mother?.let { m -> a.timer = m.timer + 0.5f; a.x = m.x - m.dir * 0.05f; a.dir = m.dir; a.tx = m.tx - m.dir * 0.05f }
            }
            Phase.RETURN -> if (animals.all { it.state == State.GRAZE || it.state == State.WALK }) {
                phase = Phase.CALM; phaseTimer = 90f + r() * 60f; quick = false
            }
        }

        for (a in animals) updateAnimal(a, dt, lay)
        for (l in lions) updateLion(l, dt, lay)
        updateVultures(dt, lay)
    }

    private fun startHunt() {
        phase = Phase.HUNT
        val fromLeft = rnd.nextBoolean()
        val n = 2 + rnd.nextInt(lions.size - 1)
        val target = 0.3f + r() * 0.4f
        for ((i, l) in lions.withIndex()) {
            if (i >= n) { l.state = State.GONE; continue }
            l.state = State.ENTER
            l.dir = if (fromLeft) 1f else -1f
            l.x = if (fromLeft) -0.12f - i * (0.06f + r() * 0.06f) else 1.12f + i * (0.06f + r() * 0.06f)
            l.tx = (target + (i - 1) * 0.08f * (0.6f + r())).coerceIn(0.1f, 0.9f)
            l.timer = 0f
        }
        vulturesArrive()
    }

    private fun persp(a: Animal) = 0.4f + 1.2f * a.d

    private fun pxSize(a: Animal) = a.len * w * (0.16f + 1.25f * a.d) * SavannaDesign.ANIMAL_SIZE * 2.2f

    /** Moves towards tx at speed (fraction of W per second); returns true on arrival. */
    private fun move(a: Animal, speed: Float, dt: Float, act: Act): Boolean {
        val dx = a.tx - a.x
        if (abs(dx) < 0.004f) return true
        a.dir = sign(dx)
        val step = min(abs(dx), speed * persp(a) * dt)
        a.x += a.dir * step
        a.pose.act = act
        a.pose.phase += step * w / (AnimalArt.cycleLength(a.sp, act) * pxSize(a))
        return false
    }

    private fun updateAnimal(a: Animal, dt: Float, lay: SavannaLayout) {
        val p = a.pose
        p.t += dt
        a.timer -= dt
        when (a.state) {
            State.GRAZE -> {
                p.act = Act.GRAZE
                a.headTimer -= dt
                if (a.headTimer <= 0f) {
                    // Mostly head down, now and then a look around.
                    a.headTarget = if (a.headTarget > 0.5f || r() < 0.6f) 0f else 1f
                    a.headTimer = if (a.headTarget > 0.5f) 2f + r() * 3f else 4f + r() * 8f
                }
                val m = a.mother
                if (m != null) {
                    a.tx = m.x - m.dir * 0.05f
                    if (abs(a.tx - a.x) > 0.03f) a.state = State.WALK
                } else if (a.timer <= 0f) {
                    a.tx = (a.x + (r() - 0.5f) * 0.18f).coerceIn(0.03f, 0.97f)
                    a.state = State.WALK
                }
            }
            State.WALK -> {
                a.headTarget = 0.85f
                val m = a.mother
                if (m != null) a.tx = m.x - m.dir * 0.05f
                if (move(a, a.walk, dt, Act.WALK)) {
                    a.state = State.GRAZE
                    a.timer = (10f + 20f * r()) / SavannaDesign.ACTIVITY
                    a.headTimer = 1f + r() * 2f
                    p.act = Act.GRAZE
                }
            }
            State.ALERT -> {
                p.act = Act.STAND
                a.headTarget = 1f
                if (a.timer <= 0f) {
                    a.state = State.FLEE
                    val lead = lions.firstOrNull { it.state != State.GONE }
                    val away = if (lead == null) (if (a.x < 0.5f) -1f else 1f) else sign(a.x - lead.x).let { if (it == 0f) 1f else it }
                    a.tx = if (away < 0) -0.3f else 1.3f
                    a.pronk = if (a.sp == Species.SPRINGBOK) 2f + floor(r() * 2f) else 0f
                    p.t = 0f
                }
            }
            State.FLEE -> {
                a.headTarget = 1f
                val m = a.mother
                if (m != null && m.state == State.FLEE) a.tx = m.tx
                when {
                    a.pronk > 0f -> {
                        // Stotting: stiff-legged bounds before the dash.
                        a.dir = sign(a.tx - a.x)
                        val u = (p.t / 0.78f) - floor(p.t / 0.78f)
                        val air = sin(PI.toFloat() * min(1f, u / 0.86f)).coerceAtLeast(0f).pow(0.9f)
                        p.act = Act.PRONK
                        p.bob = -0.48f * air
                        a.x += a.dir * a.walk * 3f * persp(a) * dt
                        if (p.t >= a.pronk * 0.78f) { a.pronk = 0f; p.bob = 0f }
                        AnimalArt.placeFeet(a.sp, p)
                        smoothHead(a, dt)
                        return
                    }
                    a.sp == Species.ELEPHANT || a.sp == Species.CALF -> move(a, a.walk * 2.5f, dt, Act.WALK)
                    else -> move(a, a.run, dt, Act.RUN)
                }
                if (a.x < -0.25f || a.x > 1.25f) a.state = State.AWAY
            }
            State.AWAY -> p.act = Act.STAND
            State.RETURN -> {
                if (a.timer <= 0f) {
                    a.headTarget = 0.85f
                    if (move(a, a.walk * 1.4f, dt, Act.WALK)) {
                        a.state = State.GRAZE
                        a.timer = (10f + 20f * r()) / SavannaDesign.ACTIVITY
                    }
                }
            }
            else -> Unit
        }
        if (p.act != Act.PRONK) p.bob = AnimalArt.bobFor(p.act, p.phase, p.t)
        AnimalArt.placeFeet(a.sp, p)
        smoothHead(a, dt)
    }

    private fun smoothHead(a: Animal, dt: Float) {
        a.pose.head += (a.headTarget - a.pose.head) * min(1f, dt * 1.5f)
    }

    private fun updateLion(l: Animal, dt: Float, lay: SavannaLayout) {
        val p = l.pose
        p.t += dt
        l.timer -= dt
        when (l.state) {
            State.ENTER -> {
                l.headTarget = 0.6f
                if (move(l, l.walk * 0.8f * pace, dt, Act.STALK)) {
                    l.state = State.REST
                    l.timer = 1f + r() * 2f
                }
            }
            State.REST -> {
                // Stand a moment, then lie down.
                p.act = if (l.timer > 0f) Act.STAND else Act.LIE
                l.headTarget = 1f
            }
            State.LEAVE -> {
                if (l.timer > 0f) p.act = Act.STAND
                else if (move(l, l.walk * pace, dt, Act.WALK) || l.x < -0.2f || l.x > 1.2f) l.state = State.GONE
            }
            else -> Unit
        }
        p.bob = AnimalArt.bobFor(p.act, p.phase, p.t)
        AnimalArt.placeFeet(l.sp, p)
        smoothHead(l, dt)
    }

    // ---- Vultures ----------------------------------------------------------------------
    // The acacia stands empty while the herd grazes. When the lions come, vultures drift in
    // one by one, circle in a thermal, glide down to the front tree and wait for scraps; they
    // flap off once the lions leave. (From the in-chat birds preview.)

    private enum class VMode { IN, GLIDE, SIT, LEAVE, GONE }

    private class Vulture(val slot: Int) {
        var mode = VMode.GONE
        var t = 0f
        var x = 0f
        var y = 0f
        var dir = 1f
        var flap = 0f
        var soar = false
        var ang = 0f
        var cx = 0f
        var cy = 0f
        var rad = 0f
        var sx = 0f
        var sy = 0f
        var leaveDir = 1f
    }

    private val vultures = ArrayList<Vulture>()
    private val slotOrder = intArrayOf(2, 0, 4, 1, 3)

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    /** Preview pixels (a 680-wide canvas) to screen pixels. */
    private val pk get() = w / 680f

    private fun vulturesArrive() {
        vultures.clear()
        val n = SavannaDesign.VULTURES.coerceIn(1, 5)
        val sky = (L ?: return).hz
        for (i in 0 until n) {
            val v = Vulture(slotOrder[i])
            val fromLeft = rnd.nextBoolean()
            v.mode = VMode.IN
            v.t = -i * 2.5f - r() * 2f - 1.5f
            v.x = if (fromLeft) -60f * pk else w + 60f * pk
            v.y = sky * 0.06f
            v.dir = if (fromLeft) 1f else -1f
            v.ang = r() * 6f
            v.cx = w * (0.35f + r() * 0.3f)
            v.cy = sky * (0.15f + r() * 0.1f)
            v.rad = (50f + r() * 40f) * pk
            vultures += v
        }
    }

    private fun vulturesLeave() {
        for ((i, v) in vultures.withIndex()) {
            if (v.mode == VMode.GONE) continue
            if (v.mode == VMode.IN && v.t < 0f) { v.mode = VMode.GONE; continue } // never showed up
            v.mode = VMode.LEAVE
            v.t = -i * 1.2f - r()
            v.sx = v.x; v.sy = v.y
            v.leaveDir = if (v.x > w / 2f) 1f else -1f
        }
    }

    private fun updateVultures(dt: Float, lay: SavannaLayout) {
        val sky = lay.hz
        for ((i, v) in vultures.withIndex()) {
            v.t += dt
            if (v.t < 0f) continue
            when (v.mode) {
                VMode.IN -> {
                    val tx = v.cx + cos(v.ang) * v.rad
                    val ty = v.cy + sin(v.ang) * v.rad * 0.3f
                    val k = min(1f, dt * 0.9f)
                    v.x += (tx - v.x) * k
                    v.y += (ty - v.y) * k
                    v.ang += 0.45f * dt
                    v.cy += dt * sky * 0.015f
                    if (v.t < 2f) v.flap += dt * 8f
                    v.soar = v.t >= 2f
                    v.dir = if (cos(v.ang + 1.57f) > 0f) -1f else 1f
                    if (v.t > (9f + i * 2f) / pace) { v.mode = VMode.GLIDE; v.t = 0f; v.sx = v.x; v.sy = v.y }
                }
                VMode.GLIDE -> {
                    val (px, py) = lay.perch(v.slot)
                    val u = min(1f, v.t / 7f)
                    val s = smooth(u)
                    v.x = v.sx + (px - v.sx) * s
                    v.y = v.sy + (py - v.sy) * s - sin(u * PI.toFloat()) * sky * 0.05f
                    v.dir = if (px > v.sx) 1f else -1f
                    v.soar = u < 0.82f
                    if (!v.soar) v.flap += dt * 11f // flare to land
                    if (u >= 1f) {
                        v.mode = VMode.SIT; v.t = 0f
                        v.x = px; v.y = py
                        v.dir = if (lay.perchOffset(v.slot) < 0f) 1f else -1f
                    }
                }
                VMode.SIT -> Unit
                VMode.LEAVE -> {
                    val u = min(1f, v.t / 6f)
                    v.x = v.sx + v.leaveDir * w * 0.7f * u * u
                    v.y = v.sy - sky * 0.55f * sin(u * PI.toFloat() / 2f)
                    v.dir = v.leaveDir
                    v.soar = u > 0.5f
                    v.flap += dt * 9f
                    if (u >= 1f) v.mode = VMode.GONE
                }
                VMode.GONE -> Unit
            }
        }
    }

    // ---- Drawing -------------------------------------------------------------------------

    private fun setTint(d: Float, silh: Float, haze: Int) {
        tintHazeColor = haze
        tintHaze = if (d < 0.12f) (0.12f - d) / 0.12f * 0.55f else 0f
        tintSilh = silh * 0.75f
    }

    /** Draws the animals whose depth lies in [d0, d1), far to near. */
    fun drawBand(c: Canvas, d0: Float, d1: Float, silh: Float, haze: Int) {
        val lay = L ?: return
        sorted.clear()
        for (a in animals) if (a.state != State.AWAY && a.d >= d0 && a.d < d1) sorted += a
        for (l in lions) if (l.state != State.GONE && l.d >= d0 && l.d < d1) sorted += l
        sorted.sortBy { it.d }
        for (a in sorted) {
            val s = pxSize(a)
            val sx = a.x * w
            if (sx < -s * 1.5f || sx > w + s * 1.5f) continue
            val sy = lay.hz + a.d * lay.gH
            shadow.color = withAlpha(0xFF2A2010.toInt(), (60 * (1f - silh)).toInt())
            val lift = (-a.pose.bob).coerceAtLeast(0f)
            val sh = 1f - lift * 0.8f
            c.drawOval(sx - s * 0.45f * sh, sy - s * 0.035f, sx + s * 0.45f * sh, sy + s * 0.035f, shadow)
            setTint(a.d, silh, haze)
            c.save()
            c.translate(sx, sy)
            c.scale(a.dir * s, s)
            art.draw(c, a.sp, a.pose, s > 40f * dp)
            c.restore()
        }
    }

    /** Vulture size: the preview drew them at 0.034 × the tree's height × the size setting. */
    private fun vultureUnit(lay: SavannaLayout) = lay.bigSize * 0.034f * SavannaDesign.VULTURE_SIZE

    fun drawPerched(c: Canvas, silh: Float) {
        val lay = L ?: return
        val u = vultureUnit(lay)
        for ((i, v) in vultures.withIndex()) {
            if (v.mode != VMode.SIT) continue
            setTint(1f, silh, 0)
            val t = v.t
            c.save()
            c.translate(v.x, v.y + u * 0.3f)
            c.scale(v.dir * u, u)
            val look = if (sin(t * 0.25f + i) > 0.6f) 0.15f else 0f
            art.vulture(c, perched = true, flap = 0f, soaring = false, bob = sin(t * 0.7f + i * 2f) * 0.06f, look = look)
            c.restore()
        }
    }

    fun drawFlying(c: Canvas, silh: Float) {
        val lay = L ?: return
        val u = vultureUnit(lay) * 1.05f
        for (v in vultures) {
            if (v.t < 0f || v.mode == VMode.SIT || v.mode == VMode.GONE) continue
            setTint(1f, silh, 0)
            c.save()
            c.translate(v.x, v.y)
            c.scale(v.dir * u, u)
            art.vulture(c, perched = false, flap = v.flap, soaring = v.soar, bob = 0f)
            c.restore()
        }
    }
}
