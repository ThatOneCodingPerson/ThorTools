package io.github.thatonecodingperson.thortools.actions

/** Narrows a list for the action picker: by category, then by every typed word appearing in the item's text. */
object ActionSearch {
    fun <T> filter(
        items: List<T>,
        category: ActionCategory?,
        query: String,
        categoryOf: (T) -> ActionCategory,
        text: (T) -> String,
    ): List<T> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return items.filter { item ->
            (category == null || categoryOf(item) == category) &&
                text(item).lowercase().let { haystack -> words.all { it in haystack } }
        }
    }
}
