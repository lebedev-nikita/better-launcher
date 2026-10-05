package app.better.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** A labeled schematic; it never hosts widgets or reads application data. */
class WidgetSpacingPreview @JvmOverloads constructor(context: Context, labelTypeface: Typeface = Typeface.DEFAULT) : FrameLayout(context) {
    private val gaps = List(2) { View(context).apply { setBackgroundColor(0x20BCD7F3) } }
    var spacingDp: Int = 8
        set(value) {
            field = value.coerceIn(0, 32)
            gaps.forEach { it.layoutParams = it.layoutParams.apply { height = context.dp(field) } }
            contentDescription = "Предпросмотр: между виджетами и перед избранным — $field dp"
            requestLayout()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(0, context.dp(12), 0, context.dp(12))
        val foreground = context.getColor(R.color.app_menu_foreground)
        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        fun row(label: String, glyph: Glyph, widget: Boolean) = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(40)
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            if (widget) background = GradientDrawable().apply {
                setColor(0xFF112B42.toInt()); cornerRadius = context.dp(10).toFloat()
                setStroke(context.dp(1), foreground)
            }
            addView(GlyphView(context, glyph, foreground), LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
            addView(TextView(context).apply {
                text = label; textSize = 13f; setTextColor(foreground); includeFontPadding = false
                typeface = labelTypeface
                setPadding(context.dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        stack.addView(row("Виджет", Glyph.WIDGETS, true))
        stack.addView(gaps[0], LinearLayout.LayoutParams(-1, context.dp(spacingDp)))
        stack.addView(row("Виджет", Glyph.WIDGETS, true))
        stack.addView(gaps[1], LinearLayout.LayoutParams(-1, context.dp(spacingDp)))
        stack.addView(row("Избранное", Glyph.STAR, false))
        addView(stack, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        spacingDp = spacingDp
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Reserve both maximum gaps, while letting native text grow with font scale.
        val stableHeight = measuredHeight + 2 * (context.dp(32) - context.dp(spacingDp))
        setMeasuredDimension(measuredWidth, resolveSize(stableHeight, heightMeasureSpec))
    }
}
