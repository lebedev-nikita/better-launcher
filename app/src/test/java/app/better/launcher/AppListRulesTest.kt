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
    @Test fun `section grouping has stable alphabetical ordering`() {
        assertEquals("E", AppListRules.section("Écran"))
        assertEquals("Е", AppListRules.section("Ёлка"))
        assertEquals("#", AppListRules.section("123"))
        assertEquals("#", AppListRules.section(""))
        assertEquals(listOf("A", "Z", "А", "Я", "#"), listOf("Я", "Z", "А", "A", "#").sortedBy(AppListRules::sectionOrder))
    }
    @Test fun `favorites can move without duplication and stop at boundaries`() {
        assertEquals(listOf("b", "a", "c"), AppListRules.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "c", "b"), AppListRules.move(listOf("a", "b", "c"), "b", 1))
        assertEquals(listOf("a", "b"), AppListRules.move(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), AppListRules.move(listOf("a", "b"), "unknown", 1))
    }

    @Test fun `number and symbol section follows every alphabet section`() {
        val letters = ('A'..'Z') + ('А'..'Я')
        for (letter in letters) {
            assertTrue("# must follow $letter as it does on the rail",
                AppListRules.sectionOrder("#") > AppListRules.sectionOrder(letter.toString()))
        }
    }
}
