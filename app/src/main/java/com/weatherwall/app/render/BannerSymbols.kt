package com.weatherwall.app.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The little picture on the left of a holiday banner, in place of the advert's two stars. */
enum class BannerSymbol {
    SANTA, TREE, CRESCENT, DIYA, CHAMPAGNE, NEW_YEAR, PARASOL, LANTERN, HEARTS, CROSS, EGG,
    FLAG, PROTEA, BRAAI, HANDS, HARD_HAT, HEART, TIE, MANDELA, ROCKET;

    companion object {
        /** The symbol for [holiday]'s banner, given its [greeting] for the day (Good Friday, Day of Goodwill...). */
        fun forHoliday(holiday: Holiday, greeting: String): BannerSymbol = when {
            greeting.contains("Goodwill") -> TREE
            greeting.contains("Good Friday") -> CROSS
            else -> when (holiday.name) {
                "Christmas" -> SANTA
                "Eid al-Fitr" -> CRESCENT
                "Diwali" -> DIYA
                "New Year's Eve" -> CHAMPAGNE
                "New Year's Day" -> NEW_YEAR
                "Tweede Nuwe Jaar" -> PARASOL
                "Chinese New Year" -> LANTERN
                "Valentine's Day" -> HEARTS
                "Easter" -> EGG
                "National Women's Day" -> PROTEA
                "Heritage Day" -> BRAAI
                "Day of Reconciliation" -> HANDS
                "Workers' Day" -> HARD_HAT
                "Mother's Day" -> HEART
                "Father's Day" -> TIE
                "Mandela Day" -> MANDELA
                "Guy Fawkes" -> ROCKET
                else -> FLAG // Freedom, Youth and Human Rights Day
            }
        }
    }
}

/** Draws [BannerSymbol]s centred on (cx, cy), [s] tall; [year] is shown on the New Year one. */
internal class BannerSymbolArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    private fun fill(col: Long) { p.shader = null; p.style = Paint.Style.FILL; p.color = col.toInt() }
    private fun stroke(col: Long, w: Float) {
        p.shader = null; p.style = Paint.Style.STROKE; p.color = col.toInt(); p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND
    }
    private fun Canvas.circle(x: Float, y: Float, r: Float) = drawCircle(x, y, r, p)
    private fun Canvas.ellipse(x: Float, y: Float, rx: Float, ry: Float) {
        oval.set(x - rx, y - ry, x + rx, y + ry); drawOval(oval, p)
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, col: Long) {
        fill(col)
        path.reset()
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val rr = if (i % 2 == 1) r * 0.42f else r
            val px = x + cos(a).toFloat() * rr
            val py = y + sin(a).toFloat() * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float, col: Long) {
        fill(col)
        path.reset()
        path.moveTo(x, y + s * .35f)
        path.cubicTo(x - s * .6f, y - s * .05f, x - s * .35f, y - s * .55f, x, y - s * .2f)
        path.cubicTo(x + s * .35f, y - s * .55f, x + s * .6f, y - s * .05f, x, y + s * .35f)
        c.drawPath(path, p)
    }

    private fun poly(c: Canvas, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        c.drawPath(path, p)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Long) {
        fill(col)
        p.textSize = size
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
    }

    fun draw(c: Canvas, sym: BannerSymbol, cx: Float, cy: Float, s: Float, year: Int) {
        when (sym) {
            BannerSymbol.SANTA -> {
                fill(0xFFF2D0B5); c.circle(cx, cy + s * .04f, s * .2f)
                // Beard and moustache.
                fill(0xFFFFFFFF)
                oval.set(cx - s * .2f, cy, cx + s * .2f, cy + s * .4f); c.drawArc(oval, 0f, 180f, true, p)
                c.circle(cx - s * .14f, cy + s * .12f, s * .08f); c.circle(cx + s * .14f, cy + s * .12f, s * .08f)
                c.ellipse(cx, cy + s * .3f, s * .16f, s * .12f)
                // Hat with its fur trim and bobble.
                fill(0xFFD62828)
                path.reset()
                path.moveTo(cx - s * .24f, cy - s * .08f)
                path.quadTo(cx - s * .05f, cy - s * .5f, cx + s * .32f, cy - s * .3f)
                path.lineTo(cx + s * .22f, cy - s * .08f); path.close()
                c.drawPath(path, p)
                fill(0xFFFFFFFF)
                c.drawRect(cx - s * .26f, cy - s * .12f, cx + s * .26f, cy - s * .04f, p)
                c.circle(cx + s * .33f, cy - s * .3f, s * .07f)
                fill(0xFF222222); c.circle(cx - s * .07f, cy - s * .01f, s * .025f); c.circle(cx + s * .07f, cy - s * .01f, s * .025f)
                fill(0xFFE8908A); c.circle(cx, cy + s * .05f, s * .035f)
            }
            BannerSymbol.TREE -> {
                fill(0xFF1FBF5A); poly(c, cx, cy - s * .4f, cx + s * .26f, cy + s * .22f, cx - s * .26f, cy + s * .22f)
                fill(0xFF7A4A2A); c.drawRect(cx - s * .05f, cy + s * .22f, cx + s * .05f, cy + s * .36f, p)
                star(c, cx, cy - s * .42f, s * .09f, 0xFFFFD84D)
                val baubles = longArrayOf(0xFFE0183A, 0xFFFFD84D, 0xFF4DC3FF)
                for (i in 0..2) {
                    fill(baubles[i]); c.circle(cx - s * .1f + i * s * .1f, cy + (if (i % 2 == 1) -.02f else .1f) * s, s * .035f)
                }
            }
            BannerSymbol.CRESCENT -> {
                fill(0xFFFFD84D); c.circle(cx - s * .04f, cy, s * .32f)
                fill(BANNER); c.circle(cx + s * .08f, cy - s * .05f, s * .27f)
                star(c, cx + s * .22f, cy - s * .02f, s * .11f, 0xFFFFD84D)
            }
            BannerSymbol.DIYA -> {
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(cx, cy - s * .18f, s * .2f,
                    intArrayOf(0xFFFFF6B0.toInt(), 0xFFFFB21A.toInt(), 0x00FF6A00), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy - s * .18f, s * .2f, p)
                fill(0xFFFFD84D)
                path.reset()
                path.moveTo(cx, cy - s * .36f)
                path.quadTo(cx + s * .08f, cy - s * .16f, cx, cy - s * .08f)
                path.quadTo(cx - s * .08f, cy - s * .16f, cx, cy - s * .36f)
                c.drawPath(path, p)
                fill(0xFFC8642A)
                path.reset()
                path.moveTo(cx - s * .32f, cy - s * .04f)
                path.quadTo(cx, cy + s * .36f, cx + s * .32f, cy - s * .04f)
                path.quadTo(cx + s * .36f, cy - s * .08f, cx + s * .4f, cy - s * .12f)
                path.lineTo(cx + s * .28f, cy - s * .04f); path.close()
                c.drawPath(path, p)
                fill(0xFFFFB21A); c.drawRect(cx - s * .2f, cy + s * .04f, cx + s * .2f, cy + s * .07f, p)
            }
            BannerSymbol.CHAMPAGNE, BannerSymbol.NEW_YEAR -> {
                val cols = longArrayOf(0xFFFFD84D, 0xFFFF4D6D, 0xFF4DC3FF)
                for (i in 0..2) {
                    stroke(cols[i], s * .05f)
                    val bx = cx + (i - 1) * s * .18f
                    val by = cy - s * .08f
                    for (j in 0 until 8) {
                        val a = i * 2.1 + j * PI / 4
                        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                        c.drawLine(bx + ca * s * .06f, by + sa * s * .06f, bx + ca * s * .16f, by + sa * s * .16f, p)
                    }
                }
                if (sym == BannerSymbol.CHAMPAGNE) {
                    fill(0xFFFFE9A8)
                    poly(c, cx - s * .08f, cy + s * .08f, cx + s * .08f, cy + s * .08f, cx + s * .03f, cy + s * .24f, cx - s * .03f, cy + s * .24f)
                    c.drawRect(cx - s * .01f, cy + s * .24f, cx + s * .01f, cy + s * .36f, p)
                    c.drawRect(cx - s * .07f, cy + s * .35f, cx + s * .07f, cy + s * .38f, p)
                } else {
                    text(c, year.toString(), cx, cy + s * .34f, s * .28f, 0xFFFFFFFF)
                }
            }
            BannerSymbol.PARASOL -> {
                val cols = longArrayOf(0xFFFF2FA0, 0xFFFFE14D, 0xFF3CF0FF, 0xFF7CFF4D, 0xFFFF7A1A, 0xFFB06BFF)
                oval.set(cx - s * .36f, cy - s * .41f, cx + s * .36f, cy + s * .31f)
                for (i in 0 until 6) { fill(cols[i]); c.drawArc(oval, 180f + i * 30f, 30f, true, p) }
                stroke(0xFFDDDDDD, s * .04f)
                p.strokeCap = Paint.Cap.BUTT
                path.reset()
                path.moveTo(cx, cy - s * .05f)
                path.lineTo(cx, cy + s * .34f)
                path.quadTo(cx, cy + s * .42f, cx - s * .08f, cy + s * .38f)
                c.drawPath(path, p)
            }
            BannerSymbol.LANTERN -> {
                stroke(0xFFFFCF3D, s * .03f); c.drawLine(cx, cy - s * .42f, cx, cy - s * .3f, p)
                fill(0xFFFFCF3D)
                c.drawRect(cx - s * .12f, cy - s * .32f, cx + s * .12f, cy - s * .27f, p)
                c.drawRect(cx - s * .12f, cy + s * .24f, cx + s * .12f, cy + s * .29f, p)
                fill(0xFFE0181E); c.ellipse(cx, cy - s * .02f, s * .28f, s * .27f)
                stroke(0xFFFF6A5A, s * .02f)
                for (o in floatArrayOf(.12f, .001f)) c.ellipse(cx, cy - s * .02f, o * s + .5f, s * .27f)
                stroke(0xFFFFCF3D, s * .025f)
                for (t in -1..1) c.drawLine(cx + t * s * .04f, cy + s * .29f, cx + t * s * .04f, cy + s * .42f, p)
            }
            BannerSymbol.HEARTS -> {
                heart(c, cx - s * .06f, cy, s * .62f, 0xFFE0183A)
                heart(c, cx + s * .14f, cy - s * .1f, s * .34f, 0xFFFF9CC2)
            }
            BannerSymbol.CROSS -> {
                fill(0xFFF3E3C0)
                c.drawRect(cx - s * .05f, cy - s * .4f, cx + s * .05f, cy + s * .4f, p)
                c.drawRect(cx - s * .24f, cy - s * .2f, cx + s * .24f, cy - s * .1f, p)
            }
            BannerSymbol.EGG -> {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, cy - s * .36f, 0f, cy + s * .36f, 0xFFC9A7FF.toInt(), 0xFF9BE7A0.toInt(), Shader.TileMode.CLAMP)
                c.ellipse(cx, cy + s * .02f, s * .24f, s * .34f)
                stroke(0xFFFFE36B, s * .05f)
                p.strokeJoin = Paint.Join.ROUND
                path.reset()
                path.moveTo(cx - s * .22f, cy - s * .04f)
                for (z in 0 until 6) path.lineTo(cx - s * .22f + (z + 1) * s * .073f, cy - s * .04f + if (z % 2 == 1) 0f else s * .07f)
                c.drawPath(path, p)
                fill(0xFFFFFFFF)
                c.circle(cx - s * .08f, cy + s * .16f, s * .035f)
                c.circle(cx + s * .08f, cy + s * .18f, s * .035f)
                c.circle(cx, cy - s * .18f, s * .035f)
            }
            BannerSymbol.FLAG -> {
                val w = s * .9f; val hh = s * .6f; val x0 = cx - w / 2; val y0 = cy - hh / 2
                c.save()
                c.clipRect(x0, y0, x0 + w, y0 + hh)
                fill(0xFFE03C31); c.drawRect(x0, y0, x0 + w, y0 + hh / 2, p)
                fill(0xFF001489); c.drawRect(x0, y0 + hh / 2, x0 + w, y0 + hh, p)
                path.reset()
                path.moveTo(x0, y0); path.lineTo(x0 + w * .42f, cy); path.lineTo(x0, y0 + hh)
                path.moveTo(x0 + w * .42f, cy); path.lineTo(x0 + w, cy)
                stroke(0xFFFFFFFF, hh * .34f); p.strokeCap = Paint.Cap.BUTT; c.drawPath(path, p)
                stroke(0xFF007749, hh * .2f); p.strokeCap = Paint.Cap.BUTT; c.drawPath(path, p)
                fill(0xFFFFB81C); poly(c, x0, y0 + hh * .16f, x0 + w * .27f, cy, x0, y0 + hh * .84f)
                fill(0xFF000000); poly(c, x0, y0 + hh * .24f, x0 + w * .2f, cy, x0, y0 + hh * .76f)
                c.restore()
            }
            BannerSymbol.PROTEA -> {
                fill(0xFF3A8A3A); c.drawRect(cx - s * .02f, cy + s * .1f, cx + s * .02f, cy + s * .4f, p)
                for (i in 0 until 9) {
                    val an = -PI + i * PI / 8
                    c.save()
                    c.translate(cx, cy + s * .08f)
                    c.rotate(Math.toDegrees(an + PI / 2).toFloat())
                    fill(if (i % 2 == 1) 0xFFF28BB0 else 0xFFE8608E)
                    c.ellipse(0f, -s * .2f, s * .07f, s * .2f)
                    c.restore()
                }
                fill(0xFFF4E3D0); c.ellipse(cx, cy - s * .02f, s * .12f, s * .16f)
            }
            BannerSymbol.BRAAI -> {
                val flames = floatArrayOf(-.15f, 0f, .15f)
                val tops = floatArrayOf(0f, -.06f, 0f)
                for (i in 0..2) {
                    val fx = cx + flames[i] * s
                    fill(0xFFFF7A1A)
                    path.reset(); path.moveTo(fx - s * .06f, cy + s * .05f)
                    path.quadTo(fx, cy + tops[i] * s - s * .28f, fx + s * .06f, cy + s * .05f); c.drawPath(path, p)
                    fill(0xFFFFD84D)
                    path.reset(); path.moveTo(fx - s * .03f, cy + s * .05f)
                    path.quadTo(fx, cy - s * .12f, fx + s * .03f, cy + s * .05f); c.drawPath(path, p)
                }
                fill(0xFF888888); c.drawRect(cx - s * .36f, cy + s * .05f, cx + s * .36f, cy + s * .09f, p)
                fill(0xFF444444); poly(c, cx - s * .32f, cy + s * .1f, cx + s * .32f, cy + s * .1f, cx + s * .22f, cy + s * .26f, cx - s * .22f, cy + s * .26f)
                stroke(0xFF666666, s * .04f)
                c.drawLine(cx - s * .16f, cy + s * .26f, cx - s * .24f, cy + s * .42f, p)
                c.drawLine(cx + s * .16f, cy + s * .26f, cx + s * .24f, cy + s * .42f, p)
                fill(0xFF8B3A2A); c.ellipse(cx - s * .08f, cy + s * .03f, s * .14f, s * .035f)
            }
            BannerSymbol.HANDS -> {
                fill(0xFF8A5A3C)
                c.save(); c.rotate(-11.5f, cx - s * .12f, cy); c.ellipse(cx - s * .12f, cy, s * .22f, s * .12f); c.restore()
                fill(0xFFF0C8A0)
                c.save(); c.rotate(11.5f, cx + s * .12f, cy + s * .02f); c.ellipse(cx + s * .12f, cy + s * .02f, s * .22f, s * .12f); c.restore()
                fill(0xFF007749); c.drawRect(cx - s * .42f, cy - s * .06f, cx - s * .3f, cy + s * .1f, p)
                fill(0xFFFFB81C); c.drawRect(cx + s * .3f, cy - s * .04f, cx + s * .42f, cy + s * .12f, p)
            }
            BannerSymbol.HARD_HAT -> {
                fill(0xFFFFD84D)
                oval.set(cx - s * .3f, cy - s * .22f, cx + s * .3f, cy + s * .38f); c.drawArc(oval, 180f, 180f, true, p)
                c.drawRect(cx - s * .38f, cy + s * .06f, cx + s * .38f, cy + s * .13f, p)
                fill(0xFFE8B800); c.drawRect(cx - s * .03f, cy - s * .22f, cx + s * .03f, cy + s * .06f, p)
            }
            BannerSymbol.HEART -> {
                heart(c, cx, cy + s * .02f, s * .6f, 0xFFFF9CC2)
                fill(0xFFFFFFFF); c.circle(cx - s * .08f, cy - s * .08f, s * .05f)
            }
            BannerSymbol.TIE -> {
                fill(0xFF4DC3FF)
                poly(c, cx - s * .08f, cy - s * .36f, cx + s * .08f, cy - s * .36f, cx + s * .05f, cy - s * .26f, cx + s * .12f, cy + s * .26f,
                    cx, cy + s * .38f, cx - s * .12f, cy + s * .26f, cx - s * .05f, cy - s * .26f)
                stroke(0xFF1D6FA0, s * .03f); p.strokeCap = Paint.Cap.BUTT
                for (q in 0 until 3) c.drawLine(cx - s * .09f, cy - s * .1f + q * s * .14f, cx + s * .09f, cy - s * .18f + q * s * .14f, p)
            }
            BannerSymbol.MANDELA -> {
                stroke(0xFFFFB81C, s * .05f); c.circle(cx, cy, s * .32f)
                fill(0xFFFFFFFF)
                p.textSize = s * .32f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
                val fm = p.fontMetrics
                c.drawText("67", cx, cy + s * .02f - (fm.ascent + fm.descent) / 2f, p)
            }
            BannerSymbol.ROCKET -> {
                stroke(0xFFFFD84D, s * .035f)
                for (r in 0 until 8) {
                    val a = r * PI / 4
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    c.drawLine(cx + s * .14f + ca * s * .05f, cy - s * .22f + sa * s * .05f, cx + s * .14f + ca * s * .14f, cy - s * .22f + sa * s * .14f, p)
                }
                c.save()
                c.translate(cx - s * .08f, cy + s * .12f)
                c.rotate(Math.toDegrees(0.6).toFloat())
                fill(0xFFE0183A); c.drawRect(-s * .05f, -s * .2f, s * .05f, s * .1f, p)
                fill(0xFFFFFFFF); poly(c, -s * .05f, -s * .2f, 0f, -s * .3f, s * .05f, -s * .2f)
                stroke(0xFFC8A060, s * .02f); c.drawLine(0f, s * .1f, 0f, s * .3f, p)
                c.restore()
            }
        }
        p.shader = null
    }

    private companion object {
        const val BANNER = 0xFF1E1C1CL
    }
}
