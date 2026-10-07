package io.github.thatonecodingperson.thortools.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiosPathsTest {
    private val storage = "com.android.externalstorage.documents"

    @Test
    fun `a folder from the picker is the path the Files app shows`() {
        assertEquals("/storage/emulated/0/Download/bios", BiosPaths.fromTree(storage, "primary:Download/bios"))
        assertEquals("/storage/emulated/0", BiosPaths.fromTree(storage, "primary:"))
        assertEquals("/storage/emulated/0", BiosPaths.fromTree(storage, "primary"))
        assertEquals("/storage/EBBF-779E/AssetsEmu/bios", BiosPaths.fromTree(storage, "EBBF-779E:AssetsEmu/bios"))
        assertEquals("/storage/EBBF-779E/Milo's BIOS", BiosPaths.fromTree(storage, "EBBF-779E:Milo's BIOS/"))
        assertEquals("/storage/emulated/0/Documents/BIOS", BiosPaths.fromTree(storage, "home:BIOS"))
        val downloads = "com.android.providers.downloads.documents"
        assertEquals("/storage/emulated/0/Download/bios", BiosPaths.fromTree(downloads, "raw:/storage/emulated/0/Download/bios"))
    }

    @Test
    fun `a folder of another app's provider has no path`() {
        assertNull(BiosPaths.fromTree("com.android.providers.downloads.documents", "msf:12"))
        assertNull(BiosPaths.fromTree("com.retroarch.aarch64.documents", "/data/user/0/com.retroarch.aarch64"))
        assertNull(BiosPaths.fromTree(null, "primary:Download"))
        assertNull(BiosPaths.fromTree(storage, "weird volume:bios"))
    }

    @Test
    fun `root reaches the shared storage at data media and an SD card at media_rw too`() {
        assertEquals(
            listOf("/data/media/0/Download/bios", "/storage/emulated/0/Download/bios"),
            BiosPaths.rootForms("/storage/emulated/0/Download/bios/"),
        )
        assertEquals(listOf("/data/media/0/bios", "/storage/emulated/0/bios"), BiosPaths.rootForms("/sdcard/bios"))
        assertEquals(
            listOf("/storage/EBBF-779E/AssetsEmu/bios", "/mnt/media_rw/EBBF-779E/AssetsEmu/bios"),
            BiosPaths.rootForms("/storage/EBBF-779E/AssetsEmu/bios"),
        )
        assertEquals(listOf("/storage/self/primary/bios"), BiosPaths.rootForms("/storage/self/primary/bios"))
        assertEquals(listOf("/data/user/0/x"), BiosPaths.rootForms("/data/user/0/x"))
    }

    @Test
    fun `a path root reached is shown the way the Files app shows it`() {
        assertEquals("/storage/emulated/0/Android/media/x/system", BiosPaths.shown("/data/media/0/Android/media/x/system"))
        assertEquals("/storage/emulated/0/bios", BiosPaths.shown("/sdcard/bios/"))
        assertEquals("/storage/EBBF-779E/bios", BiosPaths.shown("/mnt/media_rw/EBBF-779E/bios"))
        assertEquals("/storage/EBBF-779E/bios", BiosPaths.shown("/storage/EBBF-779E/bios"))
    }

    @Test
    fun `inside a folder means inside it however root reached either`() {
        val system = "/storage/emulated/0/Android/media/x/RetroArch/system"
        assertTrue(BiosPaths.isUnder("/data/media/0/Android/media/x/RetroArch/system/scph5501.bin", system))
        assertTrue(BiosPaths.isUnder("/data/media/0/Android/media/x/RetroArch/system", system))
        assertFalse(BiosPaths.isUnder("/storage/emulated/0/Android/media/x/RetroArch/system2/a.bin", system))
    }

    @Test
    fun `a folder inside another listed one and repeats are left out`() {
        assertEquals(
            listOf("/storage/EBBF-779E/bios", "/storage/EBBF-779E/AssetsEmu/bios"),
            BiosPaths.withoutNested(
                listOf(
                    "/storage/EBBF-779E/bios",
                    "/storage/EBBF-779E/bios/GBA",
                    "/storage/EBBF-779E/AssetsEmu/bios",
                    "/storage/EBBF-779E/bios/",
                ),
            ),
        )
    }

    @Test
    fun `the BIOS folder named in RetroArch's settings counts only as a full path`() {
        assertNull(BiosPaths.configured("default"))
        assertNull(BiosPaths.configured(""))
        assertNull(BiosPaths.configured(null))
        assertNull(BiosPaths.configured("/"))
        assertEquals(
            "/storage/3839-3463/bios/EmuDeck_2.3.8_BIOS_Pack",
            BiosPaths.configured("/storage/3839-3463/bios/EmuDeck_2.3.8_BIOS_Pack/"),
        )
    }

    @Test
    fun `RetroArch's own system folder lies next to its cheats folder, else in its media folder`() {
        val media = "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch"
        assertEquals(listOf("$media/system"), BiosPaths.ownSystemFolders("$media/cheats", "com.retroarch.aarch64"))
        assertEquals(listOf("$media/system"), BiosPaths.ownSystemFolders("default", "com.retroarch.aarch64"))
        assertEquals(
            listOf("/storage/emulated/0/RetroArch/system", "$media/system"),
            BiosPaths.ownSystemFolders("/storage/emulated/0/RetroArch/cheats", "com.retroarch.aarch64"),
        )
    }

    @Test
    fun `RetroArch's own system folder is offered while the folder its settings name isn't there`() {
        val own = "/storage/emulated/0/Android/media/com.retroarch.aarch64/RetroArch/system"
        val parent = "/data/media/0/Android/media/com.retroarch.aarch64/RetroArch"
        val stale = BiosTarget("/storage/3839-3463/bios/EmuDeck_2.3.8_BIOS_Pack", null, own, parent)
        assertTrue(stale.offerOwn)
        assertFalse(stale.there)
        assertNull(stale.copyRoot)
        assertNull(stale.ownerOf)

        val switched = BiosTarget(own, "$parent/system", own, parent)
        assertTrue(switched.there)
        assertFalse(switched.offerOwn)
        assertEquals("$parent/system", switched.copyRoot)
        assertEquals("$parent/system", switched.ownerOf)
        assertFalse(switched.makeRoot)

        val notMadeYet = BiosTarget(own, null, own, parent)
        assertFalse(notMadeYet.offerOwn)
        assertEquals("$parent/system", notMadeYet.copyRoot)
        assertEquals(parent, notMadeYet.ownerOf)
        assertTrue(notMadeYet.makeRoot)

        assertTrue(BiosTarget(null, null, own, parent).offerOwn)
        assertFalse(BiosTarget(null, null, own, null).offerOwn)
    }
}
