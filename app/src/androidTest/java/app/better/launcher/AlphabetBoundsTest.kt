package app.better.launcher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AlphabetBoundsTest {
    @Test fun releasingBelowAlphabetSelectsLastSectionEvenWithoutFinalMove() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        AppListRules.prepareSections()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (symbols in listOf(false, true)) {
                val labels = listOf("A App", "M App", "Z App", "Я App") +
                    if (symbols) listOf("123 App", "! App") else emptyList()
                val apps = labels.mapIndexed { i, label ->
                    AppEntry("test:$i", ComponentName("test", "App$i"), Process.myUserHandle(), label, ColorDrawable())
                }
                val index = AppSearchIndex(apps, AppEntry::originalLabel, AppEntry::originalLabel)
                val adapter = AppListAdapter(AppEntry::originalLabel,
                    { app, recycled -> ((recycled as? TextView) ?: TextView(context)).apply { text = app.originalLabel; minHeight = context.dp(62) } },
                    { section, recycled -> ((recycled as? TextView) ?: TextView(context)).apply { text = section; minHeight = context.dp(40) } })
                val list = ListView(context).apply { this.adapter = adapter }
                fun layout() {
                    list.measure(View.MeasureSpec.makeMeasureSpec(context.dp(360), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(context.dp(140), View.MeasureSpec.EXACTLY))
                    list.layout(0, 0, context.dp(360), context.dp(140))
                }
                adapter.submit(index.apps, true); layout()
                for (short in listOf(false, true)) for (left in listOf(false, true)) for (finalMove in listOf(false, true)) {
                    val rail = AlphabetRail(context).apply {
                        letters = AppListRules.railLetters(index.sections)
                        leftHanded = left
                    }
                    rail.layout(0, 0, context.dp(92), context.dp(if (short) 100 else 1000))
                    val parent = FrameLayout(context).apply { addView(rail) }
                    val selections = mutableListOf<String>()
                    rail.onSelect = { section ->
                        selections.add(section)
                        adapter.sectionPosition(section)?.let { list.setSelectionFromTop(it, 0) }
                        layout()
                    }
                    val step = ((rail.height - context.dp(16)).toFloat() / rail.letters.size)
                        .coerceAtMost(20f * context.resources.displayMetrics.scaledDensity)
                    val start = rail.height - context.dp(8) - rail.letters.size * step
                    val x = context.dp(if (left) 32 else 60).toFloat()
                    fun touch(action: Int, y: Float) {
                        val now = SystemClock.uptimeMillis()
                        MotionEvent.obtain(now, now, action, x, y, 0).let {
                            assertTrue("Gesture must start within the visible rail", rail.onTouchEvent(it)); it.recycle()
                        }
                    }
                    val initial = if (symbols) "Z" else "Y"
                    val last = if (symbols) "#" else "Z"
                    val z = start + (rail.letters.indexOf(initial) + .5f) * step
                    val below = rail.height.toFloat() + context.dp(100)
                    touch(MotionEvent.ACTION_DOWN, z)
                    touch(MotionEvent.ACTION_MOVE, z + step * .1f)
                    if (finalMove) touch(MotionEvent.ACTION_MOVE, below)
                    touch(MotionEvent.ACTION_UP, below)
                    assertEquals("symbols=$symbols, short=$short, left=$left, finalMove=$finalMove", listOf(initial, last), selections)
                    assertEquals(adapter.count - 1, list.lastVisiblePosition)
                    assertTrue("Overflow must not return to the first apps", list.firstVisiblePosition > 0)
                    touch(MotionEvent.ACTION_DOWN, z)
                    touch(MotionEvent.ACTION_CANCEL, below)
                    touch(MotionEvent.ACTION_UP, below)
                    assertEquals("A late release must not reactivate a cancelled gesture", listOf(initial, last, initial), selections)
                    parent.removeAllViews()
                }
            }
        }
    }
}
