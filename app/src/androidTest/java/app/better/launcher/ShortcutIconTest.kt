package app.better.launcher

import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShortcutIconTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun crossHidesWithoutLaunchingUndoRestoresAndHiddenIdsSurviveRecreationAndRenaming() {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        val component = ComponentName(context, MainActivity::class.java)
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val app = AppEntry("$serial:${component.flattenToString()}", component, Process.myUserHandle(), "Быстрые действия",
            context.getDrawable(R.mipmap.ic_launcher)!!)
        val store = LauncherStore(context)
        val previous = store.hiddenShortcuts(app.id)
        val ids = listOf("hide-subscriptions-test", "hide-shorts-test", "hide-search-test")
        fun shortcut(id: String, label: String, rank: Int) = ShortcutInfo.Builder(context, id)
            .setActivity(component).setShortLabel(label).setRank(rank)
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("hidden-shortcut-test", id)).build()
        try {
            store.setHiddenShortcuts(app.id, emptySet())
            assertTrue(manager.addDynamicShortcuts(listOf(shortcut(ids[0], "Подписки", 0), shortcut(ids[1], "Shorts", 1), shortcut(ids[2], "Поиск", 2))))
            val launchIntent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                lateinit var activity: MainActivity
                lateinit var dialog: Dialog
                scenario.onActivity { activity = it }
                await { activity.hasWindowFocus() }
                fun open() {
                    scenario.onActivity {
                        MainActivity::class.java.getDeclaredMethod("showAppPopup", AppEntry::class.java).apply { isAccessible = true }.invoke(it, app)
                        dialog = MainActivity::class.java.getDeclaredField("currentDialog").apply { isAccessible = true }.get(it) as Dialog
                    }
                    await { dialog.window!!.decorView.hasWindowFocus() }
                }
                fun action(label: String): View = descendants(dialog.window!!.decorView).single { it.contentDescription == label }
                fun hide(label: String) { instrumentation.runOnMainSync { action("Скрыть быстрое действие «$label»").performClick() } }
                open()
                await { action("Shorts").isShown && action("Shorts").width > 0 }
                instrumentation.runOnMainSync {
                    val cross = action("Скрыть быстрое действие «Shorts»")
                    assertTrue(cross.isFocusable)
                    assertTrue(cross.width >= activity.dp(48) && cross.height >= activity.dp(48))
                }
                instrumentation.waitForIdleSync(); SystemClock.sleep(500)
                capture(context, "hidden-shortcuts-before.png")
                hide("Shorts")
                await { !action("Shorts").isShown }
                assertTrue(dialog.isShowing)
                assertNull(activity.intent.getStringExtra("hidden-shortcut-test"))
                assertEquals(setOf(ids[1]), LauncherStore(context).hiddenShortcuts(app.id))
                instrumentation.runOnMainSync { action("Отменить скрытие «Shorts»").performClick() }
                await { action("Shorts").isShown }
                assertTrue(store.hiddenShortcuts(app.id).isEmpty())
                // Enter keyboard mode before requesting focus on a native button.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
                instrumentation.runOnMainSync { assertTrue(action("Скрыть быстрое действие «Поиск»").requestFocus()) }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                await { !action("Поиск").isShown }
                assertTrue(dialog.isShowing)
                assertNull(activity.intent.getStringExtra("hidden-shortcut-test"))
                assertTrue(manager.updateShortcuts(listOf(shortcut(ids[2], "Найти видео", 2))))
                // Close the sheet before recreating the HOME activity, then reopen it from saved state.
                instrumentation.runOnMainSync { dialog.dismiss() }
                await { activity.hasWindowFocus() }
                scenario.recreate()
                scenario.onActivity { activity = it }
                await { activity.hasWindowFocus() }
                open()
                instrumentation.runOnMainSync {
                    assertFalse("A renamed shortcut must remain hidden by ID", action("Найти видео").isShown)
                    assertTrue(action("Подписки").isShown)
                    assertTrue(action("Shorts").isShown)
                }
                hide("Shorts"); hide("Подписки")
                await { descendants(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.isShown && it.text == "Все быстрые действия скрыты." } }
                assertTrue(dialog.isShowing)
                assertTrue(manager.dynamicShortcuts.filter { it.id in ids }.all { it.isEnabled })
                assertEquals(ids.toSet(), store.hiddenShortcuts(app.id))
                instrumentation.runOnMainSync { action("Показать скрытые действия").performClick() }
                await { listOf("Подписки", "Shorts", "Найти видео").all { action(it).isShown } }
                assertTrue(store.hiddenShortcuts(app.id).isEmpty())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await { !dialog.isShowing }
                open()
                instrumentation.runOnMainSync {
                    assertFalse(action("Показать скрытые действия").isShown)
                    action("Подписки").performClick()
                }
                await { activity.intent.getStringExtra("hidden-shortcut-test") == ids[0] }
                assertFalse(dialog.isShowing)
                // The shortcut delivers ACTION_VIEW to the singleTask activity. Restore the scenario
                // intent so its lifecycle monitor can observe teardown after validating the launch.
                instrumentation.runOnMainSync { activity.intent = launchIntent }
            }
        } finally {
            manager.removeDynamicShortcuts(ids)
            store.setHiddenShortcuts(app.id, previous)
        }
    }

    private fun capture(context: android.content.Context, name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun popupShowsPublishedIconsKeepsMissingIconFallbackAndLaunchesShortcut() {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val component = ComponentName(context, MainActivity::class.java)
        val ids = listOf("avatar-test", "resource-test", "no-icon-test")
        var dialog: Dialog? = null
        try {
            await { activity.hasWindowFocus() }
            assertTrue("Test device must designate Better Launcher as HOME", activity.catalog.launcherApps.hasShortcutHostPermission())
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawCircle(48f, 48f, 48f, Paint().apply { color = Color.BLUE })
                drawCircle(48f, 36f, 16f, Paint().apply { color = Color.WHITE })
                drawOval(20f, 56f, 76f, 96f, Paint().apply { color = Color.WHITE })
            }
            fun shortcut(id: String, label: String, rank: Int, icon: Icon? = null) = ShortcutInfo.Builder(context, id)
                .setActivity(component).setShortLabel(label).setRank(rank)
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .putExtra("shortcut-icon-test", id))
                .apply { if (icon != null) setIcon(icon) }.build()
            assertTrue(manager.addDynamicShortcuts(listOf(
                shortcut(ids[0], "Беседа с аватаркой", 0, Icon.createWithBitmap(bitmap)),
                shortcut(ids[1], "Новая беседа", 1, Icon.createWithResource(context, R.mipmap.ic_launcher)),
                shortcut(ids[2], "Беседа без аватарки", 2))))
            assertNull(activity.catalog.shortcutIcon(shortcut("missing-icon-test", "Unavailable", 3)))
            val app = AppEntry("shortcut-test", component, Process.myUserHandle(), "Shortcut icon tests",
                context.getDrawable(R.mipmap.ic_launcher)!!)
            instrumentation.runOnMainSync {
                MainActivity::class.java.getDeclaredMethod("showAppPopup", AppEntry::class.java)
                    .apply { isAccessible = true }.invoke(activity, app)
                dialog = MainActivity::class.java.getDeclaredField("currentDialog")
                    .apply { isAccessible = true }.get(activity) as Dialog
            }
            fun row(label: String): LinearLayout = descendants(dialog!!.window!!.decorView)
                .filterIsInstance<LinearLayout>().single { it.contentDescription == label }
            await { row("Беседа с аватаркой").getChildAt(0) is ImageView && row("Новая беседа").getChildAt(0) is ImageView &&
                row("Беседа с аватаркой").getChildAt(0).width > 0 }
            instrumentation.waitForIdleSync()
            // Capture the settled sheet, after its 300ms arrival animation.
            SystemClock.sleep(500)
            instrumentation.uiAutomation.takeScreenshot()?.let { capture ->
                File(context.cacheDir, "shortcut-icons.png").outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                capture.recycle()
            }
            instrumentation.runOnMainSync {
                val avatar = row("Беседа с аватаркой")
                val fallback = row("Беседа без аватарки")
                val icon = avatar.getChildAt(0) as ImageView
                assertNotNull(icon.drawable)
                assertNull("Publisher colors must be preserved", icon.imageTintList)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.importantForAccessibility)
                assertTrue(fallback.getChildAt(0) is GlyphView)
                val avatarLabel = avatar.getChildAt(1) as TextView
                val fallbackLabel = fallback.getChildAt(1) as TextView
                assertEquals("Loading images must retain label alignment", fallbackLabel.left, avatarLabel.left)
                assertEquals(fallback.height, avatar.height)
                assertTrue(avatar.isFocusable)
                avatar.performClick()
            }
            await { activity.intent.getStringExtra("shortcut-icon-test") == ids[0] }
            assertFalse(dialog!!.isShowing)
        } finally {
            manager.removeDynamicShortcuts(ids)
            instrumentation.runOnMainSync { dialog?.dismiss(); activity.finish() }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error("Shortcut menu did not reach the expected state")
    }
}
