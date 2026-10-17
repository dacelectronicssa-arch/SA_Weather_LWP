package com.weatherwall.app.render

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Low-precision sun and moon positions (good to about half a degree), from the
 * Astronomical Almanac's approximations. Plenty for drawing them in the right place.
 */
object Astro {

    /** Altitude above the horizon and azimuth (0 = north, 90 = east), both in degrees. */
    data class Horizontal(val altitude: Double, val azimuth: Double)

    fun sun(nowMs: Long, latDeg: Double, lonDeg: Double): Horizontal {
        val n = daysSinceJ2000(nowMs)
        val lambda = rad(sunLongitude(n))
        val eps = obliquity(n)
        val ra = atan2(cos(eps) * sin(lambda), cos(lambda))
        val dec = asin(sin(eps) * sin(lambda))
        return toHorizontal(ra, dec, nowMs, latDeg, lonDeg)
    }

    fun moon(nowMs: Long, latDeg: Double, lonDeg: Double): Horizontal {
        val d = daysSinceJ2000(nowMs)
        val (lonDegMoon, latDegMoon) = moonEcliptic(d)
        val lambda = rad(lonDegMoon)
        val beta = rad(latDegMoon)
        val eps = obliquity(d)
        val ra = atan2(sin(lambda) * cos(eps) - tan(beta) * sin(eps), cos(lambda))
        val dec = asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lambda))
        val h = toHorizontal(ra, dec, nowMs, latDeg, lonDeg)
        // The moon is close enough that parallax lowers it by up to ~1°.
        return h.copy(altitude = h.altitude - 0.95 * cos(rad(h.altitude)))
    }

    /** True moon phase from the sun-moon elongation: 0 = new, 0.5 = full. */
    fun moonPhase(nowMs: Long): Float {
        val d = daysSinceJ2000(nowMs)
        val elongation = (moonEcliptic(d).first - sunLongitude(d)) % 360.0
        return ((if (elongation < 0) elongation + 360.0 else elongation) / 360.0).toFloat()
    }

    /** Sun's ecliptic longitude in degrees. */
    private fun sunLongitude(d: Double): Double {
        val g = rad(357.528 + 0.9856003 * d)
        return 280.460 + 0.9856474 * d + 1.915 * sin(g) + 0.020 * sin(2 * g)
    }

    /** Moon's ecliptic longitude and latitude in degrees. */
    private fun moonEcliptic(d: Double): Pair<Double, Double> {
        val l = 218.316 + 13.176396 * d             // mean longitude
        val m = rad(134.963 + 13.064993 * d)        // mean anomaly
        val f = rad(93.272 + 13.229350 * d)         // argument of latitude
        val e = rad(297.850 + 12.190749 * d)        // mean elongation from the sun
        val ms = rad(357.529 + 0.98560028 * d)      // sun's mean anomaly
        val lon = l + 6.289 * sin(m) + 1.274 * sin(2 * e - m) + 0.658 * sin(2 * e) +
            0.214 * sin(2 * m) - 0.186 * sin(ms) - 0.114 * sin(2 * f)
        val lat = 5.128 * sin(f) + 0.281 * sin(m + f) + 0.278 * sin(m - f)
        return lon to lat
    }

    private fun obliquity(d: Double) = rad(23.439 - 0.0000004 * d)

    private fun toHorizontal(ra: Double, dec: Double, nowMs: Long, latDeg: Double, lonDeg: Double): Horizontal {
        val lat = rad(latDeg)
        val hourAngle = rad(SkyClock.lstHours(nowMs, lonDeg) * 15.0) - ra
        val alt = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle))
        val az = atan2(-cos(dec) * sin(hourAngle), sin(dec) * cos(lat) - cos(dec) * cos(hourAngle) * sin(lat))
        return Horizontal(Math.toDegrees(alt), (Math.toDegrees(az) + 360.0) % 360.0)
    }

    private fun daysSinceJ2000(nowMs: Long) = nowMs / 86_400_000.0 + 2_440_587.5 - 2_451_545.0
    private fun rad(deg: Double) = Math.toRadians(deg)
}
