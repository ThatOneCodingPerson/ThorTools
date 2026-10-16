package io.github.thatonecodingperson.thortools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import io.github.thatonecodingperson.thortools.roms.RomJobRunner
import javax.inject.Inject

@HiltAndroidApp
class ThorToolsApplication : Application() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    @Inject
    lateinit var romJobs: RomJobRunner

    override fun onCreate() {
        super.onCreate()
        // Before the service or the Hotkeys screen read the list.
        prefs.restoreAynPanelOnce()
        prefs.addOwnPressesOnce()
        // A ROM compression job runs on as root when Thor Tools stops; this follows it again.
        romJobs.resume()
    }
}
