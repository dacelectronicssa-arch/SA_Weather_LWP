package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.abs
import kotlin.random.Random

/**
 * Owns the active scene and crossfades to a new one when the weather changes.
 * Also sends visitors past in independent lanes (up to three boats, the banner plane, and
 * whales or dolphins), which freely overlap, and never lets the scene sit completely empty.
 */
class WeatherRenderer(private val density: Float) {

    private var width = 0
    private var height = 0
    private var config: SceneConfig? = null
    private var current: WeatherScene? = null
    private var previous: WeatherScene? = null
    private var fade = 1f

    private val rnd = Random(System.nanoTime())
    private val plane = BannerPlane(density)
    private val ship = CruiseShip(density)
    private val yacht = Yacht(density)
    private val jetski = JetSki(density)
    private val containerShip = ContainerShip(density)
    private val sea = MarineLife(density)
    private val birds = HadedaFlock(density)
    // The savanna's animals live here, not in a scene, so they carry on through weather changes.
    private val wildlife = SavannaWildlife(density)
    private val geese = GooseSkein()
    // The Airlink stadium flypast over Cape Town Stadium, every 15 minutes or so by day.
    private val flypast = StadiumFlypast()
    private var nextFlypastAt = System.currentTimeMillis() + between(120, 360)
    // 737s over Jhb, every 40 s or so.
    private val jets = JhbAirTraffic()
    private var nextJetAt = System.currentTimeMillis() + 3_000L + Random.nextLong(10_000L)
    private var jetsBefore = 0
    // Holiday fireworks, from half an hour after sunset until sunrise, in every scene.
    private val fireworks = Fireworks()
    private var fireworksCheckIn = 0f
    /** Debug: force a holiday's fireworks now (and the New Year's finale). */
    var forcedHoliday: Holiday? = null
    var forcedFinale = false
    // Pigeon flocks over Jhb, landing on the rooftops.
    private val pigeons = PigeonFlock()
    private var nextPigeonsAt = System.currentTimeMillis() + 8_000L + Random.nextLong(15_000L)
    private var pigeonsWereActive = false
    private var nextGeeseAt = System.currentTimeMillis() + 20_000L + Random.nextLong(40_000L)
    private var geeseWereActive = false

    private val start = System.currentTimeMillis()
    private var nextShipAt = start + between(3, 10)
    private var nextPlaneAt = start + between(15, 40)
    private var nextSeaAt = start + between(2, 8)
    private var nextBirdsAt = start + between(10, 30)
    private var birdsWereActive = false
    private var lastSeaKind: MarineLife.Kind? = null
    private var shipWasActiveCount = 0
    private var planeWasActive = false
    private var seaWasActive = false

    // Everything on the bay, drawn between the far shore and the beach.
    private val drawShip: (Canvas) -> Unit = { c ->
        val cfg = config
        val night = cfg?.night ?: false
        val haze = if (cfg != null && (cfg.fog || cfg.rain > 200)) 0.7f else 1f
        // Far boats first, so nearer ones pass in front.
        drawOrder.clear()
        if (ship.active) drawOrder += MSC_DEPTH to { k: Canvas -> ship.draw(k, night, haze) }
        for (b in listOf(yacht, jetski, containerShip)) if (b.active || b === jetski) drawOrder += b.depth to { k: Canvas -> b.draw(k, haze) }
        drawOrder.sortBy { it.first }
        for ((_, draw) in drawOrder) draw(c)
        sea.draw(c, night)
    }

    private val drawOrder = ArrayList<Pair<Float, (Canvas) -> Unit>>()

    fun setSize(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        previous = null
        fade = 1f
        current = config?.let { WeatherScene(it, w, h, density, wildlife) }
        plane.setSize(w, h)
        ship.setSize(w, h)
        yacht.setSize(w, h)
        jetski.setSize(w, h)
        containerShip.setSize(w, h)
        sea.setSize(w, h)
        birds.setSize(w, h)
        wildlife.setSize(w, h)
        geese.setSize(w, h)
        flypast.setSize(w, h)
        jets.setSize(w, h)
        fireworks.setSize(w, h)
        pigeons.setSize(w, h)
    }

    fun setConfig(cfg: SceneConfig, animate: Boolean) {
        if (cfg == config) return
        config = cfg
        if (width == 0 || height == 0) return
        previous = if (animate) current else null
        current = WeatherScene(cfg, width, height, density, wildlife)
        fade = if (previous == null) 1f else 0f
    }

    /** Called when the wallpaper comes into view: visitors that came due while hidden arrive shortly. */
    fun onVisible() {
        val now = System.currentTimeMillis()
        if (now >= nextShipAt) nextShipAt = now + between(1, 6)
        if (now >= nextPlaneAt) nextPlaneAt = now + between(4, 15)
        if (now >= nextSeaAt) nextSeaAt = now + between(1, 4)
        if (now >= nextBirdsAt) nextBirdsAt = now + between(3, 12)
    }

    /** Debug: whales or dolphins right away. */
    fun launchSeaLifeNow(kind: MarineLife.Kind?) {
        if (!sea.active) sea.launch(kind, haze())
    }

    /** Debug: send a yacht, jetski or container ship across right away. */
    fun launchBoatNow(kind: String) {
        val night = config?.night ?: false
        when (kind) {
            "yacht" -> if (!yacht.active) yacht.launch(0.45f, night)
            "jetski" -> if (!jetski.active) jetski.launch(0.75f)
            "container" -> if (!containerShip.active) containerShip.launch(0.3f, night)
        }
    }

    /** Debug: send the ship past right away. */
    fun launchShipNow() {
        if (!ship.active) ship.launch()
    }

    /** Debug: send a flock of hadedas over right away. */
    fun launchBirdsNow() {
        if (!birds.active) birds.launch(haze())
    }

    /** Debug: a skein of geese over the savanna right away. */
    fun launchGeeseNow() {
        if (!geese.active) geese.launch()
    }

    /**
     * Frames per second the scene needs right now. Battery saver: full speed only while
     * something moves quickly (rain, flapping birds, whales, the jetski, the flypast, running
     * animals, a crossfade), a little slower for planes and walking animals, and a gentle
     * 10 fps for drifting clouds, twinkling stars, swaying grass and slow ships.
     */
    fun desiredFps(): Int {
        // The settings choose the top rate; slow and calm rates scale from it.
        val full = maxFps
        val slowFps = (full * 2 / 3).coerceAtLeast(CALM_FPS)
        val calm = minOf(CALM_FPS, full)
        return when (motionTier()) { FAST -> full; SLOW -> slowFps; else -> calm }
    }

    /** Top frame rate from the settings (30, 24 or 15). */
    var maxFps = 24

    /** FAST = something moves quickly, SLOW = slow movers, CALM = nothing much. */
    private fun motionTier(): Int {
        val cfg = config ?: return CALM
        val scene = cfg.scene
        if (previous != null) return FAST
        if (fireworks.active) return FAST
        if (cfg.rain > 0 || cfg.snow > 0 || cfg.lightning) return FAST
        if (birds.active) return FAST
        var slow = false
        when (scene) {
            Scenes.CAPE_TOWN -> {
                if (sea.active || jetski.active || flypast.active) return FAST
                if (plane.active) slow = true
            }
            Scenes.SAVANNA -> {
                if (geese.active) return FAST
                when (wildlife.motion) { 2 -> return FAST; 1 -> slow = true }
            }
            Scenes.GARDEN -> {
                if (current?.gardenMotion == 2 || geese.active) return FAST
                if (plane.active) slow = true
            }
            Scenes.JHB, Scenes.ALTRON -> {
                if (pigeons.flying) return FAST
                if (jets.active) slow = true
            }
        }
        return if (slow) SLOW else CALM
    }

    /** For the debug frame-cost log: the scene, weather and what's on screen. */
    fun perfLabel() = "scene=${config?.scene} ${config?.condition}${if (config?.night == true) "/night" else ""}${if (previous != null) " fading" else ""}"

    /**
     * Tonight's fireworks: the holiday whose evening began at the last sunset, from 30 minutes
     * after it until sunrise; half pace from 1am; New Year's grand finale for five minutes from
     * midnight.
     */
    private fun planFireworks() {
        val scene = current
        val cfg = config
        if (scene == null || cfg == null) { fireworks.holiday = null; return }
        val now = SkyTime.now()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val set = scene.nightSetMs
        val rise = scene.nightRiseMs
        val holiday = forcedHoliday ?: if (set != Long.MIN_VALUE && rise != Long.MIN_VALUE && now in (set + 1_800_000L)..rise) {
            Holidays.forEvening(java.util.Calendar.getInstance().apply { timeInMillis = set })
        } else null
        fireworks.holiday = holiday
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        fireworks.pace = if (hour in 1..11) 0.5f else 1f
        fireworks.finale = forcedFinale || (holiday === Holidays.NEW_YEARS_EVE &&
            cal.get(java.util.Calendar.MONTH) == java.util.Calendar.JANUARY && cal.get(java.util.Calendar.DAY_OF_MONTH) == 1 &&
            hour == 0 && cal.get(java.util.Calendar.MINUTE) < 5)
        fireworks.groundY = height * Scenes.horizon(cfg.scene)
        fireworks.water = cfg.scene == Scenes.CAPE_TOWN
        // At the head office the rockets rise behind the building and burst above its roof.
        fireworks.burstBottom = if (fireworksBehind()) (current?.skylineY ?: height * 0.5f) - height * 0.06f else Float.NaN
    }

    /** On a holiday the banner plane tows a greeting (e.g. "Merry Christmas!") instead of its advert. */
    private fun setBannerMessage() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = SkyTime.now() }
        val holiday = holidayToday(cal)
        val greeting = holiday?.let { Holidays.greeting(it, cal) }
        val symbol = if (holiday != null && greeting != null) BannerSymbol.forHoliday(holiday, greeting) else null
        plane.setMessage(greeting, symbol, cal.get(java.util.Calendar.YEAR))
    }

    private var isHoliday = false // checked once a second

    /** Today's holiday (or the one being previewed), if any. */
    private fun holidayToday(cal: java.util.Calendar = java.util.Calendar.getInstance().apply { timeInMillis = SkyTime.now() }) =
        forcedHoliday ?: Holidays.forEvening(cal)

    /** The banner plane, kept clear of the Jhb skyline and the Altron head office's roof. */
    private fun launchPlane() {
        setBannerMessage()
        // Over the Garden Route it's just the plane, unless there's a holiday greeting to tow.
        val tow = config?.scene != Scenes.GARDEN || holidayToday() != null
        if (cityTraffic()) plane.launch(haze(), top = 0.24f, band = 0.1f) else plane.launch(haze(), tow = tow)
    }

    /** Debug: the Airlink stadium flypast right away. */
    fun launchFlypastNow() = flypast.launch()

    /** Debug: a flock of pigeons over Jhb right away. */
    fun launchPigeonsNow() {
        if (!pigeons.active) pigeons.launch(current?.roofSpots(pigeons.roofGap) ?: emptyList())
    }

    /** Debug: a 737 over Jhb right away. */
    fun launchJetNow() = jets.launch()

    /** Debug and demo: a Garden Route visitor now (train, jump, dolphins, jetski, breach). */
    fun launchGardenNow(what: String) = current?.gardenNow(what)

    /** Debug: the lions arrive on the savanna right away. */
    fun launchLionsNow(fast: Boolean = false) = wildlife.lionsNow(fast)

    /** Debug: start a flight right away. */
    fun launchPlaneNow() {
        if (!plane.active) launchPlane()
    }

    /**
     * Ultra power saver's once-a-minute still frame: moves the sun, moon and stars to the
     * current time and keeps tonight's fireworks plan and holiday up to date, while clouds,
     * rain, visitors and fireworks hold still (and no new visitors set out).
     */
    fun stillTick() {
        current?.syncSky()
        previous?.syncSky()
        planFireworks()
        isHoliday = holidayToday() != null
    }

    fun update(dt: Float) {
        current?.update(dt)
        previous?.update(dt)
        if (fade < 1f) {
            fade = (fade + dt / FADE_SECONDS).coerceAtMost(1f)
            if (fade >= 1f) previous = null
        }

        val now = System.currentTimeMillis()
        val day = config?.night == false
        val savanna = config?.scene == Scenes.SAVANNA
        // The sea and its boats belong to Cape Town; the banner plane too, except on holidays.
        // The Garden Route has its own bay, and the plane (without a banner) flies there every day.
        val inland = config?.scene.let { it != null && it != Scenes.CAPE_TOWN }
        val garden = config?.scene == Scenes.GARDEN
        val planeHere = !inland || isHoliday || garden
        if (savanna) wildlife.update(dt)
        if (!inland && now >= nextFlypastAt) {
            if (planeMayFly() && !plane.active) {
                flypast.launch()
                android.util.Log.d("WeatherWallpaper", "Visitor: FLYPAST")
                val gap = StadiumFlypast.MINUTES_BETWEEN * 60
                nextFlypastAt = now + between(gap * 7 / 10, gap * 13 / 10)
            } else nextFlypastAt = now + between(60, 120)
        }
        flypast.update(dt)
        fireworksCheckIn -= dt
        if (fireworksCheckIn <= 0f) { fireworksCheckIn = 1f; planFireworks(); isHoliday = holidayToday() != null }
        fireworks.update(dt)
        // Egyptian geese over the savanna and the Garden Route, by day, every couple of minutes.
        if (geeseWereActive && !geese.active) nextGeeseAt = now + between(90, 240)
        if ((savanna || garden) && !geese.active && now >= nextGeeseAt) {
            if (day) { geese.launch(); android.util.Log.d("WeatherWallpaper", "Visitor: GEESE") } else nextGeeseAt = now + between(60, 120)
        }
        geeseWereActive = geese.active
        geese.update(dt)
        if (cityTraffic()) {
            // Mostly one jet at a time, now and then two, with a short clear sky in between.
            val n = jets.count
            if (n == 0 && (jetsBefore > 0 || nextJetAt == Long.MAX_VALUE)) nextJetAt = now + between(3, 10)
            if (n < 2 && now >= nextJetAt) {
                jets.launch()
                nextJetAt = if (n == 0 && rnd.nextFloat() < 0.3f) now + between(6, 14) else Long.MAX_VALUE
            }
            jetsBefore = jets.count
        }
        if (cityTraffic()) {
            if (pigeonsWereActive && !pigeons.active) nextPigeonsAt = now + between(15, 45)
            if (!pigeons.active && now >= nextPigeonsAt) {
                if (day) pigeons.launch(current?.roofSpots(pigeons.roofGap) ?: emptyList()) else nextPigeonsAt = now + between(60, 120)
            }
            pigeonsWereActive = pigeons.active
        }
        pigeons.update(dt)
        jets.night = config?.night ?: false
        jets.update(dt)

        // When a visitor leaves, its lane books the next one.
        if (boatsOnWater() < shipWasActiveCount) nextShipAt = minOf(nextShipAt, now + between(10, 40))
        if (planeWasActive && !plane.active) nextPlaneAt = now + between(20, 60)
        if (seaWasActive && !sea.active) nextSeaAt = now + between(5, 20)
        if (birdsWereActive && !birds.active) nextBirdsAt = now + between(15, 45)

        if (inland) {
            // No sea inland (and no banner plane, unless it's a holiday): hadedas pass over (and the herd on the savanna).
            nextShipAt = maxOf(nextShipAt, now + 5_000L)
            if (!planeHere) nextPlaneAt = maxOf(nextPlaneAt, now + 15_000L)
            nextSeaAt = maxOf(nextSeaAt, now + 2_000L)
        }
        if (!inland && boatsOnWater() < BoatDesign.MAX_BOATS && now >= nextShipAt) { // day and night, any weather
            launch(Lane.SHIP)
            nextShipAt = now + between(15, 45)
        }
        if (planeHere && !plane.active && !flypast.active && now >= nextPlaneAt) {
            if (planeMayFly()) launch(Lane.PLANE) else nextPlaneAt = now + between(30, 60)
        }
        if (!inland && !sea.active && now >= nextSeaAt) {
            if (day) launch(Lane.SEA) else nextSeaAt = now + between(30, 60) // invisible at night
        }
        if (!birds.active && now >= nextBirdsAt) {
            if (day) launch(Lane.BIRDS) else nextBirdsAt = now + between(30, 60) // hadedas roost at night
        }
        // Never an empty scene: whales or dolphins by day, the ship at night.
        if (!inland && !busy()) launch(if (day) Lane.SEA else Lane.SHIP)

        shipWasActiveCount = boatsOnWater()
        planeWasActive = plane.active
        seaWasActive = sea.active
        birdsWereActive = birds.active

        plane.update(dt)
        birds.update(dt)
        ship.update(dt)
        yacht.update(dt)
        jetski.update(dt)
        containerShip.update(dt)
        sea.update(dt)
    }

    fun draw(canvas: Canvas) {
        val cur = current ?: run { canvas.drawColor(Color.BLACK); return }
        val prev = previous
        val behind = if (fireworksBehind()) drawFireworks else null
        if (prev == null) {
            cur.draw(canvas, drawShip, behind)
        } else {
            prev.draw(canvas, drawShip, behind)
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (fade * 255).toInt())
            cur.draw(canvas, drawShip, behind)
            canvas.restore()
        }
        if (behind == null) fireworks.draw(canvas)
        if (config?.scene == Scenes.CAPE_TOWN) flypast.draw(canvas)
        plane.draw(canvas)
        birds.draw(canvas)
        if (config?.scene == Scenes.SAVANNA || config?.scene == Scenes.GARDEN) geese.draw(canvas)
        if (cityTraffic()) { pigeons.draw(canvas); jets.draw(canvas) }
    }

    /** Scenes with Johannesburg's 737s overhead and pigeons on the rooftops. */
    private fun cityTraffic() = config?.scene.let { it == Scenes.JHB || it == Scenes.ALTRON }

    /** Scenes where the fireworks go up behind the landmark instead of in front of everything. */
    private fun fireworksBehind() = config?.scene == Scenes.ALTRON
    private val drawFireworks: (Canvas) -> Unit = { fireworks.draw(it) }

    private fun busy() = plane.active || boatsOnWater() > 0 || sea.active || birds.active

    private fun boatsOnWater() =
        (if (ship.active) 1 else 0) + (if (yacht.active) 1 else 0) + (if (jetski.active) 1 else 0) + (if (containerShip.active) 1 else 0)

    /**
     * Sends out a boat that isn't already on the water, in a distance band clear of the others:
     * the MSC Armonia and container ships stay out in the bay, yachts mid-bay, jetskis close in.
     */
    private fun launchBoat() {
        val day = config?.night == false
        val night = !day
        val taken = ArrayList<Float>()
        if (ship.active) taken += MSC_DEPTH
        for (b in listOf(yacht, jetski, containerShip)) if (b.active) taken += b.depth
        fun free(d: Float) = taken.none { abs(it - d) < 0.12f }
        val options = ArrayList<Pair<Int, () -> Unit>>()
        val bigBoatOut = ship.active || containerShip.active // only one big ship in the bay at a time
        if (!bigBoatOut && free(MSC_DEPTH)) options += 25 to { ship.launch() }
        if (!bigBoatOut) floatArrayOf(0.08f, 0.3f).filter(::free).randomOrNull(rnd)?.let { d -> options += 25 to { containerShip.launch(d, night) } }
        if (!yacht.active) floatArrayOf(0.3f, 0.45f, 0.6f).filter(::free).randomOrNull(rnd)?.let { d -> options += 30 to { yacht.launch(d, night) } }
        if (day && !jetski.active) floatArrayOf(0.6f, 0.75f, 0.88f).filter(::free).randomOrNull(rnd)?.let { d -> options += 20 to { jetski.launch(d) } }
        if (options.isEmpty()) return
        var pick = rnd.nextInt(options.sumOf { it.first })
        options.first { pick -= it.first; pick < 0 }.second()
    }

    // Like the real plane: daytime only, and not in a thunderstorm.
    /** The fireworks preview: the banner plane flies at night too, towing the holiday's greeting. */
    var holidayPlaneAtNight = false
        set(value) {
            field = value
            isHoliday = holidayToday() != null
            if (value) nextPlaneAt = 0L // first pass right away
        }

    private fun planeMayFly() = config?.let { (!it.night || holidayPlaneAtNight) && !it.lightning } ?: false

    private enum class Lane { SHIP, PLANE, SEA, BIRDS }

    private fun launch(lane: Lane) {
        when (lane) {
            Lane.SHIP -> launchBoat()
            Lane.PLANE -> launchPlane()
            Lane.BIRDS -> birds.launch(haze())
            Lane.SEA -> {
                // Weighted: tails 40%, dolphins 40%, breaches 20%; never the same twice in a row.
                val kinds = listOf(MarineLife.Kind.TAIL to 4, MarineLife.Kind.DOLPHINS to 4, MarineLife.Kind.BREACH to 2)
                    .filter { it.first != lastSeaKind }
                var pick = rnd.nextInt(kinds.sumOf { it.second })
                val kind = kinds.first { pick -= it.second; pick < 0 }.first
                sea.launch(kind, haze())
                lastSeaKind = kind
            }
        }
        android.util.Log.d("WeatherWallpaper", "Visitor: $lane")
    }

    private fun haze() = config?.let { if (it.fog || it.overcast) 0.75f else 1f } ?: 1f

    private fun between(minSeconds: Int, maxSeconds: Int) =
        minSeconds * 1000L + rnd.nextLong((maxSeconds - minSeconds) * 1000L + 1)

    companion object {
        const val FADE_SECONDS = 2.5f
        const val CALM_FPS = 10
        private const val CALM = 0
        private const val SLOW = 1
        private const val FAST = 2
        const val MSC_DEPTH = 0.17f // the MSC Armonia's fixed distance out
    }
}
