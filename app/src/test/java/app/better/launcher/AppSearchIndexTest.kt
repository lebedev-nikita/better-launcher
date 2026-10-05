package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class AppSearchIndexTest {
    private data class App(val id: String, val label: String, val original: String = label)

    @Test fun `hidden apps stay searchable but leave the empty list and sections`() {
        val visible = App("0:example/.Main", "Alpha")
        val hidden = App("10:example/.Main", "Zebra Café", "Camera")
        val index = AppSearchIndex(listOf(visible, hidden), App::label, App::original) { it.id != hidden.id }
        assertEquals(listOf(visible), index.matching(""))
        assertEquals(listOf(visible), index.matching("   "))
        assertEquals(setOf("A"), index.sections)
        assertEquals(listOf(hidden), index.matching("zebra cafe"))
        assertEquals(listOf(hidden), index.matching("CAMERA"))
        assertEquals(listOf(visible, hidden), index.matching("a"))
        assertTrue(index.matching("missing").isEmpty())
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
