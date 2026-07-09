package com.ella.music.ui.playlist

internal fun <T> List<T>.movePlaylistItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply {
        add(to, removeAt(from))
    }
}

internal fun <T, K> List<T>.moveSelectedItemsAsBlock(
    from: Int,
    to: Int,
    selectedKeys: Set<K>,
    keyOf: (T) -> K
): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val draggedItem = this[from]
    val draggedKey = keyOf(draggedItem)
    if (selectedKeys.size <= 1 || draggedKey !in selectedKeys) {
        return movePlaylistItem(from, to)
    }

    val selectedEntries = withIndex().filter { keyOf(it.value) in selectedKeys }
    if (selectedEntries.size <= 1) return movePlaylistItem(from, to)

    val firstSelectedIndex = selectedEntries.first().index
    val lastSelectedIndex = selectedEntries.last().index
    val selectedIndices = selectedEntries.mapTo(mutableSetOf()) { it.index }
    if (to in firstSelectedIndex..lastSelectedIndex && to != from) return this

    val selectedItems = selectedEntries.map { it.value }
    val remainingItems = filterIndexed { index, _ -> index !in selectedIndices }
    val insertionIndex = when {
        to < firstSelectedIndex -> to
        to > lastSelectedIndex -> to - selectedItems.size + 1
        else -> return this
    }.coerceIn(0, remainingItems.size)

    return remainingItems.toMutableList().apply {
        addAll(insertionIndex, selectedItems)
    }
}
