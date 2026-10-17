package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One animated weather scene sized to the wallpaper surface.
 * [dp] is the display density so sizes look the same on every screen.
 */
class WeatherScene(
    private val cfg: SceneConfig,
    width: Int,
    height: Int,
    private val dp: Float,
    wildlife: SavannaWildlife? = null,
) {
    private val rnd = Random(System.nanoTime())
    private val wf = width.toFloat()
    private val hf = height.toFloat()
    private var time = 0f

    // ---- Sky ---------------------------------------------------------------
    private val skyPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, hf, cfg.skyTop, cfg.skyBottom, Shader.TileMode.CLAMP)
    }

    // ---- Stars -------------------------------------------------------------
    // The night sky turns around the south celestial pole. Looking south from Bloubergstrand,
    // the pole is about 34° above the horizon (Cape Town's latitude).
    private val horizonY = hf * Scenes.horizon(cfg.scene)
    private val poleX = wf * 0.5f
    private val poleY = hf * 0.34f
    private val pxPerDegree = (horizonY - poleY) / 34f
    private val skyRadius = sqrt(max(poleX, wf - poleX).pow(2) + max(poleY, horizonY - poleY).pow(2))
    private var skyAngle = SkyClock.southernSkyRotation(SkyTime.now(), cfg.longitude.toDouble())
    private var skyAngleUpdatedAt = 0f

    // Stars fill the whole disc the screen can rotate through, so none run out as the sky turns.
    private val starN = (cfg.stars * 2.2f).toInt()
    private val starX = FloatArray(starN)
    private val starY = FloatArray(starN)
    init {
        for (i in 0 until starN) {
            val r = skyRadius * sqrt(rnd.nextFloat())
            val a = rnd.nextFloat() * TWO_PI
            starX[i] = poleX + r * cos(a)
            starY[i] = poleY + r * sin(a)
        }
    }
    private val starR = FloatArray(starN) { (0.5f + rnd.nextFloat() * 1.3f) * dp }
    private val starPhase = FloatArray(starN) { rnd.nextFloat() * TWO_PI }
    private val starRate = FloatArray(starN) { 0.6f + rnd.nextFloat() * 1.8f }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    // Mostly white, some blue-white, a few orange (matches the Milky Way's stars).
    private val starColour = IntArray(starN) { MilkyWay.starColour(Color.WHITE, rnd.nextFloat()) }

    // ---- Sun / moon --------------------------------------------------------
    // Real altitudes and rise/set times. The view faces south, but from Cape Town the sun and
    // moon cross the northern sky, so azimuth is folded onto the screen: east (rising) on the
    // left, west (setting) on the right, highest in the middle.
    private val skyTopY = hf * 0.08f
    private val moonR = 34f * dp
    private var sunX = 0f
    private var sunY = 0f
    private var sunAlt = 0.0
    private var moonX = 0f
    private var moonY = 0f
    private var moonAlt = 0.0
    private val showSun = cfg.sun
    // Moon at night on clear/partly cloudy nights, and pale in a clear daytime sky.
    private val showMoon = cfg.moon || cfg.sun
    private val moonLit = MoonPhase.illumination(cfg.moonPhase)
    private val waxing = cfg.moonPhase < 0.5f

    private fun skyX(azimuth: Double) = (wf * 0.5f - wf * 0.4f * sin(Math.toRadians(azimuth))).toFloat()
    private fun skyY(altitude: Double) = (horizonY - altitude / 90.0 * (horizonY - skyTopY)).toFloat()

    // Glows are unit gradients at the origin, scaled into place, so bodies can move freely.
    private val sunRays = SunRays(wf)
    private val moonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val a = if (cfg.moon) (10 + 60 * moonLit).toInt() else 0
        shader = RadialGradient(0f, 0f, 100f, intArrayOf(withAlpha(0xFFCFDCFF.toInt(), a), 0x00CFDCFF), null, Shader.TileMode.CLAMP)
    }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF3F0E2.toInt()
        if (!cfg.moon) alpha = 170 // daytime moon is pale
    }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDD8C4.toInt()
        if (!cfg.moon) alpha = 150
    }
    // Built with the lit limb on the right (+x); drawing rotates it to face the sun.
    private val moonLitPath = MoonPhase.litPath(if (waxing) cfg.moonPhase else 1f - cfg.moonPhase, 0f, 0f, moonR)
    // The unlit part of the disc, faintly visible by earthshine at night. Opaque, so the
    // Milky Way doesn't show through it; its colour follows the sky behind it.
    private val earthshinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Twilight: a warm glow around sunrise and sunset ------------------------
    // Peaks with the sun ~2° below the horizon and fades out by +8° / -12°.
    private var twilight = 0f
    private val twilightCloudDim = when {
        cfg.rain > 0 || cfg.snow > 0 -> 0f
        cfg.overcast || cfg.fog -> 0.35f
        else -> 1f
    }
    private val twilightSkyPaint = Paint().apply {
        shader = LinearGradient(0f, hf * 0.15f, 0f, horizonY, 0x00F49A8C, 0xFFF7A08C.toInt(), Shader.TileMode.CLAMP)
    }
    private val sunsetGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 100f, intArrayOf(0xFFFFB27A.toInt(), 0x00FFB27A), null, Shader.TileMode.CLAMP)
    }

    // ---- Milky Way: rendered once; washed out by moonlight and dimmed by cloud. Its length and
    // core position come from the real band (closest to the pole ~27°, the galactic core ~57°
    // further along); where it's drawn follows a stylised night arc (see updateSky).
    private val bandOffset = 27f * pxPerDegree
    private val bandHalfLength = skyRadius + bandOffset + wf * 0.2f
    private val bandThickness = wf * 1.1f
    private val milkyWay = if (cfg.stars > 0) {
        MilkyWay.renderStrip(width, bandHalfLength * 2f, bandThickness, 0.5f - 57f * pxPerDegree / (bandHalfLength * 2f))
    } else null
    // The strip placed with its galactic core at the origin.
    private val heroRect = (0.5f - 57f * pxPerDegree / (bandHalfLength * 2f)).let { core ->
        RectF(-core * bandHalfLength * 2f, -bandThickness / 2f, (1f - core) * bandHalfLength * 2f, bandThickness / 2f)
    }
    private val milkyWayPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var milkyWayAlpha = 255
    /** Where the Milky Way is in its night arc: 0 rising .. 1 setting; outside that it's hidden. */
    private var bandProgress = -1f
    private var nightSet = Long.MIN_VALUE
    private var nightRise = Long.MIN_VALUE
    private var nightFoundAt = Long.MIN_VALUE

    /** Tonight's (or the night just ending) sunset and following sunrise; MIN_VALUE if not found. */
    val nightSetMs get() = nightSet
    val nightRiseMs get() = nightRise

    /**
     * The night we're in (or just leaving): the last sunset and the sunrise after it, found by
     * stepping the sun's altitude in 10-minute steps; refreshed every 10 minutes.
     */
    private fun findNight(now: Long, lat: Double, lon: Double) {
        if (kotlin.math.abs(now - nightFoundAt) <= 600_000L) return
        nightFoundAt = now
        val step = 600_000L
        fun down(t: Long) = Astro.sun(t, lat, lon).altitude <= -0.83
        var set = Long.MIN_VALUE
        var t = now
        while (t > now - 86_400_000L) { if (down(t) && !down(t - step)) { set = t; break }; t -= step }
        var rise = Long.MIN_VALUE
        if (set != Long.MIN_VALUE) {
            t = set
            while (t < set + 86_400_000L) { if (down(t) && !down(t + step)) { rise = t + step; break }; t += step }
        }
        nightSet = set
        nightRise = rise
    }

    /** 0..1 through [from, to] (a window within the night), or -1 outside it or with no night found. */
    private fun nightProgress(now: Long, from: Long, to: Long): Float {
        if (nightSet == Long.MIN_VALUE || nightRise == Long.MIN_VALUE) return -1f
        if (to <= from || now < from || now > to) return -1f
        return (now - from).toFloat() / (to - from)
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private val milkyWayCloudDim = when {
        cfg.clouds > 4 -> 0.4f   // cloudy
        cfg.clouds > 1 -> 0.6f   // partly cloudy
        else -> 1f
    } * (if (cfg.scene == Scenes.JHB) 0.15f else 1f) // city lights all but wash it out

    // ---- The land: Cape Town, the Savanna or Jhb City ------------------------------------
    private val landscape: Landscape =
        when (cfg.scene) {
            Scenes.SAVANNA -> SavannaLandscape(cfg, width, height, wildlife)
            Scenes.JHB -> JhbCityLandscape(cfg, width, height)
            Scenes.ALTRON -> AltronLandscape(cfg, width, height)
            Scenes.GARDEN -> GardenRouteLandscape(cfg, width, height, dp)
            else -> CapeTownLandscape(cfg, width, height, dp)
        }

    init {
        updateSky()
    }

    // Rain, drizzle and thunderstorms: a live, churning storm ceiling over the whole sky
    // (Android 13+; older phones keep the drawn clouds).
    private val storm: StormCeiling? =
        if (cfg.rain > 0 && StormDesign.isSupported()) {
            // If the shader can't compile on this GPU, fall back to the drawn clouds rather than crash.
            runCatching { StormCeiling(wf, hf, cfg.night) }
                .onFailure { android.util.Log.w("WeatherWallpaper", "Storm ceiling unavailable", it) }
                .getOrNull()
        } else null

    // Everything else: long layered decks and high wisps (see FairClouds).
    private val fairClouds: FairClouds? =
        if (storm == null) FairClouds(cfg.condition, cfg.night, wf, landscape.tableTopY) else null

    // ---- Rain --------------------------------------------------------------
    private val rainN = (cfg.rain * StormDesign.RAIN).toInt()
    private val slant = 0.06f + cfg.wind * 0.4f * StormDesign.WIND // horizontal px per vertical px
    private val rainX = FloatArray(rainN) { rnd.nextFloat() * (wf + hf * slant) - hf * slant }
    private val rainY = FloatArray(rainN) { rnd.nextFloat() * hf }
    private val rainLen = FloatArray(rainN) {
        (if (cfg.drizzle) 7f + rnd.nextFloat() * 7f else 16f + rnd.nextFloat() * 18f) * dp
    }
    private val rainSpeed = FloatArray(rainN) {
        (if (cfg.drizzle) 450f + rnd.nextFloat() * 250f else 950f + rnd.nextFloat() * 550f) * dp
    }
    private val rainPts = FloatArray(rainN * 4)
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (cfg.drizzle) 0x88CFE0F0.toInt() else 0x99C5D6E8.toInt()
        strokeWidth = (if (cfg.drizzle) 1f else 1.4f) * dp
        strokeCap = Paint.Cap.ROUND
    }

    // ---- Snow --------------------------------------------------------------
    private val snowN = cfg.snow
    private val snowX = FloatArray(snowN) { rnd.nextFloat() * wf }
    private val snowY = FloatArray(snowN) { rnd.nextFloat() * hf }
    private val snowR = FloatArray(snowN) { (1.2f + rnd.nextFloat() * 2.8f) * dp }
    private val snowSpeed = FloatArray(snowN) { 22f * dp + snowR[it] * 14f + rnd.nextFloat() * 20f * dp }
    private val snowPhase = FloatArray(snowN) { rnd.nextFloat() * TWO_PI }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }

    // ---- Fog ---------------------------------------------------------------
    private val fogN = if (cfg.fog) 8 else 0
    private val fogX = FloatArray(fogN) { rnd.nextFloat() * wf }
    private val fogY = FloatArray(fogN) { hf * (0.25f + 0.75f * it / max(1, fogN - 1)) }
    private val fogRx = FloatArray(fogN) { wf * (0.7f + rnd.nextFloat() * 0.5f) }
    private val fogRy = FloatArray(fogN) { hf * (0.07f + rnd.nextFloat() * 0.06f) }
    private val fogSpeed = FloatArray(fogN) { (6f + rnd.nextFloat() * 10f) * dp * (if (it % 2 == 0) 1f else -1f) }
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Unit puff at the origin; each band scales/translates the canvas instead of making a new shader.
        shader = RadialGradient(
            0f, 0f, 100f,
            intArrayOf(withAlpha(cfg.fogColor, 130), withAlpha(cfg.fogColor, 0)),
            null, Shader.TileMode.CLAMP,
        )
    }
    private val fogOverlay = Paint().apply {
        shader = LinearGradient(
            0f, hf * 0.3f, 0f, hf,
            withAlpha(cfg.fogColor, 0), withAlpha(cfg.fogColor, 150), Shader.TileMode.CLAMP,
        )
    }

    // ---- Lightning ---------------------------------------------------------
    private var nextStrike = 2f + rnd.nextFloat() * 4f
    private var secondFlashIn = -1f
    private var flash = 0f
    private var boltLife = 0f
    private val boltPath = Path()
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF6F4FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeJoin = Paint.Join.ROUND
    }
    private val boltGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66B9B6FF
        style = Paint.Style.STROKE
        strokeWidth = 10f * dp
        strokeJoin = Paint.Join.ROUND
    }
    private val flashPaint = Paint().apply { color = Color.WHITE }

    // ========================================================================

    /** Rooftop spots for pigeons to land on (Jhb City and the Altron head office). */
    fun roofSpots(gap: Float): List<FloatArray> = landscape.roofSpots(gap)

    /** Garden Route: how fast its own visitors move (2 = something quick on screen). */
    val gardenMotion get() = (landscape as? GardenRouteLandscape)?.motion ?: 0

    /** Debug and demo: start one of the Garden Route's visitors now. */
    fun gardenNow(what: String) {
        val g = landscape as? GardenRouteLandscape ?: return
        when (what) {
            "train" -> g.launchTrain()
            "jump" -> g.launchJump()
            "dolphins" -> g.launchDolphins()
            "jetski" -> g.launchJetski()
            "breach" -> g.launchBreach()
        }
    }

    fun update(dt: Float) {
        time += dt
        if (time - skyAngleUpdatedAt > 1f) {
            updateSky()
            skyAngleUpdatedAt = time
        }
        landscape.update(dt)

        fairClouds?.update(dt)

        for (i in 0 until rainN) {
            val dy = rainSpeed[i] * dt
            rainY[i] += dy
            rainX[i] += dy * slant
            if (rainY[i] - rainLen[i] > hf) {
                rainY[i] = -rnd.nextFloat() * hf * 0.2f
                rainX[i] = rnd.nextFloat() * (wf + hf * slant) - hf * slant
            }
        }

        val drift = cfg.wind * 45f * dp
        for (i in 0 until snowN) {
            snowY[i] += snowSpeed[i] * dt
            snowX[i] += (sin(time * 1.3f + snowPhase[i]) * 18f * dp + drift) * dt
            if (snowY[i] - snowR[i] > hf) {
                snowY[i] = -snowR[i]
                snowX[i] = rnd.nextFloat() * wf
            }
            if (snowX[i] > wf + snowR[i]) snowX[i] = -snowR[i]
            if (snowX[i] < -snowR[i]) snowX[i] = wf + snowR[i]
        }

        for (i in 0 until fogN) {
            fogX[i] += fogSpeed[i] * dt
            if (fogX[i] - fogRx[i] > wf) fogX[i] = -fogRx[i]
            if (fogX[i] + fogRx[i] < 0f) fogX[i] = wf + fogRx[i]
        }

        if (cfg.lightning) {
            nextStrike -= dt
            if (nextStrike <= 0f) {
                strike()
                nextStrike = 3f + rnd.nextFloat() * 7f
                secondFlashIn = if (rnd.nextBoolean()) 0.14f else -1f
            }
            if (secondFlashIn > 0f) {
                secondFlashIn -= dt
                if (secondFlashIn <= 0f) flash = 0.8f
            }
            flash = max(0f, flash - dt * 3.5f)
            boltLife = max(0f, boltLife - dt)
            landscape.setFlash(flash)
        }
    }

    /** Screen y of the top of the land's highest landmark. */
    val skylineY get() = landscape.tableTopY

    /** [behindLand] draws between the sky and the landscape (e.g. fireworks behind a building). */
    fun draw(canvas: Canvas, onWater: ((Canvas) -> Unit)? = null, behindLand: ((Canvas) -> Unit)? = null) {
        canvas.drawRect(0f, 0f, wf, hf, skyPaint)
        if (flash > 0f) {
            flashPaint.alpha = (flash * 90).toInt()
            canvas.drawRect(0f, 0f, wf, hf, flashPaint)
        }
        milkyWay?.let {
            canvas.save()
            // Not the real sky's timing: the band rises out of the horizon on the left about two
            // hours after sunset, arcs up through the middle of the screen (core low, band
            // climbing steeply, like a long-exposure photo) and sets on the right just before
            // sunrise. The demo pins it to the middle of the night.
            val p = if (MilkyWay.heroView) 0.5f else bandProgress
            if (p in 0f..1f) {
                val fade = smoothstep(0f, 0.06f, p) * (1f - smoothstep(0.94f, 1f, p))
                milkyWayPaint.alpha = (milkyWayAlpha * fade).toInt()
                canvas.translate(wf * (0.1f + 0.84f * p), horizonY + hf * 0.12f - sin(PI_F * p) * hf * 0.24f)
                canvas.rotate(-112f + (p - 0.5f) * 80f)
                canvas.drawBitmap(it, null, heroRect, milkyWayPaint)
            }
            canvas.restore()
        }
        if (starN > 0) {
            canvas.save()
            canvas.rotate(skyAngle, poleX, poleY)
            drawStars(canvas)
            canvas.restore()
        }
        if (twilight > 0.01f) drawTwilight(canvas)
        if (showMoon && moonAlt > -3.0) drawMoon(canvas)
        if (showSun && sunAlt > -3.0) drawSun(canvas)
        if (boltLife > 0f) drawBolt(canvas)
        fairClouds?.draw(canvas, twilight)
        storm?.drawSky(canvas, time, flash)
        behindLand?.invoke(canvas)
        landscape.draw(canvas, onWater)
        if (fogN > 0) drawFog(canvas)
        if (rainN > 0) drawRain(canvas)
        if (snowN > 0) drawSnow(canvas)
        if (flash > 0f) {
            flashPaint.alpha = (flash * 50).toInt()
            canvas.drawRect(0f, 0f, wf, hf, flashPaint)
        }
        canvas.drawRect(0f, 0f, wf, hf * 0.4f, topScrimPaint)
    }

    // Darkens the top of the screen a little so the white clock, widgets and status bar stay
    // readable over bright sky and white clouds. Stronger on bright, cloudy days; light at night.
    private val topScrimPaint = Paint().apply {
        val strength = when {
            cfg.night -> 0.18f
            cfg.clouds > 0 || cfg.fog || cfg.snow > 0 -> 0.42f
            else -> 0.3f
        }
        val a = (255 * strength).toInt()
        shader = LinearGradient(
            0f, 0f, 0f, hf * 0.4f,
            intArrayOf(withAlpha(0xFF0A1424.toInt(), a), withAlpha(0xFF0A1424.toInt(), (a * 0.55f).toInt()), 0x000A1424),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }

    // ---- Drawing helpers ---------------------------------------------------

    private fun drawStars(canvas: Canvas) {
        for (i in 0 until starN) {
            val twinkle = 0.55f + 0.45f * sin(time * starRate[i] + starPhase[i])
            starPaint.color = starColour[i]
            starPaint.alpha = (255 * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(starX[i], starY[i], starR[i], starPaint)
        }
    }

    /** For a still frame: brings the sun, moon, stars and twilight up to the clock without animating. */
    fun syncSky() = updateSky()

    /** Recomputes where the stars, sun and moon are. Called about once a second. */
    private fun updateSky() {
        val now = SkyTime.now()
        val lat = cfg.latitude.toDouble()
        val lon = cfg.longitude.toDouble()
        skyAngle = SkyClock.southernSkyRotation(now, lon)

        val sun = Astro.sun(now, lat, lon)
        sunAlt = sun.altitude
        sunX = skyX(sun.azimuth)
        sunY = skyY(sun.altitude)
        // Not the moon's real position: like the sun, it rises on the left soon after sunset,
        // arcs across the night and sets on the right soon after sunrise. (Its phase is real.)
        findNight(now, lat, lon)
        val mp = nightProgress(now, nightSet + 1_800_000L, nightRise + 1_800_000L)
        if (mp >= 0f) {
            moonAlt = sin(PI_F * mp).toDouble() * MOON_TOP_ALTITUDE
            moonX = wf * (0.1f + 0.8f * mp)
            moonY = skyY(moonAlt)
        } else {
            moonAlt = -90.0
        }

        val moonlight = if (cfg.moon && moonAlt > 0) 0.8f * moonLit else 0f
        milkyWayAlpha = (255 * (1f - moonlight) * milkyWayCloudDim).toInt().coerceIn(0, 255)
        // The band rises two hours after sunset and sets half an hour before sunrise.
        bandProgress = nightProgress(now, nightSet + 2 * 3_600_000L, nightRise - 1_800_000L)
        earthshinePaint.color = mix(mix(cfg.skyTop, cfg.skyBottom, (moonY / hf).coerceIn(0f, 1f)), 0xFFD8DEEA.toInt(), 0.16f)

        twilight = (1f - kotlin.math.abs(sunAlt.toFloat() + 2f) / 10f).coerceIn(0f, 1f) * twilightCloudDim
        landscape.setTwilight(twilight)

        // Light on the mountain's rock faces: the sun by day (soft under cloud), faint moonlight at night.
        if (sunAlt > 0) {
            val dir = ((sunX - wf / 2f) / (wf * 0.4f)).coerceIn(-1f, 1f)
            val overcast = if (cfg.overcast || cfg.fog) 0.35f else 1f
            landscape.setLight(dir, (0.45f + 0.55f * kotlin.math.abs(dir)) * overcast)
        } else {
            val moonUp = cfg.moon && moonAlt > 0
            val dir = if (moonUp) ((moonX - wf / 2f) / (wf * 0.4f)).coerceIn(-1f, 1f) else 0f
            landscape.setLight(dir, if (moonUp) 0.12f + 0.2f * moonLit else 0.1f)
        }

        when {
            showSun && sunAlt > 0 -> landscape.setGlint(sunX, 1f)
            cfg.moon && moonAlt > 0 -> landscape.setGlint(moonX, moonLit)
            else -> landscape.setGlint(0f, 0f)
        }
    }

    private fun drawTwilight(canvas: Canvas) {
        twilightSkyPaint.alpha = (150 * twilight).toInt()
        canvas.drawRect(0f, hf * 0.15f, wf, horizonY, twilightSkyPaint)
        canvas.save()
        canvas.translate(sunX.coerceIn(0f, wf), horizonY)
        canvas.scale(wf * 0.8f / 100f, hf * 0.3f / 100f)
        sunsetGlowPaint.alpha = (200 * twilight).toInt()
        canvas.drawCircle(0f, 0f, 100f, sunsetGlowPaint)
        canvas.restore()
    }

    private fun drawSun(canvas: Canvas) {
        sunRays.draw(canvas, sunX, sunY, sunAlt, time)
    }

    private fun drawMoon(canvas: Canvas) {
        canvas.save()
        canvas.translate(moonX, moonY)
        if (cfg.moon) {
            val glowScale = moonR * 3.4f / 100f
            canvas.save()
            canvas.scale(glowScale, glowScale)
            canvas.drawCircle(0f, 0f, 100f, moonGlowPaint)
            canvas.restore()
            // Earthshine only shows in a properly dark sky.
            if (twilight < 0.25f) canvas.drawCircle(0f, 0f, moonR, earthshinePaint)
        }
        // The lit side always faces the sun, wherever it is (even below the horizon).
        val toSun = Math.toDegrees(atan2((sunY - moonY).toDouble(), (sunX - moonX).toDouble())).toFloat()
        canvas.rotate(toSun)
        canvas.clipPath(moonLitPath)
        canvas.drawCircle(0f, 0f, moonR, moonPaint)
        canvas.drawCircle(-moonR * 0.35f, -moonR * 0.2f, moonR * 0.2f, craterPaint)
        canvas.drawCircle(moonR * 0.3f, moonR * 0.35f, moonR * 0.14f, craterPaint)
        canvas.drawCircle(moonR * 0.2f, -moonR * 0.4f, moonR * 0.09f, craterPaint)
        canvas.restore()
    }

    private fun drawFog(canvas: Canvas) {
        for (i in 0 until fogN) {
            canvas.save()
            canvas.translate(fogX[i], fogY[i])
            canvas.scale(fogRx[i] / 100f, fogRy[i] / 100f)
            canvas.drawCircle(0f, 0f, 100f, fogPaint)
            canvas.restore()
        }
        canvas.drawRect(0f, 0f, wf, hf, fogOverlay)
    }

    private fun drawRain(canvas: Canvas) {
        for (i in 0 until rainN) {
            val j = i * 4
            rainPts[j] = rainX[i]
            rainPts[j + 1] = rainY[i]
            rainPts[j + 2] = rainX[i] - rainLen[i] * slant
            rainPts[j + 3] = rainY[i] - rainLen[i]
        }
        canvas.drawLines(rainPts, rainPaint)
    }

    private fun drawSnow(canvas: Canvas) {
        for (i in 0 until snowN) canvas.drawCircle(snowX[i], snowY[i], snowR[i], snowPaint)
    }

    private fun drawBolt(canvas: Canvas) {
        val a = (boltLife / BOLT_SECONDS).coerceIn(0f, 1f)
        boltGlowPaint.alpha = (110 * a).toInt()
        boltPaint.alpha = (255 * a).toInt()
        canvas.drawPath(boltPath, boltGlowPaint)
        canvas.drawPath(boltPath, boltPaint)
    }

    private fun strike() {
        flash = 1f
        boltLife = BOLT_SECONDS
        boltPath.reset()
        var x = wf * (0.15f + rnd.nextFloat() * 0.7f)
        var y = hf * 0.1f
        val end = hf * (0.5f + rnd.nextFloat() * 0.25f)
        boltPath.moveTo(x, y)
        var branched = false
        while (y < end) {
            y += (18f + rnd.nextFloat() * 24f) * dp
            x += (rnd.nextFloat() - 0.5f) * 44f * dp
            boltPath.lineTo(x, y)
            if (!branched && y > end * 0.5f && rnd.nextBoolean()) {
                branched = true
                val dir = if (rnd.nextBoolean()) 1f else -1f
                boltPath.lineTo(x + dir * 30f * dp, y + 40f * dp)
                boltPath.lineTo(x + dir * 44f * dp, y + 75f * dp)
                boltPath.moveTo(x, y)
            }
        }
    }

    private companion object {
        const val BOLT_SECONDS = 0.22f
        const val PI_F = Math.PI.toFloat()
        const val MOON_TOP_ALTITUDE = 55.0 // degrees, at the middle of the night

    }
}
