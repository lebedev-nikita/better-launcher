package app.better.launcher

import java.text.Collator

/** Prepare labels and ordering once, then filter without sorting or normalizing each app again. */
class AppSearchIndex<T>(items: List<T>, label: (T) -> String, originalLabel: (T) -> String) {
    private data class Entry<T>(val item: T, val label: String, val normalized: String, val original: String, val section: String)
    private val entries: List<Entry<T>>
    val apps: List<T>
    val sections: Set<String>

    init {
        val collator = Collator.getInstance()
        entries = items.map { item ->
            val name = label(item)
            Entry(item, name, AppListRules.normalized(name), AppListRules.normalized(originalLabel(item)), AppListRules.section(name))
        }.sortedWith(compareBy<Entry<T>> { AppListRules.sectionOrder(it.section) }
            .thenComparator { a, b -> collator.compare(a.label, b.label) })
        apps = entries.map { it.item }
        sections = entries.map { it.section }.toSet()
    }

    fun matching(query: String): List<T> {
        val terms = AppListRules.terms(query)
        if (terms.isEmpty()) return apps
        return entries.filter { entry -> terms.all { entry.normalized.contains(it) || entry.original.contains(it) } }.map { it.item }
    }
}
