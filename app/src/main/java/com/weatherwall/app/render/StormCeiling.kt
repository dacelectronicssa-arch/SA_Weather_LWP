package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build

/** Storm settings, as exported from the in-chat Cape front storm designer. */
object StormDesign {
    const val DARK = 1.15f       // deck darkness
    const val ROLLS = 1.3f       // rolls in the deck
    const val SCUD = 1.25f       // amount of torn low cloud
    const val SCUD_SPEED = 1f
    const val SCUD_LOW = 0.55f   // 0 low .. 1 high
    const val RAIN = 1.2f        // rain amount (scales the rain streaks)
    const val WIND = 1.45f       // rain slant
    const val HAZE = 1.2f        // grey rain veils hanging below the deck

    fun isSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * The rain sky, a Cape cold front: a smooth grey deck with soft rolls, two layers of dark torn
 * scud racing beneath it at different speeds, and grey veils of rain. Generated live on the GPU
 * (the same shader as the designer) and drawn behind the land; Table Mountain keeps its usual
 * tablecloth.
 */
@android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
class StormCeiling(
    private val width: Float,
    private val height: Float,
    night: Boolean,
) {
    private val light = if (night) 0.25f else 1f

    private val sky = RuntimeShader(COMMON + SKY).apply { setCommon(this) }
    private val skyPaint = Paint().apply { shader = sky }


    private fun setCommon(s: RuntimeShader) {
        s.setFloatUniform("res", width, height)
        s.setFloatUniform("L", light)
        s.setFloatUniform("dark", StormDesign.DARK)
        s.setFloatUniform("rolls", StormDesign.ROLLS)
        s.setFloatUniform("scud", StormDesign.SCUD)
        s.setFloatUniform("scudSpd", StormDesign.SCUD_SPEED)
        s.setFloatUniform("scudLow", StormDesign.SCUD_LOW)
        s.setFloatUniform("haze", StormDesign.HAZE)
    }

    /** The deck, scud and rain veils, drawn as the sky behind the land. */
    fun drawSky(canvas: Canvas, time: Float, flash: Float) {
        sky.setFloatUniform("time", time)
        sky.setFloatUniform("flash", flash)
        canvas.drawRect(0f, 0f, width, height, skyPaint)
    }


    private companion object {
        const val COMMON = """
            uniform float time;
            uniform float2 res;
            uniform float L;
            uniform float flash;
            uniform float dark;
            uniform float rolls;
            uniform float scud;
            uniform float scudSpd;
            uniform float scudLow;
            uniform float haze;

            // Small-input hash that stays accurate at mobile GPU precision.
            float hash(float2 p) {
                float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = p - i;
                // Keep lattice coordinates small as the clouds drift; wrap the neighbours too.
                float2 i0 = mod(i, 289.0);
                float2 i1 = mod(i + 1.0, 289.0);
                float2 u = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i0), hash(float2(i1.x, i0.y)), u.x),
                           mix(hash(float2(i0.x, i1.y)), hash(i1), u.x), u.y);
            }
            float fbm(float2 p) {
                float v = 0.0;
                float a = 0.5;
                float2x2 m = float2x2(1.6, 1.2, -1.2, 1.6);
                for (int i = 0; i < 6; i++) {
                    v += a * noise(p);
                    p = m * p;
                    a *= 0.5;
                }
                return v;
            }
        """

        const val SKY = """
            half4 main(float2 fc) {
                float x = fc.x / res.x;
                float y = fc.y / res.y;
                float t = time;
                float lt = L + flash * 0.55;
                // The deck: a smooth grey layer with soft rolls, darker towards the horizon.
                float d1 = fbm(float2(x * 1.1 - t * 0.012, y * 2.2));
                float roll = sin(y * 38.0 + fbm(float2(x * 2.0, y * 3.0) - t * 0.01) * 6.0) * 0.5 + 0.5;
                float3 deck = mix(float3(0.70, 0.73, 0.76), float3(0.48, 0.52, 0.57), clamp(y * 1.25, 0.0, 1.0))
                    * (0.86 + 0.22 * d1) * (1.0 - rolls * 0.07 * roll) / dark;
                float3 col = deck * lt;
                // Two layers of scud: the lower one races faster. Lit tops, dark undersides.
                float lo = mix(0.62, 0.38, scudLow);
                for (int k = 0; k < 2; k++) {
                    float fk = float(k);
                    float yc = lo - 0.12 + fk * 0.12;
                    float sp = (0.10 + fk * 0.08) * scudSpd;
                    float2 q = float2(x * (2.4 + fk) - t * sp, y * (4.5 + fk) + fk * 7.0);
                    float s = fbm(q);
                    float band = smoothstep(yc - 0.13, yc - 0.03, y) * smoothstep(yc + 0.11, yc + 0.01, y);
                    float a = smoothstep(0.62 - 0.12 * scud, 0.72 - 0.1 * scud, s) * band * min(1.0, scud * 1.2);
                    float top = clamp((fbm(q + float2(0.0, 0.08)) - s) * -6.0 + 0.5, 0.0, 1.0);
                    float3 sc = mix(float3(0.17, 0.19, 0.23), float3(0.52, 0.55, 0.6), top * 0.6)
                        * (0.85 + 0.3 * fbm(q * 3.0)) / mix(1.0, dark, 0.6);
                    col = mix(col, sc * lt, a * 0.95);
                }
                // Veils of rain hanging below the deck.
                float veil = fbm(float2(x * 3.0 - t * 0.05 * scudSpd, 0.0)) * smoothstep(0.35, 0.7, y);
                col = mix(col, float3(0.5, 0.54, 0.58) * lt, veil * 0.25 * haze);
                return half4(half3(col), 1.0);
            }
        """

    }
}
