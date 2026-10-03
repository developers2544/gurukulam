package com.tech2544.gurukulam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tech2544.gurukulam.billing.BillingManager
import com.tech2544.gurukulam.data.AppState
import com.tech2544.gurukulam.data.Repo
import com.tech2544.gurukulam.data.Supa
import com.tech2544.gurukulam.media.PlayerHolder

class AppContainer(app: Application) {
    val supa = Supa(app)
    val repo = Repo(supa)
    val state = AppState(repo)
    val player = PlayerHolder(app, repo)
    val billing = BillingManager(app, repo)
}

class GurukulamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("daily", "Daily updates", NotificationManager.IMPORTANCE_DEFAULT))
    }
}
