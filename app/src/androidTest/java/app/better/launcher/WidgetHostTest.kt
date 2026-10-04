package app.better.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
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
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
            try {
                assertTrue("Test provider must bind to the real host", manager.bindAppWidgetIdIfAllowed(id, provider))
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
            instrumentation.runOnMainSync {
                activity.store.widgets = previousWidgets + id
                activity.store.homeWidgetId = null
                activity.showWidgets()
            }
            await(instrumentation) {
                descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Layout preview content" }
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
                descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Live widget update" }
            }
            instrumentation.runOnMainSync {
                activity.store.homeWidgetId = id
                activity.showHome()
            }
            await(instrumentation) {
                descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Live widget update" }
            }
            instrumentation.runOnMainSync { activity.showWidgets(); activity.store.homeWidgetId = null; activity.showWidgets() }
            await(instrumentation) {
                descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Live widget update" }
            }
        } finally {
            instrumentation.runOnMainSync {
                host.deleteAppWidgetId(id)
                activity.store.widgets = previousWidgets
                activity.store.homeWidgetId = previousHome
                activity.finish()
            }
        }
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
