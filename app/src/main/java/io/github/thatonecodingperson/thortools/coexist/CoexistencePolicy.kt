package io.github.thatonecodingperson.thortools.coexist

object CoexistencePolicy {

    /** A stored user choice wins; otherwise overlapping features stay off while OdinTools is installed. */
    fun resolve(defaultEnabled: Boolean, stored: Boolean?, odinToolsInstalled: Boolean): Boolean =
        stored ?: (defaultEnabled && !odinToolsInstalled)

    /** Switching an overlapping feature on next to OdinTools asks the user first. */
    fun needsConfirmation(turningOn: Boolean, odinToolsInstalled: Boolean): Boolean = turningOn && odinToolsInstalled
}
