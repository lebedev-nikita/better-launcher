package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class SystemTransliterationTest {
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

}
