package app.better.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetHomeLayoutTest {
    @Test fun homeWidgetIsCenteredNearTheTopAndCanFillTheInsetSafeWidthOnEitherRailSide() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val manager = AppWidgetManager.getInstance(activity)
        val host = AppWidgetHost(activity, 1201)
        val id = host.allocateAppWidgetId()
        val previousWidgets = activity.store.widgets
        val previousHome = activity.store.homeWidgetId
        val previousLeft = activity.store.bool("leftHanded")
        val user = shell("am get-current-user").trim().toInt()
        val provider = ComponentName(instrumentation.context, LayoutPreviewProvider::class.java)
        var granted = false
        val capturePrefix = InstrumentationRegistry.getArguments().getString("widgetLayoutCaptures")
        fun capture(name: String) {
            if (capturePrefix == null) return
            instrumentation.waitForIdleSync()
            // Accessibility/layout completion precedes the compositor's next displayed frame.
            SystemClock.sleep(200)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.filesDir, "$capturePrefix-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        fun widget() = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>().single { it.appWidgetId == id }
        fun node(label: String): AccessibilityNodeInfo? {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
                return null
            }
            return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
        }
        fun await(predicate: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deadline) {
                var ready = false
                instrumentation.runOnMainSync { ready = predicate() }
                if (ready) return
                SystemClock.sleep(100)
            }
            fail("Home widget layout did not reach the expected state")
        }
        try {
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
                shell("appwidget grantbind --package ${activity.packageName} --user $user")
                granted = true
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider))
            }
            for (left in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    activity.store.widgets = previousWidgets + id
                    activity.store.homeWidgetId = id
                    activity.store.setWidgetSize(id, null)
                    activity.store.set("leftHanded", left)
                    MainActivity::class.java.getDeclaredMethod("applyAppearance").apply { isAccessible = true }.invoke(activity)
                    activity.showHome()
                }
                await { widget().width > 0 && activity.hasWindowFocus() }
                instrumentation.runOnMainSync {
                    val view = widget()
                    val frame = view.parent as View
                    val screen = descendants(activity.window.decorView).filterIsInstance<PageSwipeLayout>().single()
                    val usableWidth = screen.width - screen.paddingLeft - screen.paddingRight
                    assertEquals("Widget must have its own full-width region", usableWidth, frame.width)
                    val info = manager.getAppWidgetInfo(id)
                    val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
                    val providerMaximum = info.maxResizeWidth + padding.left + padding.right
                    val preferredWidth = minOf(usableWidth - activity.dp(72), providerMaximum)
                    assertTrue("Default widget keeps balanced side margins within provider bounds",
                        kotlin.math.abs(view.width - preferredWidth) <= activity.dp(8))
                    assertEquals(frame.width / 2f, view.left + view.width / 2f, 1f)
                    val bounds = Rect().also { view.getGlobalVisibleRect(it) }
                    val screenBounds = Rect().also { screen.getGlobalVisibleRect(it) }
                    assertEquals("Widget starts immediately below system insets",
                        screenBounds.top + screen.paddingTop, bounds.top)
                }
                capture("default-$left")
                if (capturePrefix != null && activity.resources.displayMetrics.heightPixels / activity.resources.displayMetrics.density > 500) {
                    instrumentation.runOnMainSync {
                        activity.store.setWidgetSize(id, WidgetSize((widget().width / activity.resources.displayMetrics.density).toInt(), 360))
                        activity.showHome()
                    }
                    await { widget().height == activity.dp(360) }
                    capture("preferred-$left")
                }
                instrumentation.runOnMainSync {
                    assertTrue((widget().parent as View).performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                }
                await { node("Переместить и изменить размер") != null }
                assertTrue(node("Переместить и изменить размер")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { node("Правая граница виджета") != null }
                instrumentation.runOnMainSync {
                    val handle = descendants(activity.window.decorView).first { it.contentDescription == "Верхняя граница виджета" }
                    val handleBounds = Rect().also { handle.getGlobalVisibleRect(it) }
                    val screen = descendants(activity.window.decorView).filterIsInstance<PageSwipeLayout>().single()
                    val screenBounds = Rect().also { screen.getGlobalVisibleRect(it) }
                    assertTrue("Top handle stays below system bars", handleBounds.top >= screenBounds.top + screen.paddingTop)
                }
                assertTrue(node("Правая граница виджета")!!.performAction(android.R.id.accessibilityActionSetProgress,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 1600f) }))
                fun maximumWidth(): Int {
                    val info = manager.getAppWidgetInfo(id)
                    val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
                    val density = activity.resources.displayMetrics.density
                    val maximumDp = ((info.maxResizeWidth + padding.left + padding.right) / density).toInt()
                    return minOf((widget().parent as View).width, activity.dp(maximumDp))
                }
                await { widget().width == maximumWidth() }
                capture("expanded-editor-$left")
                instrumentation.runOnMainSync { activity.widgets.finishResize(); activity.showHome() }
                await { widget().width == maximumWidth() }
                capture("expanded-$left")
                instrumentation.runOnMainSync {
                    if (widget().width == (widget().parent as View).width)
                        assertEquals("Full width persists across home recreation", 0, activity.store.widgetSize(id)!!.width)
                    activity.store.setWidgetSize(id, WidgetSize(180, 324, 24, 12))
                    activity.showHome()
                }
                await { widget().width == activity.dp(180) && widget().height == activity.dp(324) }
                capture("custom-$left")
                instrumentation.runOnMainSync {
                    assertEquals(activity.dp(12).toFloat(), widget().translationX)
                    assertEquals(activity.dp(24), (widget().layoutParams as android.widget.FrameLayout.LayoutParams).topMargin)
                }
            }
            instrumentation.runOnMainSync {
                activity.store.homeWidgetId = null
                activity.showHome()
            }
            await { descendants(activity.window.decorView).any { it.tag == PageSwipeLayout.BLOCK_SWIPE && it.isClickable && it.height > 0 } }
            instrumentation.runOnMainSync {
                val row = descendants(activity.window.decorView).first { it.tag == PageSwipeLayout.BLOCK_SWIPE && it.isClickable }
                val bounds = Rect().also { row.getGlobalVisibleRect(it) }
                val screen = descendants(activity.window.decorView).filterIsInstance<PageSwipeLayout>().single()
                val screenBounds = Rect().also { screen.getGlobalVisibleRect(it) }
                assertEquals("Favorites also start immediately below system insets", screenBounds.top + screen.paddingTop, bounds.top)
            }
            capture("favorites")
        } finally {
            instrumentation.runOnMainSync {
                host.deleteAppWidgetId(id)
                activity.store.widgets = previousWidgets
                activity.store.homeWidgetId = previousHome
                activity.store.set("leftHanded", previousLeft)
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
