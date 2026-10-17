package com.weatherwall.app

enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    OVERCAST("Overcast"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    SNOW("Snow"),
    THUNDERSTORM("Thunderstorm");

    companion object {
        /**
         * Maps WMO weather interpretation codes (as returned by Open-Meteo). For the dry sky codes
         * (0–3) the real [cloudCover] percentage, when known, picks the cloud level, because the
         * codes alone are coarse.
         */
        fun fromWmoCode(code: Int, cloudCover: Int? = null): WeatherCondition = when (code) {
            in 0..3 -> if (cloudCover != null) fromCloudCover(cloudCover) else when (code) {
                0 -> CLEAR
                1 -> PARTLY_CLOUDY
                2 -> CLOUDY
                else -> OVERCAST
            }
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDERSTORM
            else -> OVERCAST
        }

        /** Total cloud cover (%) to a sky: under 25 clear, under 60 partly, under 88 cloudy, else overcast. */
        fun fromCloudCover(percent: Int): WeatherCondition = when {
            percent < 25 -> CLEAR
            percent < 60 -> PARTLY_CLOUDY
            percent < 88 -> CLOUDY
            else -> OVERCAST
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
