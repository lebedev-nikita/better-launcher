package app.better.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetResizeHapticTest {
    @Test fun sizeChangesTickWhileRepeatedGridValuesLimitsMovementAndCancellationStayQuiet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val feedback = mutableListOf<Int>()
        val initial = WidgetSize(120, 120, 120)
        var size = initial
        var snapshot = initial
        lateinit var overlay: ViewGroup
        try {
            instrumentation.runOnMainSync {
                val target = object : View(activity) {
                    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                        feedback.add(feedbackConstant)
                        return true
                    }
                }
                fun applySize(value: WidgetSize) {
                    size = value
                    target.layoutParams = FrameLayout.LayoutParams(activity.dp(size.width), activity.dp(size.height)).apply {
                        topMargin = activity.dp(size.top)
                    }
                }
                val root = FrameLayout(activity)
                root.addView(target)
                applySize(initial)
                overlay = widgetResizeOverlay(activity, target, true, true,
                    readSize = { size }, ranges = { 60..240 to 60..240 },
                    onChange = { applySize(it) }, onGestureStart = { snapshot = size },
                    onCancel = { applySize(snapshot) }, onFinish = {}) as ViewGroup
                root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val labels = listOf("Верхняя граница виджета", "Нижняя граница виджета",
                    "Левая граница виджета", "Правая граница виджета")
                fun control(label: String): View = (0 until overlay.childCount).map { overlay.getChildAt(it) }
                    .single { it.contentDescription?.toString() == label }
                fun touch(view: View, action: Int, delta: Int = 0, horizontal: Boolean = false) {
                    val now = SystemClock.uptimeMillis()
                    val offset = activity.dp(delta).toFloat()
                    val event = MotionEvent.obtain(now, now, action,
                        50f + if (horizontal) offset else 0f, 50f + if (horizontal) 0f else offset, 0)
                    try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
                }
                fun ticks(count: Int) = assertEquals(List(count) { HapticFeedbackConstants.CLOCK_TICK }, feedback)
                for ((index, label) in labels.withIndex()) {
                    size = initial; feedback.clear()
                    val handle = control(label)
                    val horizontal = index >= 2
                    // Width changes by twice the edge delta because the center stays fixed.
                    val firstStep = if (horizontal) 6 else 12
                    touch(handle, MotionEvent.ACTION_DOWN, horizontal = horizontal)
                    touch(handle, MotionEvent.ACTION_MOVE, 1, horizontal)
                    ticks(0)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep, horizontal)
                    assertNotEquals(initial, size); ticks(1)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep + 1, horizontal)
                    ticks(1)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep * 2, horizontal)
                    ticks(2)
                    touch(handle, MotionEvent.ACTION_UP, firstStep * 2, horizontal)
                    ticks(2)

                    // Backtracking is another real grid transition; cancellation itself is silent.
                    size = initial; feedback.clear()
                    touch(handle, MotionEvent.ACTION_DOWN, horizontal = horizontal)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep, horizontal)
                    touch(handle, MotionEvent.ACTION_MOVE, 0, horizontal)
                    assertEquals(initial, size); ticks(2)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep, horizontal)
                    touch(handle, MotionEvent.ACTION_CANCEL, firstStep, horizontal)
                    assertEquals(initial, size); ticks(3)
                    touch(handle, MotionEvent.ACTION_UP, firstStep, horizontal)
                    ticks(3)

                    // UP can apply the final change when no MOVE was delivered.
                    touch(handle, MotionEvent.ACTION_DOWN, horizontal = horizontal)
                    touch(handle, MotionEvent.ACTION_UP, firstStep, horizontal)
                    ticks(4)
                    feedback.clear()
                    touch(handle, MotionEvent.ACTION_DOWN, horizontal = horizontal)
                    touch(handle, MotionEvent.ACTION_MOVE, firstStep, horizontal)
                    touch(handle, MotionEvent.ACTION_POINTER_DOWN, firstStep, horizontal)
                    ticks(1)
                    touch(handle, MotionEvent.ACTION_UP, firstStep, horizontal)
                    ticks(1)

                    size = initial; feedback.clear()
                    val setMaximum = Bundle().apply {
                        putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 240f)
                    }
                    assertTrue(handle.performAccessibilityAction(android.R.id.accessibilityActionSetProgress, setMaximum))
                    ticks(1)
                    assertTrue(handle.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
                    ticks(1)
                    assertTrue(handle.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
                    ticks(1)
                    assertTrue(handle.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
                    ticks(2)
                    assertTrue(handle.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
                    ticks(3)
                }
                size = initial; feedback.clear()
                val body = control("Переместить виджет")
                touch(body, MotionEvent.ACTION_DOWN)
                touch(body, MotionEvent.ACTION_MOVE, 36)
                touch(body, MotionEvent.ACTION_UP, 36)
                assertEquals(initial.copy(top = 156), size)
                assertTrue(body.performAccessibilityAction(R.id.widget_move_up, null))
                assertTrue(body.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
                ticks(0)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
