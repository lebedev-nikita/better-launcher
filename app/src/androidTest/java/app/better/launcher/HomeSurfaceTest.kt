package app.better.launcher

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class HomeSurfaceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) { instrumentation.waitForIdleSync(); return }
            SystemClock.sleep(50)
        }
        error("Home navigation did not reach the expected state")
    }
    private fun swipe(x: Float, y: Float, endX: Float, endY: Float, duration: Long = 300) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).let {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(it, true)); it.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..10) {
            SystemClock.sleep(duration / 10)
            send(MotionEvent.ACTION_MOVE, x + (endX - x) * step / 10, y + (endY - y) * step / 10)
        }
        send(MotionEvent.ACTION_UP, endX, endY)
    }

    @Test fun fullHeightHomeRetainsTouchKeyboardAndAccessibleNavigation() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val previous = activity.store.export()
        try {
            await { activity.hasWindowFocus() && activity.catalog.apps.isNotEmpty() }
            instrumentation.runOnMainSync {
                activity.store.homeWidgetId = null; activity.store.set("weatherCity", "")
                activity.store.favorites = listOf(activity.catalog.apps.first().id)
                activity.store.set("gesturesLearned", true)
                activity.store.set("leftHanded", InstrumentationRegistry.getArguments().getString("homeLeft") == "true")
                MainActivity::class.java.getDeclaredMethod("applyAppearance").apply { isAccessible = true }.invoke(activity)
                activity.showHome()
            }
            val bounds = Rect()
            lateinit var home: PageSwipeLayout
            await {
                home = views(activity.window.decorView).filterIsInstance<PageSwipeLayout>().single()
                home.contentDescription == "Главный экран" && home.hasWindowFocus() &&
                    views(home).filterIsInstance<ScrollView>().single().getGlobalVisibleRect(bounds)
            }
            instrumentation.runOnMainSync {
                val scroll = views(home).filterIsInstance<ScrollView>().single()
                assertEquals("Favorites gain the entire footer area", home.height - home.paddingTop - home.paddingBottom, scroll.height)
                assertEquals(scroll.height, scroll.getChildAt(0).height)
                assertFalse(views(home).any { it.contentDescription in listOf("Все приложения и поиск", "Экран виджетов", "Настройки лаунчера") })
                val railBounds = Rect()
                val rail = views(activity.window.decorView).filterIsInstance<AlphabetRail>().single()
                assertEquals(activity.store.bool("leftHanded"), rail.leftHanded)
                assertTrue(rail.getGlobalVisibleRect(railBounds))
                assertEquals("Alphabet has no obsolete footer reserve", bounds.bottom, railBounds.bottom)
                val node = home.createAccessibilityNodeInfo()
                assertTrue(node.actionList.map { it.id }.containsAll(listOf(R.id.home_search, R.id.home_widgets, R.id.home_settings)))
            }
            InstrumentationRegistry.getArguments().getString("homeCapture")?.let { name ->
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(activity.filesDir, "$name.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }; bitmap.recycle()
                }
            }
            // The former footer area remains usable wallpaper, including on short windows.
            val x = bounds.exactCenterX()
            val y = (bounds.bottom - activity.dp(28)).toFloat()
            swipe(x, y, x, y - activity.dp(100), 150)
            await { views(home).filterIsInstance<EditText>().any { it.hint == "Поиск приложений" } }
            instrumentation.runOnMainSync { activity.showHome() }
            await { home.contentDescription == "Главный экран" && activity.hasWindowFocus() }
            swipe(x, y, x, y, 850)
            await { home.accessibilityPaneTitle == "Настройки" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { home.contentDescription == "Главный экран" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SEARCH)
            await { views(home).filterIsInstance<EditText>().any { it.hint == "Поиск приложений" } }
            instrumentation.runOnMainSync { activity.showHome() }
            await { activity.hasWindowFocus() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            await { home.accessibilityPaneTitle == "Настройки" }
            instrumentation.runOnMainSync { activity.showHome(); assertTrue(home.performAccessibilityAction(R.id.home_widgets, null)) }
            await { home.accessibilityPaneTitle == "Виджеты" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { home.contentDescription == "Главный экран" }
            instrumentation.runOnMainSync { assertTrue(home.performAccessibilityAction(R.id.home_settings, null)) }
            await { home.accessibilityPaneTitle == "Настройки" }
            instrumentation.runOnMainSync { activity.showHome(); assertTrue(home.performAccessibilityAction(R.id.home_search, null)) }
            await { views(home).filterIsInstance<EditText>().any { it.hint == "Поиск приложений" } }
        } finally {
            instrumentation.runOnMainSync { activity.store.import(previous); activity.finish() }
        }
    }
}
