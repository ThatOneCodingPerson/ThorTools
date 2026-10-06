package io.github.thatonecodingperson.thortools.oled

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.util.Base64

/**
 * OLED Safety inside the root helper.
 *
 * Its pixel shifter moves whole screens a pixel at a time along a [ShiftPath], by placing their pictures on the panels
 * directly (SurfaceFlinger's display projection, the same thing the display manager sets). The display manager only
 * places a screen again when its own values change, so a step stays until the next one. Every screen is put back where
 * the display manager had it when the engine stops, the helper exits, or a screen leaves the config; a new helper puts
 * them back first, in case the last one was killed.
 *
 * It also watches the screens it is asked to: a tiny copy every [WATCH_MS], compared in a [StillMap], and tells the
 * app through [report] when a screen's picture goes still or starts moving again.
 *
 * And it looks after still areas ([StillAreas]: the bright parts that stay put while the rest moves) with layers of its
 * own: one [DimLayer] per screen dimming exactly the protected blocks, and [AreaLayer]s moving their still pixels a
 * pixel at a time ([AreaPatch]), rebuilt as soon as something under them changes. Areas are found and copied from the
 * windows alone ([ContentCopies]), so its own layers never count; a screen with protection showing is checked every
 * [FAST_MS]. Nothing runs while the screens are off.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi")
internal object OledEngine {
    private val lock = Any()

    /** Display id and whether its picture is now still; set by the helper to send it to the app. */
    @Volatile
    var report: ((Int, Boolean) -> Unit)? = null

    /** Display id and the share of it protected as still areas, in percent; set by the helper too. */
    @Volatile
    var reportAreas: ((Int, Int) -> Unit)? = null

    @Volatile
    private var config = EngineConfig()

    @Volatile
    private var screenOn = true
    private var loop: Thread? = null
    private val positions = mutableMapOf<Int, Int>()
    private val nextStep = mutableMapOf<Int, Long>()
    private var path = ShiftPath(config.radius)
    private val watched = mutableMapOf<Int, Watch>()
    private val nextWatch = mutableMapOf<Int, Long>()
    private val areaStates = mutableMapOf<Int, AreaState>()
    private var hooked = false

    private class AreaState(val projection: DisplayProjection, val copies: ContentCopies) {
        val classic = ClassicAreas()
        val finder = StillAreas()
        var dim: DimLayer? = null
        val patches = mutableListOf<AreaLayer>()
        var nextCheck = 0L
        var protecting = false
        var reported = -1
        var ticks = 0
    }

    private class Watch(val sampler: ScreenSampler) {
        val map = StillMap()
        var still = false
        var lastChange = SystemClock.uptimeMillis()
    }

    fun configure(text: String): String = synchronized(lock) {
        val next = EngineConfig.decode(text)
        val radiusChanged = next.radius != config.radius
        if (areasOf(next) != areasOf(config)) clearAreas()
        val left = config.shiftDisplays - next.shiftDisplays
        config = next
        if (radiusChanged) {
            path = ShiftPath(next.radius)
            positions.clear()
        }
        val projections = projections()
        left.forEach { id -> projections[id]?.let(::place) }
        positions.keys.retainAll(next.shiftDisplays)
        nextStep.clear()
        (watched.keys - next.watching).forEach { id -> watched.remove(id)?.sampler?.close() }
        hookExit()
        if (next.idle) stopLoop() else startLoop()
        "ok"
    }

    /** Stops and puts every screen back where the display manager has it. */
    fun stop(): String = synchronized(lock) {
        config = EngineConfig()
        stopLoop()
        positions.clear()
        nextStep.clear()
        watched.values.forEach { it.sampler.close() }
        watched.clear()
        nextWatch.clear()
        clearAreas()
        projections().values.forEach(::place)
        "ok"
    }

    fun screen(on: Boolean) {
        screenOn = on
    }

    /** A copy of display [id] at [width] x [height] for the inverse refresher: "width height" and its RGB bytes in Base64. */
    fun shot(id: Int, width: Int, height: Int): String {
        val projection = projections()[id] ?: error("no display $id")
        val sampler = ScreenSampler(projection, width, height)
        val sample = try {
            sampler.take()
        } finally {
            sampler.close()
        } ?: error("no copy of display $id")
        val bytes = ByteArray(sample.pixels.size * 3)
        sample.pixels.forEachIndexed { i, pixel ->
            bytes[i * 3] = (pixel shr 16).toByte()
            bytes[i * 3 + 1] = (pixel shr 8).toByte()
            bytes[i * 3 + 2] = pixel.toByte()
        }
        return "${sample.width} ${sample.height} ${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun startLoop() {
        if (loop?.isAlive == true) {
            loop?.interrupt()
            return
        }
        loop = Thread({
            while (true) {
                val wait = try {
                    synchronized(lock) {
                        if (screenOn) tick()
                        when {
                            areaStates.values.any { it.protecting } -> FAST_MS
                            config.watching.isEmpty() && config.areaDisplays.isEmpty() -> config.everyMs
                            else -> WATCH_MS
                        }
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "engine", error)
                    ERROR_PAUSE_MS
                }
                try {
                    Thread.sleep(wait)
                } catch (_: InterruptedException) {
                    // A new config is taken up now; a stop ends the loop.
                    if (config.idle) return@Thread
                }
            }
        }, "oled-engine").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopLoop() {
        val running = loop ?: return
        loop = null
        running.interrupt()
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val due = config.shiftDisplays.filter { now >= (nextStep[it] ?: 0L) }
        val unknown = config.watching.any { it !in watched } || config.areaDisplays.any { it !in areaStates }
        val projections = if (due.isNotEmpty() || unknown) projections() else emptyMap()
        config.watching.filter { now >= (nextWatch[it] ?: 0L) }.forEach { id ->
            nextWatch[id] = now + WATCH_MS
            watch(id, now, projections)
        }
        config.areaDisplays.filter { now >= (areaStates[it]?.nextCheck ?: 0L) }.forEach { id -> areas(id, now, projections) }
        due.forEach { id ->
            val projection = projections[id] ?: return@forEach
            nextStep[id] = now + config.everyMs
            val stillFor = watched[id]?.let { if (it.still) now - it.lastChange else 0L } ?: 0L
            if (config.whenStill && stillFor < config.stillMs) return@forEach
            val index = positions[id]?.plus(1) ?: if (id == TOP_DISPLAY) path.topStart else path.bottomStart
            positions[id] = index
            place(projection, path.at(index))
        }
    }

    /** One tiny copy of display [id]; tells the app when its picture goes still or moves again. */
    private fun watch(id: Int, now: Long, projections: Map<Int, DisplayProjection>) {
        val watch = watched[id] ?: projections[id]?.let { projection ->
            val size = projection.layerStack
            Watch(ScreenSampler(projection, size.right / SAMPLE_SCALE, size.bottom / SAMPLE_SCALE)).also { watched[id] = it }
        } ?: return
        val sample = watch.sampler.take() ?: return
        val still = watch.map.update(sample, now)
        if (!still) watch.lastChange = now
        if (still != watch.still) {
            watch.still = still
            report?.invoke(id, still)
            // The picture moved on: back to the middle if chosen, so the shift starts over from there.
            if (!still && config.center && positions.remove(id) != null) projections()[id]?.let(::place)
        }
    }

    /**
     * Display [id]'s still areas: a quarter-size copy of the windows updates the finder; the dim layer shows exactly the
     * protected blocks, each as dark as it is bright; moving patches follow the joined blocks, take their step when due
     * and are rebuilt at once when anything under them changes, so what changed shows live and the rest stays moved.
     */
    private fun areas(id: Int, now: Long, projections: Map<Int, DisplayProjection>) {
        val state = areaStates[id] ?: projections[id]?.let { projection ->
            AreaState(projection, ContentCopies(id, projection.layerStack)).also { areaStates[id] = it }
        } ?: return
        if (!config.areaExperimental) return classicAreas(id, state, now)
        val sample = runCatching { state.copies.take(null, 1f / AREA_SCALE) }
            .onFailure { if (state.ticks++ % LOG_EVERY == 0) Log.w(TAG, "no copy of the windows of display $id", it) }
            .getOrNull()
        if (sample == null) {
            state.nextCheck = now + WATCH_MS
            return
        }
        val finder = state.finder
        finder.update(sample, now, config.areaStillMs)
        if (config.areaDim > 0) {
            val dim = state.dim ?: DimLayer(state.projection.layerStackId, state.projection.layerStack, finder.columns, finder.rows)
                .also { state.dim = it }
            dim.draw(finder.dimAlphas(config.areaDim * MAX_ALPHA / 100))
        }
        if (config.areaShift > 0) movePatches(state, now)
        val percent = finder.protectedPercent()
        state.protecting = percent > 0 || state.patches.isNotEmpty()
        state.nextCheck = now + if (state.protecting) FAST_MS else WATCH_MS
        if (percent != state.reported) {
            state.reported = percent
            reportAreas?.invoke(id, percent)
        }
        if (state.ticks++ % LOG_EVERY == 0) {
            Log.i(TAG, "areas on display $id: ${sample.width}x${sample.height}, $percent % protected, ${state.patches.size} moving")
        }
    }

    /**
     * Display [id]'s still areas as first built: areas found in a quarter-size copy every 2 s get a layer each, dimmed
     * and/or with their still pixels moved; any change under one removes it.
     */
    private fun classicAreas(id: Int, state: AreaState, now: Long) {
        state.nextCheck = now + WATCH_MS
        val screen = state.projection.layerStack
        val sample = runCatching { state.copies.take(null, 1f / AREA_SCALE) }
            .onFailure { if (state.ticks++ % LOG_EVERY == 0) Log.w(TAG, "no copy of the windows of display $id", it) }
            .getOrNull() ?: return
        val finder = state.classic
        finder.update(sample, now)
        val found = finder.areas(now, config.areaStillMs)
        state.patches.removeAll { layer ->
            val gone = found.none { overlaps(it, layer.area) } || finder.anyChanged(layer.stillPixels, layer.drawnAt)
            if (gone) layer.remove()
            gone
        }
        val dimAlpha = config.areaDim * MAX_ALPHA / 100
        found.filter { area -> state.patches.none { overlaps(area, it.area) } }.forEach { area ->
            val margin = config.areaShift
            val box = Box(
                (area.left * AREA_SCALE - margin).coerceAtLeast(screen.left),
                (area.top * AREA_SCALE - margin).coerceAtLeast(screen.top),
                (area.right * AREA_SCALE + margin).coerceAtMost(screen.right),
                (area.bottom * AREA_SCALE + margin).coerceAtMost(screen.bottom),
            )
            val inner = Box(
                area.left * AREA_SCALE - box.left,
                area.top * AREA_SCALE - box.top,
                area.right * AREA_SCALE - box.left,
                area.bottom * AREA_SCALE - box.top,
            )
            val layer = AreaLayer(state.projection.layerStackId, box, area, inner)
            layer.stillPixels = finder.stillPixels(area, now, config.areaStillMs)
            layer.drawnAt = now
            layer.draw(null, dimAlpha)
            layer.nextStep = now
            state.patches += layer
        }
        if (config.areaShift > 0) {
            state.patches.filter { now >= it.nextStep }.forEach { layer ->
                layer.nextStep = now + config.areaEveryMs
                val copy = runCatching { state.copies.take(layer.box, 1f) }.getOrNull() ?: return@forEach
                val previous = layer.previous
                layer.previous = copy.pixels
                if (previous == null) return@forEach
                layer.step++
                val pixels = AreaPatch.build(copy.pixels, previous, copy.width, copy.height, layer.inner, areaPath.at(layer.step))
                    ?: return@forEach
                layer.draw(pixels, dimAlpha)
                layer.stillPixels = finder.stillPixels(layer.area, now, config.areaStillMs)
                layer.drawnAt = now
            }
        }
        val covered = found.sumOf { (it.right - it.left) * (it.bottom - it.top) }
        val percent = covered * 100 / (sample.width * sample.height).coerceAtLeast(1)
        if (percent != state.reported) {
            state.reported = percent
            reportAreas?.invoke(id, percent)
        }
        if (state.ticks++ % LOG_EVERY == 0) Log.i(TAG, "classic areas on display $id: $percent %, ${state.patches.size} layers")
    }

    /** The moving patches matched to the joined protected blocks, stepped when due, rebuilt when something changed. */
    private fun movePatches(state: AreaState, now: Long) {
        val finder = state.finder
        val screen = state.projection.layerStack
        val areas = finder.components()
        state.patches.removeAll { patch ->
            val gone = areas.none { similar(it, patch.area) }
            if (gone) patch.remove()
            gone
        }
        areas.filter { area -> state.patches.none { similar(area, it.area) } }.forEach { area ->
            val margin = config.areaShift
            val box = Box(
                (area.left * AREA_SCALE - margin).coerceAtLeast(screen.left),
                (area.top * AREA_SCALE - margin).coerceAtLeast(screen.top),
                (area.right * AREA_SCALE + margin).coerceAtMost(screen.right),
                (area.bottom * AREA_SCALE + margin).coerceAtMost(screen.bottom),
            )
            val inner = Box(
                area.left * AREA_SCALE - box.left,
                area.top * AREA_SCALE - box.top,
                area.right * AREA_SCALE - box.left,
                area.bottom * AREA_SCALE - box.top,
            )
            state.patches += AreaLayer(state.projection.layerStackId, box, area, inner).also { it.nextStep = now }
        }
        state.patches.forEach { patch ->
            when {
                now >= patch.nextStep -> drawPatch(state, patch, now, step = true)
                patch.previous != null && finder.anyChanged(patch.stillPixels, patch.drawnAt) -> drawPatch(state, patch, now, step = false)
            }
        }
    }

    /**
     * A patch from a fresh full-size copy against the copy of its last step: the pixels that stayed the same are moved,
     * what changed shows live. A [step] goes one place further along the path and makes this copy the new reference.
     */
    private fun drawPatch(state: AreaState, patch: AreaLayer, now: Long, step: Boolean) {
        if (step) patch.nextStep = now + config.areaEveryMs
        val copy = runCatching { state.copies.take(patch.box, 1f) }.getOrNull() ?: return
        val previous = patch.previous
        if (step) patch.previous = copy.pixels
        if (previous == null) return
        if (step) patch.step++
        val pixels = AreaPatch.build(copy.pixels, previous, copy.width, copy.height, patch.inner, areaPath.at(patch.step)) ?: return
        patch.draw(pixels)
        patch.stillPixels = state.finder.stillPixels(patch.area, now, config.areaStillMs)
        patch.drawnAt = now
    }

    /** At exit: our layers off and the screens back, without waiting for the engine (it may be stuck mid-copy). */
    private fun releaseOnExit() {
        runCatching {
            areaStates.values.toList().forEach { state ->
                state.patches.toList().forEach { it.remove() }
                state.dim?.remove()
            }
        }
        runCatching { projections().values.forEach(::place) }
    }

    private fun clearAreas() {
        areaStates.forEach { (id, state) ->
            state.patches.forEach { it.remove() }
            state.dim?.remove()
            state.copies.close()
            if (state.reported > 0) reportAreas?.invoke(id, 0)
        }
        areaStates.clear()
    }

    private fun overlaps(a: Box, b: Box): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Two areas (copy pixels) are the same one when they overlap by most of the smaller. */
    private fun similar(a: Box, b: Box): Boolean {
        val overlap = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left)) * maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val smaller = minOf((a.right - a.left) * (a.bottom - a.top), (b.right - b.left) * (b.bottom - b.top))
        return smaller > 0 && overlap * SIMILAR_DIVISOR >= smaller * SIMILAR_SHARE
    }

    private val areaPath: ShiftPath get() = ShiftPath(config.areaShift)

    private fun areasOf(config: EngineConfig) =
        listOf(config.areaDisplays, config.areaStillMs, config.areaDim, config.areaShift, config.areaEveryMs, config.areaExperimental)

    private fun place(projection: DisplayProjection, offset: Offset = Offset(0, 0)) {
        val surfaceControl = Class.forName("android.view.SurfaceControl")
        val token = surfaceControl.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
            .invoke(null, projection.physicalId) as? IBinder ?: return
        val layerStack = projection.layerStack.let { Rect(it.left, it.top, it.right, it.bottom) }
        val display = projection.display.moved(offset).let { Rect(it.left, it.top, it.right, it.bottom) }
        surfaceControl.getMethod("openTransaction").invoke(null)
        try {
            surfaceControl.getMethod(
                "setDisplayProjection",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Rect::class.java,
                Rect::class.java,
            ).invoke(null, token, projection.orientation, layerStack, display)
        } finally {
            surfaceControl.getMethod("closeTransaction").invoke(null)
        }
    }

    private fun projections(): Map<Int, DisplayProjection> = runCatching {
        val process = ProcessBuilder("dumpsys", "display").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        DisplayProjection.parse(text)
    }.getOrDefault(emptyMap())

    /** `exitProcess`, a closed socket and a polite kill all run shutdown hooks; the screens go back first. */
    private fun hookExit() {
        if (hooked) return
        hooked = true
        Runtime.getRuntime().addShutdownHook(Thread { releaseOnExit() })
    }

    private const val TAG = "ThorToolsOled"
    private const val TOP_DISPLAY = 0
    private const val WATCH_MS = 2_000L
    private const val FAST_MS = 500L
    private const val MAX_ALPHA = 255
    private const val SIMILAR_SHARE = 7
    private const val SIMILAR_DIVISOR = 10
    private const val SAMPLE_SCALE = 16
    private const val AREA_SCALE = 4
    private const val LOG_EVERY = 30
    private const val ERROR_PAUSE_MS = 5_000L
}
