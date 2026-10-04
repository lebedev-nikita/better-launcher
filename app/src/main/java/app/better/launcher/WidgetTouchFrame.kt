package app.better.launcher

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs

/** Observe child gestures so buttons and collection views still receive taps and scrolling. */
class WidgetTouchFrame(context: Context) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var holding = false
    private var consumed = false
    private val longPress = Runnable {
        if (holding && isAttachedToWindow) {
            holding = false
            // Cancel the provider's pressed button before opening launcher controls.
            val cancel = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            consumed = true
            parent?.requestDisallowInterceptTouchEvent(true)
            performLongClick()
        }
    }

    init {
        tag = PageSwipeLayout.BLOCK_SWIPE
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelHold()
                downX = event.x; downY = event.y; consumed = false; holding = true
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) cancelHold()
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
        }
        if (consumed) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumed = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    // Only the observer owns touch long presses; do not schedule View's second timer.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_F10 && event.isShiftPressed) return performLongClick()
        return super.onKeyUp(keyCode, event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Настроить виджет"))
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelHold()
        super.onWindowFocusChanged(hasWindowFocus)
    }

    private fun cancelHold() { holding = false; removeCallbacks(longPress) }
    override fun onDetachedFromWindow() { cancelHold(); consumed = false; super.onDetachedFromWindow() }
}
