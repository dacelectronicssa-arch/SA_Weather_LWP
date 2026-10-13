package za.co.saweather.lwp.engine

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

const val TAU = (Math.PI * 2).toFloat()
const val PI_F = Math.PI.toFloat()

fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3f - 2f * t)
}

/** 0 outside [a,d], ramps up over [a,b], holds over [b,c], ramps down over [c,d]. */
fun window(a: Float, b: Float, c: Float, d: Float, x: Float): Float =
    smoothstep(a, b, x) * (1f - smoothstep(c, d, x))

/** Circular window over a 24h clock, so ranges may wrap midnight. */
fun hourWindow(a: Float, b: Float, c: Float, d: Float, hour: Float): Float {
    var best = 0f
    for (shift in floatArrayOf(-24f, 0f, 24f)) best = max(best, window(a, b, c, d, hour + shift))
    return best
}

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = clamp01(t)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * tt).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt(),
    )
}

fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((clamp01(alpha) * 255f).toInt() shl 24)

/** Multiplies a colour's RGB by an ambient light colour (component-wise, 0..255 scale). */
fun multiply(color: Int, light: Int): Int = Color.argb(
    Color.alpha(color),
    Color.red(color) * Color.red(light) / 255,
    Color.green(color) * Color.green(light) / 255,
    Color.blue(color) * Color.blue(light) / 255,
)

/** Keyframed colour track over a 24h clock (keys sorted, first at 0 and last at 24). */
class ColorTrack(private val hours: FloatArray, private val colors: IntArray) {
    fun at(hour: Float): Int {
        val h = ((hour % 24f) + 24f) % 24f
        for (i in 0 until hours.size - 1) {
            if (h <= hours[i + 1]) {
                val t = (h - hours[i]) / (hours[i + 1] - hours[i])
                return lerpColor(colors[i], colors[i + 1], smoothstep(0f, 1f, t))
            }
        }
        return colors.last()
    }

    companion object {
        fun of(vararg pairs: Pair<Float, Int>) =
            ColorTrack(FloatArray(pairs.size) { pairs[it].first }, IntArray(pairs.size) { pairs[it].second })
    }
}

/** Small fast deterministic RNG (xorshift) so procedural layouts are stable per seed. */
class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    fun float(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())
    fun range(a: Float, b: Float): Float = a + (b - a) * float()
    fun int(n: Int): Int = ((nextLong() ushr 33) % n).toInt()
    fun chance(p: Float): Boolean = float() < p
    fun sign(): Float = if (float() < 0.5f) -1f else 1f
}

/** Cheap 1D value noise, smooth, in [-1, 1]. */
fun noise1(x: Float, seed: Int = 0): Float {
    val i = kotlin.math.floor(x).toInt()
    val f = x - i
    val a = hash(i + seed * 7919)
    val b = hash(i + 1 + seed * 7919)
    val t = f * f * (3f - 2f * f)
    return lerp(a, b, t)
}

private fun hash(n: Int): Float {
    var x = n
    x = (x shl 13) xor x
    val v = (x * (x * x * 15731 + 789221) + 1376312589) and 0x7fffffff
    return 1f - v / 1073741824f
}

/** Fractal noise, roughly [-1, 1]. */
fun fbm(x: Float, octaves: Int = 4, seed: Int = 0): Float {
    var sum = 0f
    var amp = 0.5f
    var freq = 1f
    var norm = 0f
    repeat(octaves) {
        sum += noise1(x * freq, seed + it) * amp
        norm += amp
        amp *= 0.5f
        freq *= 2.03f
    }
    return sum / norm
}
