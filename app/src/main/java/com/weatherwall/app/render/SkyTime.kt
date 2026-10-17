package com.weatherwall.app.render

/** The clock the sky follows. Debug builds can shift it to preview other times of day. */
object SkyTime {
    @Volatile
    var offsetMs = 0L

    fun now() = System.currentTimeMillis() + offsetMs
}
