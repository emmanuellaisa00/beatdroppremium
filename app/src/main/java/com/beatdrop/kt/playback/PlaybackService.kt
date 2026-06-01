package com.beatdrop.kt.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 MediaSessionService — replaces react-native-track-player.
 * Provides background playback + the system media notification automatically.
 *
 * HTTP headers are injected so YouTube stream URLs (which are IP-bound and
 * Referer-gated) resolve correctly in ExoPlayer.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // YouTube CDN requires Referer + Origin; without them streams return 403
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin"  to "https://www.youtube.com",
            ))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        // Bind the real native EQ/BassBoost to this player's audio session.
        EqEngine.attach(player.audioSessionId)
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        EqEngine.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
