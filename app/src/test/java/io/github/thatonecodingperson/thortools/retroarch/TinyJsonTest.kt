package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TinyJsonTest {
    @Test
    fun `objects, arrays, numbers, booleans and null`() {
        val value = TinyJson.parse("""{"a": [1, 2.5, -3e2], "b": true, "c": false, "d": null, "e": {}, "f": []}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, -300.0), value["a"])
        assertEquals(true, value["b"])
        assertEquals(false, value["c"])
        assertNull(value["d"])
        assertEquals(emptyMap<String, Any?>(), value["e"])
        assertEquals(emptyList<Any?>(), value["f"])
    }

    @Test
    fun `string escapes, unicode ones too`() {
        val value = TinyJson.parse(""""Pokémon \"Gold\"\n\\ \/ \t"""")
        assertEquals("Pokémon \"Gold\"\n\\ / \t", value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `broken JSON is refused`() {
        TinyJson.parse("""{"a": [1, 2}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `trailing text is refused`() {
        TinyJson.parse("""{} x""")
    }
}
