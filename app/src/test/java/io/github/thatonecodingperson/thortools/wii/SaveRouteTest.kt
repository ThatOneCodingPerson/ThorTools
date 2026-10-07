package io.github.thatonecodingperson.thortools.wii

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveRouteTest {
    @Test
    fun `Dolphin's own folder access comes before root`() {
        assertEquals(listOf(SaveRoute.DOLPHIN_ACCESS, SaveRoute.ROOT), SaveRoute.order(access = true, root = true))
        assertEquals(listOf(SaveRoute.ROOT), SaveRoute.order(access = false, root = true))
        assertEquals(listOf(SaveRoute.DOLPHIN_ACCESS), SaveRoute.order(access = true, root = false))
        assertEquals(emptyList<SaveRoute>(), SaveRoute.order(access = false, root = false))
    }

    @Test
    fun `the first route that saved wins, even after one that failed`() {
        val saved = DolphinSave.Saved(SaveRoute.ROOT, listed = false)
        val end = SaveRoute.settle(listOf(DolphinSave.Failed(SaveRoute.DOLPHIN_ACCESS, "gone"), saved), canAskAccess = true)
        assertEquals(SaveEnd.InDolphin(saved), end)
    }

    @Test
    fun `with nothing saved the folder access is asked for when it can be, else it goes by hand`() {
        val failed = listOf(DolphinSave.Failed(SaveRoute.ROOT, "PServer"))
        assertEquals(SaveEnd.NeedsAccess("PServer"), SaveRoute.settle(failed, canAskAccess = true))
        assertEquals(SaveEnd.ByHand("PServer"), SaveRoute.settle(failed, canAskAccess = false))
        assertEquals(SaveEnd.NeedsAccess(null), SaveRoute.settle(emptyList(), canAskAccess = true))
        assertEquals(SaveEnd.ByHand(null), SaveRoute.settle(emptyList(), canAskAccess = false))
    }

    @Test
    fun `every failure is named`() {
        val failed = listOf(DolphinSave.Failed(SaveRoute.DOLPHIN_ACCESS, "gone"), DolphinSave.Failed(SaveRoute.ROOT, "PServer"))
        assertEquals(SaveEnd.ByHand("gone; PServer"), SaveRoute.settle(failed, canAskAccess = false))
    }
}
