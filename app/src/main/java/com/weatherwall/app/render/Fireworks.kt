package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class Burst { PEONY, CHRYS, WILLOW, RING, PALM, STROBE, CRACKLE, HEART, CRESCENT }

class Holiday(val name: String, val colours: IntArray, val bursts: Array<Burst>)

/** South African public holidays and widely celebrated days that get fireworks, by evening. */
object Holidays {
    private fun c(vararg hex: Long) = IntArray(hex.size) { hex[it].toInt() }
    private val FLAG = c(0xFF007749, 0xFFFFB81C, 0xFFE03C31, 0xFF001489, 0xFFFFFFFF)

    private fun h(name: String, colours: IntArray, vararg bursts: Burst) = Holiday(name, colours, arrayOf(*bursts))

    val NEW_YEARS_EVE = h("New Year's Eve", c(0xFFFFD84D, 0xFFFFFFFF, 0xFFFF4D6D, 0xFF4DC3FF, 0xFFB06BFF, 0xFF5DFF8A),
        Burst.PEONY, Burst.CHRYS, Burst.WILLOW, Burst.RING, Burst.STROBE, Burst.PALM)
    private val NEW_YEAR = h("New Year's Day", c(0xFFFFD84D, 0xFFFFFFFF, 0xFF4DC3FF), Burst.PEONY, Burst.CHRYS, Burst.STROBE)
    private val TWEEDE = h("Tweede Nuwe Jaar", c(0xFFFF2FA0, 0xFFFFE14D, 0xFF3CF0FF, 0xFF7CFF4D, 0xFFFF7A1A), Burst.PEONY, Burst.PALM, Burst.CRACKLE)
    private val CHINESE_NY = h("Chinese New Year", c(0xFFFF2A2A, 0xFFFFCF3D, 0xFFFFE9A8), Burst.PEONY, Burst.CRACKLE, Burst.CHRYS)
    private val VALENTINE = h("Valentine's Day", c(0xFFFF4D8D, 0xFFFF9CC2, 0xFFFFFFFF, 0xFFE0183A), Burst.HEART, Burst.PEONY)
    private val HUMAN_RIGHTS = h("Human Rights Day", FLAG, Burst.PEONY, Burst.RING, Burst.WILLOW)
    private val EID = h("Eid al-Fitr", c(0xFF2ECC71, 0xFFFFD84D, 0xFFFFFFFF), Burst.PEONY, Burst.CRESCENT, Burst.CHRYS)
    private val EASTER = h("Easter", c(0xFFFFE36B, 0xFFFFFFFF, 0xFFC9A7FF, 0xFF9BE7A0), Burst.PEONY, Burst.WILLOW)
    private val FREEDOM = h("Freedom Day", FLAG, Burst.PEONY, Burst.RING, Burst.PALM, Burst.STROBE)
    private val WORKERS = h("Workers' Day", c(0xFFE03C31, 0xFFFFFFFF, 0xFFFFB81C), Burst.PEONY, Burst.CHRYS)
    private val MOTHERS = h("Mother's Day", c(0xFFFF9CC2, 0xFFE7B3FF, 0xFFFFFFFF), Burst.PEONY, Burst.HEART, Burst.WILLOW)
    private val YOUTH = h("Youth Day", FLAG, Burst.PEONY, Burst.STROBE, Burst.PALM)
    private val FATHERS = h("Father's Day", c(0xFF4DC3FF, 0xFFFFFFFF, 0xFFFFD84D), Burst.PEONY, Burst.RING)
    private val MANDELA = h("Mandela Day", c(0xFFFFB81C, 0xFF007749, 0xFFFFFFFF), Burst.PEONY, Burst.WILLOW)
    private val WOMENS = h("National Women's Day", c(0xFFB06BFF, 0xFFFF9CC2, 0xFFFFFFFF, 0xFFFFD84D), Burst.PEONY, Burst.CHRYS, Burst.WILLOW)
    private val HERITAGE = h("Heritage Day", FLAG, Burst.PEONY, Burst.PALM, Burst.CRACKLE, Burst.RING)
    private val DIWALI = h("Diwali", c(0xFFFFB21A, 0xFFFF6A00, 0xFFFFE28A, 0xFFFF3D7F, 0xFFFFFFFF), Burst.CRACKLE, Burst.WILLOW, Burst.CHRYS, Burst.PEONY)
    private val GUY_FAWKES = h("Guy Fawkes", c(0xFFFFD84D, 0xFFFF4D4D, 0xFF4DC3FF, 0xFFFFFFFF), Burst.PEONY, Burst.STROBE, Burst.CRACKLE)
    private val RECONCILIATION = h("Day of Reconciliation", FLAG, Burst.PEONY, Burst.RING, Burst.WILLOW)
    private val CHRISTMAS = h("Christmas", c(0xFFE0183A, 0xFF1FBF5A, 0xFFFFD84D, 0xFFFFFFFF), Burst.PEONY, Burst.WILLOW, Burst.STROBE)

    /** Every holiday, for the debug hook. */
    val ALL = listOf(NEW_YEARS_EVE, NEW_YEAR, TWEEDE, CHINESE_NY, VALENTINE, HUMAN_RIGHTS, EID, EASTER, FREEDOM, WORKERS,
        MOTHERS, YOUTH, FATHERS, MANDELA, WOMENS, HERITAGE, DIWALI, GUY_FAWKES, RECONCILIATION, CHRISTMAS)

    // Dates that follow other calendars (month, day), 2026–2032.
    private val DIWALI_DATES = mapOf(2026 to (11 to 8), 2027 to (10 to 29), 2028 to (10 to 17), 2029 to (11 to 5), 2030 to (10 to 26), 2031 to (11 to 14), 2032 to (11 to 2))
    private val EID_DATES = mapOf(2026 to (3 to 20), 2027 to (3 to 10), 2028 to (2 to 27), 2029 to (2 to 15), 2030 to (2 to 5), 2031 to (1 to 25), 2032 to (1 to 14))
    private val CNY_DATES = mapOf(2026 to (2 to 17), 2027 to (2 to 6), 2028 to (1 to 26), 2029 to (2 to 13), 2030 to (2 to 3), 2031 to (1 to 23), 2032 to (2 to 11))

    /** Easter Sunday (month 1-based, day), Gregorian. */
    private fun easter(y: Int): Pair<Int, Int> {
        val a = y % 19; val b = y / 100; val cc = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val hh = (19 * a + b - d - g + 15) % 30
        val i = cc / 4; val k = cc % 4; val l = (32 + 2 * e + 2 * i - hh - k) % 7; val m = (a + 11 * hh + 22 * l) / 451
        val month = (hh + l - 7 * m + 114) / 31; val day = (hh + l - 7 * m + 114) % 31 + 1
        return month to day
    }

    /** The [n]th Sunday of [month] (1-based) in [y]. */
    private fun nthSunday(y: Int, month: Int, n: Int): Int {
        val cal = Calendar.getInstance().apply { clear(); set(y, month - 1, 1) }
        val first = (Calendar.SUNDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7 + 1
        return first + (n - 1) * 7
    }

    private val GREETINGS = mapOf(
        "New Year's Eve" to "Happy New Year's Eve!", "New Year's Day" to "Happy New Year!",
        "Tweede Nuwe Jaar" to "Happy Tweede Nuwe Jaar!", "Chinese New Year" to "Happy Chinese New Year!",
        "Valentine's Day" to "Happy Valentine's Day!", "Human Rights Day" to "Happy Human Rights Day!",
        "Eid al-Fitr" to "Eid Mubarak!", "Freedom Day" to "Happy Freedom Day!", "Workers' Day" to "Happy Workers' Day!",
        "Mother's Day" to "Happy Mother's Day!", "Youth Day" to "Happy Youth Day!", "Father's Day" to "Happy Father's Day!",
        "Mandela Day" to "Happy Mandela Day!", "National Women's Day" to "Happy Women's Day!",
        "Heritage Day" to "Happy Heritage Day!", "Diwali" to "Happy Diwali!", "Guy Fawkes" to "Happy Guy Fawkes!",
        "Day of Reconciliation" to "Happy Reconciliation Day!",
    )

    /** What the banner plane says on [holiday], on [cal]'s date. */
    fun greeting(holiday: Holiday, cal: Calendar): String {
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return when (holiday) {
            CHRISTMAS -> if (d == 26) "Happy Day of Goodwill!" else "Merry Christmas!"
            EASTER -> {
                val (em, ed) = easter(cal.get(Calendar.YEAR))
                val e = Calendar.getInstance().apply { clear(); set(cal.get(Calendar.YEAR), em - 1, ed) }
                when (cal.get(Calendar.DAY_OF_YEAR) - e.get(Calendar.DAY_OF_YEAR)) {
                    -2 -> "Blessed Good Friday"
                    1 -> "Happy Family Day!"
                    else -> "Happy Easter!"
                }
            }
            else -> GREETINGS[holiday.name] ?: "Happy ${holiday.name}!"
        }
    }

    /** The holiday whose evening falls on [cal]'s date, if any (first match wins). */
    fun forEvening(cal: Calendar): Holiday? {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        fun on(md: Pair<Int, Int>?) = md != null && md.first == m && md.second == d
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val easterCal = easter(y).let { (em, ed) -> Calendar.getInstance().apply { clear(); set(y, em - 1, ed) } }
        val fromEaster = dayOfYear - easterCal.get(Calendar.DAY_OF_YEAR)
        return when {
            m == 12 && d == 31 -> NEW_YEARS_EVE
            m == 1 && d == 1 -> NEW_YEAR
            m == 1 && d == 2 -> TWEEDE
            on(DIWALI_DATES[y]) -> DIWALI
            on(EID_DATES[y]) -> EID
            on(CNY_DATES[y]) -> CHINESE_NY
            fromEaster in -2..1 -> EASTER // Good Friday, Saturday, Easter Sunday, Family Day
            m == 2 && d == 14 -> VALENTINE
            m == 3 && d == 21 -> HUMAN_RIGHTS
            m == 4 && d == 27 -> FREEDOM
            m == 5 && d == 1 -> WORKERS
            m == 5 && d == nthSunday(y, 5, 2) -> MOTHERS
            m == 6 && d == 16 -> YOUTH
            m == 6 && d == nthSunday(y, 6, 3) -> FATHERS
            m == 7 && d == 18 -> MANDELA
            m == 8 && d == 9 -> WOMENS
            m == 9 && d == 24 -> HERITAGE
            m == 11 && d == 5 -> GUY_FAWKES
            m == 12 && d == 16 -> RECONCILIATION
            m == 12 && d in 24..26 -> CHRISTMAS
            else -> null
        }
    }
}

/**
 * Holiday fireworks (from the in-chat fireworks preview): rockets launched from the skyline
 * burst over it in the holiday's colours and shapes, with drifting smoke and, where there's
 * water, reflections. Sizes are in the preview's pixels (a 270-wide canvas).
 */
class Fireworks {

    private class Rocket(var x: Float, var y: Float, val vx: Float, var vy: Float, val ty: Float, val c1: Int, val c2: Int, val burst: Burst)
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float, val c: Int,
        val max: Float, val drag: Float, val g: Float, val size: Float, val trail: Boolean,
        val strobe: Boolean = false, val crackle: Boolean = false,
    ) { var life = 0f; var ox = x; var oy = y }
    private class Smoke(val x0: Float, val y: Float, var r: Float, val max: Float) { var life = 0f; var x = x0 }

    private val rnd = Random(System.nanoTime())
    private val rockets = ArrayList<Rocket>()
    private val sparks = ArrayList<Spark>()
    private val smoke = ArrayList<Smoke>()
    private var w = 0f
    private var h = 0f
    private var t = 0f
    private var next = 0.5f

    /** Tonight's holiday, or null when there are no fireworks. */
    var holiday: Holiday? = null
    /** 1 = the full pace; less late at night; plus [finale] for New Year's midnight. */
    var pace = 1f
    var finale = false
    /** Where the rockets go up from (the skyline / shore), and whether there's water to reflect in. */
    var groundY = 0f
    var water = false

    val active get() = holiday != null || rockets.isNotEmpty() || sparks.isNotEmpty()

    private val k get() = w / PREVIEW_W

    fun setSize(width: Int, height: Int) { w = width.toFloat(); h = height.toFloat() }

    private fun r() = rnd.nextFloat()

    fun update(dt: Float) {
        t += dt
        val hol = holiday
        if (hol != null && w > 0f) {
            next -= dt * (BUSY * pace + if (finale) 6f else 0f)
            if (next <= 0f) { launch(hol); next = 0.6f + r() * 1.2f }
        }
        val gr = 120f * k
        val it = rockets.iterator()
        while (it.hasNext()) {
            val rk = it.next()
            rk.vy += gr * dt; rk.x += rk.vx * dt; rk.y += rk.vy * dt
            if (rk.vy >= -8f * k || rk.y <= rk.ty) { explode(rk); it.remove() }
        }
        for (s in sparks) {
            s.life += dt
            s.ox = s.x; s.oy = s.y
            val dr = s.drag.pow(dt * 60f)
            s.vx *= dr; s.vy = s.vy * dr + s.g * dt
            s.x += s.vx * dt; s.y += s.vy * dt
        }
        sparks.removeAll { it.life >= it.max || it.y > groundY + 5f * k }
        for (s in smoke) { s.life += dt; s.x += 4f * k * dt; s.r += 6f * k * dt }
        smoke.removeAll { it.life >= it.max }
    }

    private fun launch(hol: Holiday) {
        val x = w * (0.15f + r() * 0.7f)
        // Bursts sit between the top of the sky and a little above the skyline.
        val ty = h * 0.14f + r() * (groundY - h * 0.28f).coerceAtLeast(h * 0.1f) * 0.75f
        val vy = -sqrt(2f * 120f * k * (groundY - ty))
        rockets += Rocket(x, groundY - 4f * k, (r() - 0.5f) * 12f * k, vy, ty,
            hol.colours[rnd.nextInt(hol.colours.size)], hol.colours[rnd.nextInt(hol.colours.size)], hol.bursts[rnd.nextInt(hol.bursts.size)])
    }

    private fun explode(rk: Rocket) {
        val s = SIZE * (0.8f + r() * 0.5f)
        val n = (70 * s).toInt()
        val sp = 55f * s * k
        val g = 22f * k
        fun add(vx: Float, vy: Float, c: Int, max: Float = 1.4f + r() * 0.8f, drag: Float = 0.985f, grav: Float = g, size: Float = 1.4f, trail: Boolean = false, strobe: Boolean = false, crackle: Boolean = false) {
            sparks += Spark(rk.x, rk.y, vx, vy, c, max, drag, grav, size * k, trail, strobe, crackle)
        }
        val tau = 2f * PI.toFloat()
        when (rk.burst) {
            Burst.PEONY -> repeat(n) { i -> val a = r() * tau; val v = sp * (0.7f + r() * 0.3f); add(cos(a) * v, sin(a) * v, if (i % 3 == 0) rk.c2 else rk.c1) }
            Burst.CHRYS -> repeat(n) { i -> val a = i.toFloat() / n * tau; val v = sp * (0.9f + r() * 0.15f); add(cos(a) * v, sin(a) * v, rk.c1, max = 1.6f, trail = true) }
            Burst.WILLOW -> repeat(n) { val a = r() * tau; val v = sp * (0.55f + r() * 0.35f); add(cos(a) * v, sin(a) * v, Color.rgb(255, 190, 80), max = 2.8f + r(), drag = 0.97f, grav = 40f * k, size = 1.1f, trail = true) }
            Burst.RING -> {
                val m = (n * 0.7f).toInt()
                repeat(m) { i -> val a = i.toFloat() / m * tau; add(cos(a) * sp, sin(a) * sp * 0.45f, rk.c1) }
                repeat(20) { val a = r() * tau; add(cos(a) * sp * 0.3f, sin(a) * sp * 0.3f, rk.c2) }
            }
            Burst.PALM -> repeat(9) { i -> val a = -PI.toFloat() / 2 + (i - 4) * 0.32f; add(cos(a) * sp * 1.1f, sin(a) * sp * 1.1f, rk.c1, max = 2f, grav = 30f * k, size = 2.2f, trail = true) }
            Burst.STROBE -> repeat((n * 0.7f).toInt()) { val a = r() * tau; val v = sp * r(); add(cos(a) * v, sin(a) * v, Color.WHITE, max = 2.2f, grav = 12f * k, strobe = true) }
            Burst.CRACKLE -> repeat(n) { val a = r() * tau; val v = sp * (0.6f + r() * 0.5f); add(cos(a) * v, sin(a) * v, rk.c1, max = 1.1f + r() * 0.5f, crackle = true) }
            Burst.HEART -> repeat(n) { i ->
                val q = i.toFloat() / n * tau
                val hx = 16f * sin(q).pow(3)
                val hy = -(13f * cos(q) - 5f * cos(2 * q) - 2f * cos(3 * q) - cos(4 * q))
                add(hx * sp / 17f, hy * sp / 17f, rk.c1, max = 1.8f, drag = 0.975f, grav = 8f * k)
            }
            Burst.CRESCENT -> {
                repeat(n) { i ->
                    val a = i.toFloat() / n * tau
                    val x = cos(a); val y = sin(a)
                    if (hypot(x - 0.45f, y) >= 0.85f) add(x * sp, y * sp, rk.c1, max = 1.9f, drag = 0.975f, grav = 8f * k)
                }
                repeat(14) { val a = r() * tau; add(cos(a) * sp * 0.2f + sp * 0.6f, sin(a) * sp * 0.2f - sp * 0.4f, Color.WHITE, max = 1.5f, grav = 6f * k) }
            }
        }
        repeat(6) { smoke += Smoke(rk.x + (r() - 0.5f) * 30f * s * k, rk.y + (r() - 0.5f) * 30f * s * k, 20f * s * k, 8f + r() * 4f) }
    }

    private val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); strokeCap = Paint.Cap.ROUND }

    fun draw(c: Canvas) {
        if (!active) return
        smokePaint.style = Paint.Style.FILL
        for (s in smoke) {
            smokePaint.color = Color.argb((255 * 0.06f * (1f - s.life / s.max)).toInt().coerceIn(0, 255), 150, 140, 170)
            c.drawCircle(s.x, s.y, s.r, smokePaint)
        }
        // Rockets: a bright head and a short fading tail.
        add.style = Paint.Style.FILL
        for (rk in rockets) {
            add.color = Color.argb(230, 255, 220, 160); c.drawRect(rk.x - 0.7f * k, rk.y, rk.x + 0.7f * k, rk.y + 3f * k, add)
            add.color = Color.argb(90, 255, 180, 90); c.drawRect(rk.x - 0.5f * k, rk.y + 3f * k, rk.x + 0.5f * k, rk.y + 11f * k, add)
        }
        for (s in sparks) {
            var a = max(0f, 1f - s.life / s.max)
            if (s.strobe && sin(t * 40f + s.x) <= 0.3f) a = 0f
            if (s.crackle && s.life > s.max * 0.6f) a *= if (r() > 0.5f) 1.5f else 0f
            if (a <= 0f) continue
            val rr = Color.red(s.c); val gg = Color.green(s.c); val bb = Color.blue(s.c)
            add.style = Paint.Style.STROKE
            add.strokeWidth = s.size * (if (s.trail) 1f else 0.9f)
            add.color = Color.argb((255 * a * if (s.trail) 0.6f else 0.45f).toInt().coerceIn(0, 255), rr, gg, bb)
            val tx = if (s.trail) s.vx * 0.05f else 0f
            val ty = if (s.trail) s.vy * 0.05f else 0f
            c.drawLine(s.ox - tx, s.oy - ty, s.x, s.y, add)
            add.style = Paint.Style.FILL
            add.color = Color.argb((255 * a).toInt().coerceIn(0, 255), rr, gg, bb)
            c.drawRect(s.x - s.size / 2, s.y - s.size / 2, s.x + s.size / 2, s.y + s.size / 2, add)
            if (water && a > 0.2f && s.y < groundY) { // a quick reflection on the bay
                val ry = groundY + (groundY - s.y) * 0.25f
                if (ry < h) { add.color = Color.argb((255 * a * 0.25f).toInt(), rr, gg, bb); c.drawRect(s.x - k, ry, s.x + k, ry + 3f * k, add) }
            }
        }
    }

    companion object {
        const val PREVIEW_W = 270f
        const val BUSY = 0.6f
        const val SIZE = 1.3f
    }
}
