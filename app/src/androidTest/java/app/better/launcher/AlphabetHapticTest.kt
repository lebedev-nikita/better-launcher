package app.better.launcher

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AlphabetHapticTest {
    @Test fun completedGesturesTickOnceOnReleaseWithoutRepeatingNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (left in listOf(false, true)) {
                val feedback = mutableListOf<Int>()
                val selections = mutableListOf<String>()
                val rail = object : AlphabetRail(context) {
                    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                        feedback.add(feedbackConstant)
                        return true
                    }
                }.apply {
                    letters = listOf("★", "A", "B", "#")
                    leftHanded = left
                    onSelect = { selections.add(it) }
                }
                val parent = FrameLayout(context).apply { addView(rail) }
                rail.layout(0, 0, context.dp(92), context.dp(400))
                val step = context.dp(22) * context.resources.configuration.fontScale.coerceAtLeast(1f)
                val start = rail.height - context.dp(24) - rail.letters.size * step
                val a = start + 1.5f * step
                val b = start + 2.5f * step
                val below = rail.height + context.dp(100).toFloat()
                fun touch(action: Int, y: Float, x: Float = context.dp(if (left) 32 else 60).toFloat()): Boolean {
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(now, now, action, x, y, 0)
                    return try { rail.onTouchEvent(event) } finally { event.recycle() }
                }
                fun release(y: Float) {
                    feedback.clear()
                    touch(MotionEvent.ACTION_UP, y)
                    assertEquals("left=$left: one tick on release", listOf(HapticFeedbackConstants.CLOCK_TICK), feedback)
                }

                touch(MotionEvent.ACTION_DOWN, a)
                assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK), feedback)
                feedback.clear()
                touch(MotionEvent.ACTION_MOVE, a + step * .1f)
                assertTrue("Movement within a section stays quiet", feedback.isEmpty())
                touch(MotionEvent.ACTION_MOVE, b)
                assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK), feedback)
                release(b)
                assertEquals(listOf("A", "B"), selections)

                // A new touch on the current section still ticks when released.
                feedback.clear()
                touch(MotionEvent.ACTION_DOWN, b)
                assertTrue(feedback.isEmpty())
                release(b)
                assertEquals(listOf("A", "B", "B"), selections)

                // The release coordinate can commit # without an intervening MOVE.
                touch(MotionEvent.ACTION_DOWN, a)
                release(below)
                assertEquals("#", selections.last())
                touch(MotionEvent.ACTION_DOWN, a)
                touch(MotionEvent.ACTION_MOVE, below)
                val selectionsBeforeRelease = selections.toList()
                release(below)
                assertEquals(selectionsBeforeRelease, selections)

                touch(MotionEvent.ACTION_DOWN, a)
                feedback.clear()
                val selectionsBeforeCancel = selections.toList()
                touch(MotionEvent.ACTION_CANCEL, below)
                touch(MotionEvent.ACTION_UP, below)
                touch(MotionEvent.ACTION_UP, a)
                assertTrue("Cancelled or inactive releases stay quiet", feedback.isEmpty())
                assertEquals(selectionsBeforeCancel, selections)
                assertFalse(touch(MotionEvent.ACTION_DOWN, a, 0f))
                touch(MotionEvent.ACTION_UP, a)
                assertTrue("Touches outside the rail stay quiet", feedback.isEmpty())
                parent.removeAllViews()
            }
        }
    }
}
