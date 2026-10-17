package com.weatherwall.app.render

import android.graphics.Color

internal const val TWO_PI = 6.2831855f

internal fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/** Multiplies each RGB channel by [f] (f < 1 darkens, f > 1 lightens). */
internal fun scale(color: Int, f: Float) = Color.rgb(
    (Color.red(color) * f).toInt().coerceIn(0, 255),
    (Color.green(color) * f).toInt().coerceIn(0, 255),
    (Color.blue(color) * f).toInt().coerceIn(0, 255),
)

/** Linear blend: t = 0 gives [a], t = 1 gives [b]. */
internal fun mix(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)
