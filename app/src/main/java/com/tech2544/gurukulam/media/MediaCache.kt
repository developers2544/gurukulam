package com.tech2544.gurukulam.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/** Dekhi hui video phone mein (300MB tak) save rehti hai, taaki dobara data na lage. */
@OptIn(UnstableApi::class)
object MediaCache {
    private var cache: SimpleCache? = null

    @Synchronized
    private fun get(ctx: Context): SimpleCache =
        cache ?: SimpleCache(
            File(ctx.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            StandaloneDatabaseProvider(ctx.applicationContext),
        ).also { cache = it }

    fun newPlayer(ctx: Context): ExoPlayer {
        val factory = CacheDataSource.Factory()
            .setCache(get(ctx))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return ExoPlayer.Builder(ctx.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
    }
}
