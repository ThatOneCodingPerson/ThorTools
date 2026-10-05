package io.github.thatonecodingperson.thortools.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreWidgetsTest {

    @Test
    fun `the stopwatch adds up its running times`() {
        var watch = PanelTimer().start(1_000)
        assertTrue(watch.running)
        watch = watch.pause(4_000)
        assertEquals(3_000, watch.elapsed(10_000))
        watch = watch.start(20_000)
        assertEquals(5_000, watch.elapsed(22_000))
        assertNull(watch.remaining(22_000))
        assertEquals(0, watch.reset().elapsed(30_000))
    }

    @Test
    fun `the timer counts down, says when it ends, and won't start at zero`() {
        var timer = PanelTimer(countdownMs = 60_000).start(0)
        assertEquals(60_000L, timer.endsAt())
        assertEquals(45_000L, timer.remaining(15_000))
        timer = timer.pause(15_000)
        assertNull(timer.endsAt())
        timer = timer.start(100_000)
        assertEquals(145_000L, timer.endsAt())
        assertEquals(0L, timer.remaining(200_000))
        val done = timer.pause(200_000)
        assertFalse(done.start(300_000).running)
        assertEquals("01:00", PanelTimer.format(60_000))
        assertEquals("1:00:01", PanelTimer.format(3_600_500))
    }

    @Test
    fun `play time shows minutes and seconds, hours when there are any`() {
        assertEquals("0:05", PlayTime.format(5_400))
        assertEquals("12:34", PlayTime.format((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", PlayTime.format(3_723_000))
    }

    @Test
    fun `a break reminder comes once per interval`() {
        assertNull(PlayTime.reminderDue(playedMs = 59 * 60_000L, remindMinutes = 60, shown = 0))
        assertEquals(1, PlayTime.reminderDue(playedMs = 61 * 60_000L, remindMinutes = 60, shown = 0))
        assertNull(PlayTime.reminderDue(playedMs = 61 * 60_000L, remindMinutes = 60, shown = 1))
        assertEquals(2, PlayTime.reminderDue(playedMs = 121 * 60_000L, remindMinutes = 60, shown = 1))
        assertNull(PlayTime.reminderDue(playedMs = 500 * 60_000L, remindMinutes = 0, shown = 0))
    }

    @Test
    fun `ping output gives the round trip in milliseconds`() {
        assertEquals(12.3f, PingParser.millis("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms"))
        assertEquals(1f, PingParser.millis("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time<1 ms"))
        assertNull(PingParser.millis("1 packets transmitted, 0 received, 100% packet loss"))
        assertNull(PingParser.millis(null))
    }

    @Test
    fun `new widgets keep their options when stored`() {
        val page = PanelPage(
            widgets = listOf(
                PanelWidget(WidgetType.PLAY_TIMER, remind = 60),
                PanelWidget(WidgetType.RECENT, appsOn = io.github.thatonecodingperson.thortools.hotkeys.LaunchScreen.BOTTOM),
                PanelWidget(WidgetType.TOGGLES),
            ),
        )
        val layout = PanelLayout(listOf(page))
        assertEquals(layout, PanelLayout.decode(layout.encode()))
        assertEquals(
            0,
            PanelLayout.decode("columns=4;tiles=;w=playtime:2x1;remind=45").pages.single().widget(WidgetType.PLAY_TIMER)?.remind,
        )
    }

    @Test
    fun `recent apps put the newest first, once each, and keep twelve`() {
        assertEquals(listOf("b", "a", "c"), RecentApps.opened(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("x"), RecentApps.opened(emptyList(), "x"))
        val full = (1..RecentApps.KEEP).map { "app$it" }
        val next = RecentApps.opened(full, "new")
        assertEquals(RecentApps.KEEP, next.size)
        assertEquals("new", next.first())
        assertEquals("app${RecentApps.KEEP - 1}", next.last())
    }
}
