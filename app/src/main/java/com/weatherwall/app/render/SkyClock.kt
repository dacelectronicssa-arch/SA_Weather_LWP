package com.weatherwall.app.render

/** Where the stars are: the sky turns once per sidereal day (23h 56m). */
object SkyClock {

    /** Local sidereal time in hours (0..24). Accurate to well under a minute. */
    fun lstHours(nowMs: Long, longitudeDeg: Double): Double {
        val daysSinceJ2000 = nowMs / 86_400_000.0 + 2_440_587.5 - 2_451_545.0
        val gmst = 18.697374558 + 24.06570982441908 * daysSinceJ2000
        val lst = (gmst + longitudeDeg / 15.0) % 24.0
        return if (lst < 0) lst + 24.0 else lst
    }

    /**
     * Clockwise rotation, in degrees, of the southern sky around the south celestial pole as
     * seen facing south. 0 means the Milky Way's closest point to the pole (near the Southern
     * Cross, RA ≈ 12h51m) is straight above the pole.
     */
    fun southernSkyRotation(nowMs: Long, longitudeDeg: Double): Float =
        ((lstHours(nowMs, longitudeDeg) - MILKY_WAY_NEAREST_RA_HOURS) * 15.0).toFloat()

    private const val MILKY_WAY_NEAREST_RA_HOURS = 12.85
}
