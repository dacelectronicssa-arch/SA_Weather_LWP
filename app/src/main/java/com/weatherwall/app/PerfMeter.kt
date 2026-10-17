package com.weatherwall.app

import android.os.SystemClock
import android.util.Log

/**
 * Debug builds only: how much work each frame costs. Every 10 s logs (tag "Perf") the frame
 * rate and the average / worst time spent updating, drawing (recording the canvas) and
 * posting the frame (unlockCanvasAndPost, which includes waiting on the GPU).
 */
class PerfMeter(private val label: () -> String) {
    private var windowStart = SystemClock.elapsedRealtime()
    private var frames = 0
    private var updSum = 0L
    private var drawSum = 0L
    private var postSum = 0L
    private var worst = 0L
    private var t0 = 0L
    private var t1 = 0L
    private var t2 = 0L

    fun begin() { t0 = SystemClock.elapsedRealtimeNanos() }
    fun updated() { t1 = SystemClock.elapsedRealtimeNanos() }
    fun drawn() { t2 = SystemClock.elapsedRealtimeNanos() }

    fun posted() {
        val t3 = SystemClock.elapsedRealtimeNanos()
        updSum += t1 - t0; drawSum += t2 - t1; postSum += t3 - t2
        worst = maxOf(worst, t3 - t0)
        frames++
        val now = SystemClock.elapsedRealtime()
        val span = now - windowStart
        if (span >= 10_000) {
            fun ms(ns: Long) = "%.2f".format(ns / 1e6 / frames)
            Log.d("Perf", "${label()} fps=${"%.1f".format(frames * 1000f / span)} update=${ms(updSum)}ms draw=${ms(drawSum)}ms post=${ms(postSum)}ms worst=${"%.1f".format(worst / 1e6)}ms")
            windowStart = now; frames = 0; updSum = 0; drawSum = 0; postSum = 0; worst = 0
        }
    }
}
