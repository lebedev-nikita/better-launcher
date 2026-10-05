package app.better.launcher

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun alphabetStaysInPlaceWhenOpeningAppsAndReturningHome() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            bounds(activity, "Все приложения и поиск")
            val before = Rect()
            lateinit var rail: AlphabetRail
            instrumentation.runOnMainSync {
                val root = activity.window.decorView as ViewGroup
                fun findRail(view: View): AlphabetRail? {
                    if (view is AlphabetRail) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) findRail(view.getChildAt(i))?.let { return it }
                    return null
                }
                rail = findRail(root)!!
                assertTrue(rail.getGlobalVisibleRect(before))
            }
            instrumentation.runOnMainSync { rail.onSelect("C") }
            bounds(activity, "Поиск приложений")
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val after = Rect()
                assertTrue(rail.getGlobalVisibleRect(after))
                assertEquals("Opening apps must keep letters under the finger", before, after)
                rail.onSelect("★")
            }
            bounds(activity, "Все приложения и поиск")
            instrumentation.runOnMainSync {
                val after = Rect()
                assertTrue(rail.getGlobalVisibleRect(after))
                assertEquals(before, after)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun widgetPageHasAccessibleNavigationAndHomeAlwaysReturnsHome() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            bounds(activity, "Все приложения и поиск")
            click(activity, "Экран виджетов")
            assertVisible(activity, "Удерживайте пустое место, чтобы добавить виджет")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertVisible(activity, "Все приложения и поиск", "system Back")
            click(activity, "Экран виджетов")
            assertVisible(activity, "Удерживайте пустое место, чтобы добавить виджет")
            // Dismissing the provider picker keeps the widget page open.
            instrumentation.runOnMainSync {
                val scroll = (find(activity.window.decorView, "Удерживайте пустое место, чтобы добавить виджет")!!.parent as View)
                assertTrue(scroll.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
            }
            bounds(activity, "Поиск виджетов", dialog = true)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertVisible(activity, "Удерживайте пустое место, чтобы добавить виджет")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertVisible(activity, "Все приложения и поиск", "system Back")
            click(activity, "Экран виджетов")
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            instrumentation.waitForIdleSync()
            // HOME can create a new task/activity; inspect the focused resumed Activity.
            val deadline = SystemClock.uptimeMillis() + 15000
            var homeVisible = false
            while (SystemClock.uptimeMillis() < deadline && !homeVisible) {
                instrumentation.runOnMainSync {
                    homeVisible = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any {
                            val bounds = Rect()
                            it.hasWindowFocus() && find(it.window.decorView, "Все приложения и поиск")?.getGlobalVisibleRect(bounds) == true && !bounds.isEmpty
                        }
                }
                if (!homeVisible) SystemClock.sleep(50)
            }
            assertTrue("HOME must show the home screen", homeVisible)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun find(view: View, label: String): View? {
        if (view.contentDescription?.toString() == label || view is TextView && (view.text.toString() == label || view.hint?.toString() == label)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), label)?.let { return it }
        return null
    }
    private fun assertVisible(activity: MainActivity, label: String, stage: String = label) {
        bounds(activity, label, stage)
    }
    private fun bounds(activity: MainActivity, label: String, stage: String = label, dialog: Boolean = false): Rect {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = Rect()
            var visible = false
            instrumentation.runOnMainSync {
                val root = if (dialog) (MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }
                    .get(activity) as? android.app.Dialog)?.window?.decorView else activity.window.decorView
                visible = root?.hasWindowFocus() == true && find(root, label)?.getGlobalVisibleRect(bounds) == true && !bounds.isEmpty
            }
            if (visible) return bounds
            SystemClock.sleep(50)
        }
        error("Missing $label after $stage")
    }
    private fun click(activity: MainActivity, label: String) {
        instrumentation.runOnMainSync { assertTrue("Missing $label", find(activity.window.decorView, label)?.performClick() == true) }
        instrumentation.waitForIdleSync()
    }
}
