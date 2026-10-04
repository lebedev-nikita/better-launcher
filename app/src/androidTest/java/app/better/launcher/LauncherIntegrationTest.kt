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
    }
}
