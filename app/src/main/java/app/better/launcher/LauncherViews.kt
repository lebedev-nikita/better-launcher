package app.better.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.exp
import kotlin.math.min

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Int): Int = dp(value.toFloat())
fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }
fun touchBackground(color: Int = 0x00000000): RippleDrawable = RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), rounded(color, 24f), rounded(Color.WHITE, 24f))

/** Original vector wallpaper; the system-wallpaper option leaves this view transparent. */
class WallpaperView(context: Context) : View(context) {
    var style = "dune"
        set(value) { field = value; invalidate() }
    var shade = 0.16f
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        paint.alpha = 255
        val w = width.toFloat(); val h = height.toFloat()
        val colors = when (style) {
            "forest" -> intArrayOf(0xFF9DAFA5.toInt(), 0xFF354C44.toInt(), 0xFF152A23.toInt())
            "night" -> intArrayOf(0xFF5D6780.toInt(), 0xFF303C55.toInt(), 0xFF141E32.toInt())
            else -> intArrayOf(0xFFD8B8A1.toInt(), 0xFFB38264.toInt(), 0xFF493A32.toInt())
        }
        if (style != "system") {
            paint.shader = LinearGradient(0f, 0f, w * .4f, h, colors, floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            val path = Path().apply {
                moveTo(-w * .1f, h * .85f)
                cubicTo(w * .13f, h * .6f, w * .49f, h * .4f, w * 1.1f, h * .35f)
                lineTo(w * 1.1f, h * 1.1f); lineTo(-w * .1f, h * 1.1f); close()
            }
            paint.shader = LinearGradient(w * .6f, h * .4f, w * .3f, h, colors[0], colors[2], Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
            val lower = Path().apply {
                moveTo(-w * .1f, h * .88f)
                cubicTo(w * .38f, h * .72f, w * .8f, h * .8f, w * 1.1f, h * .66f)
                lineTo(w * 1.1f, h * 1.1f); lineTo(-w * .1f, h * 1.1f); close()
            }
            paint.shader = LinearGradient(0f, h * .7f, w, h, colors[1], colors[2], Shader.TileMode.CLAMP)
            canvas.drawPath(lower, paint)
        }
        paint.shader = null
        paint.color = Color.argb((shade * 255).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

enum class Glyph { SEARCH, SETTINGS, BACK, CLOSE, MORE, UP, DOWN, CHEVRON, FOLDER, PLUS, CHECK, SUN, PLAY, PAUSE, NEXT, PREVIOUS, EDIT, NOTIFICATIONS }

class GlyphView(context: Context, val glyph: Glyph, var color: Int = Color.WHITE) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        val size = min(width, height) * .46f
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.color = color
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
        fun path(vararg points: Float) { val p = Path(); p.moveTo(points[0], points[1]); var i = 2; while (i < points.size) { p.lineTo(points[i], points[i + 1]); i += 2 }; canvas.drawPath(p, paint) }
        when (glyph) {
            Glyph.SEARCH -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); line(15.5f, 15.5f, 21f, 21f) }
            Glyph.BACK -> { path(14f, 5f, 7f, 12f, 14f, 19f); line(7f, 12f, 22f, 12f) }
            Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
            Glyph.CHEVRON -> path(9f, 6f, 15f, 12f, 9f, 18f)
            Glyph.UP -> path(5f, 15f, 12f, 8f, 19f, 15f)
            Glyph.DOWN -> path(5f, 9f, 12f, 16f, 19f, 9f)
            Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.CHECK -> path(4f, 12f, 9f, 17f, 20f, 6f)
            Glyph.FOLDER -> { path(3f, 7f, 3f, 19f, 21f, 19f, 21f, 7f, 12f, 7f, 10f, 4f, 3f, 4f, 3f, 7f) }
            Glyph.MORE -> { paint.style = Paint.Style.FILL; for (y in listOf(5f, 12f, 19f)) canvas.drawCircle(12f, y, 1.5f, paint); paint.style = Paint.Style.STROKE }
            Glyph.SUN -> { canvas.drawCircle(12f, 12f, 4f, paint); for (i in 0..7) { val a = i * Math.PI / 4; line(12 + kotlin.math.cos(a).toFloat() * 7, 12 + kotlin.math.sin(a).toFloat() * 7, 12 + kotlin.math.cos(a).toFloat() * 10, 12 + kotlin.math.sin(a).toFloat() * 10) } }
            Glyph.PLAY -> path(8f, 5f, 19f, 12f, 8f, 19f, 8f, 5f)
            Glyph.PAUSE -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
            Glyph.NEXT -> { path(6f, 5f, 16f, 12f, 6f, 19f, 6f, 5f); line(19f, 5f, 19f, 19f) }
            Glyph.PREVIOUS -> { path(18f, 5f, 8f, 12f, 18f, 19f, 18f, 5f); line(5f, 5f, 5f, 19f) }
            Glyph.EDIT -> { path(4f, 20f, 5f, 14f, 16f, 3f, 21f, 8f, 10f, 19f, 4f, 20f); line(14f, 5f, 19f, 10f) }
            Glyph.NOTIFICATIONS -> { path(4f, 17f, 6f, 14f, 6f, 9f); canvas.drawArc(6f, 3f, 18f, 15f, 180f, 180f, false, paint); path(18f, 9f, 18f, 14f, 20f, 17f, 4f, 17f); canvas.drawArc(9f, 16f, 15f, 22f, 0f, 180f, false, paint) }
            Glyph.SETTINGS -> { canvas.drawCircle(12f, 12f, 3f, paint); val p = Path(); for (i in 0..47) { val a = i * Math.PI / 24; val radius = if (i % 6 in 1..3) 10f else 8f; val x = 12 + kotlin.math.cos(a).toFloat() * radius; val y = 12 + kotlin.math.sin(a).toFloat() * radius; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; p.close(); canvas.drawPath(p, paint) }
        }
        canvas.restore()
    }
}

/** One continuous control, with scroll accessibility actions and keyboard alternatives. */
class AlphabetRail(context: Context) : View(context) {
    var letters = listOf("★") + ('A'..'Z').map(Char::toString) + "#"
        set(value) { field = value; invalidate() }
    var activeLetters = emptySet<String>()
    var onSelect: (String) -> Unit = {}
    var selected = "★"
        set(value) { field = value; contentDescription = "Алфавит приложений, $value. Проведите вверх или вниз для выбора."; invalidate() }
    var leftHanded = false
    private var dragging = false
    private var finger = -1000f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; selected = "★" }
    private fun top() = height * .15f
    private fun spacing() = (height * .76f / letters.size).coerceAtMost(context.dp(23).toFloat())
    override fun onDraw(canvas: Canvas) {
        val spacing = spacing(); val start = top()
        // Keep the grab point inside Android's reserved system Back gesture edge.
        val edge = if (leftHanded) context.dp(32).toFloat() else width - context.dp(32).toFloat()
        letters.forEachIndexed { i, letter ->
            val y = start + (i + .5f) * spacing
            val distance = (y - finger) / context.dp(58)
            val wave = if (dragging) exp(-distance * distance).toFloat() else 0f
            val x = edge + (if (leftHanded) 1 else -1) * context.dp(24) * wave
            paint.textSize = (11f + wave * 7f) * resources.displayMetrics.scaledDensity
            paint.color = if (letter == selected) Color.WHITE else if (letter in activeLetters || letter == "★") 0xE6FFFFFF.toInt() else 0x9EFFFFFF.toInt()
            canvas.drawText(letter, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        if (dragging) {
            val x = if (leftHanded) width - context.dp(30).toFloat() else context.dp(30).toFloat()
            val y = finger.coerceIn(context.dp(30).toFloat(), height - context.dp(30).toFloat())
            paint.color = 0xE6352E2A.toInt(); canvas.drawCircle(x, y, context.dp(25).toFloat(), paint)
            paint.textSize = 27f * resources.displayMetrics.scaledDensity; paint.color = Color.WHITE
            canvas.drawText(selected, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val insideEdge = if (leftHanded) event.x >= context.dp(24) && event.x <= context.dp(72)
                else event.x >= width - context.dp(72) && event.x <= width - context.dp(24)
            if (!insideEdge || event.y < top() || event.y > top() + letters.size * spacing()) return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true); dragging = true; selectAt(event.y) }
            MotionEvent.ACTION_MOVE -> selectAt(event.y)
            MotionEvent.ACTION_UP -> { dragging = false; performClick(); invalidate() }
            MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
        }
        return true
    }
    private fun selectAt(y: Float) {
        finger = y
        val next = letters[((y - top()) / spacing()).toInt().coerceIn(letters.indices)]
        if (selected != next) { selected = next; performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onSelect(next) }
        else if (dragging) onSelect(next)
        invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info); info.className = "android.widget.SeekBar"; info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            val index = (letters.indexOf(selected) + if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) 1 else -1).coerceIn(letters.indices)
            selected = letters[index]; onSelect(selected); announceForAccessibility(selected); return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_DOWN -> performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
        KeyEvent.KEYCODE_DPAD_UP -> performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null)
        else -> super.onKeyDown(keyCode, event)
    }
}
