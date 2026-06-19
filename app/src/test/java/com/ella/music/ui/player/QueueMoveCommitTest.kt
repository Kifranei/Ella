package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueMoveCommitTest {
    @Test
    fun validDragHandleMoveCommitsIndices() {
        val move = resolveQueueMoveCommit(
            fromIndex = 1,
            toIndex = 3,
            queueSize = 5
        )

        assertEquals(QueueMoveCommit(1, 3), move)
    }

    @Test
    fun missingDragStartOrTargetDoesNotCommit() {
        assertNull(resolveQueueMoveCommit(null, 2, queueSize = 4))
        assertNull(resolveQueueMoveCommit(2, null, queueSize = 4))
    }

    @Test
    fun unchangedDragPositionDoesNotCommit() {
        assertNull(resolveQueueMoveCommit(2, 2, queueSize = 4))
    }

    @Test
    fun outOfBoundsDragPositionDoesNotCommit() {
        assertNull(resolveQueueMoveCommit(-1, 2, queueSize = 4))
        assertNull(resolveQueueMoveCommit(1, 4, queueSize = 4))
        assertNull(resolveQueueMoveCommit(1, 0, queueSize = 0))
    }
}
