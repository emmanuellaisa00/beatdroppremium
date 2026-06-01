package com.beatdrop.kt.youtube

import android.app.Application
import android.net.Uri
import com.beatdrop.kt.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

// ─── HTTP Clients ─────────────────────────────────────────────────────────────

// Fast client for API/search calls — short timeouts are fine for JSON responses
private val okHttp = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .writeTimeout(5, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

// Dedicated download client — no read timeout ceiling so large files complete
// on slow connections without SocketTimeoutException mid-transfer
private val downloadHttp = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)    // 0 = infinite — OkHttp will not timeout reads
    .followRedirects(true)
    .build()

// ─── Constants ────────────────────────────────────────────────────────────────
private const val YT_KEY    = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
private const val YT_PLAYER = "https://www.youtube.com/youtubei/v1/player"
private const val YT_SEARCH = "https://www.youtube.com/youtubei/v1/search"
private const val IOS_UA    = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"

// Parallel chunk count and minimum file size to bother chunking
private const val CHUNK_COUNT         = 4
private const val CHUNK_MIN_BYTES     = 1_048_576L  // 1 MB

private val INVIDIOUS_INSTANCES = listOf(
    "https://yewtu.be",
    "https://invidious.privacydev.net",
    "https://inv.nadeko.net",
    "https://inv.tux.pizza",
    "https://invidious.private.coffee",
    "https://invidious.fdn.fr",
)

private data class YtClient(
    val name: String,
    val clientName: String,
    val clientVersion: String,
    val headers: Map<String, String>,
    val extraContext: JSONObject = JSONObject(),
)

private val YT_CLIENTS = listOf(
    YtClient(
        name = "IOS", clientName = "IOS", clientVersion = "20.03.02",
        headers = mapOf(
            "User-Agent"              to IOS_UA,
            "X-Youtube-Client-Name"   to "5",
            "X-Youtube-Client-Version" to "20.03.02",
        ),
        extraContext = JSONObject().apply {
            put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2")
            put("osName", "iPhone");   put("osVersion", "18.2.1.22C161")
        }
    ),
    YtClient(
        name = "MWEB", clientName = "MWEB", clientVersion = "2.20241202.07.00",
        headers = mapOf(
            "User-Agent"              to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15",
            "X-Youtube-Client-Name"   to "2",
            "X-Youtube-Client-Version" to "2.20241202.07.00",
            "Origin"                  to "https://m.youtube.com",
        )
    ),
    YtClient(
        name = "WEB_EMBEDDED", clientName = "WEB_EMBEDDED_PLAYER", clientVersion = "2.20241202.07.00",
        headers = mapOf(
            "User-Agent"              to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "X-Youtube-Client-Name"   to "56",
            "X-Youtube-Client-Version" to "2.20241202.07.00",
            "Origin"                  to "https://www.youtube.com",
        )
    ),
    YtClient(
        name = "ANDROID_VR", clientName = "ANDROID_VR", clientVersion = "1.60.19",
        headers = mapOf(
            "User-Agent"              to "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            "X-Youtube-Client-Name"   to "28",
            "X-Youtube-Client-Version" to "1.60.19",
        ),
        extraContext = JSONObject().put("deviceMake", "Oculus").put("deviceModel", "Quest 3").put("androidSdkVersion", 32)
    ),
)

// ─── URL Cache (4-hour TTL) ───────────────────────────────────────────────────
private data class CacheEntry(val url: String, val cachedAt: Long)
private val urlCache = ConcurrentHashMap<String, CacheEntry>()
private const val URL_TTL_MS = 4 * 60 * 60 * 1000L

private fun getCachedUrl(videoId: String): String? {
    val e = urlCache[videoId] ?: return null
    if (System.currentTimeMillis() - e.cachedAt > URL_TTL_MS) { urlCache.remove(videoId); return null }
    return e.url
}
private fun setCachedUrl(videoId: String, url: String) {
    urlCache[videoId] = CacheEntry(url, System.currentTimeMillis())
}

// ─── Application context ──────────────────────────────────────────────────────
object YoutubeService {
    internal var app: Application? = null
    fun init(application: Application) { app = application }
    val downloadDir: File?
        get() = app?.getExternalFilesDir(null)
            ?.let { File(it, "BeatDrop/Downloads") }
            ?.also { it.mkdirs() }
}

// ─── Search ───────────────────────────────────────────────────────────────────
suspend fun searchYoutube(query: String, maxResults: Int = 20): List<OnlineResult> =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("query", musicifyQuery(query.trim()))
            put("context", JSONObject().put("client", JSONObject().apply {
                put("clientName", "MWEB"); put("clientVersion", "2.20241202.07.00")
                put("hl", "en"); put("gl", "US"); put("utcOffsetMinutes", 0)
            }))
            put("params", "EgWKAQIIAQ%3D%3D")
        }.toString()

        val req = Request.Builder()
            .url("$YT_SEARCH?key=$YT_KEY&prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
            .header("X-Youtube-Client-Name", "2")
            .header("X-Youtube-Client-Version", "2.20241202.07.00")
            .header("Origin", "https://m.youtube.com")
            .header("Referer", "https://m.youtube.com/")
            .build()

        val json = okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Search failed (${resp.code})")
            JSONObject(resp.body!!.string())
        }

        extractVideoRenderers(json)
            .mapNotNull { parseInnertubeRenderer(it) }
            .filter { !it.isLive && it.durationSecs <= 600 && (it.durationSecs == 0 || it.durationSecs >= 60) }
            .sortedByDescending { musicRelevanceScore(it) }
            .take(maxResults)
    }

// ─── Search suggestions ───────────────────────────────────────────────────────
suspend fun getSearchSuggestions(query: String): List<String> =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val urls = listOf(
            "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&q=${Uri.encode(query)}&callback=f",
            "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${Uri.encode(query)}",
        )
        for (url in urls) {
            try {
                val text = okHttp.newCall(Request.Builder().url(url).build()).execute()
                    .use { it.body!!.string() }
                val m = Regex("\\[.*]", RegexOption.DOT_MATCHES_ALL).find(text)?.value ?: continue
                val arr = JSONArray(m)
                if (arr.length() < 2) continue
                val second = arr.get(1)
                val suggestions = when {
                    second is JSONArray && second.length() > 0 && second.get(0) is String ->
                        (0 until second.length()).map { second.getString(it) }
                    second is JSONArray ->
                        (0 until second.length()).mapNotNull {
                            (second.get(it) as? JSONArray)?.getString(0)
                        }
                    else -> continue
                }
                if (suggestions.isNotEmpty()) return@withContext suggestions.take(8)
            } catch (_: Exception) {}
        }
        emptyList()
    }

// ─── Stream URL resolution ────────────────────────────────────────────────────
/**
 * SnapTube strategy order:
 *   1. WebView extractor  — YouTube's own JS handles DroidGuard, n-sig,
 *                           signatureCipher. shouldInterceptRequest captures the
 *                           final googlevideo.com URL. Most reliable.
 *   2. IOS Innertube      — iOS client returns plain URLs most of the time,
 *                           exempt from web BotGuard checks.
 *   3. Other Innertube    — MWEB, WEB_EMBEDDED, ANDROID_VR as fallbacks.
 *   4. Invidious          — Public third-party instances as last resort.
 */
suspend fun getStreamUrl(videoId: String): String {
    getCachedUrl(videoId)?.let { return it }

    // Strategy 1 — WebView extractor (primary, SnapTube approach)
    if (YoutubeExtractor.isConfigured) {
        try {
            val url = YoutubeExtractor.extractStreamUrl(videoId, 12_000)
            if (!url.isNullOrBlank()) { setCachedUrl(videoId, url); return url }
        } catch (_: Exception) {}
    }

    // Strategy 2–4 — Innertube /player API clients
    for (client in YT_CLIENTS) {
        try {
            val body = buildPlayerBody(videoId, client)
            val req = Request.Builder()
                .url("$YT_PLAYER?key=$YT_KEY&prettyPrint=false")
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { client.headers.forEach { (k, v) -> header(k, v) } }
                .header("Content-Type", "application/json")
                .build()

            val data = okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body!!.string())
            } ?: continue
            if (data.optJSONObject("playabilityStatus")?.optString("status") != "OK") continue

            val formats = data.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            val url = getBestAudioUrl(formats)
            if (!url.isNullOrBlank()) { setCachedUrl(videoId, url); return url }
        } catch (_: Exception) {}
    }

    // Strategy 5 — Invidious public instances
    for (instance in INVIDIOUS_INSTANCES) {
        try {
            val data = okHttp.newCall(
                Request.Builder()
                    .url("$instance/api/v1/videos/$videoId?fields=adaptiveFormats,formatStreams")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body!!.string())
            } ?: continue
            val url = getBestAudioUrl(data.optJSONArray("adaptiveFormats"))
                ?: data.optJSONArray("formatStreams")?.let {
                    if (it.length() > 0) it.getJSONObject(0).optString("url") else null
                }
            if (!url.isNullOrBlank()) { setCachedUrl(videoId, url); return url }
        } catch (_: Exception) {}
    }

    throw Exception("Could not load this track. Try a different song or check your connection.")
}

// ─── Download ─────────────────────────────────────────────────────────────────
data class DownloadProgress(val bytesWritten: Long, val contentLength: Long, val percent: Int)

/**
 * SnapTube-style download:
 *   - Resolves the stream URL using the same strategy chain as getStreamUrl
 *   - Probes content-length + Accept-Ranges to decide between chunked/serial
 *   - Parallel chunked download (CHUNK_COUNT simultaneous HTTP Range requests)
 *     for files above CHUNK_MIN_BYTES — same technique as SnapTube's engine
 *   - Uses downloadHttp (no read timeout) so slow connections don't time out mid-file
 */
suspend fun downloadYoutubeTrack(
    result: OnlineResult,
    onProgress: (DownloadProgress) -> Unit = {},
): Track = withContext(Dispatchers.IO) {
    val dir = YoutubeService.downloadDir
        ?: throw Exception("External storage not available")

    val streamUrl = getStreamUrl(result.videoId)

    val safeTitle = result.title.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").take(80).trim()
    val fileExt = when {
        streamUrl.contains("mime=audio%2Fwebm") ||
        streamUrl.contains("mime=audio/webm")   -> "opus"
        else                                     -> "m4a"
    }
    val fileName = "${safeTitle}_${result.videoId}.$fileExt"
    val filePath = File(dir, fileName)

    // HEAD probe: get content-length and check Range support
    val head = downloadHttp.newCall(
        Request.Builder().url(streamUrl).head().header("User-Agent", IOS_UA).build()
    ).execute()
    val contentLength  = head.header("Content-Length")?.toLongOrNull() ?: 0L
    val acceptsRanges  = head.header("Accept-Ranges")
        ?.equals("bytes", ignoreCase = true) == true

    if (acceptsRanges && contentLength >= CHUNK_MIN_BYTES) {
        downloadChunked(streamUrl, filePath, contentLength, onProgress)
    } else {
        downloadSerial(streamUrl, filePath, contentLength, onProgress)
    }

    check(filePath.exists() && filePath.length() >= 1024) {
        "Download produced an empty or corrupt file."
    }

    // Thumbnail
    val artworkPath = result.thumbnailUrl?.let { thumbUrl ->
        runCatching {
            val artFile = File(dir, "${safeTitle}_${result.videoId}.jpg")
            downloadHttp.newCall(Request.Builder().url(thumbUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    FileOutputStream(artFile).use { it.write(resp.body!!.bytes()) }
                    "file://${artFile.absolutePath}"
                } else null
            }
        }.getOrNull()
    } ?: result.thumbnailUrl

    val (parsedTitle, parsedArtist) = parseTitle(result.title, result.author)
    val meta = runCatching { enrichTrackMetadata(parsedTitle, parsedArtist) }.getOrNull()

    Track(
        id        = "dl_${result.videoId}",
        uri       = Uri.fromFile(filePath),
        title     = meta?.title     ?: parsedTitle,
        artist    = meta?.artist    ?: parsedArtist,
        album     = meta?.album     ?: result.author,
        albumId   = 0L,
        durationMs = result.durationSecs * 1000L,
        data      = filePath.absolutePath,
        dateAdded = System.currentTimeMillis(),
        artworkOverride = meta?.artwork ?: artworkPath,
    )
}

/**
 * Parallel chunked download — CHUNK_COUNT simultaneous Range requests.
 * FileChannel.write(ByteBuffer, position) is thread-safe for non-overlapping
 * positions, so each coroutine writes its chunk at its own offset without locks.
 */
private suspend fun downloadChunked(
    url: String,
    file: File,
    contentLength: Long,
    onProgress: (DownloadProgress) -> Unit,
) = withContext(Dispatchers.IO) {
    val chunkSize = (contentLength + CHUNK_COUNT - 1) / CHUNK_COUNT
    val written   = AtomicLong(0L)

    RandomAccessFile(file, "rw").use { raf ->
        raf.setLength(contentLength)
        val channel = raf.channel

        (0 until CHUNK_COUNT).map { i ->
            async(Dispatchers.IO) {
                val start = i * chunkSize
                val end   = minOf(start + chunkSize - 1, contentLength - 1)

                val req = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$start-$end")
                    .header("User-Agent", IOS_UA)
                    .build()

                downloadHttp.newCall(req).execute().use { resp ->
                    check(resp.code in 200..206) { "Chunk $i HTTP ${resp.code}" }
                    var pos = start
                    val buf = ByteArray(65_536)
                    resp.body!!.byteStream().use { inp ->
                        while (true) {
                            val n = inp.read(buf)
                            if (n == -1) break
                            // Positional write — thread-safe, no mutex needed
                            channel.write(ByteBuffer.wrap(buf, 0, n), pos)
                            pos += n
                            val total = written.addAndGet(n.toLong())
                            val pct   = ((total * 100) / contentLength).toInt()
                            onProgress(DownloadProgress(total, contentLength, pct))
                        }
                    }
                }
            }
        }.awaitAll()
    }
}

/** Serial fallback for servers that don't support Range requests or small files */
private suspend fun downloadSerial(
    url: String,
    file: File,
    contentLength: Long,
    onProgress: (DownloadProgress) -> Unit,
) = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url).header("User-Agent", IOS_UA).build()
    downloadHttp.newCall(req).execute().use { resp ->
        check(resp.isSuccessful) { "Download failed (HTTP ${resp.code})" }
        val body = resp.body ?: throw Exception("Empty response body")
        val len  = if (contentLength > 0) contentLength else body.contentLength()
        var done = 0L
        FileOutputStream(file).use { fos ->
            body.byteStream().use { inp ->
                val buf = ByteArray(65_536)
                while (true) {
                    val n = inp.read(buf)
                    if (n == -1) break
                    fos.write(buf, 0, n)
                    done += n
                    val pct = if (len > 0) ((done * 100) / len).toInt() else 0
                    onProgress(DownloadProgress(done, len, pct))
                }
            }
        }
    }
}

// ─── iTunes metadata enrichment ──────────────────────────────────────────────
data class EnrichedMeta(val artwork: String?, val album: String?, val artist: String?, val title: String? = null)
private val metaCache = ConcurrentHashMap<String, EnrichedMeta>()

suspend fun enrichTrackMetadata(title: String, artist: String): EnrichedMeta =
    withContext(Dispatchers.IO) {
        val key = "${artist.lowercase()}::${title.lowercase()}"
        metaCache[key]?.let { return@withContext it }
        try {
            val q    = Uri.encode("$artist $title")
            val data = okHttp.newCall(
                Request.Builder()
                    .url("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=5")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body!!.string())
            }
            val results = data?.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val match = (0 until results.length()).map { results.getJSONObject(it) }
                    .firstOrNull { it.optString("artistName").lowercase().startsWith(artist.lowercase().take(4)) }
                    ?: results.getJSONObject(0)
                val art = match.optString("artworkUrl100", "")
                    .replace("100x100bb", "600x600bb").replace("100x100", "600x600")
                val meta = EnrichedMeta(
                    artwork = art.ifEmpty { null },
                    album   = match.optString("collectionName").ifEmpty { null },
                    artist  = match.optString("artistName").ifEmpty { null },
                    title   = match.optString("trackName").ifEmpty { null },
                )
                metaCache[key] = meta
                return@withContext meta
            }
        } catch (_: Exception) {}
        EnrichedMeta(null, null, null, null).also { metaCache[key] = it }
    }

// ─── Convert search result → Track (for streaming) ───────────────────────────
suspend fun youtubeResultToTrack(result: OnlineResult): Track {
    val streamUrl = getStreamUrl(result.videoId)
    val (title, artist) = parseTitle(result.title, result.author)
    return Track(
        id        = "yt_${result.videoId}",
        uri       = Uri.parse(streamUrl),
        title     = title,
        artist    = artist,
        album     = result.author,
        albumId   = 0L,
        durationMs = result.durationSecs * 1000L,
        data      = null,
        dateAdded = System.currentTimeMillis(),
        artworkOverride = result.thumbnailUrl,
    )
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private fun buildPlayerBody(videoId: String, client: YtClient): String =
    JSONObject().apply {
        put("videoId", videoId)
        put("context", JSONObject().put("client", JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en"); put("gl", "US")
            client.extraContext.keys().forEach { k -> put(k, client.extraContext.get(k)) }
        }))
        if (client.clientName != "IOS") {
            put("playbackContext", JSONObject().put("contentPlaybackContext",
                JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))
        }
        put("contentCheckOk", true); put("racyCheckOk", true)
    }.toString()

private fun getBestAudioUrl(formats: JSONArray?): String? =
    getBestAudioFormat(formats)?.let { f ->
        f.optString("url").ifBlank { null }
    }

/**
 * Selects the highest-bitrate audio format.
 * Also handles signatureCipher formats by extracting the raw URL
 * (works as-is for many streams; n-sig already applied by the IOS client).
 */
private fun getBestAudioFormat(formats: JSONArray?): JSONObject? {
    if (formats == null) return null
    val all = (0 until formats.length()).map { formats.getJSONObject(it) }

    // Prefer formats with a plain URL (no cipher decoding needed)
    val withUrl = all.filter { f ->
        val mt = (f.optString("mimeType") + f.optString("type")).lowercase()
        mt.startsWith("audio/") && f.optString("url").isNotBlank()
    }.sortedByDescending { f ->
        f.optLong("bitrate").coerceAtLeast(f.optLong("averageBitrate"))
    }
    if (withUrl.isNotEmpty()) return withUrl.first()

    // Fallback: extract URL from signatureCipher/cipher without the signature
    // (works on IOS-client streams; other clients may throttle without sig)
    val withCipher = all.filter { f ->
        val mt = (f.optString("mimeType") + f.optString("type")).lowercase()
        mt.startsWith("audio/") && (f.has("signatureCipher") || f.has("cipher"))
    }.sortedByDescending { f ->
        f.optLong("bitrate").coerceAtLeast(f.optLong("averageBitrate"))
    }
    return withCipher.firstOrNull()?.let { f ->
        val cipherStr = f.optString("signatureCipher").ifBlank { f.optString("cipher") }
        if (cipherStr.isBlank()) return@let null
        val params = cipherStr.split("&").associate { param ->
            val eq = param.indexOf('=')
            if (eq == -1) param to "" else
                param.substring(0, eq) to Uri.decode(param.substring(eq + 1))
        }
        val rawUrl = params["url"] ?: return@let null
        if (rawUrl.isBlank()) return@let null
        JSONObject(f.toString()).apply { put("url", rawUrl) }
    }
}

private fun musicifyQuery(q: String): String {
    val lower = q.lowercase()
    return if (lower.contains("official audio") || lower.contains("lyrics") ||
        lower.contains("audio") || lower.contains("music video")) q
    else "$q official audio"
}

private fun musicRelevanceScore(r: OnlineResult): Int {
    var s = 0
    val t = r.title.lowercase(); val c = r.author.lowercase()
    if (t.contains("official audio"))   s += 40
    if (t.contains("lyrics"))           s += 30
    if (t.contains("lyric video"))      s += 25
    if (t.contains("audio"))            s += 15
    if (t.contains("cover"))            s += 5
    if (c.contains("vevo"))             s += 35
    if (c.contains("records"))          s += 10
    if (c.contains("music"))            s += 5
    if (t.contains("reaction"))         s -= 50
    if (t.contains("interview"))        s -= 40
    if (t.contains("behind the scene")) s -= 30
    if (t.contains("review"))           s -= 20
    if (t.contains("tutorial"))         s -= 40
    if (Regex("^.{2,40}\\s[-–—]\\s.{2,60}$").matches(r.title)) s += 20
    if (r.durationSecs in 120..420) s += 10
    return s
}

internal fun extractVideoRenderers(obj: JSONObject, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
    if (obj.has("videoRenderer") && obj.optJSONObject("videoRenderer")?.has("videoId") == true) {
        out.add(obj.getJSONObject("videoRenderer")); return out
    }
    if (obj.has("videoWithContextRenderer") && obj.optJSONObject("videoWithContextRenderer")?.has("videoId") == true) {
        out.add(obj.getJSONObject("videoWithContextRenderer")); return out
    }
    obj.keys().forEach { key ->
        when (val v = obj.opt(key)) {
            is JSONObject -> extractVideoRenderers(v, out)
            is JSONArray  -> extractVideoRenderers(v, out)
        }
    }
    return out
}

private fun extractVideoRenderers(arr: JSONArray, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
    for (i in 0 until arr.length()) {
        when (val v = arr.opt(i)) {
            is JSONObject -> extractVideoRenderers(v, out)
            is JSONArray  -> extractVideoRenderers(v, out)
        }
    }
    return out
}

internal fun parseInnertubeRenderer(vr: JSONObject): OnlineResult? {
    val videoId  = vr.optString("videoId").ifEmpty { return null }
    val rawTitle = htmlDecode(
        vr.optJSONObject("headline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: vr.optJSONObject("title")?.optString("simpleText") ?: ""
    ).ifEmpty { return null }
    val rawAuthor = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
    val lengthText = vr.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        ?: vr.optJSONObject("lengthText")?.optString("simpleText")
        ?: vr.optJSONObject("lengthText")?.optJSONObject("accessibility")
            ?.optJSONObject("accessibilityData")?.optString("label") ?: ""
    val duration   = parseTimestamp(lengthText)
    val thumbsArr  = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
    val thumb      = if (thumbsArr != null && thumbsArr.length() > 0)
        thumbsArr.getJSONObject(thumbsArr.length() - 1).optString("url",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
    else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val (cleanTitle, cleanArtist) = parseTitle(rawTitle, rawAuthor)
    return OnlineResult(
        videoId = videoId, title = cleanTitle, author = cleanArtist,
        thumbnailUrl = thumb, durationText = formatTime(duration),
        durationSecs = duration, isLive = lengthText.isEmpty(),
    )
}

private fun parseTitle(raw: String, channelTitle: String): Pair<String, String> {
    var title  = raw
    var artist = channelTitle
        .replace(Regex("VEVO|Official|Music|Channel|TV|Topic", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[-–—]\\s*"), "").trim()
    val dash = Regex("^(.+?)\\s*[-–—]\\s*(.+?)(?:\\s*[\\(\\[].*)?$").find(raw)
    if (dash != null) { artist = dash.groupValues[1].trim(); title = dash.groupValues[2].trim() }
    title = title
        .replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\[Official.*?]",  RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\(Audio.*?\\)",   RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\(Lyric.*?\\)",   RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\(Music Video\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\(ft\\..*?\\)",   RegexOption.IGNORE_CASE), "")
        .trim()
    return Pair(title.ifEmpty { raw }, artist.ifEmpty { channelTitle })
}

private fun parseTimestamp(s: String): Int {
    if (s.isBlank()) return 0
    val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}

private fun formatTime(s: Int): String {
    if (s <= 0) return "0:00"
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun htmlDecode(s: String) = s
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")
