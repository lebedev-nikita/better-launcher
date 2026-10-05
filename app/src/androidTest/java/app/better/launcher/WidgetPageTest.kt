package app.better.launcher

import android.app.Dialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetPageTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun dialog(activity: MainActivity) = MainActivity::class.java.getDeclaredField("currentDialog")
        .apply { isAccessible = true }.get(activity) as? Dialog
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) { instrumentation.waitForIdleSync(); return }
            SystemClock.sleep(50)
        }
        error("Widget page did not reach the expected state")
    }
    private fun touch(x: Float, y: Float, afterDown: () -> Unit) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN)
        try { afterDown() } finally { send(MotionEvent.ACTION_UP) }
    }

    @Test fun emptySpaceAddsWidgetsAndEntireInsetSafeHeightIsAvailable() {
        instrumentation.uiAutomation
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val store = activity.store
        val previous = store.widgets
        val previousHome = store.homeWidgetId
        val sizes = store.string("widgetSizes", "{}")
        val spacing = store.widgetSpacing
        val host = AppWidgetHost(activity, 1201)
        val manager = AppWidgetManager.getInstance(activity)
        val ids = List(2) { host.allocateAppWidgetId() }
        val user = shell("am get-current-user").trim()
        try {
            shell("appwidget grantbind --package ${activity.packageName} --user $user")
            val provider = ComponentName(instrumentation.context, LayoutPreviewProvider::class.java)
            ids.forEach { assertTrue(manager.bindAppWidgetIdIfAllowed(it, provider)) }
            await { activity.hasWindowFocus() && activity.catalog.apps.isNotEmpty() }
            instrumentation.runOnMainSync {
                store.widgets = ids; store.homeWidgetId = null; store.widgetSpacing = 8
                ids.forEach { store.setWidgetSize(it, WidgetSize(0, 72)) }
                activity.showWidgets()
            }
            lateinit var scroll: ScrollView
            lateinit var content: ViewGroup
            val bounds = Rect()
            await {
                scroll = views(activity.window.decorView).filterIsInstance<ScrollView>().single()
                content = scroll.getChildAt(0) as ViewGroup
                scroll.getGlobalVisibleRect(bounds) && content.height == scroll.height &&
                    views(content).filterIsInstance<AppWidgetHostView>().all { it.height > 0 }
            }
            instrumentation.runOnMainSync {
                val page = scroll.parent as View
                assertEquals("No header or footer reserve", page.height - page.paddingTop - page.paddingBottom, scroll.height)
                val first = views(content).filterIsInstance<AppWidgetHostView>().first()
                val location = IntArray(2).also(first::getLocationOnScreen)
                assertEquals("First widget starts at the safe top", bounds.top, location[1])
                assertEquals(0, content.paddingTop); assertEquals(0, content.paddingBottom)
                assertFalse(views(page).any { it is TextView && it.text.toString() == "Виджеты" ||
                    it.contentDescription?.toString() in listOf("На главный экран", "Добавить виджет", "Изменить порядок виджетов") })
            }
            InstrumentationRegistry.getArguments().getString("pageCapture")?.let { name ->
                SystemClock.sleep(500)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(activity.filesDir, "$name.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            // Both the formerly occupied footer and the gap between providers add widgets.
            val firstBounds = Rect()
            instrumentation.runOnMainSync { views(content).filterIsInstance<AppWidgetHostView>().first().getGlobalVisibleRect(firstBounds) }
            for (y in listOf(bounds.bottom - activity.dp(24), firstBounds.bottom + activity.dp(4))) {
                touch(bounds.exactCenterX(), y.toFloat()) { await { dialog(activity)?.isShowing == true } }
                await { dialog(activity)?.window?.decorView?.let { root -> views(root).any { it is TextView && it.hint == "Поиск виджетов" } } == true }
                assertEquals("Browsing allocates no widget", ids, store.widgets)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { dialog(activity) == null && activity.hasWindowFocus() }
            }
            // Provider holds keep their management menu, rather than opening the picker.
            touch(firstBounds.exactCenterX(), firstBounds.exactCenterY()) { await { dialog(activity)?.isShowing == true } }
            await { dialog(activity)?.window?.decorView?.let { root -> views(root).any { it.contentDescription == "Удалить виджет" } } == true }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { dialog(activity) == null && activity.hasWindowFocus() }
            instrumentation.runOnMainSync { store.widgets = emptyList(); activity.showWidgets() }
            await { views(activity.window.decorView).filterIsInstance<ScrollView>().single().height > 0 }
            touch(bounds.exactCenterX(), (bounds.bottom - activity.dp(24)).toFloat()) { await { dialog(activity)?.isShowing == true } }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { dialog(activity) == null && activity.hasWindowFocus() }
            // Keyboard offers the same action without consuming visual widget space.
            instrumentation.runOnMainSync { views(activity.window.decorView).filterIsInstance<ScrollView>().single().getChildAt(0).let { assertTrue(it.requestFocus()); assertTrue(it.hasFocus()) } }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            await { dialog(activity)?.isShowing == true }
        } finally {
            instrumentation.runOnMainSync {
                dialog(activity)?.dismiss()
                store.widgets = previous; store.homeWidgetId = previousHome
                store.set("widgetSizes", sizes); store.widgetSpacing = spacing
                activity.finish()
            }
            ids.forEach(host::deleteAppWidgetId)
            shell("appwidget revokebind --package ${activity.packageName} --user $user")
        }
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }
}
