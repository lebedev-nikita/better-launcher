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
            await(instrumentation) { activeNode("Переместить и изменить размер") != null }
            touch(MotionEvent.ACTION_UP)
            assertTrue(activeNode("Переместить и изменить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activeNode("Правая граница виджета") != null }
            assertFalse("Provider content is excluded from accessibility while editing", activeNode("Виджет A00 Layout preview")?.isImportantForAccessibility ?: false)
            fun progress(label: String, value: Float) {
                val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
                assertTrue(activeNode(label)!!.performAction(android.R.id.accessibilityActionSetProgress, arguments))
            }
            progress("Правая граница виджета", 180f)
            progress("Нижняя граница виджета", 320f)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(180, 324) }
            await(instrumentation) {
                manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > oldOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            }
            assertTrue(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) < oldOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            fun drag(label: String, dx: Int, dy: Int, cancel: Boolean = false) {
                val rect = Rect()
                await(instrumentation) {
                    val views = descendants(activity.window.decorView)
                    val widget = views.filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }
                    val widgetBounds = Rect().also { widget.getGlobalVisibleRect(it) }
                    val handle = views.first { it.contentDescription?.toString() == label }
                    handle.getGlobalVisibleRect(rect)
                    when (label) {
                        "Правая граница виджета" -> kotlin.math.abs(rect.centerX() - widgetBounds.right) <= 2
                        "Левая граница виджета" -> kotlin.math.abs(rect.centerX() - widgetBounds.left) <= 2
                        "Переместить виджет" -> kotlin.math.abs(rect.centerX() - widgetBounds.centerX()) <= 2 && kotlin.math.abs(rect.centerY() - widgetBounds.centerY()) <= 2
                        else -> kotlin.math.abs(rect.centerY() - widgetBounds.top) <= 2
                    }
                }
                val started = SystemClock.uptimeMillis()
                fun event(action: Int, x: Int, y: Int) {
                    val motion = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
                    motion.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)); motion.recycle()
                }
                event(MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY())
                event(MotionEvent.ACTION_MOVE, rect.centerX() + dx, rect.centerY() + dy)
                event(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, rect.centerX() + dx, rect.centerY() + dy)
            }
            drag("Правая граница виджета", -activity.dp(8), 0)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(168, 324) }
            progress("Правая граница виджета", 180f)
            drag("Левая граница виджета", activity.dp(8), 0, cancel = true)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(180, 324) }
            drag("Верхняя граница виджета", 0, activity.dp(24))
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(180, 300, 24) }
            progress("Верхняя граница виджета", 320f)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(180, 324) }
            progress("Правая граница виджета", 186f)
            await(instrumentation) { activity.store.widgetSize(id) == WidgetSize(192, 324) }
            progress("Правая граница виджета", 180f)
            drag("Переместить виджет", activity.dp(19), activity.dp(23))
            val moved = WidgetSize(180, 324, 24, 24)
            await(instrumentation) { activity.store.widgetSize(id) == moved }
            drag("Переместить виджет", -activity.dp(23), activity.dp(15), cancel = true)
            await(instrumentation) { activity.store.widgetSize(id) == moved }
            // Dragging focuses the movement surface; keys and TalkBack actions use the same grid.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await(instrumentation) { activity.store.widgetSize(id) == moved.copy(offsetX = 36) }
            assertTrue(activeNode("Переместить виджет")!!.performAction(R.id.widget_move_left))
            assertTrue(activeNode("Переместить виджет")!!.performAction(R.id.widget_move_up))
            await(instrumentation) { activity.store.widgetSize(id) == moved.copy(top = 12) }
            assertTrue(activeNode("Переместить виджет")!!.performAction(R.id.widget_move_down))
            await(instrumentation) { activity.store.widgetSize(id) == moved }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertFalse("Back finishes editing on the same page", activity.widgets.isResizing)
            instrumentation.runOnMainSync { activity.showWidgets() }
            await(instrumentation) {
                val widget = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }
                widget.width == activity.dp(180) && widget.height == activity.dp(324) &&
                    widget.translationX == activity.dp(24).toFloat() && (widget.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin == activity.dp(24)
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
                await(instrumentation) { activeNode("Переместить и изменить размер") != null }
            }
            openControls()
            assertNull(activeNode("Сбросить размер и положение"))
            assertNull(activeNode("На экран виджетов"))
            assertTrue(activeNode("На главный экран")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activity.store.homeWidgetId == id }
            instrumentation.runOnMainSync { activity.showHome() }
            await(instrumentation) { hasWidgetText(activity, id, "Live widget update") }
            assertEquals(moved, activity.store.widgetSize(id))
            openControls()
            assertNull(activeNode("Сбросить размер и положение"))
            assertNull(activeNode("На экран виджетов"))
            assertTrue(activeNode("Переместить и изменить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await(instrumentation) { activeNode("Готово") != null }
            assertTrue(activeNode("Готово")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            openControls()
            assertNull(activeNode("Сбросить размер и положение"))
            assertNull(activeNode("На экран виджетов"))
            assertEquals(moved, activity.store.widgetSize(id))
            assertEquals(id, activity.store.homeWidgetId)
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
        fail("Widget did not reach expected state; stored sizes: ${LauncherStore(instrumentation.targetContext).string("widgetSizes", "{}")}")
    }
}
