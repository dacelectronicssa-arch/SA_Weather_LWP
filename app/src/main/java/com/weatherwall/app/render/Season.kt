package com.weatherwall.app.render

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos

/** How green the land is at this time of year. */
object Season {
    /**
     * 1 = lush green (end of winter, about 1 September in the south), 0 = driest (end of summer,
     * about 1 March), easing smoothly between: winter and spring read green, summer and autumn brown.
     * The northern hemisphere is six months out.
     */
    fun greenness(nowMs: Long, latitude: Float): Float {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
        val day = cal.get(Calendar.DAY_OF_YEAR) - 1
        val peak = if (latitude < 0f) 243 else 60 // 1 Sep south, 1 Mar north
        return (0.5f + 0.5f * cos(2.0 * PI * (day - peak) / 365.25).toFloat()).coerceIn(0f, 1f)
    }
}
