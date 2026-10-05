package app.better.launcher

class FavoritesOrderEditor(activity: MainActivity, onChanged: () -> Unit) {
    val view = OrderEditor(
        activity,
        activity.store.favorites.mapNotNull { id ->
            val label = activity.store.folder(id)?.name ?: activity.catalog.find(id)?.let(activity.catalog::label)
            label?.let { OrderEntry(id, it) }
        },
        "Порядок избранного",
        readOrder = { activity.store.favorites },
        saveOrder = { activity.store.favorites = it },
        onChanged = onChanged,
        onRemove = { activity.store.favorites = activity.store.favorites - it },
    ).view
}
