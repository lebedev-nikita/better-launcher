package app.better.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

/** ListView creates and recycles only the rows needed for the current viewport. */
class AppListAdapter(
    private val label: (AppEntry) -> String,
    private val appView: (AppEntry, View?) -> View,
    private val headingView: (String, View?) -> View,
) : BaseAdapter() {
    private sealed interface Row {
        data class App(val entry: AppEntry) : Row
        data class Heading(val section: String) : Row
    }
    private var rows = emptyList<Row>()
    private val sections = linkedMapOf<String, Int>()
    private var source: List<AppEntry>? = null
    private var showSections = false

    fun submit(apps: List<AppEntry>, headings: Boolean) {
        if (source === apps && showSections == headings) return
        source = apps; showSections = headings; sections.clear()
        rows = buildList {
            var previous = ""
            apps.forEach { app ->
                if (headings) {
                    val section = AppListRules.section(label(app))
                    if (section != previous) {
                        sections[section] = size
                        add(Row.Heading(section)); previous = section
                    }
                }
                add(Row.App(app))
            }
        }
        notifyDataSetChanged()
    }

    fun sectionPosition(letter: String): Int? {
        val section = sections.keys.firstOrNull { AppListRules.sectionOrder(it) >= AppListRules.sectionOrder(letter) }
            ?: sections.keys.lastOrNull()
        return sections[section]
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int): Any = when (val row = rows[position]) {
        is Row.App -> row.entry
        is Row.Heading -> row.section
    }
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = if (rows[position] is Row.App) 0 else 1
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int) = rows[position] is Row.App
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = rows[position]) {
        is Row.App -> appView(row.entry, convertView)
        is Row.Heading -> headingView(row.section, convertView)
    }
}
