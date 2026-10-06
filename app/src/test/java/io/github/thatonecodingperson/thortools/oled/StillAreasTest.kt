package io.github.thatonecodingperson.thortools.oled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StillAreasTest {
    private fun grey(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** A 64 x 32 screen whose background changes with [frame], with a still badge of [badge] at x 8..23, y 8..15. */
    private fun frame(frame: Int, badge: Int? = grey(230)): Sample {
        val pixels = IntArray(64 * 32) { i ->
            val x = i % 64
            val y = i / 64
            if (badge != null && x in 8..23 && y in 8..15) badge else grey((x * 3 + y * 5 + frame * 40) % 200)
        }
        return Sample(64, 32, pixels)
    }

    /** The blocks (4 px) a badge at x 8..23, y 8..15 covers on a 16 x 8 block grid. */
    private val badgeBlocks = (2..3).flatMap { row -> (2..5).map { column -> row * 16 + column } }.toSet()

    private fun protectedBlocks(areas: StillAreas): Set<Int> = areas.mask().withIndex().filter { it.value }.map { it.index }.toSet()

    private fun run(areas: StillAreas, steps: IntRange, stillMs: Long = 8_000, badge: (Int) -> Int? = { grey(230) }) {
        for (step in steps) areas.update(frame(step, badge(step)), now = step * 2_000L, stillMs = stillMs)
    }

    @Test
    fun `exactly the blocks of a bright part that stays put are protected, once still long enough`() {
        val areas = StillAreas()
        run(areas, 0..3)
        assertEquals("not still long enough yet", emptySet<Int>(), protectedBlocks(areas))
        run(areas, 4..5)
        assertEquals(badgeBlocks, protectedBlocks(areas))
        assertEquals(badgeBlocks.size * 100 / (16 * 8), areas.protectedPercent())
        assertEquals(listOf(Box(8, 8, 24, 16)), areas.components())
    }

    @Test
    fun `a part is dimmed evenly as dark as its brightest block, with a half-dim ring around it`() {
        val areas = StillAreas()
        run(areas, 0..5)
        val alphas = areas.dimAlphas(full = 100)
        val ring = (1..4).flatMap { row -> (1..6).map { column -> row * 16 + column } }.toSet() - badgeBlocks
        val expected = (100 * 230 / 255f).toInt()
        alphas.forEachIndexed { index, alpha ->
            val want = when (index) {
                in badgeBlocks -> expected
                in ring -> expected / 2
                else -> 0
            }
            assertEquals("block $index", want, alpha)
        }
    }

    @Test
    fun `protection holds through a short change and goes when the change keeps on`() {
        val areas = StillAreas()
        run(areas, 0..5)
        // The badge changes once and changes back, like a number updating.
        run(areas, 6..10) { step -> if (step == 6) grey(120) else grey(230) }
        assertEquals(badgeBlocks, protectedBlocks(areas))
        // Now it keeps changing: let go after RELEASE_MS.
        run(areas, 11..12) { step -> grey(100 + step * 10) }
        assertEquals("still held within the grace time", badgeBlocks, protectedBlocks(areas))
        run(areas, 13..14) { step -> grey(100 + step * 10) }
        assertEquals(emptySet<Int>(), protectedBlocks(areas))
    }

    @Test
    fun `a part that goes dark is let go`() {
        val areas = StillAreas()
        run(areas, 0..5)
        run(areas, 6..9) { grey(10) }
        assertEquals(emptySet<Int>(), protectedBlocks(areas))
    }

    @Test
    fun `a pure blue part counts, and dimming follows brightness`() {
        val blue = StillAreas()
        run(blue, 0..5) { 0xFF0000FF.toInt() }
        assertEquals(badgeBlocks, protectedBlocks(blue))
        assertEquals(1f, blue.strength(badgeBlocks.first()), 0.01f)

        val grey = StillAreas()
        run(grey, 0..5) { grey(128) }
        assertEquals(128 / 255f, grey.strength(badgeBlocks.first()), 0.01f)
    }

    @Test
    fun `a still screen has no areas, that is the shifter's and refresher's job`() {
        val still = StillAreas()
        for (step in 0..5) still.update(frame(0), now = step * 2_000L, stillMs = 8_000)
        assertEquals(emptySet<Int>(), protectedBlocks(still))
        assertEquals(0, still.protectedPercent())
        assertEquals(emptyList<Box>(), still.components())
    }

    @Test
    fun `a patch learns which of its still pixels changed`() {
        val areas = StillAreas()
        run(areas, 0..5)
        val badge = areas.stillPixels(Box(8, 8, 24, 16), now = 10_000, stillMs = 8_000)
        assertEquals(16 * 8, badge.size)
        assertFalse(areas.anyChanged(badge, after = 10_000))
        areas.update(frame(6, grey(120)), now = 12_000, stillMs = 8_000)
        assertTrue(areas.anyChanged(badge, after = 10_000))
    }

    @Test
    fun `only the still pixels move, their old places go black, the rest stays see-through`() {
        // 4 x 1: a still bright pixel at x 1, a changing one at x 2.
        val previous = intArrayOf(grey(0), grey(200), grey(10), grey(0))
        val current = intArrayOf(grey(0), grey(200), grey(90), grey(0))
        val inner = Box(1, 0, 3, 1)
        val out = AreaPatch.build(current, previous, 4, 1, inner, Offset(1, 0))!!
        assertEquals(0, out[0])
        assertEquals("the old place of the still pixel", 0xFF000000.toInt(), out[1])
        assertEquals("the still pixel, one to the right", grey(200), out[2])
        assertEquals(0, out[3])
        assertNull(AreaPatch.build(current, IntArray(3), 4, 1, inner, Offset(1, 0)))
    }

    @Test
    fun `colour changes count, the strongest channel decides brightness`() {
        assertEquals(255, StillAreas.strongest(0xFF0000FF.toInt()))
        assertEquals(255, StillAreas.difference(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
        assertEquals(0, StillAreas.difference(grey(40), grey(40)))
    }

    @Test
    fun `still areas travel to the engine and back`() {
        val config = EngineConfig(areaDisplays = setOf(4), areaStillMs = 60_000, areaDim = 30, areaShift = 2, areaEveryMs = 20_000)
        assertEquals(config, EngineConfig.decode(config.encode()))
        assertFalse(config.idle)
        assertEquals(EngineConfig.MAX_DIM, EngineConfig.decode("areas=4;adim=500").areaDim)
    }
}
