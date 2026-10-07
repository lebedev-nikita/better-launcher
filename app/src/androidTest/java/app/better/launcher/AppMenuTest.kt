package app.better.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        val originalServiceFlags = instrumentation.uiAutomation.serviceInfo.flags
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
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
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Additional" })
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Apps in the pop-up menu" })
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Notifications and quick actions" })
            node { it.contentDescription == "Hide app" }
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Rename" })
            val menu = currentDialog(activity)
            instrumentation.runOnMainSync {
                fun commands(view: View): List<String> = buildList {
                    if (!view.isShown) return@buildList
                    if (view.isClickable && view.contentDescription != null) add(view.contentDescription.toString())
                    if (view is ViewGroup) for (index in 0 until view.childCount) addAll(commands(view.getChildAt(index)))
                }
                assertEquals(listOf("Rename $initialLabel", "Remove from favorites", "Reorder",
                    "Hide app", "Uninstall app", "App info", "Better Launcher settings"),
                    commands((menu as Dialog).window!!.decorView))
            }
            node { it.contentDescription == "Rename $initialLabel" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Renamed app")
            assertSame("Editing must keep the original app menu", menu, currentDialog(activity))
            node { it.text == "Rename $initialLabel" }
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Hide app" })
            node { it.text == "Save" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            await(30000) { !imeWindowVisible() }
            // The next menu must use the saved alias, and HOME must discard unsaved edits.
            openMenu("Renamed app")
            node { it.contentDescription == "Rename Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Unsaved name")
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            await(30000) { !imeWindowVisible() }
            openMenu("Renamed app")
            val backDialog = currentDialog(activity) as Dialog
            val ime = observeIme(backDialog)
            node { it.contentDescription == "Rename Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            setName("Discard with Back")
            // Insets report the target visibility before the animation finishes.
            // Both Back presses must start with the keyboard in a settled state.
            await(30000) { imeSettled(backDialog, ime, visible = true) }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            await { imeSettled(backDialog, ime, visible = false) }
            instrumentation.runOnMainSync {
                assertTrue("First Back must hide the keyboard and keep the editor", backDialog.isShowing)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            node { it.text == "Renamed app" }
            assertEquals("Renamed app", LauncherStore(instrumentation.targetContext).alias(entry.id))

            openMenu("Renamed app")
            node { it.contentDescription == "Rename Renamed app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply { flags = originalServiceFlags }
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
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Additional" })
            node { it.contentDescription == "Hide app" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.contentDescription == "Home screen" }
            assertTrue(entry.id in store.hidden)
            assertFalse(entry.id in store.favorites)
            assertFalse(activity.catalog.visible().any { it.id == entry.id })

            node { it.contentDescription == "Home screen" }.performAction(R.id.home_search)
            setName(label)
            openMenu(label)
            // Restore is immediately available, without expanding additional actions.
            node { it.contentDescription == "Show again" }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node { it.className == "android.widget.EditText" && it.text?.toString() == label }
            node { it.text?.toString() == label && it.className != "android.widget.EditText" }
            assertFalse(entry.id in LauncherStore(instrumentation.targetContext).hidden)
            assertFalse("Showing again must not add the app to favorites", entry.id in store.favorites)
            assertTrue(activity.catalog.visible().any { it.id == entry.id })
            assertTrue(AppCatalog(instrumentation.targetContext, LauncherStore(instrumentation.targetContext))
                .apply { load() }.visible().any { it.id == entry.id })

            openMenu(label)
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Show again" })
            assertNull(findNode(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Additional" })
            node { it.contentDescription == "Hide app" }
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
    private class ImeAnimations : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        val running = mutableSetOf<WindowInsetsAnimation>()
        override fun onPrepare(animation: WindowInsetsAnimation) {
            if (animation.typeMask and WindowInsets.Type.ime() != 0) running.add(animation)
        }
        override fun onProgress(insets: WindowInsets, runningAnimations: MutableList<WindowInsetsAnimation>): WindowInsets = insets
        override fun onEnd(animation: WindowInsetsAnimation) {
            running.remove(animation)
        }
    }
    private fun observeIme(dialog: Dialog): ImeAnimations = ImeAnimations().also { observer ->
        instrumentation.runOnMainSync { dialog.window!!.decorView.setWindowInsetsAnimationCallback(observer) }
    }
    private fun imeSettled(dialog: Dialog, observer: ImeAnimations, visible: Boolean): Boolean {
        var settled = false
        instrumentation.runOnMainSync {
            val insets = dialog.window?.decorView?.rootWindowInsets
            settled = insets != null && insets.isVisible(WindowInsets.Type.ime()) == visible && observer.running.isEmpty()
        }
        return settled && imeWindowVisible() == visible
    }
    private fun imeWindowVisible(): Boolean = instrumentation.uiAutomation.windows.any {
        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root != null
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
        node { it.contentDescription == "Rename $label" }
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
