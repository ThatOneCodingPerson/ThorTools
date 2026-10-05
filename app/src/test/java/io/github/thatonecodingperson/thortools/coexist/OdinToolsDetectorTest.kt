package io.github.thatonecodingperson.thortools.coexist

import io.github.thatonecodingperson.thortools.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Test

class OdinToolsDetectorTest {

    @Test
    fun `Thor Tools never shares OdinTools' package name`() {
        assertFalse(BuildConfig.APPLICATION_ID.startsWith(OdinToolsDetector.PACKAGE))
    }
}
