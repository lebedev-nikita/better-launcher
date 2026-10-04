package app.better.launcher

import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AppListRules {
    private val marks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")
    private val transliterator by lazy { Transliterator.getInstance("Russian-Latin/BGN") }
    private val cyrillicSections = ConcurrentHashMap<String, String>()

    // ICU loads its rule resources once; AppCatalog calls this from its background loader.
    fun prepareSections() { section("Я") }

    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT).replace('ё', 'е').trim()

    fun section(label: String): String {
        // NFC preserves Й even when a provider supplies a decomposed И + breve.
        val canonical = Normalizer.normalize(label.trim(), Normalizer.Form.NFC)
        val original = canonical.firstOrNull() ?: return "#"
        if (Character.UnicodeScript.of(original.code) != Character.UnicodeScript.CYRILLIC) return latinSection(canonical)
        return cyrillicSections.computeIfAbsent(canonical) { name ->
            // ICU transliterators are mutable; retain one protected instance and cache results.
            synchronized(transliterator) { latinSection(transliterator.transliterate(name)) }
        }
    }

    private fun latinSection(label: String): String {
        val first = normalized(label).firstOrNull()?.uppercaseChar() ?: return "#"
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    fun railLetters(sections: Set<String>): List<String> =
        listOf("★") + ('A'..'Z').map(Char::toString).filter { it in sections } + "#"

    fun matches(query: String, vararg labels: String): Boolean {
        val terms = terms(query)
        return terms.all { term -> labels.any { normalized(it).contains(term) } }
    }

    fun terms(query: String): List<String> = normalized(query).split(whitespace).filter(String::isNotEmpty)

    fun sectionOrder(section: String): Int = when (val c = section.first()) {
        in 'A'..'Z' -> c - 'A'
        else -> 26
    }

    fun move(items: List<String>, id: String, delta: Int): List<String> {
        val result = items.toMutableList()
        val index = result.indexOf(id)
        if (index < 0) return result
        val destination = (index + delta).coerceIn(result.indices)
        result.removeAt(index)
        result.add(destination, id)
        return result
    }
}
