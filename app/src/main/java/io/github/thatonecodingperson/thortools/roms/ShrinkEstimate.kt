package io.github.thatonecodingperson.thortools.roms

import java.util.zip.Deflater
import kotlin.math.roundToLong

/** About how big a game's compressed file will be: between [low] and [high] bytes; [measured] from its own data. */
data class Estimate(val low: Long, val high: Long, val measured: Boolean) {
    val middle: Long get() = (low + high) / 2

    /** The saving in percent of [original], from the middle of the range. */
    fun savingPercent(original: Long): Int = if (original <= 0) 0 else (100 - middle * 100 / original).toInt().coerceIn(0, 100)
}

/** A finished compression: what was estimated (middle) and what it became. */
data class ShrinkResult(val original: Long, val estimated: Long, val actual: Long)

/**
 * Estimates a disc's compressed size. Data tracks: chunks read from spread-out places are compressed with Deflate, and
 * chdman's LZMA usually does [LZMA_GAIN] better. Audio tracks: FLAC keeps about [AUDIO_RATIO] of CD audio. A DVD image
 * is compressed in the blocks its format uses ([blockSize]), each on its own, and scaled by how its codec usually does
 * against Deflate ([gain]). Without samples, the console's usual saving. Finished jobs correct the measured estimates.
 */
object ShrinkEstimate {
    const val LZMA_GAIN = 0.9
    const val AUDIO_RATIO = 0.65
    private const val SPREAD = 0.08
    private const val AUDIO_SPREAD = 0.1

    /** The usual result in [format] for a game of [size] bytes on [console], without looking at its data. */
    fun typical(size: Long, console: RomConsole, format: RomFormat = console.allFormats.first()): Estimate {
        val range = console.saving(format)
        return Estimate(low = size * (100 - range.last) / 100, high = size * (100 - range.first) / 100, measured = false)
    }

    /** The block a DVD [format] compresses on its own, for an image of [size] bytes. */
    fun blockSize(format: RomFormat, size: Long): Int = when (format) {
        RomFormat.CHD_DVD_ZSTD -> 16 * 1024
        RomFormat.CSO, RomFormat.ZSO -> if (size >= MAXCSO_LARGE) 16 * 1024 else 2048
        else -> 4096
    }

    /** How [format]'s codec usually does against Deflate on the same blocks (smaller is better). */
    fun gain(format: RomFormat): Double = when (format) {
        RomFormat.CHD_DVD -> 0.92
        RomFormat.CHD_DVD_ZSTD -> 0.95
        RomFormat.CSO -> 0.98
        RomFormat.ZSO -> 1.15
        else -> 1.0
    }

    /** The estimate in DVD [format] for an image of [size] bytes whose sampled blocks compress to [blockRatio]. */
    fun image(size: Long, format: RomFormat, blockRatio: Double, history: List<ShrinkResult> = emptyList()): Estimate {
        val middle = size * minOf(1.0, blockRatio * gain(format) * correction(history))
        return Estimate(
            low = (middle * (1 - SPREAD)).roundToLong().coerceIn(0, size),
            high = (middle * (1 + SPREAD)).roundToLong().coerceIn(0, size),
            measured = true,
        )
    }

    /**
     * Compressed size over original size of [chunks] cut into blocks of [block] bytes, each compressed on its own with
     * Deflate (best); a block that doesn't shrink counts as stored.
     */
    fun blockRatio(chunks: List<ByteArray>, block: Int): Double? {
        val total = chunks.sumOf { it.size.toLong() }
        if (total == 0L || block <= 0) return null
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val buffer = ByteArray(BUFFER)
        var compressed = 0L
        try {
            chunks.forEach { chunk ->
                var start = 0
                while (start < chunk.size) {
                    val length = minOf(block, chunk.size - start)
                    deflater.reset()
                    deflater.setInput(chunk, start, length)
                    deflater.finish()
                    var size = 0L
                    while (!deflater.finished()) size += deflater.deflate(buffer)
                    compressed += minOf(size, length.toLong())
                    start += length
                }
            }
        } finally {
            deflater.end()
        }
        return compressed.toDouble() / total
    }

    /**
     * The estimate from [dataRatio] (compressed / original of the sampled data chunks, Deflate) for [dataSize] bytes of
     * data tracks and [audioSize] bytes of audio, corrected by [history].
     */
    fun measured(dataSize: Long, audioSize: Long, dataRatio: Double, history: List<ShrinkResult> = emptyList()): Estimate {
        val correction = correction(history)
        val data = dataSize * dataRatio * LZMA_GAIN * correction
        val audio = audioSize * AUDIO_RATIO
        val low = data * (1 - SPREAD) + audioSize * (AUDIO_RATIO - AUDIO_SPREAD)
        val high = data * (1 + SPREAD) + audioSize * (AUDIO_RATIO + AUDIO_SPREAD)
        val total = dataSize + audioSize
        return Estimate(
            low = low.roundToLong().coerceIn(0, total),
            high = high.roundToLong().coerceIn(0, total).coerceAtLeast((data + audio).roundToLong().coerceAtMost(total)),
            measured = true,
        )
    }

    /** Compressed size over original size of [chunks] together, each compressed on its own with Deflate (best). */
    fun deflateRatio(chunks: List<ByteArray>): Double? {
        val total = chunks.sumOf { it.size.toLong() }
        if (total == 0L) return null
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val buffer = ByteArray(BUFFER)
        var compressed = 0L
        try {
            chunks.forEach { chunk ->
                deflater.reset()
                deflater.setInput(chunk)
                deflater.finish()
                while (!deflater.finished()) compressed += deflater.deflate(buffer)
            }
        } finally {
            deflater.end()
        }
        return compressed.toDouble() / total
    }

    /**
     * How much the real results differed from the estimates: the median of actual / estimated over [history], kept
     * between 0.7 and 1.3 so one odd game can't swing it; 1 without history.
     */
    fun correction(history: List<ShrinkResult>): Double {
        val ratios = history.filter { it.estimated > 0 && it.actual > 0 }.map { it.actual.toDouble() / it.estimated }.sorted()
        if (ratios.isEmpty()) return 1.0
        val median = if (ratios.size % 2 == 1) ratios[ratios.size / 2] else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2
        return median.coerceIn(MIN_CORRECTION, MAX_CORRECTION)
    }

    /**
     * Where to read samples: [count] offsets spread evenly over [size] bytes, each aligned to [chunk] and leaving room
     * for a whole chunk.
     */
    fun sampleOffsets(size: Long, chunk: Int, count: Int): List<Long> {
        if (size <= 0 || count <= 0) return emptyList()
        if (size <= chunk) return listOf(0L)
        val last = (size - chunk) / chunk
        return (0 until count).map { i -> (last * i / maxOf(1, count - 1)) * chunk }.distinct()
    }

    private const val BUFFER = 64 * 1024

    /** How big a format's file usually is next to the same game's CSO (from the published comparisons). */
    private fun relative(format: RomFormat): Double = when (format) {
        RomFormat.CHD_DVD -> 0.88
        RomFormat.CHD_DVD_ZSTD -> 0.91
        RomFormat.CHD_DVD_ZLIB -> 0.97
        RomFormat.CHD_CD -> 0.9
        RomFormat.CSO -> 1.0
        RomFormat.ZSO -> 1.08
        RomFormat.ISO -> 1.0
        RomFormat.RVZ, RomFormat.WIA -> 0.9
        RomFormat.WBFS, RomFormat.CISO -> 1.0
        RomFormat.GCZ -> 0.95
        RomFormat.ZIP, RomFormat.SEVEN_Z, RomFormat.Z3DS, RomFormat.ROM_FILE, RomFormat.WUA, RomFormat.FOLDERS -> 1.0
    }

    /**
     * A Wii U game's WUA from [size] bytes of folders: about what Deflate made of its sampled chunks ([deflate]; null:
     * not sampled, then [TYPICAL_WUA]), corrected by [history].
     */
    fun folders(size: Long, deflate: Double?, history: List<ShrinkResult> = emptyList()): Estimate {
        val middle = size * (deflate ?: TYPICAL_WUA) * correction(history)
        return Estimate(
            low = (middle * (1 - SPREAD)).roundToLong().coerceIn(0, size),
            high = (middle * (1 + SPREAD)).roundToLong().coerceIn(0, size),
            measured = deflate != null,
        )
    }

    /**
     * A cartridge game of [rom] bytes in [format]: a zip keeps about what Deflate made of its sampled chunks
     * ([deflate]; null: not sampled, then [TYPICAL_ZIP]), a 7z that times its [console]'s usual 7z-to-zip ratio, a
     * 3DS game's Z3DS about what Deflate made too (a card's padding counts in the samples), the plain ROM its size;
     * corrected by [history].
     */
    fun cartridge(
        rom: Long,
        deflate: Double?,
        console: RomConsole,
        format: RomFormat,
        history: List<ShrinkResult> = emptyList(),
    ): Estimate {
        if (format == RomFormat.ROM_FILE) return Estimate(rom, rom, true)
        val zip = rom * (deflate ?: if (format == RomFormat.Z3DS) TYPICAL_Z3DS else TYPICAL_ZIP)
        val middle = (if (format == RomFormat.SEVEN_Z) zip * console.sevenZipRatio else zip) * correction(history)
        return Estimate(
            low = (middle * (1 - SPREAD)).roundToLong().coerceIn(0, rom),
            high = (middle * (1 + SPREAD)).roundToLong().coerceIn(0, rom),
            measured = deflate != null,
        )
    }

    /**
     * Turning a cartridge archive of [size] bytes in [from] (its ROM [rom] bytes) into [to]: a zip becomes about its
     * [console]'s 7z-to-zip ratio as a 7z, a 7z the other way round; the plain ROM is its size.
     */
    fun cartridgeConverted(size: Long, rom: Long?, from: RomFormat?, to: RomFormat, console: RomConsole): Estimate {
        val whole = rom ?: (size * 2)
        if (to == RomFormat.ROM_FILE) return Estimate(whole, whole, rom != null)
        val ratio = console.sevenZipRatio
        val middle = when {
            from == RomFormat.ZIP && to == RomFormat.SEVEN_Z -> size * ratio
            from == RomFormat.SEVEN_Z && to == RomFormat.ZIP -> size / ratio
            else -> size.toDouble()
        }
        return Estimate(
            low = (middle * (1 - SPREAD)).roundToLong().coerceIn(0, whole),
            high = (middle * (1 + SPREAD)).roundToLong().coerceIn(0, whole),
            measured = false,
        )
    }

    /**
     * The RVZ of a GameCube or Wii disc whose files take [used] bytes (nod lists them), at most [disc] bytes. RVZ keeps
     * the disc's padding as seeds, so only the files count: their RVZ is [RVZ_LOW] to [RVZ_HIGH] of their size (on
     * fifteen measured discs 55 to 96 %, about 80 % in the middle), corrected by [history].
     */
    fun rvz(used: Long, disc: Long?, history: List<ShrinkResult> = emptyList()): Estimate {
        val correction = correction(history)
        val top = disc ?: Long.MAX_VALUE
        return Estimate(
            low = (used * RVZ_LOW * correction).roundToLong().coerceIn(0, top),
            high = (used * RVZ_HIGH * correction).roundToLong().coerceIn(0, top),
            measured = true,
        )
    }

    /** The bytes of the files a disc holds, from `nodtool list -s` (each file's size, a tab, its name). */
    fun listedBytes(listing: String): Long =
        listing.lineSequence().mapNotNull { line -> line.substringBefore('\t', "").trim().toLongOrNull() }.sum()

    /**
     * The estimate for turning a compressed file of [size] bytes in [from] (null: a CHD of a kind not told) into [to];
     * [image] is the image inside when its header told it. A plain ISO is the image itself.
     */
    fun converted(size: Long, image: Long?, from: RomFormat?, to: RomFormat, history: List<ShrinkResult> = emptyList()): Estimate {
        if (to == RomFormat.ISO) {
            val whole = image ?: (size * 2)
            return Estimate(whole, whole, image != null)
        }
        val middle = size * relative(to) / relative(from ?: RomFormat.CHD_DVD) * correction(history)
        val top = image ?: Long.MAX_VALUE
        return Estimate(
            low = (middle * (1 - SPREAD)).roundToLong().coerceAtMost(top),
            high = (middle * (1 + SPREAD)).roundToLong().coerceAtMost(top),
            measured = false,
        )
    }

    /** The system identifier of the disc whose first bytes are [start] (sector 16, offset 8), trimmed; null if none. */
    fun systemId(start: ByteArray?): String? {
        val at = 16 * 2048 + 8
        if (start == null || start.size < at + 32) return null
        return String(start, at, 32, Charsets.US_ASCII).trim { it.isWhitespace() || it == '\u0000' }.trim('"').ifEmpty { null }
    }

    /** True when [start] (an image's first bytes) holds an ISO 9660 volume: `CD001` at the start of sector 16. */
    fun holdsDisc(start: ByteArray?): Boolean {
        val at = 16 * 2048 + 1
        if (start == null || start.size < at + 5) return false
        return String(start, at, 5, Charsets.US_ASCII) == "CD001"
    }

    /** What Deflate usually keeps of a cartridge ROM, before one is sampled. */
    private const val TYPICAL_ZIP = 0.6

    /** A 3DS card's Z3DS when nothing was sampled: about 25 % smaller (Azahar's own figure for trimmed ROMs). */
    private const val TYPICAL_Z3DS = 0.75

    /** A Wii U game's WUA when nothing was sampled: about 18 % smaller (a LaunchBox user's 23 games). */
    private const val TYPICAL_WUA = 0.82
    private const val RVZ_LOW = 0.6
    private const val RVZ_HIGH = 1.0

    /** maxcso uses 16 KB blocks from this size on (2 GiB). */
    private const val MAXCSO_LARGE = 0x80000000L
    private const val MIN_CORRECTION = 0.7
    private const val MAX_CORRECTION = 1.3
}
