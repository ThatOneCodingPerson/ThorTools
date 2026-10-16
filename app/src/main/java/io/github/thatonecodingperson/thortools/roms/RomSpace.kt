package io.github.thatonecodingperson.thortools.roms

/**
 * Whether a run of compress jobs fits on its storage: [fit] of the [total] new jobs fit, in order, after the jobs
 * already waiting; [needBytes] is what all of them need at once (originals kept), [freeBytes] what the storage of the
 * first one that doesn't fit has free now (or of the first job when all fit).
 */
data class SpacePlan(val fit: Int, val total: Int, val needBytes: Long, val freeBytes: Long) {
    val fitsAll: Boolean get() = fit >= total
}

/**
 * Plans a run's room on each storage. A job needs its estimate's top plus a margin while it runs; afterwards its new
 * file stays, and deleting the originals gives their room back (moving them keeps them on the same storage).
 */
object RomSpace {
    /**
     * [jobs] (new, in order) after [waiting] (queued already, in order) against [freeBytes] (free bytes per storage
     * top, see [RomJobs.volumeRoot]). Only compress jobs take room; storages not in [freeBytes] count as roomy.
     */
    fun plan(jobs: List<RomJob>, waiting: List<RomJob>, freeBytes: Map<String, Long>): SpacePlan {
        val free = freeBytes.toMutableMap()
        waiting.filter { it.kind == JobKind.COMPRESS }.forEach { job -> run(job, free) }
        var fit = 0
        var blocked: String? = null
        for (job in jobs) {
            if (job.kind != JobKind.COMPRESS) {
                fit++
                continue
            }
            if (!run(job, free)) {
                blocked = RomJobs.volumeRoot(job.folder)
                break
            }
            fit++
        }
        val shown = blocked ?: jobs.firstOrNull()?.let { RomJobs.volumeRoot(it.folder) }
        return SpacePlan(
            fit = fit,
            total = jobs.size,
            needBytes = jobs.filter { it.kind == JobKind.COMPRESS }.sumOf { it.need },
            freeBytes = shown?.let { freeBytes[it] } ?: 0L,
        )
    }

    /** Runs [job] against [free]: false when it doesn't fit (nothing changes then). */
    private fun run(job: RomJob, free: MutableMap<String, Long>): Boolean {
        val volume = RomJobs.volumeRoot(job.folder)
        val room = free[volume] ?: return true
        if (room < job.need) return false
        val made = if (job.from != null && job.format.tool == RomTool.NONE) job.temp else job.need - RomJobPlan.MARGIN - job.temp
        val freed = if (job.choice == Originals.DELETE) job.inputBytes else 0L
        free[volume] = room - made + freed
        return true
    }

    /** The storage tops [jobs] write to. */
    fun volumes(jobs: List<RomJob>): Set<String> = jobs.map { RomJobs.volumeRoot(it.folder) }.toSet()
}
