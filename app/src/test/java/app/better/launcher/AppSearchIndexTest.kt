package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class AppSearchIndexTest {
    private data class App(val id: String, val label: String, val original: String = label)

    @Test fun `prepared search keeps alias and original matching and section order`() {
        val apps = listOf(App("maps", "Карты", "Google Maps"), App("cafe", "Café"), App("tree", "Ёлка"), App("number", "123"))
        val index = AppSearchIndex(apps, App::label, App::original)
        assertEquals(listOf("cafe", "maps", "tree", "number"), index.apps.map(App::id))
        for (query in listOf("cafe", "CAFÉ", "елка", "google карты", "maps", "maps camera", "  ")) {
            assertEquals(index.apps.filter { AppListRules.matches(query, it.label, it.original) }, index.matching(query))
        }
        assertEquals(setOf("C", "K", "Y", "#"), index.sections)
    }

    @Test fun `opening and repeated queries do not reread app labels`() {
        val apps = (0 until 1000).map { App(it.toString(), "App $it") }
        var reads = 0
        val index = AppSearchIndex(apps, { reads++; it.label }, { reads++; it.original })
        val preparedReads = reads
        repeat(20) { assertSame(index.apps, index.matching("")); index.matching("app 12") }
        assertEquals(preparedReads, reads)
        assertTrue(index.matching("app 999").single().id == "999")
    }
}
