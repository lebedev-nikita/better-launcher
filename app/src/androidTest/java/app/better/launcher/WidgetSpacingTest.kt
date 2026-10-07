package app.better.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetSpacingTest {
    @Test fun pageAndHomeUseTheSameSavedGapOnBothRailSides() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val store = activity.store
        val original = store.export()
        val previousWidgets = store.widgets
        val previousHome = store.homeWidgetId
        val previousSizes = store.string("widgetSizes", "{}")
        val manager = AppWidgetManager.getInstance(activity)
        val host = AppWidgetHost(activity, 1201)
        val ids = List(3) { host.allocateAppWidgetId() }
        val provider = ComponentName(instrumentation.context, LayoutPreviewProvider::class.java)
        val user = shell("am get-current-user").trim()
        val capturePrefix = InstrumentationRegistry.getArguments().getString("spacingCaptures")
        var granted = false
        fun hostView(id: Int) = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }
        fun content(id: Int) = hostView(id).getChildAt(0)
        fun y(view: View) = IntArray(2).also(view::getLocationOnScreen)[1]
        fun await(predicate: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deadline) {
                var ready = false
                instrumentation.runOnMainSync { ready = predicate() }
                if (ready) { instrumentation.waitForIdleSync(); return }
                SystemClock.sleep(100)
            }
            fail("Widget spacing layout did not settle")
        }
        fun capture(name: String) {
            if (capturePrefix == null) return
            SystemClock.sleep(250)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.filesDir, "$capturePrefix-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            shell("appwidget grantbind --package ${activity.packageName} --user $user")
            granted = true
            ids.forEach { assertTrue(manager.bindAppWidgetIdIfAllowed(it, provider)) }
            await { activity.catalog.apps.isNotEmpty() && activity.hasWindowFocus() }
            instrumentation.runOnMainSync {
                store.widgets = ids
                store.homeWidgetId = ids.first()
                store.set("weatherCity", "")
                store.set("notifications", false)
                store.favorites = listOf(activity.catalog.apps.first().id)
                ids.forEach { store.setWidgetSize(it, WidgetSize(0, 120)) }
            }
            for (left in listOf(false, true)) for (spacing in listOf(0, 8, 32)) {
                instrumentation.runOnMainSync {
                    store.set("leftHanded", left)
                    MainActivity::class.java.getDeclaredMethod("applyAppearance").apply { isAccessible = true }.invoke(activity)
                    store.widgetSpacing = spacing
                    activity.showWidgets()
                }
                await { ids.drop(1).all { id -> hostView(id).height > 0 && descendants(hostView(id)).filterIsInstance<TextView>().any { it.text == "Layout preview content" } } }
                if (spacing == 0) capture("page-zero-$left")
                instrumentation.runOnMainSync {
                    assertEquals("Page gap uses saved dp", activity.dp(spacing),
                        y(hostView(ids[2])) - y(hostView(ids[1])) - hostView(ids[1]).height)
                    val providerHeight = (content(ids[1]).height / activity.resources.displayMetrics.density).toInt()
                    assertEquals("Reported provider height matches visible content", providerHeight,
                        manager.getAppWidgetOptions(ids[1]).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
                    assertEquals("Visible provider content has no extra page gap", activity.dp(spacing),
                        y(content(ids[2])) - y(content(ids[1])) - content(ids[1]).height)
                    activity.showHome()
                }
                await { hostView(ids[0]).height > 0 && descendants(hostView(ids[0])).filterIsInstance<TextView>().any { it.text == "Layout preview content" } }
                instrumentation.runOnMainSync {
                    val label = activity.catalog.label(activity.catalog.find(store.favorites.first())!!)
                    val name = descendants(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label }
                    val row = generateSequence(name.parent as? View) { it.parent as? View }.first { it.isClickable }
                    assertEquals("Home gap matches the page gap", activity.dp(spacing),
                        y(row) - y(hostView(ids[0])) - hostView(ids[0]).height)
                    assertEquals("Visible provider content has the same gap before favorites", activity.dp(spacing),
                        y(row) - y(content(ids[0])) - content(ids[0]).height)
                }
                if (spacing == 8) {
                    capture("home-$left")
                    instrumentation.runOnMainSync { activity.showWidgets() }
                    await { hostView(ids[1]).height > 0 }
                    capture("page-$left")
                }
            }
            instrumentation.runOnMainSync {
                store.widgetSpacing = 8
                activity.showSettings()
            }
            await { descendants(activity.window.decorView).first { it.contentDescription == "Widget spacing" }.height > 0 }
            instrumentation.runOnMainSync {
                val slider = descendants(activity.window.decorView).first { it.contentDescription == "Widget spacing" }
                slider.requestRectangleOnScreen(android.graphics.Rect(0, 0, slider.width, slider.height), true)
            }
            instrumentation.waitForIdleSync()
            capture("settings")
        } finally {
            instrumentation.runOnMainSync {
                ids.forEach(host::deleteAppWidgetId)
                store.widgets = previousWidgets
                store.homeWidgetId = previousHome
                store.set("widgetSizes", previousSizes)
                store.import(original)
                activity.finish()
            }
            if (granted) shell("appwidget revokebind --package ${activity.packageName} --user $user")
        }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
