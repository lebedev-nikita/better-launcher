package app.better.launcher

import android.app.Dialog
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppGesturesTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun iconAndNameHoldsOpenDifferentMenusAndRowSwipesOpenWidgets() {
        withApp { activity, label ->
            val icon = awaitView(activity) { it.contentDescription?.toString() == "$label: удерживайте для уведомлений и быстрых действий" }
            hold(icon)
            awaitView(activity, dialog = true) { it.contentDescription?.toString() == "Добавить приложения" }
            dismiss(activity)
            val name = awaitView(activity) { it is TextView && it.text.toString() == label }
            hold(name)
            awaitView(activity, dialog = true) { it.contentDescription?.toString() == "О приложении" }
            dismiss(activity)
            // A leftward gesture no longer opens the app popup or launches the app.
            swipe(name, -1)
            awaitView(activity) { it.contentDescription?.toString() == "Все приложения и поиск" }
            instrumentation.runOnMainSync { assertNull(dialog(activity)) }
            // Both the name and icon must allow the parent page gesture.
            for (onIcon in listOf(false, true)) {
                val target = awaitView(activity) {
                    if (onIcon) it.contentDescription?.toString() == "$label: удерживайте для уведомлений и быстрых действий"
                    else it is TextView && it.text.toString() == label
                }
                swipe(target, 1)
                awaitView(activity) { it is TextView && it.text.toString() == "Виджеты" }
                instrumentation.runOnMainSync {
                    views(activity.window.decorView).first { it.contentDescription?.toString() == "На главный экран" }.performClick()
                }
                awaitView(activity) { it.contentDescription?.toString() == "Все приложения и поиск" }
            }
        }
    }

    @Test fun hiddenIconsKeepBothMenusAccessible() {
        withApp(hideIcons = true) { activity, label ->
            val name = awaitView(activity) { it is TextView && it.text.toString() == label }
            instrumentation.runOnMainSync {
                val info = name.createAccessibilityNodeInfo()
                val popup = info.actionList.first { it.label?.toString() == "Уведомления и быстрые действия" }
                assertTrue(name.performAccessibilityAction(popup.id, null))
            }
            awaitView(activity, dialog = true) { it.contentDescription?.toString() == "Добавить приложения" }
            dismiss(activity)
            instrumentation.runOnMainSync { assertTrue(name.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null)) }
            awaitView(activity, dialog = true) { it.contentDescription?.toString() == "О приложении" }
        }
    }

    private fun withApp(hideIcons: Boolean = false, test: (MainActivity, String) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val oldHide = activity.store.bool("hideIcons")
        try {
            awaitView(activity) { it.contentDescription?.toString() == "Все приложения и поиск" }
            instrumentation.runOnMainSync {
                activity.store.set("hideIcons", hideIcons)
                activity.showHome()
            }
            var label = ""
            // The home footer appears before the asynchronously loaded catalog and favorites.
            awaitView(activity) {
                val favorite = activity.store.favorites.firstNotNullOfOrNull(activity.catalog::find)
                val loadedLabel = favorite?.let(activity.catalog::label)
                if (it is TextView && loadedLabel != null && it.text.toString() == loadedLabel) {
                    label = loadedLabel
                    true
                } else false
            }
            test(activity, label)
        } finally {
            instrumentation.runOnMainSync {
                dialog(activity)?.dismiss()
                activity.store.set("hideIcons", oldHide)
                activity.finish()
            }
        }
    }

    private fun dialog(activity: MainActivity) = MainActivity::class.java.getDeclaredField("currentDialog")
        .apply { isAccessible = true }.get(activity) as? Dialog
    private fun dismiss(activity: MainActivity) {
        instrumentation.runOnMainSync { dialog(activity)?.dismiss() }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun awaitView(activity: MainActivity, dialog: Boolean = false, predicate: (View) -> Boolean): View {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var result: View? = null
            instrumentation.runOnMainSync {
                if (!dialog && !activity.hasWindowFocus()) return@runOnMainSync
                val root = if (dialog) dialog(activity)?.window?.decorView else activity.window.decorView
                result = root?.let { views(it).firstOrNull { v -> predicate(v) && v.isShown && v.getGlobalVisibleRect(Rect()) } }
            }
            if (result != null) { instrumentation.waitForIdleSync(); return result!! }
            SystemClock.sleep(50)
        }
        error("Expected visible ${if (dialog) "menu" else "launcher"} element")
    }
    private fun hold(view: View) = gesture(view, 0, 850)
    private fun swipe(view: View, direction: Int) = gesture(view, direction, 300)
    private fun gesture(view: View, direction: Int, duration: Int) {
        val rect = Rect()
        instrumentation.runOnMainSync { assertTrue(view.getGlobalVisibleRect(rect)) }
        val width = instrumentation.targetContext.resources.displayMetrics.widthPixels
        val distance = instrumentation.targetContext.dp(90).toFloat()
        val x = if (direction < 0) minOf(rect.right - 1f, width * .6f) else rect.left + minOf(rect.width() / 2f, width * .2f)
        val y = rect.exactCenterY()
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, targetX: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, targetX, y, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, x)
        for (step in 1..10) {
            SystemClock.sleep((duration / 10).toLong())
            send(MotionEvent.ACTION_MOVE, x + direction * distance * step / 10f)
        }
        send(MotionEvent.ACTION_UP, x + direction * distance)
        instrumentation.waitForIdleSync()
    }
}
