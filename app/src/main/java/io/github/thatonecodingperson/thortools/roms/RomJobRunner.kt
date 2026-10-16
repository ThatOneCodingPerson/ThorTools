package io.github.thatonecodingperson.thortools.roms

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thatonecodingperson.thortools.tools.RootFiles
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * The job running now: what it is doing and how far it got. [started] and [pausedFor] (milliseconds spent paused) give
 * the time it has really been working; [pausedAt] is set while it is paused.
 */
data class RunningJob(
    val job: RomJob,
    val phase: String = "",
    val progress: ChdProgress? = null,
    val started: Long = System.currentTimeMillis(),
    val pausedFor: Long = 0L,
    val pausedAt: Long? = null,
) {
    fun working(now: Long): Long = (pausedAt ?: now) - started - pausedFor
}

/** A job that ended this session. */
data class EndedJob(val job: RomJob, val end: JobEnd, val time: Long = System.currentTimeMillis())

/** Why the queue stopped before it was empty. */
enum class QueueStop { LOW_BATTERY, NO_ENGINE, NO_ROOT }

/**
 * The jobs: the one running, the ones waiting, the ones that ended this session. [paused]: the running job is frozen
 * and no next one starts. [done] counts the jobs finished since the queue last ran empty, for "game 3 of 12"; [rate]
 * is how many input bytes a second the finished compressions managed ([rates] per format) and [checkRate] how many
 * bytes of compressed file a second the finished clean-ups checked, for the time left.
 */
data class RunnerState(
    val current: RunningJob? = null,
    val queue: List<RomJob> = emptyList(),
    val ended: List<EndedJob> = emptyList(),
    val stopped: QueueStop? = null,
    val paused: Boolean = false,
    val done: Int = 0,
    val rate: Double? = null,
    val checkRate: Double? = null,
    val rates: Map<RomFormat, Double> = emptyMap(),
) {
    val active: Boolean get() = current != null || queue.isNotEmpty()
    val total: Int get() = done + (if (current != null) 1 else 0) + queue.size

    /** Where [input] stands: 0 for the running job, 1 for the next one, and so on; null when it isn't waiting. */
    fun place(input: String): Int? {
        if (current?.job?.input == input) return 0
        val index = queue.indexOfFirst { it.input == input }
        return if (index < 0) null else index + 1
    }
}

/**
 * Runs ROM compression's jobs one at a time. Each job is a root script started in the background through PServer, which
 * returns at once (its one lock is shared by the whole app); the script writes its progress into the job's folder,
 * which is read here every half second. Pausing freezes chdman where it is. The queue is kept in a file, and a job still
 * running after Thor Tools restarts is picked up again.
 */
@Singleton
class RomJobRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShellExecutor,
    private val shelf: RomShelf,
    private val notice: RomJobNotice,
    private val rootFiles: RootFiles,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val jobsFolder get() = File(context.cacheDir, "roms-jobs")
    private val queueFile get() = File(File(context.filesDir, "roms"), QUEUE)
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _state = MutableStateFlow(RunnerState())
    val state: StateFlow<RunnerState> = _state.asStateFlow()

    /** The bundled chdman; null when this device has no copy (not an arm64 build). */
    val engine: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled maxcso, for CSO and ZSO; null when this device has no copy. */
    val maxcso: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.MAXCSO_FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled nodtool, for GameCube and Wii; null when this device has no copy. */
    val nod: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.NOD_FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled 7zz, for the classic consoles; null when this device has no copy. */
    val sevenZip: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.SEVENZIP_FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled z3ds tool, for the 3DS; null when this device has no copy. */
    val z3ds: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.Z3DS_FILE).takeIf { it.exists() }?.absolutePath

    /** The bundled wua tool, for the Wii U; null when this device has no copy. */
    val wua: String? get() = File(context.applicationInfo.nativeLibraryDir, ChdTool.WUA_FILE).takeIf { it.exists() }?.absolutePath

    /** Picks up a job left running when Thor Tools stopped and the queue it left, and clears what dead jobs left behind. */
    fun resume() = scope.launch {
        val kept = runCatching { RomJobPlan.decodeQueue(queueFile.readText()) }.getOrDefault(emptyList())
        if (kept.isNotEmpty()) _state.update { it.copy(queue = it.queue + kept.filter { job -> it.queue.none { q -> q.id == job.id } }) }
        val folders = jobsFolder.listFiles()?.filter { it.isDirectory }.orEmpty()
        folders.forEach { folder ->
            val job = runCatching { RomJobPlan.decode(File(folder, RomJobPlan.JOB).readText()) }.getOrNull()
            when {
                job == null -> folder.deleteRecursively()
                File(folder, RomJobPlan.RESULT).exists() -> finish(job, folder)
                alive(folder) -> {
                    // After an update chdman lies in a new folder; the job's script takes it from here.
                    listOf(
                        RomJobPlan.ENGINE to engine,
                        RomJobPlan.ENGINE_MAXCSO to maxcso,
                        RomJobPlan.ENGINE_NOD to nod,
                        RomJobPlan.ENGINE_7ZZ to sevenZip,
                        RomJobPlan.ENGINE_Z3DS to z3ds,
                        RomJobPlan.ENGINE_WUA to wua,
                    ).forEach { (name, path) ->
                        if (path != null) {
                            runCatching {
                                val file = File(folder, name)
                                file.writeText(path)
                                file.setReadable(true, false)
                            }
                        }
                    }
                    val paused = File(folder, RomJobPlan.PAUSED).exists()
                    val now = System.currentTimeMillis()
                    _state.update { it.copy(current = RunningJob(job, pausedAt = now.takeIf { paused }), paused = paused) }
                    if (!paused) hold()
                    follow(job, folder, launched = 0L)
                    release()
                    notice.clear()
                }
                else -> {
                    executor.script("rm -f ${RootFiles.quote(RomJobPlan.part(job.output))}")
                    record(job, JobEnd.Failed("stopped", null))
                    folder.deleteRecursively()
                }
            }
        }
        if (_state.value.queue.isNotEmpty() && !_state.value.paused) startWorker()
    }

    fun enqueue(jobs: List<RomJob>) {
        if (jobs.isEmpty()) return
        _state.update { state ->
            val waiting = state.queue.map { it.output } + listOfNotNull(state.current?.job?.output)
            state.copy(queue = state.queue + jobs.filter { it.output !in waiting }, stopped = null)
        }
        scope.launch {
            saveQueue()
            if (!_state.value.paused) startWorker()
        }
    }

    /** Takes a waiting job off the queue, or skips the running one (its unfinished file is removed). */
    fun cancel(id: Long) {
        _state.update { it.copy(queue = it.queue.filterNot { job -> job.id == id }) }
        scope.launch {
            saveQueue()
            val current = _state.value.current ?: return@launch
            if (current.job.id == id) stop(current.job)
        }
    }

    /** Stops the running job; the next one starts. */
    fun skip() {
        val current = _state.value.current ?: return
        scope.launch { stop(current.job) }
    }

    /** Stops the running job and empties the queue; the files of the stopped game are untouched. */
    fun stopAll() {
        _state.update { it.copy(queue = emptyList(), paused = false) }
        scope.launch {
            saveQueue()
            _state.value.current?.let { stop(it.job) }
        }
    }

    /** Freezes the running job where it is and starts no next one, until [resumeRun]. */
    fun pause() {
        if (_state.value.paused) return
        val now = System.currentTimeMillis()
        _state.update { it.copy(paused = true, current = it.current?.copy(pausedAt = now)) }
        scope.launch {
            val current = _state.value.current ?: return@launch
            val folder = File(jobsFolder, current.job.id.toString())
            runCatching { File(folder, RomJobPlan.PAUSED).writeText("1") }
            child(folder)?.let { executor.script("kill -STOP $it") }
            release()
            notice.show(current.job, current.phase, current.progress, _state.value.done + 1, _state.value.total, paused = true)
        }
    }

    fun resumeRun() {
        if (!_state.value.paused) return
        val now = System.currentTimeMillis()
        _state.update { state ->
            val current = state.current?.let { it.copy(pausedFor = it.pausedFor + (now - (it.pausedAt ?: now)), pausedAt = null) }
            state.copy(paused = false, current = current)
        }
        scope.launch {
            val current = _state.value.current
            if (current != null) {
                val folder = File(jobsFolder, current.job.id.toString())
                child(folder)?.let { executor.script("kill -CONT $it") }
                runCatching { File(folder, RomJobPlan.PAUSED).delete() }
                hold()
            } else {
                startWorker()
            }
        }
    }

    fun clearEnded() = _state.update { it.copy(ended = emptyList()) }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            hold()
            try {
                while (true) {
                    if (_state.value.paused) break
                    val job = _state.value.queue.firstOrNull() ?: break
                    val stop = blocker()
                    if (stop != null) {
                        _state.update { it.copy(stopped = stop) }
                        break
                    }
                    _state.update { it.copy(queue = it.queue.drop(1), current = RunningJob(job)) }
                    saveQueue()
                    hold()
                    run(job)
                }
            } finally {
                release()
                if (!_state.value.paused) notice.clear()
                if (_state.value.queue.isEmpty() && _state.value.current == null) _state.update { it.copy(done = 0) }
                shelf.rescan()
            }
        }
    }

    private fun blocker(): QueueStop? {
        if (engine == null) return QueueStop.NO_ENGINE
        val next = _state.value.queue.firstOrNull()
        if (next != null && next.format.tool == RomTool.MAXCSO && maxcso == null) return QueueStop.NO_ENGINE
        if (next != null && next.console.check == DiscCheck.NINTENDO && nod == null) return QueueStop.NO_ENGINE
        val threeDs = next != null && (next.format.tool == RomTool.Z3DS || next.from?.tool == RomTool.Z3DS)
        if (threeDs && z3ds == null) return QueueStop.NO_ENGINE
        if (next != null && next.console.listKind == DiscKind.FOLDER && wua == null) return QueueStop.NO_ENGINE
        if (next != null && next.console.cartridges && !threeDs && sevenZip == null) return QueueStop.NO_ENGINE
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (level in 0 until MIN_BATTERY && battery?.isCharging != true) return QueueStop.LOW_BATTERY
        return null
    }

    private suspend fun run(job: RomJob) {
        val chdman = engine ?: return
        val folder = File(jobsFolder, job.id.toString())
        folder.deleteRecursively()
        val written = runCatching {
            folder.mkdirs()
            File(folder, RomJobPlan.JOB).writeText(RomJobPlan.encode(job))
            job.sheet?.let { File(folder, RomJobPlan.LONE_SHEET).writeText(it.trimEnd() + "\n") }
            job.playlists.forEachIndexed { index, playlist ->
                // Read now, not at the last look: the job before may have changed the same list (another disc).
                val text = rootFiles.read(playlist) ?: shelf.text(playlist).orEmpty()
                val updated = RomSets.rewritePlaylist(text, job.input.substringAfterLast('/'), job.outputName) ?: text
                File(folder, RomJobPlan.playlistFile(index)).writeText(if (updated.endsWith("\n")) updated else "$updated\n")
            }
            File(folder, RUN).writeText(RomJobPlan.script(job, chdman, folder.absolutePath, maxcso, nod, sevenZip, z3ds, wua))
            folder.setExecutable(true, false)
            folder.listFiles()?.forEach { it.setReadable(true, false) }
        }.isSuccess
        if (!written) {
            record(job, JobEnd.Failed("write", null))
            folder.deleteRecursively()
            _state.update { it.copy(current = null) }
            return
        }
        val launch = "sh ${RootFiles.quote(File(folder, RUN).absolutePath)} > /dev/null 2>&1 &\nsleep 1"
        if (executor.script(launch).isFailure) {
            _state.update { it.copy(stopped = QueueStop.NO_ROOT, current = null, queue = listOf(job) + it.queue) }
            folder.deleteRecursively()
            return
        }
        follow(job, folder, launched = System.currentTimeMillis())
    }

    /**
     * Reads the job's progress until its script writes how it ended. A script that never started ([launched] more than
     * a while ago and no pid) or died without a result ends the job as failed, so nothing waits for ever.
     */
    private suspend fun follow(job: RomJob, folder: File, launched: Long) {
        val result = File(folder, RomJobPlan.RESULT)
        val pid = File(folder, RomJobPlan.PID)
        var checks = 0
        var lastNotice = 0L
        while (!result.exists()) {
            delay(POLL_MS)
            val paused = _state.value.paused
            val phase = runCatching { File(folder, RomJobPlan.PHASE).readText().trim() }.getOrDefault("")
            val progress = runCatching { ChdTool.progress(tail(File(folder, RomJobPlan.PROGRESS))) }.getOrNull()
            _state.update { state ->
                state.copy(current = state.current?.let { it.copy(phase = phase, progress = progress ?: it.progress) })
            }
            val now = System.currentTimeMillis()
            if (now - lastNotice >= NOTICE_MS) {
                lastNotice = now
                val state = _state.value
                notice.show(job, phase, progress, state.done + 1, state.total, paused)
            }
            if (paused || result.exists()) continue
            val gone = when {
                launched > 0 && !pid.exists() -> now - launched > START_LIMIT_MS
                ++checks % ALIVE_EVERY == 0 -> !alive(folder)
                else -> false
            }
            if (gone) {
                delay(POLL_MS)
                if (result.exists()) break
                executor.script("rm -f ${RootFiles.quote(RomJobPlan.part(job.output))}")
                val end = if (File(
                        folder,
                        RomJobPlan.CANCEL,
                    ).exists()
                ) {
                    JobEnd.Cancelled
                } else {
                    JobEnd.Failed(if (pid.exists()) "stopped" else "start", null)
                }
                record(job, end)
                folder.deleteRecursively()
                _state.update { it.copy(current = null, done = it.done + 1) }
                shelf.refreshList()
                return
            }
        }
        finish(job, folder)
    }

    private fun finish(job: RomJob, folder: File) {
        val end = JobEnd.parse(runCatching { File(folder, RomJobPlan.RESULT).readText() }.getOrNull()) ?: JobEnd.Failed("result", null)
        record(job, end)
        val now = System.currentTimeMillis()
        if (end is JobEnd.Done && job.kind != JobKind.ORIGINALS) shelf.recordResult(job, end.size)
        folder.deleteRecursively()
        _state.update { state ->
            val worked = state.current?.takeIf { it.job.id == job.id }?.working(now) ?: 0L
            val measured = (job.inputBytes * 1000.0 / worked).takeIf { end is JobEnd.Done && worked > MIN_RATE_MS }
            val compressed = measured?.takeIf { job.kind == JobKind.COMPRESS }
            state.copy(
                current = null,
                done = state.done + 1,
                rate = compressed ?: state.rate,
                rates = if (compressed != null) state.rates + (job.format to compressed) else state.rates,
                checkRate = measured?.takeIf { job.kind == JobKind.ORIGINALS } ?: state.checkRate,
            )
        }
        shelf.refreshList()
    }

    private fun record(job: RomJob, end: JobEnd) {
        _state.update { it.copy(ended = (it.ended + EndedJob(job, end)).takeLast(MAX_ENDED)) }
    }

    private suspend fun stop(job: RomJob) {
        val folder = File(jobsFolder, job.id.toString())
        runCatching { File(folder, RomJobPlan.CANCEL).writeText("1") }
        runCatching { File(folder, RomJobPlan.PAUSED).delete() }
        // A frozen chdman takes the stop once it runs again.
        child(folder)?.let { executor.script("kill -CONT $it 2>/dev/null; kill $it 2>/dev/null") }
        delay(POLL_MS)
    }

    private fun child(folder: File): Int? = runCatching { File(folder, RomJobPlan.CHILD).readText().trim().toInt() }.getOrNull()

    private fun saveQueue() {
        runCatching {
            queueFile.parentFile?.mkdirs()
            val queue = _state.value.queue
            if (queue.isEmpty()) queueFile.delete() else queueFile.writeText(RomJobPlan.encodeQueue(queue))
        }
    }

    /** True while the job's script still runs (root looks, since other users' processes may be hidden). */
    private fun alive(folder: File): Boolean {
        val pid = runCatching { File(folder, RomJobPlan.PID).readText().trim().toInt() }.getOrNull() ?: return true
        return executor.executeAsRoot("kill -0 $pid && echo yes || echo no").getOrNull()?.trim() != "no"
    }

    private fun tail(file: File): String {
        if (!file.exists()) return ""
        val length = file.length()
        return file.inputStream().use { input ->
            input.skip(maxOf(0L, length - TAIL))
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    /** Keeps the Thor awake for the next job; taken again for each job, so a long queue never outlasts its timeout. */
    private fun hold() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_MS)
            }
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val RUN = "run.sh"
        const val QUEUE = "queue.txt"
        const val POLL_MS = 500L
        const val NOTICE_MS = 1000L
        const val ALIVE_EVERY = 20
        const val START_LIMIT_MS = 30_000L
        const val MIN_RATE_MS = 5_000L
        const val TAIL = 512L
        const val MAX_ENDED = 50
        const val MIN_BATTERY = 15
        const val WAKE_TAG = "ThorTools:RomCompression"
        const val MAX_WAKE_MS = 6 * 60 * 60 * 1000L
    }
}

/** Time left for the running job and for the whole queue, from how fast compressing goes. */
object QueueTime {
    /** A compressed game's check afterwards takes about this share of its compressing (chdman checks on one core). */
    private const val CHECK_SHARE = 0.6
    private const val MIN_PERCENT = 2.0
    private const val MIN_WORK_MS = 5_000L

    /** Input bytes a second the running compression manages; null until it has run long enough to tell. */
    fun currentRate(running: RunningJob, now: Long): Double? = measured(running, now, JobKind.COMPRESS, "compress")

    /** Bytes of compressed file a second the running clean-up's check manages; null until it can tell. */
    fun currentCheckRate(running: RunningJob, now: Long): Double? = measured(running, now, JobKind.ORIGINALS, "verify")

    private fun measured(running: RunningJob, now: Long, kind: JobKind, phase: String): Double? {
        val percent = running.progress?.percent ?: return null
        val worked = running.working(now)
        if (running.job.kind != kind || running.phase != phase || percent < MIN_PERCENT || worked < MIN_WORK_MS) return null
        return running.job.inputBytes * percent / 100 * 1000 / worked
    }

    /** Seconds left for the running job, and for it plus every waiting one; null where it can't be told yet. */
    fun left(state: RunnerState, now: Long): Pair<Long?, Long?> {
        val running = state.current ?: return null to null
        val live = currentRate(running, now)
        val rate = live ?: state.rates[running.job.format] ?: state.rate
        val checkRate = currentCheckRate(running, now) ?: state.checkRate
        val rateOf = { job: RomJob -> state.rates[job.format] ?: live?.takeIf { job.format == running.job.format } ?: rate }
        val rest = (100 - (running.progress?.percent ?: 0.0)) / 100
        val bytes = running.job.inputBytes
        val thisOne = when (running.job.kind) {
            JobKind.COMPRESS -> rate?.let {
                when (running.phase) {
                    "compress" -> bytes / it * (rest + CHECK_SHARE)
                    "verify" -> bytes / it * CHECK_SHARE * rest
                    "finish" -> 0.0
                    else -> bytes / it * (1 + CHECK_SHARE)
                }
            }
            JobKind.ORIGINALS -> checkRate?.let { if (running.phase == "finish") 0.0 else bytes / it * rest }
            JobKind.EXTRACT -> null
        } ?: return null to null
        val waiting = state.queue.map { job ->
            when (job.kind) {
                JobKind.COMPRESS -> rateOf(job)?.let { job.inputBytes / it * (1 + CHECK_SHARE) }
                JobKind.ORIGINALS -> checkRate?.let { job.inputBytes / it }
                JobKind.EXTRACT -> null
            }
        }
        val all = if (waiting.any { it == null }) null else thisOne + waiting.sumOf { it ?: 0.0 }
        return thisOne.roundToLong() to all?.roundToLong()
    }

    /** True when [left] can tell the running job's time once it has run a few seconds (it can't for turning back). */
    fun canTell(state: RunnerState): Boolean = state.current?.job?.kind != JobKind.EXTRACT
}
