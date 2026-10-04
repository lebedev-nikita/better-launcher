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

    @Test fun draggingBelowAlphabetKeepsListAtEndWithAndWithoutSymbolApps() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (symbolApps in listOf(false, true)) {
                val labels = ('A'..'Z').map { "$it App" } + listOf("Я App") +
                    if (symbolApps) listOf("123 App", "! App") else emptyList()
                val apps = labels.mapIndexed { i, label ->
                    AppEntry("test:$i", ComponentName("test", "App$i"), Process.myUserHandle(), label, ColorDrawable())
                }
                val index = AppSearchIndex(apps, AppEntry::originalLabel, AppEntry::originalLabel)
                val adapter = AppListAdapter(AppEntry::originalLabel,
                    { app, recycled -> ((recycled as? TextView) ?: TextView(context)).apply { text = app.originalLabel; minHeight = context.dp(62) } },
                    { section, recycled -> ((recycled as? TextView) ?: TextView(context)).apply { text = section; minHeight = context.dp(40) } })
                val list = ListView(context).apply { this.adapter = adapter }
                fun layout() {
                    list.measure(View.MeasureSpec.makeMeasureSpec(context.dp(360), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(context.dp(300), View.MeasureSpec.EXACTLY))
                    list.layout(0, 0, context.dp(360), context.dp(300))
                }
                adapter.submit(index.apps, true); layout()
                val fullRail = listOf("★") + ('A'..'Z').map(Char::toString) + listOf("Я", "#")
                val compactRail = listOf("★") + index.sections.sortedBy(AppListRules::sectionOrder)
                for ((railLetters, leftHanded) in listOf(fullRail, compactRail).flatMap { letters -> listOf(false, true).map { letters to it } }) {
                    val rail = AlphabetRail(context).apply {
                        letters = railLetters
                        this.leftHanded = leftHanded
                    }
                    rail.layout(0, 0, context.dp(92), context.dp(1000))
                    val parent = android.widget.FrameLayout(context).apply { addView(rail) }
                    val selections = mutableListOf<String>()
                    rail.onSelect = { section ->
                        selections.add(section)
                        adapter.sectionPosition(section)?.let { list.setSelectionFromTop(it, 0) }
                        layout()
                    }
                    val start = rail.height * .15f
                    val step = (rail.height * .76f / rail.letters.size).coerceAtMost(context.dp(23).toFloat())
                    val x = context.dp(if (leftHanded) 32 else 60).toFloat()
                    fun touch(action: Int, y: Float) {
                        val now = SystemClock.uptimeMillis()
                        MotionEvent.obtain(now, now, action, x, y, 0).let { rail.onTouchEvent(it); it.recycle() }
                    }
                    touch(MotionEvent.ACTION_DOWN, start + (rail.letters.indexOf("Я") + .5f) * step)
                    assertTrue(list.firstVisiblePosition > 0)
                    touch(MotionEvent.ACTION_MOVE, start + (rail.letters.size - .5f) * step)
                    touch(MotionEvent.ACTION_MOVE, rail.height.toFloat() + context.dp(100))
                    touch(MotionEvent.ACTION_UP, rail.height.toFloat() + context.dp(100))
                    assertEquals(if ("#" in railLetters) listOf("Я", "#") else listOf("Я"), selections)
                    assertEquals("Dragging below the rail must keep the last apps visible (symbols=$symbolApps, left=$leftHanded)",
                        adapter.count - 1, list.lastVisiblePosition)
                    assertTrue("Dragging below the rail must not return to the top", list.firstVisiblePosition > 0)
                    parent.removeAllViews()
                }
            }
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
            val step = 16f * context.resources.configuration.fontScale.coerceAtLeast(1f)
            val start = 400f - 24f - rail.letters.size * step
            val a = start + 1.5f * step
            val b = start + 2.5f * step
            touch(MotionEvent.ACTION_DOWN, a)
            touch(MotionEvent.ACTION_MOVE, a + 1f)
            touch(MotionEvent.ACTION_MOVE, a + 2f)
            touch(MotionEvent.ACTION_MOVE, b)
            touch(MotionEvent.ACTION_UP, b)
            assertEquals(listOf("A", "B"), selections)
            touch(MotionEvent.ACTION_DOWN, b)
            touch(MotionEvent.ACTION_UP, b)
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
