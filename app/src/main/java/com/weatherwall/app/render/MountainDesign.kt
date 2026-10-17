package com.weatherwall.app.render

/**
 * The mountains' shape and rock settings, as exported from the in-chat mountain designer.
 * Ridges are (x, height, sharp) triples: x as a fraction of screen width, height as a fraction
 * of the mountain unit, sharp = 1 for a corner instead of a smooth curve.
 */
object MountainDesign {

    /** Table Mountain, Lion's Head and Signal Hill. */
    val backRidge = floatArrayOf(
        -0.02f, 0.03f, 0f, 0.05f, 0.11f, 0f, 0.11f, 0.17f, 0f, 0.145f, 0.19f, 0f,
        0.165f, 0.192f, 0f, 0.178f, 0.208f, 0f, 0.187f, 0.221f, 1f, 0.26f, 0.222f, 0f,
        0.35f, 0.22f, 0f, 0.45f, 0.222f, 0f, 0.55f, 0.22f, 0f, 0.62f, 0.221f, 0f,
        0.6281f, 0.1849f, 0f, 0.6699f, 0.1817f, 1f, 0.6913f, 0.1762f, 0f, 0.7f, 0.165f, 0f,
        0.715f, 0.135f, 0f, 0.735f, 0.108f, 0f, 0.755f, 0.088f, 0f, 0.775f, 0.076f, 0f,
        0.8f, 0.094f, 0f, 0.825f, 0.12f, 0f, 0.8349f, 0.1452f, 0f, 0.86f, 0.156f, 0f,
        0.872f, 0.161f, 1f, 0.885f, 0.153f, 0f, 0.8939f, 0.1262f, 0f, 0.92f, 0.112f, 0f,
        0.9496f, 0.0952f, 0f, 0.97f, 0.077f, 0f, 1f, 0.069f, 0f, 1.02f, 0.065f, 0f,
    )

    /** Devil's Peak, standing in front of the table. */
    val devilsPeak = floatArrayOf(
        -0.02f, 0.03f, 0f, -0.018f, 0.0944f, 0f, 0.0418f, 0.119f, 0f, 0.045f, 0.1492f, 0f,
        0.089f, 0.1849f, 0f, 0.091f, 0.2159f, 0f, 0.123f, 0.2127f, 0f, 0.1511f, 0.204f, 0f,
        0.1651f, 0.1793f, 0f, 0.2208f, 0.1698f, 0f, 0.2508f, 0.1365f, 0f, 0.3762f, 0.0896f, 0f,
    )

    const val HEIGHT_SCALE = 1f
    const val BUTTRESS_WIDTH = 0.022f
    const val WIDTH_VARIATION = 0.05f
    const val GORGE_WIDTH = 0.002f
    const val CONTRAST = 1f
    const val DEVILS_PEAK_DEPTH = 0.93f
    const val ROCK_PATTERN = 7

    /** (x, h, sharp) triples to (x, h) pairs; sharp points are doubled so the smoothing keeps a corner. */
    fun expand(triples: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        for (i in triples.indices step 3) {
            val repeat = if (triples[i + 2] != 0f) 2 else 1
            repeat(repeat) { out += triples[i]; out += triples[i + 1] }
        }
        return out.toFloatArray()
    }
}

/** Tablecloth waterfall settings, as exported from the in-chat tablecloth preview. */
object TableclothDesign {
    const val THICKNESS = 0.95f
    const val FALL_LENGTH = 0.4f
    const val FLOW_SPEED = 0.3f
    const val STREAMS = 39
    const val PUFF_SIZE = 2f
    const val OPACITY = 0.75f
}

/** The designer's random generator (mulberry32), so rock patterns match the preview exactly. */
class Mulberry32(seed: Int) {
    private var s = seed

    fun next(): Float {
        s += 0x6D2B79F5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
    }
}
