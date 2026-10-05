package io.github.thatonecodingperson.thortools.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSearchTest {

    private val text = mapOf(
        ThorAction.SWAP_SCREENS to "Swap screens. The apps on the two screens trade places",
        ThorAction.TOGGLE_BOTTOM_SCREEN to "Bottom screen on / off. Turns the bottom screen off",
        ThorAction.LOUDER to "Volume up. Turns the media volume up one step",
    )
    private val items = text.keys.toList()

    private fun search(category: ActionCategory?, query: String) = ActionSearch.filter(items, category, query, {
        it.category
    }) { text.getValue(it) }

    @Test
    fun `an empty search shows everything in the category`() {
        assertEquals(items, search(null, ""))
        assertEquals(listOf(ThorAction.SWAP_SCREENS, ThorAction.TOGGLE_BOTTOM_SCREEN), search(ActionCategory.SCREENS, "  "))
    }

    @Test
    fun `every word must match, in any case and order`() {
        assertEquals(listOf(ThorAction.TOGGLE_BOTTOM_SCREEN), search(null, "SCREEN bottom"))
        assertEquals(listOf(ThorAction.LOUDER), search(null, "media"))
        assertEquals(emptyList<ThorAction>(), search(ActionCategory.LEVELS, "swap"))
    }
}
