package za.co.saweather.lwp.engine

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

enum class Precip { RAIN, DRIZZLE, SNOW }

/** Per-scene weather tuning applied on top of the shared cycle. */
data class SceneConfig(
    val starCount: Int,
    /** 0 = pristine dark sky, 1 = heavy city glow on the horizon. */
    val lightPollution: Float,
    val lightPollutionColor: Int = Color.rgb(255, 150, 80),
    val milkyWayAllowed: Boolean,
    val precip: Precip = Precip.RAIN,
    val stormStrength: Float = 1f,
    val precipStrength: Float = 1f,
    val windStrength: Float = 1f,
    val lightningStrength: Float = 1f,
    val morningMist: Float = 0.3f,
    val baseMist: Float = 0f,
    val baseCloud: Float = 0.12f,
    val heatShimmer: Boolean = false,
    val dust: Boolean = false,
    /** Fraction of the screen height where the land meets the sky. */
    val horizon: Float = 0.62f,
    val shootingStarRate: Float = 1f,
    /** Silent heat lightning flickering on the far horizon at night (Highveld). */
    val distantLightning: Boolean = false,
    val fireflies: Int = 0,
    val fallingLeaves: Boolean = false,
)

/**
 * Continuous weather & lighting state derived from the hour of day. Everything downstream
 * (sky, particles, wildlife) reads from here, so the whole world stays in sync.
 */
class Atmosphere {
    var hour = 12f
    var phase = Phase.MIDDAY

    var sunElev = 1f       // -1..1, >0 above horizon
    var sunU = 0.5f        // 0 at sunrise (east) .. 1 at sunset (west)
    var daylight = 1f
    var night = 0f
    var golden = 0f
    var dawn = 0f          // dawn-side (morning) twilight weighting

    var cloudCover = 0f
    var storm = 0f
    var precip = 0f
    var wind = 0f          // signed, + blows to the right
    var mist = 0f
    var clearing = 0f      // post-storm wetness / steam
    var heat = 0f
    var lightningRate = 0f // strikes per second

    var moonPhase = 0f     // 0 new, 0.5 full
    var moonIllum = 0f
    var moonU = 0f
    var moonAlt = 0f       // 0..1, 0 when below horizon
    var moonLight = 0f     // how much moonlight reaches the landscape

    var flash = 0f         // lightning flash, written by the weather system each frame

    var ambient = Color.WHITE
    var skyTop = Color.BLUE
    var skyHorizon = Color.CYAN

    fun update(hour: Float, lunarDays: Double, cfg: SceneConfig, moonGlow: Boolean, lockHour: Float?) {
        val h = lockHour ?: (((hour % 24f) + 24f) % 24f)
        this.hour = h
        phase = Phase.at(h)

        // --- Sun: rises 06:00, sets 19:00 (summer-ish Southern Africa).
        if (h in SUNRISE..SUNSET) {
            sunU = (h - SUNRISE) / (SUNSET - SUNRISE)
            sunElev = sin(PI_F * sunU)
        } else {
            val hn = ((h - SUNSET) + 24f) % 24f
            sunU = if (h > SUNSET) 1f + hn / 24f else -((24f - SUNSET + SUNRISE) - hn) / 24f
            sunElev = -sin(PI_F * hn / (24f - SUNSET + SUNRISE))
        }
        daylight = smoothstep(-0.12f, 0.25f, sunElev)
        night = 1f - smoothstep(-0.28f, 0.02f, sunElev)
        golden = (1f - smoothstep(0.06f, 0.38f, sunElev)) * smoothstep(-0.12f, 0.02f, sunElev)
        dawn = if (h < 12f) 1f else 0f

        // --- Shared afternoon storm.
        val s = cfg.stormStrength
        storm = hourWindow(13.6f, 15.4f, 16.9f, 18.0f, h) * s
        cloudCover = max(cfg.baseCloud, hourWindow(12.8f, 15.0f, 17.2f, 18.8f, h) * (0.35f + 0.65f * s))
        precip = hourWindow(15.2f, 15.7f, 16.8f, 17.4f, h) * cfg.precipStrength
        clearing = hourWindow(16.9f, 17.4f, 18.2f, 19.4f, h) * s
        lightningRate = hourWindow(14.8f, 15.6f, 16.7f, 17.3f, h) * 0.55f * cfg.lightningStrength
        val gust = 0.5f + 0.5f * sin(h * 37f)
        wind = (0.12f + storm * (0.9f + 0.25f * gust)) * cfg.windStrength
        mist = max(
            cfg.baseMist,
            (hourWindow(4.0f, 5.6f, 7.6f, 9.5f, h) + clearing * 0.6f) * cfg.morningMist,
        ).coerceAtMost(1f)
        heat = if (cfg.heatShimmer) hourWindow(10.5f, 12f, 13.6f, 14.6f, h) * (1f - storm) else 0f

        // --- Moon: real phase, rises ~50 min later each day.
        moonPhase = (((lunarDays / SYNODIC) % 1.0 + 1.0) % 1.0).toFloat()
        moonIllum = (1f - kotlin.math.cos(TAU * moonPhase)) / 2f
        val transit = (12f + moonPhase * 24.8f) % 24f
        var ha = h - transit
        if (ha > 12f) ha -= 24f
        if (ha < -12f) ha += 24f
        moonU = (ha + 6.2f) / 12.4f
        moonAlt = if (moonU in 0f..1f) sin(PI_F * moonU) else 0f
        moonLight = if (moonGlow) moonIllum * smoothstep(0f, 0.35f, moonAlt) * night * (1f - cloudCover * 0.8f) else 0f

        // --- Colour.
        val stormSky = storm.coerceAtLeast(cloudCover * 0.35f)
        val stormTop = lerpColor(Color.rgb(16, 18, 26), Color.rgb(62, 67, 80), daylight)
        val stormHorizon = lerpColor(Color.rgb(26, 28, 36), Color.rgb(108, 112, 122), daylight)
        skyTop = lerpColor(SKY_TOP.at(h), stormTop, stormSky * 0.9f)
        skyHorizon = lerpColor(SKY_HORIZON.at(h), stormHorizon, stormSky * 0.85f)

        var amb = AMBIENT.at(h)
        amb = lerpColor(amb, lerpColor(Color.rgb(40, 44, 58), Color.rgb(120, 126, 140), daylight), storm * 0.8f)
        if (moonLight > 0f) {
            amb = Color.rgb(
                (Color.red(amb) + 26 * moonLight).toInt().coerceAtMost(255),
                (Color.green(amb) + 34 * moonLight).toInt().coerceAtMost(255),
                (Color.blue(amb) + 52 * moonLight).toInt().coerceAtMost(255),
            )
        }
        ambient = amb
    }

    /** Ambient light including the current lightning flash. */
    fun lit(): Int = if (flash > 0.01f) lerpColor(ambient, Color.rgb(235, 238, 255), flash * 0.75f) else ambient

    /** True when it is dark enough for nocturnal behaviour. */
    val isNight: Boolean get() = night > 0.5f

    fun windSign(): Float = if (wind >= 0f) 1f else -1f
    fun windAbs(): Float = abs(wind)

    companion object {
        const val SUNRISE = 6f
        const val SUNSET = 19f
        const val SYNODIC = 29.530588853
        /** Reference new moon: 2000-01-06 18:14 UTC. */
        const val NEW_MOON_EPOCH_MS = 947182440000L

        val SKY_TOP = ColorTrack.of(
            0f to Color.rgb(5, 8, 24), 4.8f to Color.rgb(7, 11, 30), 5.6f to Color.rgb(30, 38, 86),
            6.3f to Color.rgb(66, 96, 168), 7.5f to Color.rgb(64, 136, 214), 12f to Color.rgb(36, 112, 212),
            17f to Color.rgb(54, 124, 206), 18.3f to Color.rgb(58, 92, 170), 19.0f to Color.rgb(44, 52, 118),
            19.7f to Color.rgb(24, 24, 68), 20.6f to Color.rgb(9, 12, 36), 24f to Color.rgb(5, 8, 24),
        )
        val SKY_HORIZON = ColorTrack.of(
            0f to Color.rgb(18, 24, 52), 4.8f to Color.rgb(24, 28, 58), 5.6f to Color.rgb(150, 88, 112),
            6.2f to Color.rgb(255, 158, 108), 7.0f to Color.rgb(255, 214, 172), 8.5f to Color.rgb(182, 214, 240),
            12f to Color.rgb(196, 224, 246), 17f to Color.rgb(200, 220, 240), 18.0f to Color.rgb(255, 202, 142),
            18.6f to Color.rgb(255, 138, 66), 19.2f to Color.rgb(228, 86, 78), 19.8f to Color.rgb(98, 58, 110),
            20.6f to Color.rgb(30, 32, 70), 24f to Color.rgb(18, 24, 52),
        )
        val AMBIENT = ColorTrack.of(
            0f to Color.rgb(46, 56, 100), 4.8f to Color.rgb(50, 58, 102), 5.5f to Color.rgb(138, 108, 140),
            6.2f to Color.rgb(232, 162, 140), 7.5f to Color.rgb(246, 236, 224), 11f to Color.rgb(255, 255, 252),
            14f to Color.rgb(255, 252, 244), 17.5f to Color.rgb(255, 232, 200), 18.4f to Color.rgb(255, 178, 116),
            19.0f to Color.rgb(222, 128, 108), 19.6f to Color.rgb(128, 94, 140), 20.5f to Color.rgb(58, 66, 112),
            24f to Color.rgb(46, 56, 100),
        )
    }
}
