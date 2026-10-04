package app.better.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetGestureTest {
    @Test fun holdCancelsChildClickWhileTapsMovementAndCancelledTouchesKeepTheirMeaning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var frame: WidgetTouchFrame
        var clicks = 0
        var holds = 0
        val delay = ViewConfiguration.getLongPressTimeout().toLong() + 150
        try {
            instrumentation.runOnMainSync {
                frame = WidgetTouchFrame(activity).apply {
                    setOnLongClickListener { holds++; true }
                    addView(Button(activity).apply { text = "Provider button"; setOnClickListener { clicks++ } })
                }
                activity.setContentView(frame)
            }
            instrumentation.waitForIdleSync()
            var down = 0L
            fun touch(action: Int, x: Float = 40f) = instrumentation.runOnMainSync {
                if (action == MotionEvent.ACTION_DOWN) down = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, 40f, 0)
                frame.dispatchTouchEvent(event); event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
            instrumentation.waitForIdleSync()
            assertEquals(1, clicks); assertEquals(0, holds)
            touch(MotionEvent.ACTION_DOWN); SystemClock.sleep(delay); touch(MotionEvent.ACTION_UP)
            assertEquals("One launcher menu per hold", 1, holds)
            assertEquals("Releasing a hold must not launch the provider button", 1, clicks)
            touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 140f)
            SystemClock.sleep(delay); touch(MotionEvent.ACTION_CANCEL)
            assertEquals("Scrolling must not become a long press", 1, holds)
            touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL)
            SystemClock.sleep(delay)
            assertEquals(1, holds)
            touch(MotionEvent.ACTION_DOWN)
            instrumentation.runOnMainSync { activity.setContentView(Button(activity)) }
            SystemClock.sleep(delay)
            assertEquals("Detached hosts must cancel pending holds", 1, holds)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
