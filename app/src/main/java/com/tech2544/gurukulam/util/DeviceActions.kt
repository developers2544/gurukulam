package com.tech2544.gurukulam.util

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tech2544.gurukulam.wallpaper.VideoWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Wallpaper, ringtone, WhatsApp share jaise phone-level kaam. */
object DeviceActions {

    suspend fun download(ctx: Context, http: OkHttpClient, url: String, name: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "dl").apply { mkdirs() }
            val f = File(dir, name)
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed ${r.code}")
                r.body!!.byteStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            }
            f
        }

    /**
     * Wallpaper phone ki screen ke hisaab se: photo ko bina kheenche "cover" tarah zoom karke
     * beech se crop karta hai. Alag-alag phone par stretch nahi hota.
     */
    suspend fun setWallpaper(ctx: Context, file: File) = withContext(Dispatchers.IO) {
        val dm = ctx.resources.displayMetrics
        val tw = minOf(dm.widthPixels, dm.heightPixels)
        val th = maxOf(dm.widthPixels, dm.heightPixels)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
        val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Bad image")

        val scale = maxOf(tw.toFloat() / src.width, th.toFloat() / src.height)
        val sw = maxOf(tw, (src.width * scale).toInt())
        val sh = maxOf(th, (src.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(scaled, (sw - tw) / 2, (sh - th) / 2, tw, th)

        WallpaperManager.getInstance(ctx).setBitmap(
            out, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        )
        if (scaled !== src) src.recycle()
    }

    /** Live wallpaper picker kholta hai. Video app ke apne folder mein copy hoti hai. */
    suspend fun startLiveWallpaper(ctx: Context, file: File): Intent = withContext(Dispatchers.IO) {
        val dst = File(ctx.filesDir, "live.mp4")
        file.copyTo(dst, overwrite = true)
        ctx.getSharedPreferences("gk_live", Context.MODE_PRIVATE).edit().putString("path", dst.path).apply()
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(ctx, VideoWallpaperService::class.java)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun openWriteSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Android 10+ : MediaStore se Ringtones folder mein save, phir default ringtone set. */
    suspend fun setRingtone(ctx: Context, file: File, title: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.mp3")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not save ringtone")
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, uri)
    }

    /** Pehle WhatsApp, na ho to WhatsApp Business. Status lagana user WhatsApp mein khud karta hai. */
    fun shareToWhatsApp(ctx: Context, file: File, mime: String): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage(pkg)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(i)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
