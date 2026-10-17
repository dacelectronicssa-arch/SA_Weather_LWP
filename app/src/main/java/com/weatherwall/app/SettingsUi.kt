package com.weatherwall.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/** The settings screen's look: colours, type and a few small widgets built on plain Android views. */
object Ui {
    const val BG = 0xFF0B111C.toInt()
    const val CARD = 0xFF141D2B.toInt()
    const val RAISED = 0xFF1C2738.toInt()
    const val LINE = 0xFF263349.toInt()
    const val TEXT = 0xFFEAF0F7.toInt()
    const val MUTED = 0xFF8C9AB0.toInt()
    const val ACCENT = 0xFF5CC8F2.toInt()
    const val ON_ACCENT = 0xFF06202E.toInt()

    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).roundToInt()

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    /** A touch ripple over [content], clipped to the same rounded shape. */
    fun ripple(content: Drawable, radius: Float) =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, rounded(Color.WHITE, radius))

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int = TEXT, face: Typeface? = null) = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (face != null) typeface = face
    }

    /** Small all-caps label above each card. */
    fun section(ctx: Context, s: String) = text(ctx, s.uppercase(), 12f, MUTED, MEDIUM).apply {
        letterSpacing = 0.12f
        setPadding(dp(ctx, 4f), dp(ctx, 28f), 0, dp(ctx, 10f))
    }

    /** A rounded panel that groups related settings. */
    fun card(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, dp(ctx, 20f).toFloat())
        val p = dp(ctx, 18f)
        setPadding(p, p, p, p)
    }

    /** A full-width pill button: filled with the accent, or quiet (outlined). */
    fun pillButton(ctx: Context, label: String, filled: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = MEDIUM
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val r = dp(ctx, 28f).toFloat()
        if (filled) {
            setTextColor(ON_ACCENT)
            background = ripple(rounded(ACCENT, r), r)
        } else {
            setTextColor(TEXT)
            background = ripple(rounded(Color.TRANSPARENT, r, dp(ctx, 1f), LINE), r)
        }
        minHeight = dp(ctx, 52f)
        setPadding(dp(ctx, 20f), 0, dp(ctx, 20f), 0)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** A chip: rounded, filled with the accent when selected. */
    fun chip(ctx: Context, label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = MEDIUM
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(ctx, 14f), dp(ctx, 8f), dp(ctx, 14f), dp(ctx, 8f))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun setChipSelected(chip: TextView, selected: Boolean, enabled: Boolean = true) {
        val r = Ui.dp(chip.context, 18f).toFloat()
        chip.isEnabled = enabled
        chip.alpha = if (enabled) 1f else 0.4f
        chip.setTextColor(if (selected) ON_ACCENT else TEXT)
        chip.background = ripple(if (selected) rounded(ACCENT, r) else rounded(RAISED, r, dp(chip.context, 1f), LINE), r)
    }

    /** A thin divider inside a card. */
    fun divider(ctx: Context) = View(ctx).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, max(1, dp(ctx, 1f))).apply {
            topMargin = dp(ctx, 16f); bottomMargin = dp(ctx, 16f)
        }
    }
}

/**
 * A row of equal segments, one of which is selected (like a Material segmented button).
 * [onSelect] gets the chosen index.
 */
class Segmented(ctx: Context, labels: List<String>, private val onSelect: (Int) -> Unit) : LinearLayout(ctx) {
    private val items = ArrayList<TextView>()
    var selected = -1
        private set

    init {
        orientation = HORIZONTAL
        val r = Ui.dp(ctx, 14f).toFloat()
        background = Ui.rounded(Ui.RAISED, r)
        val p = Ui.dp(ctx, 4f)
        setPadding(p, p, p, p)
        for ((i, label) in labels.withIndex()) {
            val t = TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                typeface = Ui.MEDIUM
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                minHeight = Ui.dp(ctx, 40f)
                isClickable = true
                setOnClickListener { if (isEnabled) { select(i); onSelect(i) } }
            }
            items += t
            addView(t, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    fun select(index: Int) {
        selected = index
        val r = Ui.dp(context, 11f).toFloat()
        for ((i, t) in items.withIndex()) {
            val on = i == index
            t.setTextColor(if (on) Ui.ON_ACCENT else Ui.TEXT)
            t.background = Ui.ripple(Ui.rounded(if (on) Ui.ACCENT else Color.TRANSPARENT, r), r)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
        for (t in items) t.isEnabled = enabled
    }
}

/** Lays children out left to right, wrapping onto new lines (for chips). */
class FlowLayout(ctx: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + vGap; lineH = 0 }
            x += c.measuredWidth + hGap
            lineH = max(lineH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + lineH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + vGap; lineH = 0 }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + hGap
            lineH = max(lineH, c.measuredHeight)
        }
    }
}
