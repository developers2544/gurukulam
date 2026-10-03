package com.tech2544.gurukulam.push

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tech2544.gurukulam.GurukulamApp
import com.tech2544.gurukulam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val c = (application as GurukulamApp).container
        if (!c.supa.loggedIn.value) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { c.repo.saveFcm(token, c.state.profile.value?.notifyHour ?: 8) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val n = NotificationCompat.Builder(this, "daily")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), n)
    }
}
