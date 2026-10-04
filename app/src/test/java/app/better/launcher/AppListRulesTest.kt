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
        assertEquals("Y", AppListRules.section("Ёлка"))
        assertEquals("#", AppListRules.section("123"))
        assertEquals("#", AppListRules.section(""))
        val labels = listOf("Яндекс", "Zoom", "Альбом", "123", "Apple", "Банк")
        assertEquals(listOf("A", "A", "B", "Y", "Z", "#"),
            labels.map(AppListRules::section).sortedBy(AppListRules::sectionOrder))
    }
    @Test fun `Cyrillic sections follow sound rather than letter shape`() {
        listOf("Банк" to "B", "Видео" to "V", "Ноты" to "N", "Радио" to "R",
            "Связь" to "S", "Хром" to "K", "йога" to "Y", "игры" to "I",
            " Экраны" to "E", "почта" to "P").forEach { (label, section) ->
            assertEquals(label, section, AppListRules.section(label))
        }
    }
    @Test fun `library transliteration uses the first letter including multiple letter equivalents`() {
        listOf("Яндекс" to "Y", "яндекс" to "Y", "Юла" to "Y", "Журнал" to "Z",
            "Центр" to "T", "Часы" to "C", "Шахматы" to "S", "Щит" to "S", "Ы" to "Y",
            "И\u0306ога" to "Y").forEach { (label, section) ->
            assertEquals(label, section, AppListRules.section(label))
        }
    }
    @Test fun `numbers symbols and initials without Latin transliteration use the last section`() {
        listOf("123", "!app", "Ъ", "Ь", "", "東京")
            .forEach { assertEquals(it, "#", AppListRules.section(it)) }
        assertTrue(AppListRules.sectionOrder("#") > AppListRules.sectionOrder("Z"))
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
}
