package app.better.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetHostTest {
    @Test fun addedWidgetDisplaysProviderContentAndLiveUpdatesOnBothScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val manager = AppWidgetManager.getInstance(activity)
        val host = AppWidgetHost(activity, 1201)
        val id = host.allocateAppWidgetId()
        val provider = ComponentName(instrumentation.context, LayoutPreviewProvider::class.java)
        val previousWidgets = activity.store.widgets
        val previousHome = activity.store.homeWidgetId
        var grantedUser: Int? = null
        try {
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
                // appwidget does not resolve the special USER_CURRENT value on Android 12/16.
                val user = shell(instrumentation, "am get-current-user").trim().toInt()
                shell(instrumentation, "appwidget grantbind --package ${activity.packageName} --user $user")
                grantedUser = user
                assertTrue("Test provider must bind to the real host", manager.bindAppWidgetIdIfAllowed(id, provider))
            }
            instrumentation.runOnMainSync {
                activity.store.widgets = previousWidgets + id
                activity.store.homeWidgetId = null
                activity.showWidgets()
            }
            await(instrumentation) {
                hasWidgetText(activity, id, "Layout preview content")
            }
            instrumentation.runOnMainSync {
                val frame = descendants(activity.window.decorView).filterIsInstance<WidgetTouchFrame>().single()
                assertEquals("No launcher label or menu row above the widget", 1, (frame.parent as ViewGroup).childCount)
            }
            await(instrumentation) { activity.hasWindowFocus() && manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0 }
            val oldOptions = manager.getAppWidgetOptions(id)
            val bounds = Rect()
            instrumentation.runOnMainSync {
                descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }.getGlobalVisibleRect(bounds)
            }
            val down = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), (bounds.top + 40).toFloat(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN)
            await(instrumentation) { activeNode("Изменить размер") != null }
            touch(MotionEvent.ACTION_UP)
            assertTrue(activeNode("Изменить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activeNode("Ширина") != null }
            fun progress(label: String, value: Float) {
                val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
                fun adjustable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    if (node.actionList.any { it.id == android.R.id.accessibilityActionSetProgress }) return node
                    for (index in 0 until node.childCount) node.getChild(index)?.let { adjustable(it)?.let { found -> return found } }
                    return null
                }
                val slider = adjustable(activeNode(label)!!) ?: error("Missing adjustable thumb for $label")
                assertTrue(slider.performAction(android.R.id.accessibilityActionSetProgress, arguments))
            }
            progress("Ширина", 180f)
            progress("Высота", 320f)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(180, 320) }
            await(instrumentation) {
                manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > oldOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            }
            assertTrue(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) < oldOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync { activity.showWidgets() }
            await(instrumentation) {
                val widget = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }
                widget.width == activity.dp(180) && widget.height == activity.dp(320)
            }
            // Updates must reach the view registered by the real WidgetController host.
            val resources = instrumentation.context.resources
            val layout = resources.getIdentifier("widget_preview", "layout", instrumentation.context.packageName)
            val textId = resources.getIdentifier("widget_text", "id", instrumentation.context.packageName)
            manager.updateAppWidget(id, RemoteViews(instrumentation.context.packageName, layout).apply {
                setTextViewText(textId, "Live widget update")
                setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                setInt(textId, "setBackgroundResource", android.R.color.transparent)
            })
            await(instrumentation) {
                hasWidgetText(activity, id, "Live widget update")
            }
            instrumentation.runOnMainSync {
                activity.store.homeWidgetId = id
                activity.showHome()
            }
            await(instrumentation) {
                hasWidgetText(activity, id, "Live widget update")
            }
            instrumentation.runOnMainSync { activity.showWidgets(); activity.store.homeWidgetId = null; activity.showWidgets() }
            await(instrumentation) {
                hasWidgetText(activity, id, "Live widget update")
            }
            fun openControls() {
                instrumentation.runOnMainSync {
                    assertTrue(descendants(activity.window.decorView).filterIsInstance<WidgetTouchFrame>().single()
                        .performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                }
                await(instrumentation) { activeNode("Изменить размер") != null }
            }
            openControls()
            assertTrue(activeNode("На главный экран")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activity.store.homeWidgetId == id }
            instrumentation.runOnMainSync { activity.showHome() }
            await(instrumentation) { hasWidgetText(activity, id, "Live widget update") }
            assertEquals(WidgetSize(180, 320), activity.store.widgetSize(id))
            openControls()
            assertTrue(activeNode("Изменить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activeNode("Сбросить размер") != null }
            assertTrue(activeNode("Сбросить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activity.store.widgetSize(id) == null }
            instrumentation.waitForIdleSync()
            openControls()
            assertTrue(activeNode("На экран виджетов")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activity.store.homeWidgetId == null }
            instrumentation.runOnMainSync { activity.showWidgets() }
            await(instrumentation) { hasWidgetText(activity, id, "Live widget update") }
            openControls()
            assertTrue(activeNode("Удалить виджет")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { id !in activity.store.widgets }
        } finally {
            instrumentation.runOnMainSync {
                host.deleteAppWidgetId(id)
                activity.store.widgets = previousWidgets
                activity.store.homeWidgetId = previousHome
                activity.finish()
            }
            grantedUser?.let { shell(instrumentation, "appwidget revokebind --package ${activity.packageName} --user $it") }
        }
    }
    private fun activeNode(label: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentationRoot()?.let(::find)
    }
    private fun instrumentationRoot() = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
    private fun shell(instrumentation: android.app.Instrumentation, command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    private fun hasWidgetText(activity: MainActivity, id: Int, text: String): Boolean {
        val view = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>()
            .firstOrNull { it.appWidgetId == id } ?: return false
        return descendants(view).filterIsInstance<TextView>().any { it.text.toString() == text }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun await(instrumentation: android.app.Instrumentation, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var found = false
            instrumentation.runOnMainSync { found = predicate() }
            if (found) return
            SystemClock.sleep(100)
        }
        fail("Added widget did not display provider content")
    }
}
