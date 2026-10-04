package app.better.launcher

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.withTranslation
import kotlin.math.roundToInt

fun widgetResizeOverlay(
    activity: MainActivity, target: View, canResizeWidth: Boolean, canResizeHeight: Boolean,
    readSize: () -> WidgetSize, ranges: () -> Pair<IntRange, IntRange>, onChange: (WidgetSize) -> Unit,
    onGestureStart: () -> Unit, onCancel: () -> Unit, onFinish: () -> Unit
): View = object : FrameLayout(activity) {
    private val density = resources.displayMetrics.density
    private val foregroundColor = context.getColor(R.color.app_menu_foreground)
    private val surface = context.getColor(R.color.app_menu_surface)
    private val bounds = RectF()
    private val targetLocation = IntArray(2)
    private val overlayLocation = IntArray(2)
    private val frameLocation = IntArray(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surface; style = Paint.Style.STROKE; strokeWidth = context.dp(2).toFloat(); strokeCap = Paint.Cap.ROUND
    }
    private val sizeLabel = activity.text("", 14f, foregroundColor)
    private val toolbar = activity.horizontal().apply {
        setPadding(context.dp(16), context.dp(6), context.dp(4), context.dp(6))
        background = rounded(surface, context.dp(24).toFloat())
    }
    private val handles = mutableMapOf<WidgetEdge, View>()
    private val dragSurface = moveSurface()
    private var observer: ViewTreeObserver? = null
    private var barsLeft = 0
    private var barsRight = 0
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (target.isAttachedToWindow) positionHandles() else onFinish()
        true
    }

    init {
        setWillNotDraw(false)
        tag = PageSwipeLayout.BLOCK_SWIPE
        addView(dragSurface, LayoutParams(1, 1))
        toolbar.addView(sizeLabel, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(activity.text("Готово", 16f, foregroundColor).apply {
            minHeight = context.dp(48); gravity = Gravity.CENTER
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
            background = touchBackground(); isFocusable = true; setOnClickListener { onFinish() }
        }, LinearLayout.LayoutParams(-2, -2))
        addView(toolbar, LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = context.dp(28); rightMargin = context.dp(28); bottomMargin = context.dp(12)
        })
        listOf(WidgetEdge.TOP, WidgetEdge.BOTTOM).filter { canResizeHeight }.plus(
            listOf(WidgetEdge.LEFT, WidgetEdge.RIGHT).filter { canResizeWidth }
        ).forEach { edge ->
            val handle = resizeHandle(edge)
            handles[edge] = handle
            addView(handle, LayoutParams(context.dp(48), context.dp(48)))
        }
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            barsLeft = bars.left; barsRight = bars.right
            (toolbar.layoutParams as LayoutParams).apply {
                leftMargin = context.dp(28) + bars.left; rightMargin = context.dp(28) + bars.right
                bottomMargin = context.dp(12) + bars.bottom
            }.also { toolbar.layoutParams = it }
            insets
        }
        updateLabel(readSize())
    }

    private fun geometry(): WidgetResizeGeometry {
        val (widths, heights) = ranges()
        (target.parent as View).getLocationInWindow(frameLocation)
        getLocationInWindow(overlayLocation)
        val toolbarTop = toolbar.top.takeIf { it > 0 } ?: (
            (height.takeIf { it > 0 } ?: activity.window.decorView.height) -
                toolbar.measuredHeight.coerceAtLeast(context.dp(60)) - (toolbar.layoutParams as LayoutParams).bottomMargin)
        val bottom = ((toolbarTop + overlayLocation[1] - context.dp(32) - frameLocation[1]) / density).toInt()
        return WidgetResizeGeometry(widths, heights, bottom, ((target.parent as View).width / density).toInt())
    }

    private fun updateLabel(size: WidgetSize) {
        sizeLabel.text = activity.getString(if (handles.isEmpty()) R.string.widget_fixed_size else R.string.widget_edit_size,
            size.width, size.height, WidgetResizeGeometry.GRID_DP)
        dragSurface.stateDescription = "По горизонтали ${size.offsetX} dp, по вертикали ${size.top} dp"
        handles.forEach { (edge, handle) -> handle.stateDescription = activity.getString(R.string.widget_dimension_value,
            if (edge == WidgetEdge.LEFT || edge == WidgetEdge.RIGHT) size.width else size.height) }
    }

    private fun change(size: WidgetSize) { onChange(size); updateLabel(size) }

    private fun positionHandles() {
        target.getLocationInWindow(targetLocation); getLocationInWindow(overlayLocation)
        val leftBound = (targetLocation[0] - overlayLocation[0]).toFloat()
        val topBound = (targetLocation[1] - overlayLocation[1]).toFloat()
        val rightBound = leftBound + target.width
        val bottomBound = topBound + target.height
        val changed = bounds.left != leftBound || bounds.top != topBound || bounds.right != rightBound || bounds.bottom != bottomBound
        bounds.set(leftBound, topBound, rightBound, bottomBound)
        dragSurface.x = bounds.left; dragSurface.y = bounds.top
        val dragParams = dragSurface.layoutParams
        if (dragParams.width != target.width || dragParams.height != target.height) {
            dragParams.width = target.width; dragParams.height = target.height; dragSurface.layoutParams = dragParams
        }
        val safeTop = paddingTop + context.dp(24).toFloat()
        val safeBottom = (toolbar.top - context.dp(32)).toFloat().coerceAtLeast(safeTop)
        val top = bounds.top.coerceIn(safeTop, safeBottom)
        val bottom = bounds.bottom.coerceIn(top, safeBottom)
        val left = bounds.left.coerceAtLeast(barsLeft + context.dp(32).toFloat())
        val right = bounds.right.coerceAtMost(width - barsRight - context.dp(32).toFloat())
        handles.forEach { (edge, handle) ->
            val x = when (edge) { WidgetEdge.LEFT -> left; WidgetEdge.RIGHT -> right; else -> bounds.centerX() }
            val y = when (edge) { WidgetEdge.TOP -> top; WidgetEdge.BOTTOM -> bottom; else -> (top + bottom) / 2f }
            handle.x = x - handle.measuredWidth / 2f; handle.y = y - handle.measuredHeight / 2f
        }
        if (changed) {
            invalidate()
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.top, paint)
        canvas.drawLine(bounds.left, bounds.bottom, bounds.right, bounds.bottom, paint)
        canvas.drawLine(bounds.left, bounds.top, bounds.left, bounds.bottom, paint)
        canvas.drawLine(bounds.right, bounds.top, bounds.right, bounds.bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !bounds.contains(event.x, event.y)) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); onFinish(); return true }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); requestApplyInsets()
        observer = viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
    }
    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw); observer = null
        super.onDetachedFromWindow()
    }

    private fun moveSurface(): View = object : View(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var start = readSize()
        private var downX = 0f
        private var downY = 0f
        private var active = false
        private var moved = false
        private val actions = listOf(
            Triple(R.id.widget_move_left, "Переместить влево", -WidgetResizeGeometry.GRID_DP to 0),
            Triple(R.id.widget_move_right, "Переместить вправо", WidgetResizeGeometry.GRID_DP to 0),
            Triple(R.id.widget_move_up, "Переместить вверх", 0 to -WidgetResizeGeometry.GRID_DP),
            Triple(R.id.widget_move_down, "Переместить вниз", 0 to WidgetResizeGeometry.GRID_DP)
        )
        init {
            contentDescription = "Переместить виджет"
            isFocusableInTouchMode = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            fun applyDrag() {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                if (!moved && dx * dx + dy * dy > slop * slop) moved = true
                if (moved) change(geometry().move(start, (dx / density).roundToInt(), (dy / density).roundToInt()))
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = readSize(); downX = event.rawX; downY = event.rawY; active = true; moved = false
                    parent.requestDisallowInterceptTouchEvent(true); onGestureStart()
                }
                MotionEvent.ACTION_MOVE -> if (active) applyDrag()
                MotionEvent.ACTION_UP -> {
                    if (active) { applyDrag(); performClick() }
                    active = false; parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> if (active) {
                    active = false; onCancel(); updateLabel(start); parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); requestFocus(); return true }
        private fun move(dx: Int, dy: Int): Boolean {
            onGestureStart(); change(geometry().move(readSize(), dx, dy)); return true
        }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            actions.forEach { (id, label, _) -> info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, label)) }
        }
        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            val delta = actions.firstOrNull { it.first == action }?.third
            return if (delta != null) move(delta.first, delta.second) else super.performAccessibilityAction(action, arguments)
        }
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-WidgetResizeGeometry.GRID_DP, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(WidgetResizeGeometry.GRID_DP, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -WidgetResizeGeometry.GRID_DP)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, WidgetResizeGeometry.GRID_DP)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun resizeHandle(edge: WidgetEdge): View = object : View(context) {
        val isWidthHandle = edge == WidgetEdge.LEFT || edge == WidgetEdge.RIGHT
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor; style = Paint.Style.STROKE; strokeWidth = context.dp(2).toFloat(); strokeCap = Paint.Cap.ROUND
        }
        private var start = readSize()
        private var down = 0f
        private var dragging = false
        init {
            contentDescription = when (edge) {
                WidgetEdge.TOP -> "Верхняя граница виджета"
                WidgetEdge.BOTTOM -> "Нижняя граница виджета"
                WidgetEdge.LEFT -> "Левая граница виджета"
                WidgetEdge.RIGHT -> "Правая граница виджета"
            }
            isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            background = InsetDrawable(RippleDrawable(ColorStateList.valueOf(0x28BCD7F3), rounded(surface, context.dp(18).toFloat()), null), context.dp(6))
            elevation = context.dp(4).toFloat()
        }
        override fun onDraw(canvas: Canvas) {
            canvas.withTranslation(width / 2f, height / 2f) {
                if (isWidthHandle) canvas.rotate(90f)
                val unit = resources.displayMetrics.density
                canvas.drawLine(-4 * unit, -5 * unit, 0f, -9 * unit, arrowPaint)
                canvas.drawLine(0f, -9 * unit, 4 * unit, -5 * unit, arrowPaint)
                canvas.drawLine(-4 * unit, 5 * unit, 0f, 9 * unit, arrowPaint)
                canvas.drawLine(0f, 9 * unit, 4 * unit, 5 * unit, arrowPaint)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            fun applyDrag() {
                val delta = (((if (isWidthHandle) event.rawX else event.rawY) - down) / resources.displayMetrics.density).roundToInt()
                change(geometry().drag(start, edge, delta))
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = readSize(); down = if (isWidthHandle) event.rawX else event.rawY
                    dragging = true; isPressed = true; parent.requestDisallowInterceptTouchEvent(true); onGestureStart()
                }
                MotionEvent.ACTION_MOVE -> if (dragging) applyDrag()
                MotionEvent.ACTION_UP -> {
                    if (dragging) applyDrag()
                    dragging = false; isPressed = false; parent.requestDisallowInterceptTouchEvent(false); performClick()
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> if (dragging) {
                    dragging = false; isPressed = false; onCancel(); updateLabel(start); parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); requestFocus(); return true }
        private fun value() = readSize().let { if (isWidthHandle) it.width else it.height }
        private fun range() = geometry().let { if (isWidthHandle) it.widths else if (edge == WidgetEdge.TOP) it.topRange(readSize()) else it.heightRange(readSize().top) }
        private fun progress(value: Int): Boolean {
            val size = readSize(); val selected = WidgetResizeGeometry.snap(value, range())
            onGestureStart()
            change(if (isWidthHandle) geometry().resizeWidth(size, selected) else geometry().drag(size, edge,
                if (edge == WidgetEdge.TOP) size.height - selected else selected - size.height))
            return true
        }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.SeekBar"; info.isScrollable = true
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                range().first.toFloat(), range().last.toFloat(), value().coerceIn(range()).toFloat())
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
            android.R.id.accessibilityActionSetProgress -> arguments?.takeIf { it.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) }
                ?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.takeIf { it.isFinite() }?.let { progress(it.roundToInt()) } ?: false
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> progress(value() + WidgetResizeGeometry.GRID_DP)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> progress(value() - WidgetResizeGeometry.GRID_DP)
            else -> super.performAccessibilityAction(action, arguments)
        }
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> progress(value() + WidgetResizeGeometry.GRID_DP)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN -> progress(value() - WidgetResizeGeometry.GRID_DP)
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
