package za.co.saweather.lwp.engine

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import za.co.saweather.lwp.ThemeId

/** Visual grade applied to the composed frame, plus a few knobs read by the renderers. */
class Theme(val id: ThemeId) {
    /** Colour grade for the whole frame, or null for none (Authentic). */
    val filter: ColorMatrixColorFilter? = matrixFor(id)?.let { ColorMatrixColorFilter(it) }

    /**
     * Optional second pass drawn over the graded frame. Noir uses it for its single warm accent:
     * the matrix derives alpha from how much red exceeds green, so only warm light (sun, fire,
     * lamps, red earth) shows through, recoloured to one amber tone.
     */
    val accentFilter: ColorMatrixColorFilter? = if (id == ThemeId.NOIR) {
        ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.36f, 0.71f, 0.13f, 0f, 70f,
            0.17f, 0.33f, 0.06f, 0f, 22f,
            0.06f, 0.12f, 0.02f, 0f, 0f,
            3.2f, -3.4f, -0.6f, 0f, -40f,
        )))
    } else null

    val bloom: Float = when (id) {
        ThemeId.VIVID, ThemeId.GOLDEN_AFRICA -> 1.3f
        ThemeId.NOIR -> 1.4f
        ThemeId.DARK_MODE -> 0.55f
        ThemeId.MOODY_CAPE -> 0.85f
        else -> 1f
    }

    val fog: Float = when (id) {
        ThemeId.MOODY_CAPE -> 1.45f
        ThemeId.NOIR -> 1.2f
        ThemeId.VIVID -> 0.75f
        ThemeId.DARK_MODE -> 0.8f
        else -> 1f
    }

    /** Draw wildlife as flat silhouettes (cinematic themes). */
    val silhouettes: Boolean = id == ThemeId.SUNSET_ALWAYS || id == ThemeId.NOIR

    /** Sunset Always pins lighting to golden hour. */
    val lockHour: Float? = if (id == ThemeId.SUNSET_ALWAYS) 18.45f else null

    val rainTint: Int = when (id) {
        ThemeId.GOLDEN_AFRICA, ThemeId.SUNSET_ALWAYS -> Color.rgb(230, 210, 180)
        ThemeId.MOODY_CAPE -> Color.rgb(190, 205, 225)
        else -> Color.rgb(200, 215, 235)
    }

    val lightningColor: Int = when (id) {
        ThemeId.GOLDEN_AFRICA, ThemeId.SUNSET_ALWAYS -> Color.rgb(255, 236, 200)
        ThemeId.VIVID -> Color.rgb(220, 200, 255)
        else -> Color.rgb(232, 236, 255)
    }

    val uiText: Int = when (id) {
        ThemeId.GOLDEN_AFRICA, ThemeId.SUNSET_ALWAYS -> Color.rgb(255, 228, 190)
        ThemeId.MOODY_CAPE -> Color.rgb(215, 225, 235)
        ThemeId.DARK_MODE -> Color.rgb(150, 160, 180)
        ThemeId.INFRARED -> Color.rgb(255, 210, 230)
        else -> Color.WHITE
    }
    val uiAlpha: Float = if (id == ThemeId.DARK_MODE) 0.55f else 0.85f

    companion object {
        private fun matrixFor(id: ThemeId): ColorMatrix? = when (id) {
            ThemeId.AUTHENTIC -> null
            ThemeId.GOLDEN_AFRICA -> grade(sat = 1.15f, contrast = 1.12f, r = 1.12f, g = 1.0f, b = 0.8f, bright = -6f)
            ThemeId.MOODY_CAPE -> grade(sat = 0.55f, contrast = 1.22f, r = 0.9f, g = 0.98f, b = 1.1f, bright = -10f)
            ThemeId.VIVID -> grade(sat = 1.65f, contrast = 1.08f)
            ThemeId.SUNSET_ALWAYS -> grade(sat = 1.1f, contrast = 1.08f, r = 1.1f, g = 0.95f, b = 0.82f)
            ThemeId.DARK_MODE -> grade(sat = 0.8f, contrast = 1.0f, r = 0.5f, g = 0.53f, b = 0.6f, bright = -4f)
            ThemeId.NOIR -> ColorMatrix().apply { setSaturation(0f) }.also { it.postConcat(contrast(1.3f, -10f)) }
            ThemeId.INFRARED -> ColorMatrix(floatArrayOf(
                // Chlorophyll reflects IR: greens go white/pink, blue skies go near-black.
                0.30f, 1.70f, -0.90f, 0f, 0f,
                0.10f, 1.60f, -0.95f, 0f, 0f,
                0.15f, 1.30f, -0.50f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ))
        }

        private fun grade(sat: Float, contrast: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f, bright: Float = 0f): ColorMatrix {
            val m = ColorMatrix().apply { setSaturation(sat) }
            m.postConcat(contrast(contrast, bright))
            m.postConcat(ColorMatrix().apply { setScale(r, g, b, 1f) })
            return m
        }

        private fun contrast(c: Float, bright: Float = 0f): ColorMatrix {
            val t = 128f * (1f - c) + bright
            return ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ))
        }
    }
}
