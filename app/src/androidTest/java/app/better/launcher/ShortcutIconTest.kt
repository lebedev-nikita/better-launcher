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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShortcutIconTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

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
