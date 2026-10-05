package io.github.thatonecodingperson.thortools.panel

import io.github.thatonecodingperson.thortools.panel.LaunchCheck.Next
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchCheckTest {
    @Test
    fun `showing on the top screen is done`() {
        assertEquals(Next.DONE, LaunchCheck.next(attempt = 1, shownOn = 0, target = 0))
        assertEquals(Next.DONE, LaunchCheck.next(attempt = LaunchCheck.MAX_ATTEMPTS, shownOn = 0, target = 0))
    }

    @Test
    fun `showing on the other screen moves it over`() {
        assertEquals(Next.MOVE, LaunchCheck.next(attempt = 1, shownOn = 2, target = 0))
    }

    @Test
    fun `not showing anywhere starts it again`() {
        assertEquals(Next.START_AGAIN, LaunchCheck.next(attempt = 1, shownOn = null, target = 0))
        assertEquals(Next.START_AGAIN, LaunchCheck.next(attempt = 2, shownOn = null, target = 0))
    }

    @Test
    fun `it gives up after the last attempt`() {
        assertEquals(Next.GIVE_UP, LaunchCheck.next(attempt = LaunchCheck.MAX_ATTEMPTS, shownOn = null, target = 0))
        assertEquals(Next.GIVE_UP, LaunchCheck.next(attempt = LaunchCheck.MAX_ATTEMPTS, shownOn = 2, target = 0))
    }
}
