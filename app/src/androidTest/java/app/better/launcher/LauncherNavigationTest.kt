package app.better.launcher

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun widgetPageHasAccessibleNavigationAndHomeAlwaysReturnsHome() {
        val activity = instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClass(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            bounds(activity, "Все приложения")
            click(activity, "Экран виджетов")
            assertVisible(activity, "Виджеты")
            click(activity, "На главный экран")
            assertVisible(activity, "Все приложения", "home button")
            click(activity, "Экран виджетов")
            assertVisible(activity, "Виджеты")
            // Dismissing the provider picker keeps the widget page open.
            click(activity, "Добавить виджет")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertVisible(activity, "Виджеты")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertVisible(activity, "Все приложения", "system Back")
            click(activity, "Экран виджетов")
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            instrumentation.waitForIdleSync()
            // HOME can create a new task/activity; inspect the active window, not the old decor.
            val deadline = SystemClock.uptimeMillis() + 15000
            var homeVisible = false
            while (SystemClock.uptimeMillis() < deadline && !homeVisible) {
                val window = instrumentation.uiAutomation.rootInActiveWindow
                homeVisible = window?.packageName == activity.packageName && containsText(window, "Все приложения")
                if (!homeVisible) SystemClock.sleep(50)
            }
            assertTrue("HOME must show the home screen", homeVisible)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun find(view: View, label: String): View? {
        if (view.contentDescription?.toString() == label || view is TextView && view.text.toString() == label) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), label)?.let { return it }
        return null
    }
    private fun containsText(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == label) return true
        return (0 until node.childCount).any { containsText(node.getChild(it), label) }
    }
    private fun assertVisible(activity: MainActivity, label: String, stage: String = label) {
        bounds(activity, label, stage)
    }
    private fun bounds(activity: MainActivity, label: String, stage: String = label): Rect {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = Rect()
            var visible = false
            instrumentation.runOnMainSync {
                visible = activity.hasWindowFocus() && find(activity.window.decorView, label)?.getGlobalVisibleRect(bounds) == true && !bounds.isEmpty
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
