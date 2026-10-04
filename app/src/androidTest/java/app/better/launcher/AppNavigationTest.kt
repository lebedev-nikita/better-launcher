package app.better.launcher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.widget.ListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun compactAlphabetLeavesUpperSpaceAndBackEdgeFreeInBothHandedModes() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val rail = AlphabetRail(context).apply { letters = AppListRules.railLetters(setOf("C", "Y")) }
            val parent = android.widget.FrameLayout(context).apply { addView(rail) }
            rail.layout(0, 0, context.dp(92), context.dp(700))
            val selections = mutableListOf<String>()
            rail.onSelect = { selections.add(it) }
            fun touch(action: Int, x: Float, y: Float): Boolean {
                val now = SystemClock.uptimeMillis()
                return MotionEvent.obtain(now, now, action, context.dp(x).toFloat(), context.dp(y).toFloat(), 0).let {
                    rail.onTouchEvent(it).also { _ -> it.recycle() }
                }
            }
            for (leftHanded in listOf(false, true)) {
                rail.leftHanded = leftHanded
                val x = if (leftHanded) 32f else 60f
                assertFalse("Empty upper space must not navigate", touch(MotionEvent.ACTION_DOWN, x, 350f))
                assertFalse("System Back edge must stay free", touch(MotionEvent.ACTION_DOWN, if (leftHanded) 8f else 84f, 682f))
                assertTrue("Last section must be reachable near the bottom", touch(MotionEvent.ACTION_DOWN, x, 682f))
                touch(MotionEvent.ACTION_UP, x, 682f)
                assertEquals("Y", selections.last())
                rail.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                assertEquals("C", selections.last())
                rail.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                assertEquals("★", selections.last())
            }
            parent.removeAllViews()
        }
    }

    @Test fun alphabetDispatchesOnlyChangedSectionsButNewTouchReselects() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val rail = AlphabetRail(context).apply { letters = listOf("★", "A", "B") }
            rail.layout(0, 0, context.dp(92), context.dp(400))
            val selections = mutableListOf<String>()
            rail.onSelect = { selections.add(it) }
            fun touch(action: Int, y: Float) {
                val now = SystemClock.uptimeMillis()
                MotionEvent.obtain(now, now, action, context.dp(60).toFloat(), context.dp(y).toFloat(), 0).let {
                    // Touching the rail directly does not require an attached Activity.
                    rail.onTouchEvent(it); it.recycle()
                }
            }
            val parent = android.widget.FrameLayout(context).apply { addView(rail) }
