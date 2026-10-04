package app.better.launcher

import android.content.Context
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
}
