package com.ella.music.player

internal object OPlusLyricPublishPolicy {
    const val COMPAT_REAPPLY_DELAY_MS = 800L
    val COMPAT_REAPPLY_DELAYS_MS = longArrayOf(COMPAT_REAPPLY_DELAY_MS)

    fun actionFor(
        currentLyricInfo: String?,
        currentRawLyric: String?,
        targetLyricInfo: String?,
        targetRawLyric: String?
    ): OPlusLyricPublishAction {
        return if (targetLyricInfo.isNullOrBlank()) {
            if (currentLyricInfo != null || currentRawLyric != null) {
                OPlusLyricPublishAction.Clear
            } else {
                OPlusLyricPublishAction.None
            }
        } else if (currentLyricInfo == targetLyricInfo && currentRawLyric == targetRawLyric) {
            OPlusLyricPublishAction.None
        } else {
            OPlusLyricPublishAction.Write
        }
    }
}

internal enum class OPlusLyricPublishAction {
    None,
    Clear,
    Write
}
