package app.better.launcher

import android.content.Intent
import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
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
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Дополнительно" })
            node { it.contentDescription == "Приложения во всплывающем меню" }
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
            val backDialog = currentDialog(activity) as Dialog
            // IME appearance/hiding is asynchronous on cold CI emulators.
            await(30000) { imeVisible(backDialog) }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            await { !imeVisible(backDialog) }
            instrumentation.runOnMainSync {
                assertTrue("First Back must hide the keyboard and keep the editor", backDialog.isShowing)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
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
                val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>()
                (resumed + activity).distinct().forEach { current ->
                    (MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(current) as? Dialog)?.dismiss()
                    current.finish()
                }
            }
        }
    }

    @Test fun hiddenAppCanBeFoundAndShownAgainFromSearchMenu() {
        val store = LauncherStore(instrumentation.targetContext)
        val oldHidden = store.hidden
        var oldFavorites: List<String>? = null
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var app: AppEntry? = null
        try {
            await(30000) {
                instrumentation.runOnMainSync {
                    if (activity.hasWindowFocus()) app = activity.store.favorites.firstNotNullOfOrNull(activity.catalog::find)
                }
                app != null
            }
            oldFavorites = store.favorites
            val entry = app!!
            val label = activity.catalog.label(entry)
            openMenu(label)
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Дополнительно" })
            node { it.contentDescription == "Скрыть приложение" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.contentDescription == "Все приложения и поиск" }
            assertTrue(entry.id in store.hidden)
            assertFalse(entry.id in store.favorites)
            assertFalse(activity.catalog.visible().any { it.id == entry.id })

            node { it.contentDescription == "Все приложения и поиск" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName(label)
            openMenu(label)
            // Restore is immediately available, without expanding additional actions.
            node { it.contentDescription == "Показывать снова" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.className == "android.widget.EditText" && it.text?.toString() == label }
            node { it.text?.toString() == label && it.className != "android.widget.EditText" }
            assertFalse(entry.id in LauncherStore(instrumentation.targetContext).hidden)
            assertFalse("Showing again must not add the app to favorites", entry.id in store.favorites)
            assertTrue(activity.catalog.visible().any { it.id == entry.id })
            assertTrue(AppCatalog(instrumentation.targetContext, LauncherStore(instrumentation.targetContext))
                .apply { load() }.visible().any { it.id == entry.id })

            openMenu(label)
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Показывать снова" })
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Дополнительно" })
            node { it.contentDescription == "Скрыть приложение" }
        } finally {
            instrumentation.runOnMainSync {
                store.hidden = oldHidden
                oldFavorites?.let { store.favorites = it }
                (MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(activity) as? Dialog)?.dismiss()
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
    private fun imeVisible(dialog: Dialog): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            visible = dialog.window?.decorView?.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        return visible
    }
    private fun openMenu(label: String) {
        // HOME replaces the Activity, and an exiting sheet can briefly expose its old title.
        // Resolve a fresh home row and retry only rejected long-click actions.
        await {
            var row: AccessibilityNodeInfo? = findNode(instrumentation.uiAutomation.rootInActiveWindow) {
                it.text?.toString() == label && it.className != "android.widget.EditText" && it.isVisibleToUser
            }
            while (row != null && !row.isLongClickable) row = row.parent
            row?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
        }
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
