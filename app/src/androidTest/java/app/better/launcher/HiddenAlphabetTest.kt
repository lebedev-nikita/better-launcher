package app.better.launcher

import android.app.Dialog
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class HiddenAlphabetTest {
    @Test fun hidingLastAppUpdatesRailImmediatelyAndUndoAndSearchRestoreIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = LauncherStore(context)
        val backup = store.export()
        val catalog = AppCatalog(context, store).apply { load() }
        val apps = catalog.apps.take(4)
        assertEquals(4, apps.size)
        val labels = listOf("Alpha fixture", "Another fixture", "Zebra fixture", "123 fixture")
        try {
            store.set("initialized", true)
            apps.zip(labels).forEach { (app, label) -> store.rename(app.id, label) }
            store.favorites = apps.map { it.id }
            store.hidden = catalog.apps.map { it.id }.toSet() - apps.map { it.id }.toSet()
            for (left in listOf(false, true)) {
                store.set("leftHanded", left)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val deadline = SystemClock.uptimeMillis() + 30000
                    var ready = false
                    while (!ready && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { activity ->
                            ready = activity.hasWindowFocus() && activity.catalog.apps.isNotEmpty() &&
                                !MainActivity::class.java.getDeclaredField("loadingApps").apply { isAccessible = true }.getBoolean(activity)
                        }
                        if (!ready) SystemClock.sleep(50)
                    }
                    assertTrue("Catalog must finish before checking hide actions", ready)
                    scenario.onActivity { activity ->
                        val rail = views(activity.window.decorView).filterIsInstance<AlphabetRail>().single()
                        fun expect(vararg letters: String) {
                            assertEquals("left=$left", letters.toList(), rail.letters)
                            assertEquals(letters.filter { it != "★" }.toSet(), rail.activeLetters)
                            assertEquals(activity.catalog.sections(), rail.activeLetters)
                        }
                        fun menu(label: String) {
                            val name = views(activity.window.decorView).filterIsInstance<TextView>()
                                .first { it !is EditText && it.text.toString() == label && it.isLongClickable }
                            assertTrue(name.performLongClick())
                        }
                        fun command(label: String) {
                            val dialog = MainActivity::class.java.getDeclaredField("currentDialog")
                                .apply { isAccessible = true }.get(activity) as Dialog
                            assertTrue(views(dialog.window!!.decorView).first { it.contentDescription == label }.performClick())
                        }
                        fun hide(index: Int) { menu(labels[index]); command("Скрыть приложение") }

                        expect("★", "A", "Z", "#")
                        hide(2)
                        assertFalse(activity.catalog.visible().any { it.id == apps[2].id })
                        expect("★", "A", "#")
                        assertTrue(views(activity.window.decorView).filterIsInstance<TextView>()
                            .first { it.text.toString() == "Отменить" }.performClick())
                        expect("★", "A", "Z", "#")
                        hide(3)
                        expect("★", "A", "Z")
                        hide(0)
                        expect("★", "A", "Z")
                        hide(1)
                        expect("★", "Z")
                        hide(2)
                        expect("★")

                        views(activity.window.decorView).first { it.contentDescription == "Все приложения и поиск" }.performClick()
                        views(activity.window.decorView).filterIsInstance<EditText>().single().setText(labels[3])
                        assertEquals(apps[3].id, activity.catalog.visible(labels[3]).single().id)
                        expect("★")
                    }
                    // ListView lays out the searched row on the next frame.
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val rail = views(activity.window.decorView).filterIsInstance<AlphabetRail>().single()
                        val name = views(activity.window.decorView).filterIsInstance<TextView>()
                            .first { it !is EditText && it.text.toString() == labels[3] && it.isLongClickable }
                        assertTrue(name.performLongClick())
                        val dialog = MainActivity::class.java.getDeclaredField("currentDialog")
                            .apply { isAccessible = true }.get(activity) as Dialog
                        assertTrue(views(dialog.window!!.decorView).first { it.contentDescription == "Показывать снова" }.performClick())
                        assertEquals(listOf("★", "#"), rail.letters)
                        assertEquals(setOf("#"), rail.activeLetters)
                        assertEquals(labels[3], views(activity.window.decorView).filterIsInstance<EditText>().single().text.toString())
                        activity.showHome()
                        assertEquals(listOf("★", "#"), rail.letters)
                    }
                }
                store.hidden = catalog.apps.map { it.id }.toSet() - apps.map { it.id }.toSet()
                store.favorites = apps.map { it.id }
            }
        } finally { store.import(backup) }
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
