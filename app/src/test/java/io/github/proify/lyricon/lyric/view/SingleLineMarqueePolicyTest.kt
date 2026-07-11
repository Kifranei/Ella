package io.github.proify.lyricon.lyric.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleLineMarqueePolicyTest {
    @Test
    fun initialHoldKeepsTheFirstCharacterAtTheLeftEdge() {
        val frame = frame(elapsedMs = 899L)

        assertEquals(0f, frame.offsetPx, 0.001f)
    }

    @Test
    fun trailingCopyApproachesFromTheRightAfterTheFirstCopyFinishes() {
        val textWidth = 300f
        val viewportWidth = 200f
        val gap = 48f
        val overflow = textWidth - viewportWidth
        val elapsedAtOverflow = 900L + (overflow / 100f * 1000f).toLong()

        val frame = calculateSingleLineMarqueeFrame(
            elapsedMs = elapsedAtOverflow,
            textWidth = textWidth,
            speedPxPerSecond = 100f,
            gapPx = gap,
            initialHoldMs = 900L
        )
        val trailingCopyStart = -frame.offsetPx + textWidth + gap

        assertTrue(trailingCopyStart >= viewportWidth)
        assertTrue(trailingCopyStart <= viewportWidth + gap + 1f)
    }

    @Test
    fun cycleWrapIsSeamlessBecauseTheTrailingCopyReachesTheStart() {
        val distance = 300f + 48f
        val scrollMs = (distance / 100f * 1000f).toLong()
        val beforeWrap = frame(elapsedMs = 900L + scrollMs - 1L)
        val atWrap = frame(elapsedMs = 900L + scrollMs)

        val trailingStartBeforeWrap = -beforeWrap.offsetPx + distance
        assertTrue(trailingStartBeforeWrap in 0f..1f)
        assertEquals(0f, atWrap.offsetPx, 0.001f)
    }

    private fun frame(elapsedMs: Long) = calculateSingleLineMarqueeFrame(
        elapsedMs = elapsedMs,
        textWidth = 300f,
        speedPxPerSecond = 100f,
        gapPx = 48f,
        initialHoldMs = 900L
    )
}
