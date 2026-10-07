package app.better.launcher

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TransientMessageTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun ordinaryAndUndoMessagesExpireWithinThreeSecondsEvenDuringTouch() = launch { scenario ->
        for (undo in listOf(null, "Undo")) {
            lateinit var bar: TransientMessageView
            var undone = false
            var shownAt = 0L
            scenario.onActivity {
                it.message(if (undo == null) "Не удалось открыть приложение. Возможно, приложение отключено." else "App hidden", undo) { undone = true }
                bar = message(it)
                shownAt = SystemClock.uptimeMillis()
            }
            await { bar.width > 0 }
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("messageCapture") == "true") {
                SystemClock.sleep(150)
                val file = java.io.File(context.getExternalFilesDir(null), "message-${if (undo == null) "plain" else "undo"}.png")
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/${file.name}").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
            }
            scenario.onActivity { assertNotNull(bar.parent); touch(bar, MotionEvent.ACTION_DOWN, 20f, shownAt) }
            SystemClock.sleep((shownAt + 3100 - SystemClock.uptimeMillis()).coerceAtLeast(0))
            scenario.onActivity {
                assertNull("Feedback must expire even with a held finger", bar.parent)
                assertFalse("Expiry must not invoke undo", undone)
            }
        }
    }

    @Test fun swipesInBothDirectionsIncludingFromUndoDismissWithoutUndoing() = launch { scenario ->
        for (direction in listOf(-1, 1)) {
            lateinit var bar: TransientMessageView
            var undone = false
            scenario.onActivity {
                it.message("App hidden", "Undo") { undone = true }
                bar = message(it)
            }
            await { bar.width > 0 }
            scenario.onActivity {
                val button = bar.getChildAt(1)
                val start = button.left + button.width / 2f
                val down = SystemClock.uptimeMillis()
                touch(bar, MotionEvent.ACTION_DOWN, start, down)
                touch(bar, MotionEvent.ACTION_MOVE, start + direction * bar.width * .1f, down)
                touch(bar, MotionEvent.ACTION_MOVE, start + direction * bar.width * .6f, down)
                touch(bar, MotionEvent.ACTION_UP, start + direction * bar.width * .6f, down)
                assertNull("Sideways swipe must remove the message", bar.parent)
                assertFalse("Swiping the undo action must cancel its click", undone)
            }
        }
    }

    @Test fun shortAndCancelledSwipesKeepUndoAndReplacementHasItsOwnTimeout() = launch { scenario ->
        lateinit var first: TransientMessageView
        var undone = 0
        scenario.onActivity { it.message("Первое", "Undo") { undone++ }; first = message(it) }
        await { first.width > 0 }
        scenario.onActivity {
            val start = first.width / 2f
            for (end in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                val down = SystemClock.uptimeMillis()
                touch(first, MotionEvent.ACTION_DOWN, start, down)
                touch(first, MotionEvent.ACTION_MOVE, start + first.width * .1f, down)
                touch(first, end, start + first.width * .1f, down)
                assertNotNull(first.parent)
            }
            first.getChildAt(1).performClick()
            assertEquals(1, undone)
            assertNull(first.parent)
        }
        lateinit var replaced: TransientMessageView
        lateinit var latest: TransientMessageView
        scenario.onActivity { it.message("Заменяемое"); replaced = message(it) }
        SystemClock.sleep(1800)
        scenario.onActivity { it.message("Новое"); latest = message(it); assertNull(replaced.parent) }
        SystemClock.sleep(1400)
        scenario.onActivity {
            assertNotNull("Old timeout must not remove the replacement", latest.parent)
            assertTrue(latest.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null))
            assertNull(latest.parent)
        }
        for (key in listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL)) {
            lateinit var keyboard: TransientMessageView
            scenario.onActivity { it.message("Закрытие с клавиатуры"); keyboard = message(it) }
            // ViewGroup only dispatches keys after its focus target has been laid out.
            await { keyboard.width > 0 }
            scenario.onActivity {
                assertTrue("Keyboard dismissal requires focus", keyboard.requestFocusFromTouch())
                assertTrue("A visible focused message must handle the key", keyboard.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
                assertNull(keyboard.parent)
            }
        }
    }

    @Test fun feedbackInAnOpenMenuUsesTheActiveWindowAndCanBeSwiped() = launch { scenario ->
        var app: AppEntry? = null
        await { scenario.onActivity { app = it.catalog.apps.firstOrNull() }; app != null }
        lateinit var dialog: android.app.Dialog
        scenario.onActivity {
            MainActivity::class.java.getDeclaredMethod("showAppMenu", AppEntry::class.java).apply { isAccessible = true }.invoke(it, app!!)
            dialog = MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(it) as android.app.Dialog
        }
        await { dialog.window!!.decorView.hasWindowFocus() }
        lateinit var bar: TransientMessageView
        scenario.onActivity { it.message("Не удалось выполнить действие"); bar = message(it) }
        await { bar.windowToken === dialog.window!!.decorView.windowToken && bar.width > 0 }
        scenario.onActivity {
            val down = SystemClock.uptimeMillis()
            val start = bar.width / 2f
            touch(bar, MotionEvent.ACTION_DOWN, start, down)
            touch(bar, MotionEvent.ACTION_MOVE, start - bar.width * .4f, down)
            touch(bar, MotionEvent.ACTION_UP, start - bar.width * .4f, down)
            assertNull(bar.parent)
            assertTrue("Dismissing feedback must keep the menu open", dialog.isShowing)
            dialog.dismiss()
        }
    }

    @Test fun hidingAppOffersWorkingUndoAndDismissalKeepsItHidden() {
        val store = LauncherStore(context)
        val original = store.export()
        try {
            launch { scenario ->
                var app: AppEntry? = null
                await { scenario.onActivity { app = it.catalog.apps.firstOrNull() }; app != null }
                val entry = app!!
                store.hidden = store.hidden - entry.id
                lateinit var bar: TransientMessageView
                scenario.onActivity {
                    val menu = MainActivity::class.java.getDeclaredMethod("showAppMenu", AppEntry::class.java)
                    // Use the actual long-press menu and hide action, not a synthetic store write.
                    menu.isAccessible = true; menu.invoke(it, entry)
                    val dialog = MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(it) as android.app.Dialog
                    descendants(dialog.window!!.decorView).firstOrNull { it.contentDescription == "Additional" }?.performClick()
                    descendants(dialog.window!!.decorView).first { it.contentDescription == "Hide app" }.performClick()
                    bar = message(it)
                    assertTrue(entry.id in store.hidden)
                    bar.getChildAt(1).performClick()
                    assertFalse(entry.id in store.hidden)
                }
                scenario.onActivity {
                    it.message("App hidden", "Undo") { store.hidden = store.hidden - entry.id }
                    store.hidden = store.hidden + entry.id
                    bar = message(it)
                    bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null)
                    assertTrue("Dismissing feedback must retain the action's result", entry.id in store.hidden)
                }
            }
        } finally { store.import(original) }
    }

    private fun message(activity: MainActivity) = descendants(activity.window.decorView).filterIsInstance<TransientMessageView>().single()
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun touch(bar: View, action: Int, x: Float, down: Long) {
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, bar.height / 2f, 0).let {
            bar.dispatchTouchEvent(it); it.recycle()
        }
    }
    private fun launch(block: (ActivityScenario<MainActivity>) -> Unit) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use(block)
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(30)
        }
        error("Expected transient-message state did not appear")
    }
}
