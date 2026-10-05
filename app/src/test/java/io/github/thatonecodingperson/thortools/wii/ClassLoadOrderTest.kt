package io.github.thatonecodingperson.thortools.wii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggestions must come out whole whichever Wii class the app happens to load first: the builder screen touches
 * [WiiControl] before [WiiSetup], a test usually the other way round. Each order gets its own fresh copy of the
 * package's classes.
 */
class ClassLoadOrderTest {
    /** Defines this package's classes anew from the class path; everything else comes from [parent]. */
    private class FreshWiiClasses(parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("$PACKAGE.")) return super.loadClass(name, resolve)
            synchronized(this) {
                findLoadedClass(name)?.let { return it }
                val bytes = checkNotNull(parent.getResourceAsStream(name.replace('.', '/') + ".class")) { name }
                    .use { it.readBytes() }
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
    }

    /** Each tab's suggested button and combo controls by name (null for a control that wasn't there yet). */
    private fun suggestions(first: String): Map<String, Pair<Set<String?>, Set<String?>>> {
        val loader = FreshWiiClasses(checkNotNull(javaClass.classLoader))
        Class.forName("$PACKAGE.$first", true, loader)
        val setup = Class.forName("$PACKAGE.WiiSetup", true, loader)
        fun names(map: Any?) = (map as Map<*, *>).keys.map { (it as Enum<*>?)?.name }.toSet()
        return setup.enumConstants.associate { entry ->
            (entry as Enum<*>).name to
                (names(setup.getMethod("getSuggestedButtons").invoke(entry)) to names(setup.getMethod("getSuggestedCombos").invoke(entry)))
        }
    }

    @Test
    fun `the Wii Remote tabs suggest Shake and both shortcuts whichever class loads first`() {
        listOf("WiiControl", "WiiSetup", "WiiControlKt").forEach { first ->
            val tabs = suggestions(first)
            listOf("NUNCHUK", "SIDEWAYS", "POINTING").forEach { tab ->
                val (buttons, combos) = tabs.getValue(tab)
                assertTrue("$first first: $tab has a control that didn't exist yet: $buttons", null !in buttons)
                assertTrue("$first first: $tab lost Shake: $buttons", "SHAKE" in buttons)
                assertEquals("$first first: $tab shortcuts", setOf("SIDEWAYS_TOGGLE", "UPRIGHT_TOGGLE"), combos)
            }
        }
    }

    private companion object {
        const val PACKAGE = "io.github.thatonecodingperson.thortools.wii"
    }
}
