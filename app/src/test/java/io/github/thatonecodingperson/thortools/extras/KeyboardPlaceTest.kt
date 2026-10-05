package io.github.thatonecodingperson.thortools.extras

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardPlaceTest {
    @Test
    fun `AYN's two settings read as a place`() {
        assertEquals(KeyboardPlace.SAME, KeyboardPlace.from(0, 1))
        assertEquals(KeyboardPlace.SAME, KeyboardPlace.from(null, null))
        assertEquals(KeyboardPlace.TOP, KeyboardPlace.from(1, 0))
        assertEquals(KeyboardPlace.TOP, KeyboardPlace.from(1, null))
        assertEquals(KeyboardPlace.BOTTOM, KeyboardPlace.from(1, 1))
    }

    @Test
    fun `each place writes what reads back as itself`() {
        KeyboardPlace.entries.forEach { place ->
            val (fixed, onSecond) = place.settings()
            assertEquals(place, KeyboardPlace.from(fixed, onSecond ?: 1))
            assertEquals(place, KeyboardPlace.from(fixed, onSecond ?: 0))
        }
    }
}
