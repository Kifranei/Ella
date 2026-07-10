package com.ella.music.viewmodel

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.parseNameSplitSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchNameSplitConfigObservers(
    settingsManager: SettingsManager,
    onConfigChanged: () -> Unit
) {
    launch {
        settingsManager.artistSeparators.collect {
            NameSplitConfigStore.artistCustomSeparators = parseNameSplitSetting(it)
            onConfigChanged()
        }
    }
    launch {
        settingsManager.artistProtectedNames.collect {
            NameSplitConfigStore.artistProtectedNames = parseNameSplitSetting(it)
            onConfigChanged()
        }
    }
    launch {
        settingsManager.genreSeparators.collect {
            NameSplitConfigStore.genreCustomSeparators = parseNameSplitSetting(it)
            onConfigChanged()
        }
    }
    launch {
        settingsManager.genreProtectedNames.collect {
            NameSplitConfigStore.genreProtectedNames = parseNameSplitSetting(it)
            onConfigChanged()
        }
    }
    launch {
        settingsManager.tagIgnoreCase.collect {
            NameSplitConfigStore.tagIgnoreCase = it
            onConfigChanged()
        }
    }
}
