package app.better.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetOrderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun pageDragAndArrowsKeepHomeBindingSizesAndUpdatesAcrossRecreation() {
        val store = LauncherStore(context)
        val previous = store.widgets
        val previousHome = store.homeWidgetId
        val previousSizes = store.string("widgetSizes", "{}")
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 1201)
        val ids = List(4) { host.allocateAppWidgetId() }
        val provider = ComponentName(instrumentation.context, LayoutPreviewProvider::class.java)
        var grantedUser: Int? = null
        try {
            if (!manager.bindAppWidgetIdIfAllowed(ids[0], provider)) {
                val user = shell("am get-current-user").trim().toInt()
                shell("appwidget grantbind --package ${context.packageName} --user $user")
                grantedUser = user
                assertTrue(manager.bindAppWidgetIdIfAllowed(ids[0], provider))
            }
            ids.drop(1).forEach { assertTrue(manager.bindAppWidgetIdIfAllowed(it, provider)) }
            store.widgets = ids
            store.homeWidgetId = ids[1]
            val sizes = ids.associateWith { WidgetSize(180, 144, 12, 12) }
            sizes.forEach { (id, size) -> store.setWidgetSize(id, size) }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { it.showWidgets() }
                click("Изменить порядок виджетов")
                val label = manager.getAppWidgetInfo(ids[0]).loadLabel(context.packageManager)
                val first = "$label · 1"
                val second = "$label · 2"
                assertFalse(node("Переместить $first вверх").isEnabled)
                var originalViews = emptyList<AppWidgetHostView>()
                // The actual hosted views stay on the page during ordering.
                scenario.onActivity { activity ->
                    originalViews = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>()
                    assertEquals(listOf(ids[0], ids[2], ids[3]), originalViews.map { it.appWidgetId })
                }
                val from = Rect().also { node("Перетащить $first").getBoundsInScreen(it) }
                val to = Rect().also { node("Перетащить $second").getBoundsInScreen(it) }
                drag(from.centerX().toFloat(), from.centerY().toFloat(), to.bottom.toFloat())
                val dragged = listOf(ids[1], ids[2], ids[0], ids[3])
                await { store.widgets == dragged }
                click("Переместить $first вверх")
                await { store.pageWidgets == listOf(ids[0], ids[2], ids[3]) }
                // Scroll to the last real widget, whose controls may be below the viewport.
                scenario.onActivity { activity ->
                    descendants(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().single().fullScroll(View.FOCUS_DOWN)
                }
                click("Переместить $label · 3 вверх")
                val pageOrder = listOf(ids[0], ids[3], ids[2])
                await { store.pageWidgets == pageOrder }
                scenario.onActivity { activity ->
                    val reorderedViews = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>()
                    originalViews.forEach { original -> assertSame("Reordering must retain the live host view", original, reorderedViews.single { it.appWidgetId == original.appWidgetId }) }
                }
                assertEquals(ids[1], store.homeWidgetId)
                sizes.forEach { (id, size) -> assertEquals(size, store.widgetSize(id)) }
                assertTrue(ids.all { manager.getAppWidgetInfo(it) != null })
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { find(instrumentation.uiAutomation.rootInActiveWindow, "Перетащить $first") == null }
                scenario.onActivity { activity ->
                    assertEquals(pageOrder, descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().map { it.appWidgetId })
                }
                scenario.recreate()
                await {
                    var rendered = emptyList<Int>()
                    scenario.onActivity { rendered = descendants(it.window.decorView).filterIsInstance<AppWidgetHostView>().map { widget -> widget.appWidgetId } }
                    rendered == pageOrder
                }
                val resources = instrumentation.context.resources
                val layout = resources.getIdentifier("widget_preview", "layout", instrumentation.context.packageName)
                val textId = resources.getIdentifier("widget_text", "id", instrumentation.context.packageName)
                manager.updateAppWidget(ids[2], RemoteViews(instrumentation.context.packageName, layout).apply {
                    setTextViewText(textId, "Updated after reorder")
                })
                await {
                    var updated = false
                    scenario.onActivity { activity ->
                        val widget = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == ids[2] }
                        updated = descendants(widget).filterIsInstance<TextView>().any { it.text.toString() == "Updated after reorder" }
                    }
                    updated
                }
                assertEquals(pageOrder, LauncherStore(context).pageWidgets)
                val backup = store.export()
                store.import(backup)
                assertEquals(pageOrder, store.pageWidgets)
                sizes.forEach { (id, size) -> assertEquals(size, store.widgetSize(id)) }
                // The widget's own menu also reaches the editor; HOME dismisses it and preserves order.
                scenario.onActivity { activity ->
                    descendants(activity.window.decorView).filterIsInstance<WidgetTouchFrame>().first().performLongClick()
                }
                click("Изменить порядок")
                node("Готово")
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClass(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                node("Все приложения и поиск")
                await { find(instrumentation.uiAutomation.rootInActiveWindow, "Перетащить $label · 1") == null }
                assertEquals(pageOrder, store.pageWidgets)
                scenario.onActivity { activity ->
                    assertEquals(listOf(ids[1]), descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().map { it.appWidgetId })
                    store.homeWidgetId = null
                    store.widgets = listOf(ids[0])
                    activity.showWidgets()
                }
                instrumentation.waitForIdleSync()
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "Изменить порядок виджетов"))
            }
        } finally {
            ids.forEach(host::deleteAppWidgetId)
            store.widgets = previous
            store.homeWidgetId = previousHome
            store.set("widgetSizes", previousSizes)
            grantedUser?.let { shell("appwidget revokebind --package ${context.packageName} --user $it") }
        }
    }

    private fun click(label: String) { await { node(label).performAction(AccessibilityNodeInfo.ACTION_CLICK) } }
    private fun node(label: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await { found = find(instrumentation.uiAutomation.rootInActiveWindow, label); found != null }
        return found!!
    }
    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if ((node.text?.toString() == label || node.contentDescription?.toString() == label) && node.isVisibleToUser) return node
        for (index in 0 until node.childCount) find(node.getChild(index), label)?.let { return it }
        return null
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("Widget order or editor state did not appear")
    }
    private fun drag(x: Float, from: Float, to: Float) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from)
        repeat(20) { index ->
            SystemClock.sleep(25)
            send(MotionEvent.ACTION_MOVE, from + (to - from) * (index + 1) / 20)
        }
        SystemClock.sleep(100)
        send(MotionEvent.ACTION_UP, to)
    }
}
