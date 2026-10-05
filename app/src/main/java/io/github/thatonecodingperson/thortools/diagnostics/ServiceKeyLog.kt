package io.github.thatonecodingperson.thortools.diagnostics

import javax.inject.Inject
import javax.inject.Singleton

/** The last key events the accessibility service saw, and what it did with them (it takes only buttons hotkeys use). */
@Singleton
class ServiceKeyLog @Inject constructor() {
    private val entries = ArrayDeque<KeyLogEntry>()

    @Synchronized
    fun add(entry: KeyLogEntry) {
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<KeyLogEntry> = entries.toList()

    private companion object {
        const val MAX_ENTRIES = 60
    }
}
