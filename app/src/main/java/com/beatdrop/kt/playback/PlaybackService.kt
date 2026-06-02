package com.beatdrop.kt.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.net.Uri

/**
 * Media3 MediaSessionService — replaces react-native-track-player.
 * Provides background playback + the system media notification automatically.
 *
 * Per-stream headers
 * ──────────────────
 * googlevideo CDN URLs resolved by certain clients (e.g. ANDROID_VR) are bound
 * to that client's User-Agent — fetching them with a generic browser UA returns
 * **HTTP 403**. We carry the resolving client's UA + Origin/Referer alongside
 * the URL via the URI fragment, and a `ResolvingDataSource` applies them as
 * HTTP request headers + the `User-Agent` on the underlying HttpDataSource.
 *
 * The fragment encoding is Base64URL (`StreamHeaderCodec`) — chosen because it
 * round-trips through `Uri.parse(...)` without re-encoding, unlike a naive
 * `k=v&k=v` fragment.
 *
 * Streams that don't have our fragment (local files, Piped HTTPS, Invidious)
 * fall through and use the default UA.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    // Default UA — used when the resolved URL doesn't carry its own.
    // Piped proxies, Invidious direct, and most CDNs accept this.
    private val defaultUa =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(defaultUa)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        // Wrap in a ResolvingDataSource so we can inspect each DataSpec and:
        //   1. Strip our `#bdh1.<base64>` fragment from the URI before the
        //      request goes on the wire (CDNs reject unknown query/fragments).
        //   2. Re-apply the encoded UA + Referer/Origin headers per-request,
        //      so the CDN sees exactly the identity it expects.
        val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { spec: DataSpec ->
            val uri: Uri = spec.uri
            val fragment = uri.fragment
            val headers = StreamHeaderCodec.decode(fragment)
            if (headers == null || headers.isEmpty()) {
                spec  // no fragment → leave it alone
            } else {
                // Strip the fragment from the wire URI.
                val cleanUri = uri.buildUpon().fragment(null).build()
                val ua = headers[StreamHeaderCodec.userAgentKey()]
                val extraHeaders = headers.filterKeys { it != StreamHeaderCodec.userAgentKey() }
                var builder = spec.buildUpon().setUri(cleanUri)
                if (extraHeaders.isNotEmpty()) {
                    val merged = LinkedHashMap(spec.httpRequestHeaders).apply {
                        putAll(extraHeaders)
                        if (!ua.isNullOrBlank()) put("User-Agent", ua)
                    }
                    builder = builder.setHttpRequestHeaders(merged)
                } else if (!ua.isNullOrBlank()) {
                    val merged = LinkedHashMap(spec.httpRequestHeaders).apply {
                        put("User-Agent", ua)
                    }
                    builder = builder.setHttpRequestHeaders(merged)
                }
                builder.build()
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, resolvingFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .build()

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
