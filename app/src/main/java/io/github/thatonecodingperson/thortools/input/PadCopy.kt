package io.github.thatonecodingperson.thortools.input

import android.annotation.SuppressLint
import android.os.SystemClock
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.Writer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock

/**
 * Thor Tools' copy of AYN's pad, inside the root helper. While desktop controls keep sticks from apps, the helper makes
 * the copy (Android's uinput tool, in a process of its own, so the copy can go on its own), takes the real pad
 * (`EVIOCGRAB`: from then on only the helper reads it) and passes every event on to the copy except the hidden sticks'.
 * Apps and Thor Tools' own key filter then get the pad's buttons, D-pad and triggers from the copy, and nothing turns
 * the hidden sticks into D-pad presses ([PadCopyPlan]).
 *
 * Everything slow (starting the tool, waiting for the copy's node, waiting for the pad to rest) happens on this class's
 * own thread; the desktop engine only says what it wants ([want]). The pad is taken only while it rests (no key down,
 * sticks and D-pad in the middle, nothing sent for a moment), since Android keeps the last state it saw from the real
 * pad. Going off gently gives the sticks back first and waits (at most [LINGER_MS]) until no key is down on the copy,
 * so a button held then lets go where it went down. The pad comes back the moment the file that took it closes: here,
 * when the helper ends, or by the kernel if it dies. Holding Home and Back together for [ESCAPE_MS] drops the copy until
 * desktop controls go off and on again.
 */
@SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
object PadCopy {
    private const val TOOL = "/system/bin/uinput"
    private const val GETEVENT = "/system/bin/getevent"
    private const val EVIOCGRAB = 0x40044590
    private const val KEY_HOME = 102
    private const val KEY_BACK = 158
    private const val POLL_MS = 50L
    private const val QUIET_MS = 100L
    private const val CHECK_AFTER_TAKING_MS = 30L
    private const val NODE_WAIT_MS = 3_000L
    private const val NODE_SETTLE_MS = 800L
    private const val LINGER_MS = 2_000L
    private const val STALL_MS = 300L
    private const val ESCAPE_MS = 4_000L
    private const val RETRY_MS = 1_000L
    private const val LOCK_WAIT_MS = 200L
    private const val MAX_PROBLEMS = 16

    /** The pad the helper reads now. */
    var currentPad: () -> InputNode? = { null }

    /** When the pad last sent anything, if it rests now (no key down, sticks and D-pad in the middle); else null. */
    var restingSince: () -> Long? = { null }

    /** The helper's own handling of a pad event, which the forwarder keeps feeding while it has the pad. */
    var onPadEvent: (type: Int, code: Int, value: Int) -> Unit = { _, _, _ -> }

    /** Tells the app about a problem (its log). */
    var report: (HelperMessage) -> Unit = {}

    private class Wish(val axes: Set<Int>, val now: Boolean)

    @Volatile
    private var wish = Wish(emptySet(), now = false)

    // Given up on (a problem, or Home and Back held): no copy again until the wish goes empty.
    @Volatile
    private var givenUp = false

    private val session = AtomicReference<Session?>(null)
    private val managerLock = Any()
    private var manager: Thread? = null
    private var retryAt = 0L
    private val problemsTold = mutableSetOf<String>()

    /**
     * The axes desktop controls want kept from apps; empty for no copy. [now] ends a copy at once instead of gently
     * (desktop controls stop). Never waits: safe under the engine's lock.
     */
    fun want(axes: Set<Int>, now: Boolean = false) {
        val old = wish
        if (old.axes == axes && (!now || old.now)) return
        wish = Wish(axes, now)
        if (axes.isEmpty()) givenUp = false
        wake()
    }

    /** The pad back to Android and the copy gone, from any thread, without waiting on anything (the helper quits). */
    fun shutdown() {
        session.getAndSet(null)?.kill()
    }

    private fun wake() {
        val thread = synchronized(managerLock) {
            manager ?: Thread(::manage).apply {
                isDaemon = true
                name = "pad-copy"
                manager = this
                start()
            }
        }
        LockSupport.unpark(thread)
    }

    private fun manage() {
        while (true) {
            try {
                step()
            } catch (e: Throwable) {
                problem("${e.javaClass.simpleName}: ${e.message}")
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(POLL_MS))
        }
    }

    private fun step() {
        val w = wish
        val s = session.get()
        val pad = currentPad()
        val now = SystemClock.uptimeMillis()
        when {
            s == null -> if (w.axes.isNotEmpty() && !givenUp && pad != null && now >= retryAt) start(pad, w.axes)
            // The real pad went away (AYN re-makes it on a style switch and on sleep), or the tool did.
            s.ended || !s.process.isAlive -> end(s, if (s.ended) null else "the uinput tool ended")
            pad?.path != s.source.path -> end(s)
            w.axes.isEmpty() || givenUp -> if (w.now || s.grab == null) end(s) else linger(s, now)
            else -> {
                s.lingerSince = 0L
                s.hide(w.axes)
                if (s.grab == null) take(s, now) else watch(s, now)
            }
        }
    }

    /** Makes the copy of [pad] and checks that it stays (AYN hides, within half a second, a gamepad it doesn't own). */
    private fun start(pad: InputNode, axes: Set<Int>) {
        val keys = PadCopyPlan.bits(File(pad.sysfs, "device/capabilities/key").readText())
        val ranges = runCatching { PadCopyPlan.axes(run(GETEVENT, "-p", pad.path)) }.getOrDefault(emptyMap())
            .ifEmpty { PadCopyPlan.KNOWN_AXES }
            .filterKeys { code -> (pad.abs shr code) and 1L == 1L }
        val process = ProcessBuilder(TOOL, "-").redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
        val s = Session(pad, process, process.outputStream.bufferedWriter())
        try {
            s.writer.write(PadCopyPlan.register(pad.name, pad.vendor, pad.product, keys, ranges))
            s.writer.flush()
            val copy = findCopy(pad) ?: return giveUp(s, "the copy didn't come up")
            Thread.sleep(NODE_SETTLE_MS)
            if (!File(copy.path).exists()) return giveUp(s, "AYN took the copy")
            s.hide(axes)
            session.set(s)
        } catch (e: Exception) {
            s.kill()
            throw e
        }
    }

    private fun findCopy(pad: InputNode): InputNode? {
        val until = SystemClock.uptimeMillis() + NODE_WAIT_MS
        while (SystemClock.uptimeMillis() < until) {
            RawInputHelper.scanNodes().firstOrNull { node ->
                ThorPad.isCopy(node) && node.name == pad.name && node.product == pad.product && File(node.path).exists()
            }?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return null
    }

    /** Takes the real pad once it has rested a moment, and checks it still rested when taken. */
    private fun take(s: Session, now: Long) {
        val since = restingSince() ?: return
        if (now - since < QUIET_MS) return
        val stream = FileInputStream(s.source.path)
        try {
            grab(stream.fd)
        } catch (e: Exception) {
            runCatching { stream.close() }
            return giveUp(s, "taking the pad: ${(e.cause ?: e).message}")
        }
        // Events already on their way to the helper's own reader still arrive there: Android saw them.
        Thread.sleep(CHECK_AFTER_TAKING_MS)
        if (restingSince() == null) {
            runCatching { stream.close() }
            return
        }
        s.grab = stream
        Thread {
            try {
                RawInputHelper.readEvents(stream) { type, code, value ->
                    onPadEvent(type, code, value)
                    s.pass(type, code, value)
                }
            } catch (_: Exception) {
            } finally {
                s.ended = true
                manager?.let(LockSupport::unpark)
            }
        }.apply {
            isDaemon = true
            name = "pad-copy-forward"
            start()
        }
    }

    /** A copy that hangs on a write (its tool stuck) holds the pad: give the pad back. Home and Back held: the escape. */
    private fun watch(s: Session, now: Long) {
        val writing = s.writingSince
        if (writing != 0L && now - writing > STALL_MS) return giveUp(s, "the uinput tool stalled")
        val escape = s.escapeSince
        if (escape != 0L && now - escape > ESCAPE_MS) {
            givenUp = true
            end(s, "Home and Back held: the copy is off until desktop controls switch on again")
        }
    }

    /** Going off gently: the sticks back to apps at once, then the end once no key is down on the copy. */
    private fun linger(s: Session, now: Long) {
        if (s.lingerSince == 0L) s.lingerSince = now
        s.hide(emptySet())
        if (!s.anyKeyDown() || now - s.lingerSince > LINGER_MS) end(s)
    }

    /**
     * The pad back to Android first, then the copy gone. Nothing is let go on the copy itself (a release there is a
     * real one, and could make a held Home a tap): Android cancels what a removed device had down, and the app forgets
     * a button held on it.
     */
    private fun end(s: Session, why: String? = null) {
        session.compareAndSet(s, null)
        runCatching { s.grab?.close() }
        s.kill()
        why?.let(::problem)
        retryAt = SystemClock.uptimeMillis() + RETRY_MS
    }

    private fun giveUp(s: Session, why: String) {
        session.compareAndSet(s, null)
        s.kill()
        givenUp = true
        problem(why)
    }

    /**
     * Takes [fd]'s device for this process alone. `ioctlInt` hands the kernel a pointer, never null, which is what a
     * grab needs; letting go is closing the file.
     */
    private fun grab(fd: FileDescriptor) {
        val os = Class.forName("libcore.io.Libcore").getField("os").get(null) ?: error("no libcore")
        val ioctl = os.javaClass.methods.first { it.name == "ioctlInt" && it.parameterCount in 2..3 }
        if (ioctl.parameterCount == 2) {
            ioctl.invoke(os, fd, EVIOCGRAB)
        } else {
            val ref = Class.forName("android.system.Int32Ref").getConstructor(Int::class.javaPrimitiveType).newInstance(1)
            ioctl.invoke(os, fd, EVIOCGRAB, ref)
        }
    }

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        return process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }

    private fun problem(text: String) {
        val line = "copy of the pad: ${text.take(MAX_PROBLEM_LENGTH)}"
        if (synchronized(problemsTold) { problemsTold.size < MAX_PROBLEMS && problemsTold.add(line) }) {
            runCatching { report(HelperMessage.DesktopProblem(line)) }
        }
    }

    private const val MAX_PROBLEM_LENGTH = 120

    /** One copy and, once taken, the real pad behind it. */
    private class Session(val source: InputNode, val process: Process, val writer: Writer) {
        private val lock = ReentrantLock()
        private val frame = CopyFrame()

        @Volatile
        var grab: FileInputStream? = null

        @Volatile
        var ended = false

        @Volatile
        var writingSince = 0L

        @Volatile
        var escapeSince = 0L

        /** When gently going off began; the manager's alone. */
        var lingerSince = 0L

        /** One event from the real pad, passed on (the forwarder's thread). */
        fun pass(type: Int, code: Int, value: Int) {
            lock.lock()
            try {
                val line = frame.onEvent(type, code, value)
                escapeSince = if (KEY_HOME in frame.keysDown && KEY_BACK in frame.keysDown) {
                    escapeSince.takeIf { it != 0L } ?: SystemClock.uptimeMillis()
                } else {
                    0L
                }
                if (line != null) write(line)
            } finally {
                lock.unlock()
            }
        }

        fun hide(axes: Set<Int>) = locked { frame.hide(axes)?.let(::write) }

        fun anyKeyDown(): Boolean = locked { frame.keysDown.isNotEmpty() } ?: true

        /** Without waiting on anything: the pad back (closing the taking file wakes the forwarder), the copy gone. */
        fun kill() {
            runCatching { grab?.close() }
            runCatching { process.destroyForcibly() }
        }

        private fun write(line: String) {
            writingSince = SystemClock.uptimeMillis()
            try {
                writer.write(line)
                writer.flush()
            } finally {
                writingSince = 0L
            }
        }

        /** Runs [block] under the lock if it comes free soon; a forwarder stuck on a write holds it. */
        private fun <T> locked(block: () -> T): T? {
            if (!lock.tryLock(LOCK_WAIT_MS, TimeUnit.MILLISECONDS)) return null
            return try {
                runCatching(block).getOrNull()
            } finally {
                lock.unlock()
            }
        }
    }
}
