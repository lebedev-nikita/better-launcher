package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class AppSearchIndexTest {
    private data class App(val id: String, val label: String, val original: String = label)

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
