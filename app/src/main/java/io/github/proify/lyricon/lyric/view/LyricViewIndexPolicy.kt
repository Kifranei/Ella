package io.github.proify.lyricon.lyric.view

internal data class LyricViewLineWindow(
    val begin: Long,
    val end: Long
)

private const val POSITION_JITTER_TOLERANCE_MS = 32L

internal fun previewLyricViewIndexAt(
    effectivePositionMs: Long,
    lines: List<LyricViewLineWindow>
): Int {
    if (lines.isEmpty()) return -1
    // 选择当前 active 行中 begin 最大（最晚开始）的那一行。
    //
    // 对重叠/嵌套对唱歌词（TTML 多 ttm:agent，如 v1/v1000），同一时刻可能有多行同时
    // active：外层长行 ⊃ 内层短行。"最晚开始"对应"当前主唱的最新一行"，与
    // LyricView.resolveActiveHighlightIndices 的高亮集合保持一致，且在嵌套内层行结束后
    // 自然回到仍 active 的外层行，避免 currentIndex 与高亮行错位导致 scrollToCurrentLine
    // 锚定到已结束的内层行而反复闪烁。
    val active = pickActiveLineIndex(effectivePositionMs, lines)
    if (active >= 0) return active
    // 间奏段（无 active 行）：返回最后一个 begin<=pos 的行，停留在刚唱完的行上。
    for (index in lines.indices.reversed()) {
        if (lines[index].begin <= effectivePositionMs) return index
    }
    return -1
}

internal fun resolveLyricViewIndex(
    positionMs: Long,
    previousPositionMs: Long,
    currentIndex: Int,
    lines: List<LyricViewLineWindow>
): Int {
    if (lines.isEmpty()) return -1

    val monotonicPlayback = positionMs + POSITION_JITTER_TOLERANCE_MS >= previousPositionMs
    if (monotonicPlayback && currentIndex in lines.indices) {
        val active = pickActiveLineIndex(positionMs, lines)
        if (active >= 0) {
            // 与 Apple Music 一样，滚动/高亮/翻译在真正进入下一句时一起切换。
            return active
        }

        // 无 active 行（间奏段）：保留原有"从 currentIndex 单调推进到下一行 begin 附近"的停留逻辑，
        // 避免间奏期 currentIndex 在相邻行之间反复跳动。
        var candidate = currentIndex
        while (candidate + 1 < lines.size) {
            val nextBegin = lines[candidate + 1].begin
            if (positionMs < nextBegin) break
            candidate++
        }
        return candidate
    }

    return previewLyricViewIndexAt(positionMs, lines)
}

/**
 * 在所有 `begin <= positionMs < end` 的行中，返回 begin 最大（最晚开始）的那一行；
 * 平手时取 index 更大者（更靠近列表尾部的行）。无 active 行时返回 -1。
 *
 * 选择"最晚开始"而非"index 最大"是因为歌词行的列表顺序（TTML 出现顺序）不一定与
 * begin 时间顺序一致——多人对唱交错时，key 靠后的行 begin 可能更早。按 begin 选择
 * 才能稳定地指向"当前正在唱的最新一行"。
 */
private fun pickActiveLineIndex(
    positionMs: Long,
    lines: List<LyricViewLineWindow>
): Int {
    var bestActive = -1
    var bestBegin = Long.MIN_VALUE
    for (index in lines.indices) {
        val w = lines[index]
        if (positionMs >= w.begin && positionMs < w.end) {
            if (w.begin > bestBegin || (w.begin == bestBegin && index > bestActive)) {
                bestBegin = w.begin
                bestActive = index
            }
        }
    }
    return bestActive
}
