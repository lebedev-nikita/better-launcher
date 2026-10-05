package app.better.launcher

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import kotlin.math.abs

/** App feedback with a hard lifetime limit, including while a finger is held down. */
class TransientMessageView(context: Context) : LinearLayout(context) {
    private val timeout = Runnable { dismiss() }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dismissed = false

    init {
        isClickable = true
        isFocusable = true
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(timeout, 3000)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(timeout)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        removeCallbacks(timeout)
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                downX = event.rawX; downY = event.rawY; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                if (abs(dx) > slop && abs(dx) > abs(event.rawY - downY)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true // Cancels the undo button's click when dragging from it.
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                if (dragging || abs(dx) > slop && abs(dx) > abs(event.rawY - downY)) {
                    dragging = true
                    translationX = dx
                    alpha = (1f - abs(dx) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging && abs(event.rawX - downX) >= maxOf(context.dp(48).toFloat(), width * .2f)) dismiss()
                else {
                    resetPosition()
                    if (!dragging) performClick()
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; resetPosition() }
        }
        return true
    }

    private fun resetPosition() {
        animate().translationX(0f).alpha(1f).setDuration(140).start()
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isDismissable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_DISMISS) { dismiss(); return true }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) { dismiss(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
