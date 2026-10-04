package app.better.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppMenuTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun titleRenamesAppAndEmptyNameRestoresOriginalLabel() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var app: AppEntry? = null
        var oldAlias: String? = null
        try {
            await {
                instrumentation.runOnMainSync {
                    if (activity.hasWindowFocus()) app = activity.store.favorites.firstNotNullOfOrNull(activity.catalog::find)
                }
                app != null
            }
            val entry = app!!
            oldAlias = activity.store.alias(entry.id)
            val initialLabel = activity.catalog.label(entry)
            openMenu(initialLabel)
            node { it.contentDescription == "Дополнительно" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.contentDescription == "Скрыть приложение" }
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Переименовать" })
            val menu = currentDialog(activity)
            node { it.contentDescription == "Переименовать $initialLabel" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Renamed app")
            assertSame("Editing must keep the original app menu", menu, currentDialog(activity))
            node { it.text == "Переименовать $initialLabel" }
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Скрыть приложение" })
            node { it.text == "Сохранить" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            // The next menu must use the saved alias, and HOME must discard unsaved edits.
            openMenu("Renamed app")
            node { it.contentDescription == "Переименовать Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Unsaved name")
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            openMenu("Renamed app")
            node { it.contentDescription == "Переименовать Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Discard with Back")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync(); SystemClock.sleep(500)
            // HOME may replace the Activity; inspect the active window, not the old Activity.
            if (findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.className == "android.widget.EditText" } != null) {
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            openMenu("Renamed app")
            node { it.contentDescription == "Переименовать Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("")
            val dialog = currentDialog(activity) as android.app.Dialog
            instrumentation.runOnMainSync {
                fun inputs(view: android.view.View): List<android.widget.EditText> =
                    if (view is android.widget.EditText) listOf(view) else if (view is android.view.ViewGroup)
                        (0 until view.childCount).flatMap { inputs(view.getChildAt(it)) } else emptyList()
                inputs(dialog.window!!.decorView).single().onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            }
            node { it.text == entry.originalLabel }
            assertNull(LauncherStore(instrumentation.targetContext).alias(entry.id))
        } finally {
            instrumentation.runOnMainSync {
                app?.let { activity.store.rename(it.id, oldAlias.orEmpty()) }
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                activity.finish()
            }
        }
    }

    private fun currentDialog(activity: MainActivity): Any? {
        var dialog: Any? = null
        instrumentation.runOnMainSync {
            val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() ?: activity
            dialog = MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(resumed)
        }
        return dialog
    }
    private fun openMenu(label: String) {
        // HOME can replace the Activity; act on the currently visible row.
        var row: AccessibilityNodeInfo? = node { it.text?.toString() == label }
        while (row != null && !row.isLongClickable) row = row.parent
        assertTrue("Missing long-press action for $label", row?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true)
        node { it.contentDescription == "Переименовать $label" }
    }
    private fun setName(value: String) {
        val input = node { it.className == "android.widget.EditText" }
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        node {
            it.className == "android.widget.EditText" &&
                (it.text?.toString().orEmpty() == value || value.isEmpty() && it.isShowingHintText)
        }
    }
    private fun findNode(node: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (index in 0 until node.childCount) findNode(node.getChild(index), matches)?.let { return it }
        return null
    }
    private fun node(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await { found = findNode(instrumentation.uiAutomation.rootInActiveWindow, matches); found != null }
        return found!!
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("Expected app-menu state did not appear")
    }
}
