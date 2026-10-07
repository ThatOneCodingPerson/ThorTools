package io.github.thatonecodingperson.thortools.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsQueueTest {
    @Test
    fun `three requests waiting with nothing under way is stuck`() {
        val dump = """
            OverviewCommandHelper:
              mPendingCommands=3
                pendingCommandType=4
              isRecentsAnimationRunning=false
        """.trimIndent()
        val queue = RecentsQueue.parse(dump)
        assertEquals(RecentsQueue(pending = 3, animating = false), queue)
        assertTrue(queue!!.stuck)
    }

    @Test
    fun `a request under way or an empty queue is not stuck`() {
        assertFalse(RecentsQueue.parse("mPendingCommands=1\nisRecentsAnimationRunning=true")!!.stuck)
        assertFalse(RecentsQueue.parse("mPendingCommands=0\nisRecentsAnimationRunning=false")!!.stuck)
    }

    @Test
    fun `no dump, or another provider without these lines, is unknown`() {
        assertNull(RecentsQueue.parse(null))
        assertNull(RecentsQueue.parse("Can't find service"))
    }
}
