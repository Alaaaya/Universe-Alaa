package com.alaa.universe

/** Immutable, worker-built catalogue. Normal page redraws only take a bounded subList. */
internal class BrowseIndex(snapshot: ProviderSnapshot, allows: (MediaEntry) -> Boolean, categoryAllows: (MediaCategory) -> Boolean = { true }) {
    val all: List<MediaEntry> = snapshot.entries.filter(allows)
    private val kinds = all.groupBy { it.kind }
    // Preserve primary-category semantics; categoryIds is ownership for security, not a UI duplicate.
    private val groups = all.groupBy { it.kind to it.categoryId }
    val categories = snapshot.categories.filter(categoryAllows).groupBy { it.kind }
    val home = all.asSequence().filter { it.kind=="movie" || it.kind=="series" }.take(6).toList()
    fun count(kind: String) = kinds[kind].orEmpty().size
    fun select(kind: String, category: String, favorites: Set<String>, recents: Set<String>): List<MediaEntry> =
        when(category) {
            "" -> kinds[kind].orEmpty()
            "favorites", "recent" -> {
                val ids = if(category == "favorites") favorites else recents
                kinds[kind].orEmpty().filter { it.kind+":"+it.id in ids }
            }
            else -> groups[kind to category].orEmpty()
        }
    companion object {
        fun <T> page(list: List<T>, offset: Int, count: Int): List<T> {
            val start = offset.coerceIn(0, list.size)
            return list.subList(start, (start + count).coerceAtMost(list.size))
        }
        fun ordered(values: List<MediaEntry>, ids: List<String>): List<MediaEntry> {
            val byId = values.associateBy { it.id }
            return ids.mapNotNull { byId[it] }
        }
    }
}
