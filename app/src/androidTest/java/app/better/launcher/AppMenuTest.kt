package app.better.launcher

import android.content.Intent
import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppMenuTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun menuStaysAtScreenBottomThroughoutArrival() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val bottoms = mutableListOf<Int>()
        var dialog: Dialog? = null
        var decor: View? = null
        var observer: ViewTreeObserver.OnDrawListener? = null
        var screenBottom = 0
        var animated = false
        try {
            var label: String? = null
            await(30000) {
                instrumentation.runOnMainSync {
                    if (activity.hasWindowFocus()) label = activity.store.favorites.firstNotNullOfOrNull(activity.catalog::find)?.let(activity.catalog::label)
                }
                label != null
            }
            instrumentation.runOnMainSync {
                animated = android.animation.ValueAnimator.areAnimatorsEnabled() &&
                    Settings.Global.getFloat(activity.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) > 0f &&
                    Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
                fun views(view: View): Sequence<View> = sequence {
                    yield(view)
                    if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
                }
                var row: View = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label }
                while (!row.isLongClickable) row = row.parent as View
                assertTrue(row.performLongClick())
                dialog = MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(activity) as Dialog
                decor = dialog!!.window!!.decorView
                screenBottom = activity.windowManager.currentWindowMetrics.bounds.bottom
                val scroll = views(decor!!).filterIsInstance<ScrollView>().first()
                observer = ViewTreeObserver.OnDrawListener {
                    val location = IntArray(2)
                    scroll.getLocationOnScreen(location)
                    // Remove the intentional content slide: its bottom anchor must never move.
                    bottoms.add(location[1] - scroll.translationY.toInt() + scroll.height)
                }
                decor!!.viewTreeObserver.addOnDrawListener(observer)
            }
            SystemClock.sleep(1200)
            instrumentation.runOnMainSync {
                decor!!.viewTreeObserver.removeOnDrawListener(observer)
                observer = null
                assertTrue("Menu must render${if (animated) " multiple frames" else " a frame"}", bottoms.size >= if (animated) 3 else 1)
                assertTrue("Menu bottom moved away from screen bottom $screenBottom: $bottoms",
                    bottoms.all { kotlin.math.abs(it - screenBottom) <= 1 })
            }
        } finally {
            instrumentation.runOnMainSync {
                observer?.let { decor?.viewTreeObserver?.removeOnDrawListener(it) }
                dialog?.dismiss()
                activity.finish()
            }
        }
    }

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
        await { found = findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.isVisibleToUser && matches(it) }; found != null }
        return found!!
    }
    private fun await(timeout: Long = 10000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("Expected app-menu state did not appear")
    }
}
