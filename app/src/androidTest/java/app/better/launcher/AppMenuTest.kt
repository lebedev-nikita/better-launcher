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
            node { it.contentDescription == "Переименовать $initialLabel" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Renamed app")
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
            setName("")
            node { it.text == "Сохранить" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
