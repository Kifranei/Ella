package com.ella.music.ui.folder

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSortModelsTest {
    @Test
    fun pinnedFoldersStayAheadOfTheSelectedSortOrder() {
        val folders = listOf(
            folder("/music/alpha", "Alpha"),
            folder("/music/beta", "Beta"),
            folder("/music/gamma", "Gamma")
        )

        val sorted = folders.sortedForFolderList(
            mode = FolderListSortMode.Name,
            pinnedPaths = listOf("/MUSIC/GAMMA", "/music/beta")
        )

        assertEquals(listOf("Gamma", "Beta", "Alpha"), sorted.map { it.name })
    }

    @Test
    fun pinsOutsideTheCurrentHierarchyLevelAreIgnored() {
        val folders = listOf(
            folder("/music/beta", "Beta"),
            folder("/music/alpha", "Alpha")
        )

        val sorted = folders.sortedForFolderList(
            mode = FolderListSortMode.Name,
            pinnedPaths = listOf("/other/folder")
        )

        assertEquals(listOf("Alpha", "Beta"), sorted.map { it.name })
    }

    private fun folder(path: String, name: String) = FolderTreeEntry(
        path = path,
        name = name,
        songCount = 1,
        albumCount = 1,
        duration = 1L
    )
}
