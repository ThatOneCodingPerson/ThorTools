package io.github.thatonecodingperson.thortools.coexist

object CoexistencePolicy {

    /**
     * A stored user choice wins; otherwise overlapping features stay off while OdinTools is installed. A [guardOnly]
     * switch (who handles a feature next to OdinTools) means nothing without OdinTools: then it is on.
     */
    fun resolve(defaultEnabled: Boolean, stored: Boolean?, odinToolsInstalled: Boolean, guardOnly: Boolean = false): Boolean =
        if (guardOnly && !odinToolsInstalled) true else stored ?: (defaultEnabled && !odinToolsInstalled)

    /** Switching an overlapping feature on next to OdinTools asks the user first. */
    fun needsConfirmation(turningOn: Boolean, odinToolsInstalled: Boolean): Boolean = turningOn && odinToolsInstalled
}
