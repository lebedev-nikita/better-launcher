package app.better.launcher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

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
            touch(MotionEvent.ACTION_DOWN, 95f)
            touch(MotionEvent.ACTION_MOVE, 96f)
            touch(MotionEvent.ACTION_MOVE, 97f)
            touch(MotionEvent.ACTION_MOVE, 118f)
            touch(MotionEvent.ACTION_UP, 118f)
            assertEquals(listOf("A", "B"), selections)
            touch(MotionEvent.ACTION_DOWN, 118f)
            touch(MotionEvent.ACTION_UP, 118f)
            assertEquals(listOf("A", "B", "B"), selections)
            parent.removeAllViews()
        }
    }

    @Test fun largeCatalogBindsOnlyViewportAndJumpsToLatestSection() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val apps = (0 until 1000).map { i -> AppEntry("test:$i", ComponentName("test", "App$i"), Process.myUserHandle(), "${'A' + i / 40} App $i", ColorDrawable()) }
            var bindings = 0
            var creations = 0
            val adapter = AppListAdapter(AppEntry::originalLabel,
                { app, recycled ->
                    bindings++
                    ((recycled as? TextView) ?: TextView(context).also { creations++ }).apply { text = app.originalLabel; minHeight = context.dp(62) }
                },
                { section, recycled -> ((recycled as? TextView) ?: TextView(context)).apply { text = section; minHeight = context.dp(40) } })
            val list = ListView(context).apply { this.adapter = adapter }
            fun layout() {
                list.measure(View.MeasureSpec.makeMeasureSpec(context.dp(360), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(context.dp(600), View.MeasureSpec.EXACTLY))
                list.layout(0, 0, context.dp(360), context.dp(600))
            }
            adapter.submit(apps, true); layout()
            assertTrue("Opening must not bind the entire catalog: $bindings", bindings < 30)
            list.setSelectionFromTop(adapter.sectionPosition("C")!!, 0)
            list.setSelectionFromTop(adapter.sectionPosition("T")!!, 0)
            layout()
            // In keyboard mode ListView selects the first enabled app after the heading.
            assertEquals("T", (list.getChildAt(0) as TextView).text.toString().substringBefore(" "))
            val createdAfterJump = creations
            list.setSelectionFromTop(adapter.sectionPosition("B")!!, 0); layout()
            assertTrue("Rows should be reused after another jump", creations <= createdAfterJump + 1)
            adapter.submit(apps.filter { it.originalLabel.contains("999") }, false); layout()
            assertEquals("Y App 999", (list.getChildAt(0) as TextView).text.toString())
            assertNull(adapter.sectionPosition("T"))
        }
    }
}
