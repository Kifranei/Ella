package io.github.proify.lyricon.lyric.view

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricViewIndexPolicyTest {
    private val lines = listOf(
        LyricViewLineWindow(begin = 0L, end = 4_000L),
        LyricViewLineWindow(begin = 4_500L, end = 7_000L),
        LyricViewLineWindow(begin = 7_200L, end = 9_000L)
    )

    @Test
    fun activeLineDoesNotAdvanceBeforeNextBegin() {
        val beforeNextBegin = resolveLyricViewIndex(
            positionMs = 3_890L,
            previousPositionMs = 3_840L,
            currentIndex = 0,
            lines = lines
        )

        val atNextBegin = resolveLyricViewIndex(
            positionMs = 4_500L,
            previousPositionMs = 4_490L,
            currentIndex = beforeNextBegin,
            lines = lines
        )

        assertEquals(0, beforeNextBegin)
        assertEquals(1, atNextBegin)
    }

    @Test
    fun backwardSeekCanReturnToPreviousLine() {
        val result = resolveLyricViewIndex(
            positionMs = 3_200L,
            previousPositionMs = 3_780L,
            currentIndex = 1,
            lines = lines
        )

        assertEquals(0, result)
    }

    /**
     * 多人对唱/合唱的 TTML 歌词存在时间嵌套重叠：外层长行 ⊃ 内层短行。
     * 回归测试——内层行结束后，currentIndex 必须回到仍 active 的外层行，而不是卡在
     * 已结束的内层行上（否则 currentIndex 与高亮行错位，滚动锚点来回闪烁）。
     */
    @Test
    fun nestedOverlapReturnsToOuterLineWhenInnerEnds() {
        // 外层 A[0,100] ⊃ 内层 B[20,80]，列表顺序 A,B
        val overlap = listOf(
            LyricViewLineWindow(begin = 0L, end = 100L),
            LyricViewLineWindow(begin = 20L, end = 80L)
        )

        // 进入内层：currentIndex 推进到 B（最晚开始的 active 行）
        val inInner = resolveLyricViewIndex(
            positionMs = 50L,
            previousPositionMs = 40L,
            currentIndex = 0,
            lines = overlap
        )
        assertEquals(1, inInner)

        // 内层 B 结束（pos=85 >= 80），外层 A 仍 active：必须回到 A，不能卡在 B
        val afterInner = resolveLyricViewIndex(
            positionMs = 85L,
            previousPositionMs = 82L,
            currentIndex = 1,
            lines = overlap
        )
        assertEquals(0, afterInner)
    }

    /**
     * 三行平行重叠（A 早开始早结束、B 中间、C 晚开始晚结束，列表顺序 A,B,C）：
     * currentIndex 应始终指向"最晚开始且仍 active"的行，与高亮集合一致。
     */
    @Test
    fun parallelOverlapPicksLatestBeginActiveLine() {
        val parallel = listOf(
            LyricViewLineWindow(begin = 0L, end = 60L),    // A
            LyricViewLineWindow(begin = 10L, end = 50L),   // B
            LyricViewLineWindow(begin = 20L, end = 70L)    // C
        )

        // pos=30：三者均 active，最晚开始=C(index2)
        assertEquals(
            2,
            resolveLyricViewIndex(
                positionMs = 30L,
                previousPositionMs = 25L,
                currentIndex = 0,
                lines = parallel
            )
        )

        // pos=55：A、C active（B 已结束），最晚开始=C(index2)
        assertEquals(
            2,
            resolveLyricViewIndex(
                positionMs = 55L,
                previousPositionMs = 52L,
                currentIndex = 2,
                lines = parallel
            )
        )
    }
}
