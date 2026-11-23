package com.example

import android.app.Application
import com.example.data.local.NoorDatabase
import com.example.data.local.NoorDatabaseSeeder
import com.example.notifications.NotificationHelper
import com.example.quran.MushafCoordinateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoorApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: NoorDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = NoorDatabase.getInstance(this)
        NotificationHelper.ensureChannels(this)
        appScope.launch {
            NoorDatabaseSeeder.seedIfEmpty(database)
            MushafCoordinateEngine.loadSavedPackFromDisk(this@NoorApplication)
        }
    }
}
