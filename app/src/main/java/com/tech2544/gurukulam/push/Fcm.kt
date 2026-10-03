package com.tech2544.gurukulam.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Firebase set up na ho (google-services.json nahi) to null deta hai, app crash nahi hota. */
suspend fun fcmToken(): String? = try {
    suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
            cont.resume(if (t.isSuccessful) t.result else null)
        }
    }
} catch (e: Exception) {
    null
}
