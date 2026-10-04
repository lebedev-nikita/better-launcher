package app.better.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import com.google.android.material.slider.Slider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(block: (LauncherStore) -> Unit) {
        val original = context.getSharedPreferences("launcher", Context.MODE_PRIVATE).all
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try { block(LauncherStore(context)) } finally {
            prefs.edit().apply {
                clear()
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                } }
            }.commit()
        }
    }
    @Test fun settingsAndFoldersSurviveRecreationAndBackup() = isolated { store ->
        store.favorites = listOf("a", "b", "a")
        store.hidden = setOf("c")
        store.rename("a", "Почта")
        val folder = store.createFolder("Работа", listOf("b", "c"))
        store.setPopUp("a", listOf("b"))
        store.widgets = listOf(42)
        store.set("wallpaper", "dune") // Legacy version 1 backups remain importable.
        val backup = store.export()
        assertFalse(backup.contains("\"widgets\""))
        store.favorites = emptyList(); store.rename("a", "Другое")
        store.import(backup)
        val reloaded = LauncherStore(context)
        assertEquals(listOf("a", "b", folder), reloaded.favorites)
        assertEquals("Почта", reloaded.alias("a"))
        assertEquals(listOf("b", "c"), reloaded.folder(folder)?.apps)
        assertEquals(listOf("b"), reloaded.popUp("a"))
        assertEquals(setOf("c"), reloaded.hidden)
        assertEquals(listOf(42), reloaded.widgets)
        assertEquals("dune", reloaded.string("wallpaper"))
    }
    @Test fun corruptedBackupDoesNotOverwriteCurrentState() = isolated { store ->
        store.favorites = listOf("keep-me")
        val before = store.export()
        val corrupt = """{"version":1,"preferences":{"favorites":"broken-json"}}"""
        assertTrue(runCatching { store.import(corrupt) }.isFailure)
        assertEquals(before, store.export())
        assertTrue(runCatching { store.import("""{"version":1,"preferences":{"favorites":true}}""") }.isFailure)
        assertTrue(runCatching { store.import("""{"version":1,"preferences":{"folder:x":"{}"}}""") }.isFailure)
        assertEquals(before, store.export())
    }
    @Test fun widgetPlacementMigratesAndSurvivesRecreationAndPortableImport() = isolated { store ->
        // Existing stacks keep their first widget on home; the others move left.
        store.prefs.edit().putString("widgets", "[\"41\",\"42\",\"43\"]").commit()
        assertEquals(41, store.homeWidgetId)
        assertEquals(listOf(42, 43), store.pageWidgets)
        store.homeWidgetId = 42
        assertEquals(listOf(41, 43), store.pageWidgets)
        store.set("pendingWidgetId", "44")
        store.set("pendingWidgetDestination", "widgets")
        val backup = store.export()
        assertFalse(backup.contains("homeWidgetId"))
        assertFalse(backup.contains("pendingWidget"))
        store.import(backup)
        val reloaded = LauncherStore(context)
        assertEquals(42, reloaded.homeWidgetId)
        assertEquals(listOf(41, 43), reloaded.pageWidgets)
        assertEquals("44", reloaded.string("pendingWidgetId"))
        assertEquals("widgets", reloaded.string("pendingWidgetDestination"))
        // Backups from another device cannot replace host IDs or the home selection.
        reloaded.import("""{"version":1,"preferences":{"widgets":"[\"99\"]","homeWidgetId":"99","pendingWidgetId":"100","pendingWidgetDestination":"home"}}""")
        assertEquals(listOf(41, 42, 43), reloaded.widgets)
        assertEquals(42, reloaded.homeWidgetId)
        assertEquals("44", reloaded.string("pendingWidgetId"))
        assertEquals("widgets", reloaded.string("pendingWidgetDestination"))
        reloaded.homeWidgetId = null
        reloaded.import(backup)
        assertNull(LauncherStore(context).homeWidgetId)
        assertEquals(listOf(41, 42, 43), reloaded.pageWidgets)
    }
    @Test fun deletingHomeDoesNotPromoteAnotherWidgetAndInvalidImportIsAtomic() = isolated { store ->
        store.widgets = listOf(41, 42, 43)
        store.homeWidgetId = 42
        store.widgets = listOf(41, 43)
        assertNull(store.homeWidgetId)
        assertEquals(listOf(41, 43), store.pageWidgets)
        val before = store.prefs.all
        listOf(
            """{"homeWidgetId":42}""",
            """{"homeWidgetId":"invalid"}""",
            """{"widgets":"invalid"}""",
            """{"pendingWidgetDestination":"invalid"}"""
        ).forEach { invalid ->
            assertTrue(runCatching { store.import("""{"version":1,"preferences":$invalid}""") }.isFailure)
            assertEquals(before, store.prefs.all)
        }
    }
    @Test fun widgetSizesStayLocalSurviveImportAndAreRemovedWithWidget() = isolated { store ->
        store.widgets = listOf(41, 42)
        store.set("widgetSizes", """{"42":{"width":180,"height":320}}""")
        assertEquals("Existing rc.21 sizes keep their default position", WidgetSize(180, 320), store.widgetSize(42))
        store.set("widgetSizes", """{"42":{"width":180,"height":320,"top":24}}""")
        assertEquals("Existing rc.22 offsets keep centered width", WidgetSize(180, 320, 24), store.widgetSize(42))
        store.setWidgetSize(42, WidgetSize(180, 320, 24, -20))
        assertEquals(WidgetSize(180, 320, 24, -20), LauncherStore(context).widgetSize(42))
        val backup = store.export()
        assertFalse(backup.contains("widgetSizes"))
        store.import(backup)
        assertEquals(WidgetSize(180, 320, 24, -20), store.widgetSize(42))
        store.import("""{"version":1,"preferences":{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100}}"}}""")
        assertEquals(WidgetSize(180, 320, 24, -20), store.widgetSize(42))
        val before = store.prefs.all
        listOf(
            """{"widgetSizes":true}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100,\"offsetX\":1601}}"}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100,\"offsetX\":1.5}}"}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100,\"offsetX\":\"left\"}}"}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100,\"top\":-1}}"}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":100,\"top\":1.5}}"}""",
            """{"widgetSizes":"broken"}""",
            """{"widgetSizes":"{\"42\":{\"width\":-1,\"height\":100}}"}""",
            """{"widgetSizes":"{\"42\":{\"width\":100,\"height\":1.5}}"}""",
            """{"widgetSizes":"{\"bad\":{\"width\":100,\"height\":100}}"}"""
        ).forEach { invalid ->
            assertTrue(runCatching { store.import("""{"version":1,"preferences":$invalid}""") }.isFailure)
            assertEquals(before, store.prefs.all)
        }
        store.homeWidgetId = 42
        assertEquals(WidgetSize(180, 320, 24, -20), store.widgetSize(42))
        store.widgets = listOf(41)
        assertNull(store.widgetSize(42))
        store.widgets = listOf(41, 42)
        assertNull(store.widgetSize(42))
    }
    @Test fun dimmingSurvivesBackupAndRejectsInvalidValuesAtomically() = isolated { store ->
        assertEquals(20, store.wallpaperDimming)
        store.set("dimWallpaper", false)
        assertEquals(0, store.wallpaperDimming)
        store.set("dimWallpaper", true)
        assertEquals(20, store.wallpaperDimming)
        store.favorites = listOf("keep-me")
        store.widgets = listOf(42)
        for (level in listOf(0, 37, 100)) {
            store.wallpaperDimming = level
            val backup = store.export()
            store.wallpaperDimming = 50
            store.import(backup)
            assertEquals(level, LauncherStore(context).wallpaperDimming)
            assertEquals(listOf(42), store.widgets)
        }
        val before = store.export()
        for (invalid in listOf("-1", "101", "20.5", "\"20\"", "true", "null")) {
            val backup = """{"version":1,"preferences":{"favorites":"[]","wallpaperDimming":$invalid}}"""
            assertTrue(runCatching { store.import(backup) }.isFailure)
            assertEquals(before, store.export())
        }
        store.import("""{"version":1,"preferences":{"dimWallpaper":false}}""")
        assertEquals(0, store.wallpaperDimming)
        store.import("""{"version":1,"preferences":{"dimWallpaper":true}}""")
        assertEquals(20, store.wallpaperDimming)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
    @Test fun dimmingSliderSupportsKeyboardAccessibilityAndRecreation() = isolated { store ->
        store.set("initialized", true)
        store.wallpaperDimming = 20
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).first { it.contentDescription == "Настройки лаунчера" }.performClick()
                val slider = descendants(activity.window.decorView).filterIsInstance<Slider>().single()
                assertEquals(20f, slider.value, 0f)
                slider.requestFocus()
                slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
                slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                assertEquals(21, activity.store.wallpaperDimming)
                val progress = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 37f) }
                assertTrue(slider.accessibilityNodeProvider.performAction(0, android.R.id.accessibilityActionSetProgress, progress))
                assertEquals(37, activity.store.wallpaperDimming)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val scrim = descendants(activity.window.decorView).filterIsInstance<WallpaperScrimView>().single()
                val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                scrim.draw(Canvas(bitmap))
                assertEquals((.37f * 255).toInt(), Color.alpha(bitmap.getPixel(0, 0)))
                bitmap.recycle()
                descendants(activity.window.decorView).first { it.contentDescription == "Настройки лаунчера" }.performClick()
                assertEquals(37f, descendants(activity.window.decorView).filterIsInstance<Slider>().single().value, 0f)
            }
        }
    }
    @Test fun catalogLoadsRealAppsAndRespectsHiddenAppsAndAliases() = isolated { store ->
        val catalog = AppCatalog(context, store)
        catalog.load()
        assertTrue(catalog.apps.isNotEmpty())
        assertFalse(catalog.apps.any { it.packageName == context.packageName })
        val app = catalog.apps.first()
        store.rename(app.id, "Ёлка Café")
        assertEquals(app.id, catalog.visible("елка cafe").first().id)
        store.hidden = setOf(app.id)
        assertTrue(catalog.visible("елка cafe").isEmpty())
        store.hidden = emptySet()
        assertEquals(app.id, catalog.visible("елка cafe").single().id)
        store.rename(app.id, "Zebra")
        assertTrue(catalog.visible("елка cafe").isEmpty())
        assertEquals(app.id, catalog.visible("zebra").single().id)
        assertTrue(catalog.sections().contains("Z"))
        store.rename(app.id, "")
        assertEquals(app.originalLabel, catalog.label(catalog.visible(app.originalLabel).first { it.id == app.id }))
    }
    @Test fun catalogGroupsLatinAndCyrillicAliasesAndPutsOtherNamesLast() = isolated { store ->
        val catalog = AppCatalog(context, store)
        catalog.load()
        val labels = listOf("Bank", "Банк", "Видео", "Zoom", "Яндекс", "123")
        val apps = catalog.apps.take(labels.size)
        assertEquals(labels.size, apps.size)
        apps.zip(labels).forEach { (app, label) -> store.rename(app.id, label) }
        val ids = apps.map { it.id }.toSet()
        val otherIds = catalog.apps.filter { it.id !in ids }.map { it.id }.toSet()
        store.hidden = otherIds
        assertEquals(listOf("★", "B", "V", "Y", "Z", "#"), AppListRules.railLetters(catalog.sections()))
        val sections = catalog.visible().filter { it.id in ids }.map { AppListRules.section(catalog.label(it)) }
        assertEquals(listOf("B", "B", "V", "Y", "Z", "#"), sections)
        store.hidden = otherIds + apps[2].id
        assertFalse(catalog.visible().any { it.id == apps[2].id })
        assertEquals(setOf("B", "Y", "Z", "#"), catalog.visible().filter { it.id in ids }
            .map { AppListRules.section(catalog.label(it)) }.toSet())
        assertEquals(listOf("★", "B", "Y", "Z", "#"), AppListRules.railLetters(catalog.sections()))
        store.rename(apps[0].id, "Alpha")
        assertEquals(listOf("★", "A", "B", "Y", "Z", "#"), AppListRules.railLetters(catalog.sections()))
        store.hidden = otherIds
        assertEquals(listOf("★", "A", "B", "V", "Y", "Z", "#"), AppListRules.railLetters(catalog.sections()))
    }
}
