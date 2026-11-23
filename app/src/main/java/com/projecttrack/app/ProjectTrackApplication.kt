package com.projecttrack.app

import android.app.Application
import com.projecttrack.app.data.AppContainer

class ProjectTrackApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startMaintenance()
    }
}
