package app.better.launcher

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class FavoritesOrderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun dragHandleKeyboardAndAccessibilitySaveOrderOnHome() {
        val store = LauncherStore(context)
        val original = store.export()
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var apps: List<AppEntry>
                await {
                    scenario.onActivity { apps = it.catalog.apps.take(3) }
                    apps.size == 3
                }
                val folder = store.createFolder("Test folder", listOf(apps[2].id))
                val missing = "99999:unavailable/.Main"
                store.favorites = listOf(apps[0].id, missing, apps[1].id, folder, apps[2].id)
                store.set("initialized", true)
                store.rename(apps[0].id, "Очень длинное название избранного приложения")
                // HOME refreshes the surface from the persisted list.
                home(scenario)
                val firstLabel = store.alias(apps[0].id) ?: apps[0].originalLabel
                val secondLabel = store.alias(apps[1].id) ?: apps[1].originalLabel
                assertNull("Home must not duplicate the menu's reorder action", find(instrumentation.uiAutomation.rootInActiveWindow) {
                    it.text?.toString() == "Изменить порядок избранного"
                })
                openEditor(firstLabel)
                node("Готово")
                node(firstLabel)
                assertFalse(node("Перетащить $firstLabel").actionList.any { it.id == R.id.reorder_up })
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription?.toString() == "Переместить $firstLabel вниз" })
                val from = Rect().also { node("Перетащить $firstLabel").getBoundsInScreen(it) }
                val to = Rect().also { node("Перетащить $secondLabel").getBoundsInScreen(it) }
                val nameBounds = Rect().also { node(firstLabel).getBoundsInScreen(it) }
                assertTrue("Handle must share the title line, including wrapped names", kotlin.math.abs(from.centerY() - nameBounds.centerY()) <= 2)
                val beforeCancel = store.favorites
                drag(from.centerX().toFloat(), from.centerY().toFloat(), to.bottom.toFloat(), cancel = true)
                assertEquals("Cancelled drag must preserve saved order", beforeCancel, store.favorites)
                // Cross the target row's boundary; matching its handle center exactly is not a move.
                drag(from.centerX().toFloat(), from.centerY().toFloat(), to.bottom.toFloat())
                val dragged = listOf(missing, apps[1].id, apps[0].id, folder, apps[2].id)
                try { await { store.favorites == dragged } } catch (failure: IllegalStateException) {
                    throw AssertionError("Dragged order: expected $dragged, actual ${store.favorites}", failure)
                }
                await { node("Перетащить $firstLabel").actionList.any { it.id == R.id.reorder_up } }
                await { node("Перетащить $firstLabel").performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                await { store.favorites == listOf(missing, apps[0].id, apps[1].id, folder, apps[2].id) }
                // The same editor stays open, with accessible controls and the folder intact.
                node("Перетащить $firstLabel")
                node("Перетащить Test folder")
                assertTrue(node("Перетащить Test folder").performAction(R.id.reorder_up))
                val reordered = listOf(missing, apps[0].id, folder, apps[1].id, apps[2].id)
                await { store.favorites == reordered }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { find(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription?.toString() == "Перетащить $firstLabel" } == null }
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "Изменить порядок избранного" })
                // Back rebuilds the rows; accessibility bounds settle after the next layout.
                await {
                    val folderBounds = Rect().also { node("Test folder").getBoundsInScreen(it) }
                    val appBounds = Rect().also { node(secondLabel).getBoundsInScreen(it) }
                    folderBounds.top < appBounds.top
                }
                scenario.recreate()
                assertEquals(reordered, LauncherStore(context).favorites)
                val backup = store.export()
                store.favorites = emptyList()
                store.import(backup)
                assertEquals(reordered, LauncherStore(context).favorites)
                home(scenario)
                openEditor(firstLabel)
                home(scenario)
                await { find(instrumentation.uiAutomation.rootInActiveWindow) { it.contentDescription == "Перетащить $firstLabel" } == null }
                assertEquals(reordered, store.favorites)
            }
        } finally { store.import(original) }
    }

    private fun home(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        instrumentation.waitForIdleSync()
        node("Главный экран")
    }

    private fun openEditor(label: String) {
        // HOME and sheet exit animations may briefly leave an old accessibility node in the cache.
        // Retry only rejected actions, resolving a fresh node each time.
        await {
            var row = node(label)
            while (!row.isLongClickable) row = row.parent ?: error("Missing app row")
            row.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }
        await { node("Изменить порядок").performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        node("Перетащить $label")
    }

    private fun drag(x: Float, fromY: Float, toY: Float, cancel: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, fromY)
        repeat(20) { index ->
            SystemClock.sleep(25)
            send(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * (index + 1) / 20)
        }
        SystemClock.sleep(100)
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, toY)
    }

    private fun node(label: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            found = if (root?.packageName == context.packageName) find(root) {
                (it.contentDescription?.toString() == label || it.text?.toString() == label) &&
                    it.isVisibleToUser && !Rect().also(it::getBoundsInScreen).isEmpty
            } else null
            found != null
        }
        return found!!
    }
    private fun find(node: AccessibilityNodeInfo?, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (match(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), match)?.let { return it }
        return null
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("Expected favorites order or editor state did not appear")
    }
}
