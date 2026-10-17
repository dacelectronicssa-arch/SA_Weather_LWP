package com.weatherwall.app

import android.content.Context
import android.content.SharedPreferences
import com.weatherwall.app.render.Scenes
import java.util.Calendar

object Prefs {
    private const val NAME = "weather_wallpaper"

    const val KEY_CITY = "city"
    const val KEY_OVERRIDE = "override"
    const val OVERRIDE_AUTO = "auto"
    /** Preview: a clear night with a random holiday's fireworks (named in KEY_PREVIEW_HOLIDAY). */
    const val OVERRIDE_FIREWORKS = "FIREWORKS"
    const val KEY_PREVIEW_HOLIDAY = "preview_holiday"

    /** Top frame rate (30, 24 or 15): smoothness versus battery; or [ULTRA_FPS]. */
    const val KEY_MAX_FPS = "max_fps"
    const val DEFAULT_MAX_FPS = 24
    /** The "Ultra power saver" choice: a still picture, animated briefly after unlocking. */
    const val ULTRA_FPS = 0
    /** Switch to Ultra power saver while Android's Battery Saver is on (default on). */
    const val KEY_ULTRA_ON_BATTERY_SAVER = "ultra_on_battery_saver"

    fun maxFps(ctx: Context): Int = get(ctx).getInt(KEY_MAX_FPS, DEFAULT_MAX_FPS)

    /** The frame rate to animate at (Ultra's brief animations use the default). */
    fun frameFps(ctx: Context): Int = maxFps(ctx).let { if (it == ULTRA_FPS) DEFAULT_MAX_FPS else it }

    fun isUltraChoice(ctx: Context) = maxFps(ctx) == ULTRA_FPS

    fun ultraOnBatterySaver(ctx: Context) = get(ctx).getBoolean(KEY_ULTRA_ON_BATTERY_SAVER, true)

    /** Which landscape to show: Scenes.CAPE_TOWN (default), SAVANNA, JHB, ALTRON or GARDEN. */
    const val KEY_SCENE = "scene"

    /** Debug builds only: hours to shift the sky clock by. */
    const val KEY_DEBUG_TIME_OFFSET = "debug_time_offset_h"

    /** Debug builds only: written to make the banner plane fly now. */
    const val KEY_DEBUG_PLANE = "debug_plane"
    const val KEY_DEBUG_SHIP = "debug_ship"
    const val KEY_DEBUG_SEA = "debug_sea"
    const val KEY_DEBUG_BIRDS = "debug_birds"
    const val KEY_DEBUG_BOAT = "debug_boat"
    const val KEY_DEBUG_LIONS = "debug_lions"
    const val KEY_DEBUG_GEESE = "debug_geese"
    const val KEY_DEBUG_JET = "debug_jet"
    const val KEY_DEBUG_PIGEONS = "debug_pigeons"
    const val KEY_DEBUG_FLYPAST = "debug_flypast"
    const val KEY_DEBUG_DEMO = "debug_demo"
    const val KEY_DEBUG_FIREWORKS = "debug_fireworks"

    const val KEY_FETCHED_AT = "w_fetched_at"
    private const val KEY_CONDITION = "w_condition"
    private const val KEY_IS_DAY = "w_is_day"
    private const val KEY_TEMP = "w_temp"
    private const val KEY_WIND = "w_wind"
    private const val KEY_PLACE = "w_place"

    const val KEY_LAST_LAT = "last_lat"
    const val KEY_LAST_LON = "last_lon"
    const val KEY_GEO_QUERY = "geo_query"
    const val KEY_GEO_LAT = "geo_lat"
    const val KEY_GEO_LON = "geo_lon"
    const val KEY_GEO_NAME = "geo_name"

    fun scene(ctx: Context): String =
        get(ctx).getString(KEY_SCENE, Scenes.CAPE_TOWN) ?: Scenes.CAPE_TOWN

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun saveWeather(ctx: Context, s: WeatherState) {
        get(ctx).edit()
            .putString(KEY_CONDITION, s.condition.name)
            .putBoolean(KEY_IS_DAY, s.isDay)
            .putFloat(KEY_TEMP, s.temperatureC.toFloat())
            .putFloat(KEY_WIND, s.windKmh.toFloat())
            .putString(KEY_PLACE, s.place)
            // Written last-ish in the same commit; listeners key off this one.
            .putLong(KEY_FETCHED_AT, s.fetchedAt)
            .apply()
    }

    fun loadWeather(ctx: Context): WeatherState? {
        val p = get(ctx)
        val name = p.getString(KEY_CONDITION, null) ?: return null
        val condition = runCatching { WeatherCondition.valueOf(name) }.getOrNull() ?: return null
        return WeatherState(
            condition = condition,
            isDay = p.getBoolean(KEY_IS_DAY, true),
            temperatureC = p.getFloat(KEY_TEMP, Float.NaN).toDouble(),
            windKmh = p.getFloat(KEY_WIND, 0f).toDouble(),
            place = p.getString(KEY_PLACE, "") ?: "",
            fetchedAt = p.getLong(KEY_FETCHED_AT, 0L),
        )
    }

    /** Latitude of the place the weather is for; defaults to Cape Town. */
    fun latitude(ctx: Context): Double {
        val p = get(ctx)
        val usingCity = !p.getString(KEY_CITY, "").isNullOrBlank()
        val key = if (usingCity) KEY_GEO_LAT else KEY_LAST_LAT
        return if (p.contains(key)) p.getFloat(key, 0f).toDouble() else -33.92
    }

    /** Longitude of the place the weather is for; defaults to Cape Town. */
    fun longitude(ctx: Context): Double {
        val p = get(ctx)
        val usingCity = !p.getString(KEY_CITY, "").isNullOrBlank()
        val key = if (usingCity) KEY_GEO_LON else KEY_LAST_LON
        return if (p.contains(key)) p.getFloat(key, 0f).toDouble() else 18.42
    }

    /** Override value format: "auto" or "<CONDITION>:day" / "<CONDITION>:night". */
    fun overrideValue(condition: WeatherCondition, night: Boolean) =
        "${condition.name}:${if (night) "night" else "day"}"

    /** The weather the wallpaper should show: a manual preview override, else live weather. */
    fun effectiveWeather(ctx: Context): WeatherState {
        val live = loadWeather(ctx)
        val override = get(ctx).getString(KEY_OVERRIDE, OVERRIDE_AUTO) ?: OVERRIDE_AUTO
        if (override == OVERRIDE_FIREWORKS) {
            return WeatherState(WeatherCondition.CLEAR, isDay = false, windKmh = live?.windKmh ?: 10.0)
        }
        if (override != OVERRIDE_AUTO) {
            val parts = override.split(":")
            val condition = runCatching { WeatherCondition.valueOf(parts[0]) }.getOrNull()
            if (condition != null) {
                return WeatherState(
                    condition = condition,
                    isDay = parts.getOrNull(1) != "night",
                    windKmh = live?.windKmh ?: 10.0,
                )
            }
        }
        if (live != null) return live
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return WeatherState(WeatherCondition.CLEAR, isDay = hour in 6..17)
    }
}
