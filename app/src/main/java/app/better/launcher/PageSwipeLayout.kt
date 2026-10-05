package app.better.launcher

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Page swipes include app rows; widget content and ordering controls keep their gestures. */
class PageSwipeLayout(context: Context) : FrameLayout(context) {
    var direction = 0 // +1: widgets to the left of home; -1: home to the right of widgets.
    var onSwipe: () -> Unit = {}
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var eligible = false
    private var swiping = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; swiping = false
                eligible = direction != 0 && event.x >= context.dp(24) && event.x <= width - context.dp(24)
                    && !blocked(this, event.rawX.toInt(), event.rawY.toInt())
            }
            MotionEvent.ACTION_MOVE -> detect(event)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { eligible = false; swiping = false }
        }
        return swiping
    }

    private fun detect(event: MotionEvent) {
        if (!eligible || swiping) return
        val dx = event.x - downX
        val dy = abs(event.y - downY)
        if (dy > slop && dy >= abs(dx)) eligible = false
        else if (dx * direction > maxOf(slop, context.dp(24)) && abs(dx) > dy * 1.5f) swiping = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> detect(event)
            MotionEvent.ACTION_UP -> {
                val changePage = swiping && (event.x - downX) * direction >= context.dp(72)
                swiping = false; eligible = false
                if (changePage) onSwipe() else performClick()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { swiping = false; eligible = false }
        }
        return direction != 0
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun blocked(view: View, x: Int, y: Int): Boolean {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return false
        if (view.tag == BLOCK_SWIPE) return true
        return view is ViewGroup && (0 until view.childCount).any { blocked(view.getChildAt(it), x, y) }
    }

    companion object { const val BLOCK_SWIPE = "launcher:keep-child-gestures" }
}
