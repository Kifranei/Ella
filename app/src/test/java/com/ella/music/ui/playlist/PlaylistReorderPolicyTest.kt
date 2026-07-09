package com.ella.music.ui.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistReorderPolicyTest {
    private data class Entry(val id: String)

    @Test
    fun draggingAnySelectedItemMovesContiguousSelectionBlockDown() {
        val items = entries("1", "2", "3", "4", "5", "6", "7")

        listOf(2, 3, 4).forEach { draggedIndex ->
            val reordered = items.moveSelectedItemsAsBlock(
                from = draggedIndex,
                to = 5,
                selectedKeys = setOf("3", "4", "5"),
                keyOf = Entry::id
            )

            assertEquals(entries("1", "2", "6", "3", "4", "5", "7"), reordered)
        }
    }

    @Test
    fun draggingAnySelectedItemMovesContiguousSelectionBlockUp() {
        val items = entries("1", "2", "3", "4", "5", "6", "7")

        listOf(2, 3, 4).forEach { draggedIndex ->
            val reordered = items.moveSelectedItemsAsBlock(
                from = draggedIndex,
                to = 1,
                selectedKeys = setOf("3", "4", "5"),
                keyOf = Entry::id
            )

            assertEquals(entries("1", "3", "4", "5", "2", "6", "7"), reordered)
        }
    }

    @Test
    fun draggingInsideSelectedBlockDoesNotReorder() {
        val items = entries("1", "2", "3", "4", "5", "6", "7")

        val reordered = items.moveSelectedItemsAsBlock(
            from = 3,
            to = 4,
            selectedKeys = setOf("3", "4", "5"),
            keyOf = Entry::id
        )

        assertEquals(items, reordered)
    }

    @Test
    fun draggingUnselectedItemFallsBackToSingleItemMove() {
        val items = entries("1", "2", "3", "4", "5", "6", "7")

        val reordered = items.moveSelectedItemsAsBlock(
            from = 2,
            to = 3,
            selectedKeys = setOf("4", "5", "6"),
            keyOf = Entry::id
        )

        assertEquals(entries("1", "2", "4", "3", "5", "6", "7"), reordered)
    }

    private fun entries(vararg ids: String): List<Entry> = ids.map(::Entry)
}
