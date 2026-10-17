package com.weatherwall.app

enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    SNOW("Snow"),
    THUNDERSTORM("Thunderstorm");

    companion object {
        /** Maps WMO weather interpretation codes (as returned by Open-Meteo). */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}

data class WeatherState(
    val condition: WeatherCondition,
    val isDay: Boolean,
    val temperatureC: Double = Double.NaN,
    val windKmh: Double = 0.0,
    val place: String = "",
    val fetchedAt: Long = 0L,
)
