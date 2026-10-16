package io.github.thatonecodingperson.thortools.roms

/**
 * What the bundled z3ds tool says about a 3DS file: its [type] (`NCSD` a card image, `NCCH` a `.cxi`, `Z3DS` a
 * compressed one, `?` anything else), a Z3DS file's [inner] type, whether a plain ROM is [encrypted] (null: not
 * known), the ROM's [size] (unpacked, for Z3DS) and [extra]: where a plain ROM's data ends, or a Z3DS file's
 * compressed size.
 */
data class CardInfo(val type: String, val inner: String?, val encrypted: Boolean?, val size: Long, val extra: Long) {
    val packed: Boolean get() = type == N3ds.Z3DS_TYPE

    /** A ROM the tool compresses: a card image or a `.cxi`. */
    val rom: Boolean get() = type == N3ds.CARD || type == N3ds.CXI
}

/** The Nintendo 3DS: its emulators, its files and the names they get. */
object N3ds {
    const val CARD = "NCSD"
    const val CXI = "NCCH"
    const val Z3DS_TYPE = "Z3DS"

    /** Plain ROMs Thor Tools compresses: card images (`.3ds`, `.cci`) and `.cxi`; never CIAs, which are installed. */
    val ROM_TYPES = listOf("3ds", "cci", "cxi")

    /** Compressed ROMs: Z3DS of a card image and of a `.cxi`. */
    val PACKED_TYPES = mapOf("zcci" to RomFormat.Z3DS, "zcxi" to RomFormat.Z3DS)

    /** Folders of Azahar's own data (its system memory and SD card) that may sit among the games: never games. */
    private val DATA_FOLDERS = setOf("nand", "sdmc")

    private fun z3ds(support: Support, since: String? = null) =
        listOf(Reads(RomFormat.Z3DS, support, since), Reads(RomFormat.ROM_FILE, Support.DOCUMENTED))

    val EMULATORS = listOf(
        RomEmulator("Azahar", listOf("org.azahar_emu.azahar", "org.azahar_emu.azahar.debug"), z3ds(Support.DOCUMENTED, "2123")),
        RomEmulator("AzaharPlus", listOf("io.github.azaharplus.android"), z3ds(Support.DOCUMENTED)),
        RomEmulator("Lime3DS", listOf("io.github.lime3ds.android"), z3ds(Support.DOCUMENTED, "2123")),
        RomEmulator("Citra", listOf("org.citra.citra_emu", "org.citra.citra_emu.canary"), z3ds(Support.NO)),
        RomEmulator("Citra MMJ", listOf("org.citra.emu"), z3ds(Support.NO)),
        RomEmulator("Mandarine", listOf("io.github.mandarine3ds.mandarine"), z3ds(Support.NO)),
        RomEmulator("Panda3DS", listOf("com.panda3ds.pandroid"), z3ds(Support.NO)),
    )

    val CORES = listOf(
        RomCore("Azahar", listOf("azahar_libretro"), z3ds(Support.DOCUMENTED)),
        RomCore("Citra", listOf("citra_libretro", "citra2018_libretro", "citra_canary_libretro"), z3ds(Support.NO)),
    )

    /** True for a file inside Azahar's data folders. */
    fun inEmulatorData(path: String): Boolean = path.lowercase().split('/').dropLast(1).any { it in DATA_FOLDERS }

    /** The extension a ROM named [rom] gets compressed: `.zcxi` for a `.cxi`, else `.zcci`. */
    fun packedExtension(rom: String?): String = if (rom?.lowercase()?.endsWith(".cxi") == true) "zcxi" else "zcci"

    /** The ROM a compressed file named [title] holds, by its [inner] type: `<title>.cxi`, else `<title>.cci`. */
    fun romName(title: String, inner: String?): String = "$title.${if (inner == CXI) "cxi" else "cci"}"

    /**
     * Reads `z3ds info`'s lines (type, inner type, crypto, size, extra, path; tab-separated) into what each says, by
     * path.
     */
    fun parseInfo(output: String): Map<String, CardInfo> = output.lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 6)
        if (parts.size < 6 || parts[5].isEmpty()) return@mapNotNull null
        parts[5] to CardInfo(
            type = parts[0],
            inner = parts[1].takeIf { it != "-" },
            encrypted = when (parts[2]) {
                "encrypted" -> true
                "decrypted" -> false
                else -> null
            },
            size = parts[3].toLongOrNull() ?: 0L,
            extra = parts[4].toLongOrNull() ?: 0L,
        )
    }.toMap()

    /** [info] as one line, the way [decodeInfo] reads it back. */
    fun encodeInfo(info: CardInfo?): String =
        if (info == null) "-" else listOf(info.type, info.inner ?: "-", crypto(info.encrypted), info.size, info.extra).joinToString("\t")

    fun decodeInfo(text: String): CardInfo? {
        if (text == "-") return null
        val parts = text.split('\t')
        if (parts.size < 5) return null
        return parseInfo((parts.take(5) + "x").joinToString("\t"))["x"]
    }

    private fun crypto(encrypted: Boolean?) = when (encrypted) {
        true -> "encrypted"
        false -> "decrypted"
        null -> "-"
    }
}
