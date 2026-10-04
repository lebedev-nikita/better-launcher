package app.better.launcher

import java.text.Normalizer
import java.util.Locale

object AppListRules {
    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ё', 'е').trim()

    fun section(label: String): String {
        val first = normalized(label).firstOrNull()?.uppercaseChar() ?: return "#"
        return if (first in 'A'..'Z' || first in 'А'..'Я') first.toString() else "#"
    }

    fun matches(query: String, vararg labels: String): Boolean {
        val terms = normalized(query).split(Regex("\\s+")).filter(String::isNotEmpty)
        return terms.all { term -> labels.any { normalized(it).contains(term) } }
    }

    fun sectionOrder(section: String): Int = when (val c = section.first()) {
        '#' -> 0
        in 'A'..'Z' -> 1 + (c - 'A')
        in 'А'..'Я' -> 27 + (c - 'А')
        else -> 100
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
