package com.audiophile.musicplayer.ui

/**
 * Reusable multi-select helper for list/grid management screens.
 */
class MultiSelectState<T>(
    initialItems: List<T> = emptyList()
) {
    private val selected = linkedSetOf<T>()
    private var items: List<T> = initialItems

    fun updateItems(newItems: List<T>) {
        items = newItems
        selected.retainAll(newItems.toSet())
    }

    fun toggle(item: T) {
        if (!selected.add(item)) selected.remove(item)
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(items)
    }

    fun clear() {
        selected.clear()
    }

    fun isSelected(item: T): Boolean = item in selected
    fun selectedCount(): Int = selected.size
    fun selectedItems(): List<T> = selected.toList()
}
