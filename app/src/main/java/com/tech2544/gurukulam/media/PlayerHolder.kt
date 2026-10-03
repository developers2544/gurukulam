package com.tech2544.gurukulam.media

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tech2544.gurukulam.data.Content
import com.tech2544.gurukulam.data.PremiumRequired
import com.tech2544.gurukulam.data.Repo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** App ka audio player. Asli playback PlaybackService mein hota hai (background ke liye). */
class PlayerHolder(private val app: Application, private val repo: Repo) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ready = CompletableDeferred<MediaController>()
    private var connecting = false
    private var ctl: MediaController? = null
    private var sleepJob: Job? = null

    val current = MutableStateFlow<Content?>(null)
    val isPlaying = MutableStateFlow(false)
    val needPremium = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val sleepMinutes = MutableStateFlow(0)

    private var queue: List<Content> = emptyList()
    private var index = -1

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerHolder.isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) next()
        }
    }

    private fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.addListener(listener)
                ctl = c
                ready.complete(c)
            } catch (e: Exception) {
                connecting = false
                error.value = e.message
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private suspend fun controller(): MediaController {
        connect()
        return ready.await()
    }

    fun play(list: List<Content>, start: Int) {
        queue = list
        startAt(start)
    }

    private fun startAt(i: Int) {
        if (i !in queue.indices) return
        index = i
        val c = queue[i]
        current.value = c
        scope.launch {
            try {
                val url = repo.mediaUrl(c)
                val mc = controller()
                val meta = MediaMetadata.Builder()
                    .setTitle(c.title)
                    .setArtist(c.subtitle)
                    .setArtworkUri(c.thumbUrl?.let { Uri.parse(it) })
                    .build()
                mc.setMediaItem(MediaItem.Builder().setMediaId(c.id).setUri(url).setMediaMetadata(meta).build())
                mc.prepare()
                mc.play()
            } catch (e: PremiumRequired) {
                current.value = null
                needPremium.value = true
            } catch (e: Exception) {
                error.value = e.message ?: "Playback error"
            }
        }
    }

    fun toggle() {
        val c = ctl ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = startAt(index + 1)
    fun previous() = startAt(index - 1)

    fun seekTo(ms: Long) {
        ctl?.seekTo(ms)
    }

    fun positionMs(): Long = ctl?.currentPosition ?: 0L
    fun durationMs(): Long = ctl?.duration?.takeIf { it > 0 } ?: 0L

    fun stop() {
        ctl?.run { pause(); clearMediaItems() }
        current.value = null
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutes.value = minutes
        if (minutes <= 0) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            ctl?.pause()
            sleepMinutes.value = 0
        }
    }
}
