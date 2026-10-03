package com.tech2544.gurukulam.wallpaper

import android.content.Context
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/** Live wallpaper: chhoti loop video (bina awaaz). Screen band hone par ruk jaati hai (battery bachti hai). */
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var mp: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val path = getSharedPreferences("gk_live", Context.MODE_PRIVATE).getString("path", null) ?: return
            try {
                mp = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(path)
                    isLooping = true
                    setVolume(0f, 0f)
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                release()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            try {
                if (visible) mp?.start() else mp?.pause()
            } catch (_: Exception) {
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            super.onSurfaceDestroyed(holder)
        }

        private fun release() {
            mp?.release()
            mp = null
        }
    }
}
