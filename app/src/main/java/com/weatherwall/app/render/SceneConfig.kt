package com.weatherwall.app.render

import com.weatherwall.app.WeatherCondition
import com.weatherwall.app.WeatherState
import kotlin.math.roundToInt

/** Everything a [WeatherScene] needs to know about what to draw. */
data class SceneConfig(
    val skyTop: Int,
    val skyBottom: Int,
    val sun: Boolean = false,
    val moon: Boolean = false,
    val stars: Int = 0,
    val clouds: Int = 0,
    val cloudColor: Int = 0xFFFFFFFF.toInt(),
    val cloudAlpha: Int = 235,
    val rain: Int = 0,
    val drizzle: Boolean = false,
    val snow: Int = 0,
    val fog: Boolean = false,
    val fogColor: Int = 0xFFE6EAED.toInt(),
    val lightning: Boolean = false,
    /** 0..1, drives cloud speed, rain slant and snow drift. */
    val wind: Float = 0f,
    val night: Boolean = false,
    /** Cape Town's "tablecloth" cloud spilling over Table Mountain. */
    val tablecloth: Boolean = false,
    /** 0 = new, 0.5 = full; see [MoonPhase]. */
    val moonPhase: Float = 0.5f,
    /** Viewer is south of the equator, so the moon is drawn mirrored. */
    val southern: Boolean = true,
    /** Viewer's position, for where the stars, sun and moon are. */
    val latitude: Float = -33.92f,
    val longitude: Float = 18.42f,
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    /** Which landscape: one of [Scenes]. */
    val scene: String = Scenes.CAPE_TOWN,
) {
    /** A grey ceiling over the whole sky (overcast, drizzle, rain, storms): soft light, no glow. */
    val overcast: Boolean get() = clouds > 6

    companion object {
        fun forWeather(
            w: WeatherState,
            latitude: Double,
            longitude: Double,
            scene: String = Scenes.CAPE_TOWN,
            nowMs: Long = SkyTime.now(),
        ): SceneConfig {
            // Quantised so tiny wind changes don't trigger a scene rebuild.
            val wind = ((w.windKmh / 50.0).coerceIn(0.0, 1.0) * 10).roundToInt() / 10f
            val day = w.isDay
            fun sky(dayTop: Long, dayBottom: Long, nightTop: Long, nightBottom: Long) =
                if (day) dayTop.toInt() to dayBottom.toInt() else nightTop.toInt() to nightBottom.toInt()

            val base = when (w.condition) {
                WeatherCondition.CLEAR -> {
                    val (t, b) = sky(0xFF2E7BD6, 0xFF9BD4F5, 0xFF0B1026, 0xFF2B3A67)
                    SceneConfig(t, b, sun = day, moon = !day, stars = if (day) 0 else 180,
                        clouds = if (day) 1 else 0, wind = wind)
                }
                WeatherCondition.PARTLY_CLOUDY -> {
                    val (t, b) = sky(0xFF3F82CF, 0xFFB3D6EE, 0xFF111833, 0xFF34416A)
                    SceneConfig(t, b, sun = day, moon = !day, stars = if (day) 0 else 100,
                        clouds = 4, cloudColor = if (day) 0xFFFFFFFF.toInt() else 0xFF566078.toInt(),
                        wind = wind)
                }
                WeatherCondition.CLOUDY -> {
                    // Busier than partly cloudy, but the sky is still blue between the clouds
                    // and the sun, moon and stars still show through the gaps.
                    val (t, b) = sky(0xFF5A82B4, 0xFFB9CCDC, 0xFF121A2E, 0xFF333E58)
                    SceneConfig(t, b, sun = day, moon = !day, stars = if (day) 0 else 70,
                        clouds = 6, cloudColor = if (day) 0xFFF2F4F7.toInt() else 0xFF4E586E.toInt(),
                        wind = wind)
                }
                WeatherCondition.OVERCAST -> {
                    // A full grey ceiling: the only dry sky that hides the sun, moon and stars.
                    val (t, b) = sky(0xFF6F7F8F, 0xFFB0BAC4, 0xFF181D27, 0xFF363E4C)
                    SceneConfig(t, b, clouds = 10,
                        cloudColor = if (day) 0xFFD9DEE4.toInt() else 0xFF3A4252.toInt(), wind = wind)
                }
                WeatherCondition.FOG -> {
                    val (t, b) = sky(0xFF98A2AA, 0xFFD3D7DA, 0xFF262B33, 0xFF4E555E)
                    SceneConfig(t, b, clouds = 3,
                        cloudColor = if (day) 0xFFE2E6EA.toInt() else 0xFF4A515C.toInt(),
                        fog = true, fogColor = if (day) 0xFFE8ECEF.toInt() else 0xFF7C858F.toInt(),
                        wind = wind)
                }
                WeatherCondition.DRIZZLE -> {
                    val (t, b) = sky(0xFF5F6F80, 0xFF9DAAB6, 0xFF141A24, 0xFF2E3A48)
                    SceneConfig(t, b, clouds = 8,
                        cloudColor = if (day) 0xFF9CA6B0.toInt() else 0xFF2F3743.toInt(),
                        rain = 140, drizzle = true, wind = wind)
                }
                WeatherCondition.RAIN -> {
                    val (t, b) = sky(0xFF3E4C5C, 0xFF7A8896, 0xFF0E131B, 0xFF26303C)
                    SceneConfig(t, b, clouds = 9,
                        cloudColor = if (day) 0xFF7C8691.toInt() else 0xFF262D38.toInt(),
                        rain = 340, wind = wind)
                }
                WeatherCondition.SNOW -> {
                    val (t, b) = sky(0xFF8FA3B8, 0xFFE3E9EF, 0xFF1C2433, 0xFF46536A)
                    SceneConfig(t, b, clouds = 6,
                        cloudColor = if (day) 0xFFE8ECF0.toInt() else 0xFF4A5466.toInt(),
                        snow = 200, wind = wind)
                }
                WeatherCondition.THUNDERSTORM -> {
                    val (t, b) = sky(0xFF1F2630, 0xFF4A5260, 0xFF07090D, 0xFF1B2029)
                    SceneConfig(t, b, clouds = 10,
                        cloudColor = if (day) 0xFF4B525C.toInt() else 0xFF1A1F27.toInt(),
                        rain = 420, lightning = true, wind = maxOf(wind, 0.4f))
                }
            }
            val tablecloth = scene == Scenes.CAPE_TOWN && when (w.condition) {
                WeatherCondition.CLEAR -> wind >= 0.5f // the south-easter lays the cloth on clear days
                WeatherCondition.SNOW -> false
                else -> true
            }
            // Rounded so the scene is rebuilt only every few hours as the phase advances.
            val moonPhase = (MoonPhase.phase(nowMs) * 100).roundToInt() % 100 / 100f
            // The city's light pollution: only the brighter third of the stars show over Jhb.
            val stars = if (scene == Scenes.JHB) base.stars * 3 / 10 else base.stars
            return base.copy(night = !day, tablecloth = tablecloth, moonPhase = moonPhase, condition = w.condition, scene = scene, stars = stars,
                southern = latitude < 0,
                latitude = latitude.toFloat(), longitude = longitude.toFloat())
        }
    }
}
