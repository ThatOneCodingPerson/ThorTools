package io.github.thatonecodingperson.thortools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.thatonecodingperson.thortools.data.SharedPrefsRepo
import javax.inject.Inject

@HiltAndroidApp
class ThorToolsApplication : Application() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    override fun onCreate() {
        super.onCreate()
        // Before the service or the Hotkeys screen read the list.
        prefs.restoreAynPanelOnce()
        prefs.addOwnPressesOnce()
    }
}
