package io.github.thatonecodingperson.thortools.setup

import io.github.thatonecodingperson.thortools.BuildConfig
import io.github.thatonecodingperson.thortools.service.ServiceStatus
import io.github.thatonecodingperson.thortools.setup.AccessReport.Companion.ok
import io.github.thatonecodingperson.thortools.setup.AccessReport.Companion.okIfNeeded
import io.github.thatonecodingperson.thortools.tools.SettingsRepo
import io.github.thatonecodingperson.thortools.tools.ShellExecutor
import javax.inject.Inject

class AccessChecker @Inject constructor(
    private val settings: SettingsRepo,
    private val executor: ShellExecutor,
    private val status: ServiceStatus,
) {
    /** Only local checks, cheap enough for the main thread. */
    fun quick(): AccessReport = AccessReport.of(
        AccessCheck.ACCESSIBILITY to ok(status.connected),
        AccessCheck.NOTIFICATIONS to ok(settings.notificationsAllowed()),
        AccessCheck.BATTERY to ok(settings.isBatteryExempt()),
    )

    /** Everything, including a root probe. Never on the main thread. */
    fun full(): AccessReport = AccessReport(
        quick().states + mapOf(
            AccessCheck.ROOT to ok(executor.probe()),
            AccessCheck.BACKGROUND_LIST to okIfNeeded(needed = !BuildConfig.DEBUG, present = settings.inBackgroundList()),
            AccessCheck.INPUT_HELPER to ok(status.rawInput?.state == CONNECTED),
        ),
    )

    private companion object {
        const val CONNECTED = "connected"
    }
}
