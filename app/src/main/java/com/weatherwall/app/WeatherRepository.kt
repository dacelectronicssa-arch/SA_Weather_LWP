package com.weatherwall.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Fetches current conditions from Open-Meteo (free, no API key).
 * All methods block — call them off the main thread.
 */
class WeatherRepository(context: Context) {

    private val app = context.applicationContext
    private val prefs = Prefs.get(app)

    private data class Place(val lat: Double, val lon: Double, val name: String)

    fun refresh(): Result<WeatherState> = runCatching {
        val place = resolvePlace()
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=weather_code,is_day,temperature_2m,wind_speed_10m" +
                "&timezone=auto"
        )
        val current = JSONObject(httpGet(url)).getJSONObject("current")
        val state = WeatherState(
            condition = WeatherCondition.fromWmoCode(current.getInt("weather_code")),
            isDay = current.optInt("is_day", 1) == 1,
            temperatureC = current.optDouble("temperature_2m", Double.NaN),
            windKmh = current.optDouble("wind_speed_10m", 0.0),
            place = place.name,
            fetchedAt = System.currentTimeMillis(),
        )
        Prefs.saveWeather(app, state)
        state
    }

    private fun resolvePlace(): Place {
        val city = prefs.getString(Prefs.KEY_CITY, "")?.trim().orEmpty()
        if (city.isNotEmpty()) return geocode(city)

        deviceLocation()?.let {
            prefs.edit()
                .putFloat(Prefs.KEY_LAST_LAT, it.latitude.toFloat())
                .putFloat(Prefs.KEY_LAST_LON, it.longitude.toFloat())
                .apply()
            return Place(it.latitude, it.longitude, "")
        }
        if (prefs.contains(Prefs.KEY_LAST_LAT)) {
            return Place(
                prefs.getFloat(Prefs.KEY_LAST_LAT, 0f).toDouble(),
                prefs.getFloat(Prefs.KEY_LAST_LON, 0f).toDouble(),
                "",
            )
        }
        throw IllegalStateException("No location yet — allow location access or enter a city.")
    }

    private fun geocode(city: String): Place {
        if (prefs.getString(Prefs.KEY_GEO_QUERY, null) == city) {
            return Place(
                prefs.getFloat(Prefs.KEY_GEO_LAT, 0f).toDouble(),
                prefs.getFloat(Prefs.KEY_GEO_LON, 0f).toDouble(),
                prefs.getString(Prefs.KEY_GEO_NAME, city) ?: city,
            )
        }
        val url = URL(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
                URLEncoder.encode(city, "UTF-8")
        )
        val results = JSONObject(httpGet(url)).optJSONArray("results")
        if (results == null || results.length() == 0) {
            throw IllegalStateException("Couldn't find a place called \"$city\".")
        }
        val r = results.getJSONObject(0)
        val place = Place(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
        prefs.edit()
            .putString(Prefs.KEY_GEO_QUERY, city)
            .putFloat(Prefs.KEY_GEO_LAT, place.lat.toFloat())
            .putFloat(Prefs.KEY_GEO_LON, place.lon.toFloat())
            .putString(Prefs.KEY_GEO_NAME, place.name)
            .apply()
        return place
    }

    @SuppressLint("MissingPermission")
    private fun deviceLocation(): Location? {
        val granted = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = app.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)

        val last = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < THREE_HOURS) return last

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                val latch = CountDownLatch(1)
                var fresh: Location? = null
                runCatching {
                    lm.getCurrentLocation(provider, null, Runnable::run) { loc ->
                        fresh = loc
                        latch.countDown()
                    }
                    latch.await(15, TimeUnit.SECONDS)
                }
                fresh?.let { return it }
            }
        }
        return last
    }

    private fun httpGet(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} from ${url.host}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val THREE_HOURS = 3 * 60 * 60 * 1000L
    }
}
