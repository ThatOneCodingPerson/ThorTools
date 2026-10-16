package io.github.thatonecodingperson.thortools.roms

/**
 * What a compressed disc file's first bytes say: its [format] (null when it is a CHD made in a way Thor Tools doesn't
 * make), the size of the image it holds ([imageSize]; 0 when the header doesn't say), the size of the blocks it
 * compresses on its own ([block]) and the kind of disc ([kind], null when it can't be told).
 */
data class PackedHeader(val format: RomFormat?, val imageSize: Long, val block: Int, val kind: DiscKind?)

/** Reads the headers of CHD (version 5), CSO and ZSO files, and of GameCube and Wii RVZ, WIA, WBFS, GCZ and CISO files. */
object PackedHeaders {
    /** How many bytes of a file are read for its header. */
    const val SIZE = 96

    private const val CHD_SIZE = 64
    private const val CD_UNIT = 2448
    private const val DVD_UNIT = 2048

    /** Where an RVZ or WIA file keeps the size of the disc inside, and the size of its blocks. */
    private const val WIA_DISC_SIZE = 0x24
    private const val WIA_BLOCK = 0x54

    /** GCZ's magic number as its first four bytes read, little-endian. */
    private const val GCZ_MAGIC = 0xB10BC001L

    /**
     * The header in [bytes], the start of a file with the [extension] (lower case); the extension tells a GameCube or
     * Wii `.ciso` from PSP's CSO, which start alike.
     */
    fun parse(bytes: ByteArray, extension: String? = null): PackedHeader? {
        if (bytes.size < 24) return null
        val magic = String(bytes, 0, 4, Charsets.ISO_8859_1)
        when {
            magic == "CISO" && extension == RomFormat.CISO.extension ->
                return PackedHeader(RomFormat.CISO, 0L, littleInt(bytes, 4), DiscKind.DVD)
            magic == "CISO" || magic == "ZISO" -> {
                val format = if (magic == "ZISO") RomFormat.ZSO else RomFormat.CSO
                return PackedHeader(format, littleLong(bytes, 8), littleInt(bytes, 16), DiscKind.DVD)
            }
            magic == "RVZ\u0001" || magic == "WIA\u0001" -> {
                if (bytes.size < WIA_BLOCK + 4) return null
                val format = if (magic[0] == 'R') RomFormat.RVZ else RomFormat.WIA
                return PackedHeader(format, bigLong(bytes, WIA_DISC_SIZE), bigInt(bytes, WIA_BLOCK), DiscKind.DVD)
            }
            magic == "WBFS" -> return PackedHeader(RomFormat.WBFS, 0L, 1 shl (bytes[9].toInt() and 0x1f), DiscKind.DVD)
            littleInt(bytes, 0).toLong() and 0xffffffffL == GCZ_MAGIC ->
                return PackedHeader(RomFormat.GCZ, littleLong(bytes, 16), littleInt(bytes, 24), DiscKind.DVD)
        }
        if (bytes.size < CHD_SIZE || String(bytes, 0, 8, Charsets.US_ASCII) != "MComprHD") return null
        if (bigInt(bytes, 12) != 5) return PackedHeader(null, bigLong(bytes, 28), 0, null)
        val codecs = (0 until 4).map { String(bytes, 16 + it * 4, 4, Charsets.US_ASCII) }.filter { it.any { c -> c.code != 0 } }
        val unit = bigInt(bytes, 60)
        val kind = when (unit) {
            CD_UNIT -> DiscKind.CD
            DVD_UNIT -> DiscKind.DVD
            else -> null
        }
        val format = when {
            kind == DiscKind.CD -> RomFormat.CHD_CD
            kind != DiscKind.DVD -> null
            codecs.firstOrNull() == "zstd" -> RomFormat.CHD_DVD_ZSTD
            codecs.firstOrNull() == "lzma" -> RomFormat.CHD_DVD
            codecs.isNotEmpty() && codecs.all { it == "zlib" } -> RomFormat.CHD_DVD_ZLIB
            else -> null
        }
        return PackedHeader(format, bigLong(bytes, 32), bigInt(bytes, 56), kind)
    }

    /** The bytes of a header from `od -An -tx1` (hex pairs, spaces and line breaks anywhere); null when it isn't hex. */
    fun fromHex(text: String): ByteArray? {
        val hex = text.filter { !it.isWhitespace() }
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        return runCatching { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    private fun byte(bytes: ByteArray, at: Int): Long = bytes[at].toLong() and 0xff

    private fun littleLong(bytes: ByteArray, at: Int): Long = (0 until 8).sumOf { byte(bytes, at + it) shl (8 * it) }

    private fun littleInt(bytes: ByteArray, at: Int): Int = (0 until 4).sumOf { byte(bytes, at + it) shl (8 * it) }.toInt()

    private fun bigLong(bytes: ByteArray, at: Int): Long = (0 until 8).sumOf { byte(bytes, at + it) shl (8 * (7 - it)) }

    private fun bigInt(bytes: ByteArray, at: Int): Int = (0 until 4).sumOf { byte(bytes, at + it) shl (8 * (3 - it)) }.toInt()
}
