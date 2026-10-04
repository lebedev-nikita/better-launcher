package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class AppListRulesTest {
    @Test fun `search ignores accents and case and supports Cyrillic`() {
        assertTrue(AppListRules.matches("cafe", "Café"))
        assertTrue(AppListRules.matches("елка", "Ёлка"))
        assertTrue(AppListRules.matches("google maps", "Google Карты", "Google Maps"))
        assertFalse(AppListRules.matches("maps camera", "Google Maps"))
    }
    @Test fun `rail keeps hash last even in short windows and empty catalogs`() {
        val sections = setOf("A", "B", "V", "#")
        assertEquals(listOf("★") + ('A'..'Z').map(Char::toString) + "#",
            AppListRules.railLetters(sections, shortWindow = false))
        assertEquals(listOf("★", "A", "B", "V", "#"), AppListRules.railLetters(sections, shortWindow = true))
        assertEquals(listOf("★", "#"), AppListRules.railLetters(emptySet(), shortWindow = true))
    }
    @Test fun `favorites can move without duplication and stop at boundaries`() {
        assertEquals(listOf("b", "a", "c"), AppListRules.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "c", "b"), AppListRules.move(listOf("a", "b", "c"), "b", 1))
        assertEquals(listOf("a", "b"), AppListRules.move(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), AppListRules.move(listOf("a", "b"), "unknown", 1))
    }
    @Test fun `dragging across several favorites preserves folders and profile identities`() {
        val personal = "0:example/.Main"
        val work = "10:example/.Main"
        val items = listOf(personal, "folder:work", "unavailable-profile", work)
        assertEquals(listOf("folder:work", "unavailable-profile", work, personal),
            AppListRules.move(items, personal, 3))
        assertEquals(listOf(work, personal, "folder:work", "unavailable-profile"),
            AppListRules.move(items, work, -3))
        assertEquals(items, AppListRules.move(items, work, 0))
        assertEquals(emptyList<String>(), AppListRules.move(emptyList(), personal, 1))
    }
}
