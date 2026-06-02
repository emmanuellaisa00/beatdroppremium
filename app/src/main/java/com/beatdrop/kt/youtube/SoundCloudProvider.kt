package com.beatdrop.kt.youtube

import com.beatdrop.kt.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * SoundCloud search provider — multi-platform download support.
 * Implements the SearchProvider interface so it plugs into the existing search system.
 *
 * Uses SoundCloud's public API endpoints (no API key needed) to search tracks.
 * Stream URLs are resolved via SoundCloud's oEmbed + URL resolution.
 */
class SoundCloudProvider : SearchProvider {

    companion object {
        private const val SC_API = "https://api-v2.soundcloud.com"
        // SoundCloud client ID — these rotate periodically. We discover it dynamically.
        @Volatile private var clientId: String? = null

        suspend fun discoverClientId(): String? {
            if (clientId != null) return clientId
            return withContext(Dispatchers.IO) {
                try {
                    // Fetch the SoundCloud homepage and extract the client_id from the bundled JS
                    val html = com.beatdrop.kt.youtube.okHttp.newCall(
                        Request.Builder().url("https://soundcloud.com").build()
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        resp.body?.string() ?: return@withContext null
                    }
                    // Extract JS bundle URLs
                    val jsUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^"]+\.js""").findAll(html)
                        .map { it.value }.toList()
                    for (url in jsUrls.take(5)) {
                        try {
                            val response = com.beatdrop.kt.youtube.okHttp.newCall(
                                Request.Builder().url(url).build()
                            ).execute()
                            val js = response.use { resp ->
                                if (!resp.isSuccessful) null
                                else resp.body?.string()
                            }
                            if (js == null) continue
                            // Search for client_id in the JS source
                            val match = Regex("""client_id\s*[:=]\s*"([a-zA-Z0-9]{32})"""").find(js)
                            if (match != null) {
                                clientId = match.groupValues[1]
                                DebugLog.i("soundcloud", "client_id discovered: ${clientId!!.take(8)}...")
                                return@withContext clientId
                            }
                        } catch (_: Exception) { continue }
                    }
                    null
                } catch (e: Exception) {
                    DebugLog.w("soundcloud", "client_id discovery failed: ${e.message}")
                    null
                }
            }
        }
    }

    override suspend fun search(query: String): List<OnlineResult> =
        withContext(Dispatchers.IO) {
            val id = clientId ?: discoverClientId() ?: return@withContext emptyList()
            try {
                val url = "$SC_API/search/tracks?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                        "&limit=30&offset=0&client_id=$id"
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()

                val json = com.beatdrop.kt.youtube.okHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    JSONObject(resp.body!!.string())
                }

                val collection = json.optJSONArray("collection") ?: return@withContext emptyList()
                (0 until collection.length()).mapNotNull { i ->
                    val track = collection.optJSONObject(i) ?: return@mapNotNull null
                    val videoId = track.optLong("id").toString()
                    val title = track.optString("title") ?: return@mapNotNull null
                    val artist = track.optJSONObject("user")?.optString("username") ?: ""
                    val durationMs = track.optLong("duration", 0)
                    val durationSecs = (durationMs / 1000).toInt()
                    val thumb = track.optJSONObject("artwork_url")
                        ?.optString("url")
                        ?.replace("-large", "-t300x300")
                        ?: track.optString("artwork_url", "")
                            .replace("-large", "-t300x300")
                    val permalink = track.optString("permalink_url", "")

                    OnlineResult(
                        videoId = "sc_$videoId",
                        title = title,
                        author = artist,
                        thumbnailUrl = if (thumb.isNotBlank()) thumb else null,
                        durationText = formatDuration(durationSecs),
                        durationSecs = durationSecs,
                        isLive = false,
                        sourcePlatform = "SoundCloud",
                        sourceUrl = permalink,
                    )
                }
            } catch (e: Exception) {
                DebugLog.w("soundcloud", "search failed: ${e.message}")
                emptyList()
            }
        }

    override suspend fun suggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api-v2.soundcloud.com/search/queries?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                        "&limit=8&client_id=${clientId ?: return@withContext emptyList()}"
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                val json = com.beatdrop.kt.youtube.okHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    JSONObject(resp.body!!.string())
                }
                val collection = json.optJSONArray("collection") ?: return@withContext emptyList()
                (0 until collection.length()).mapNotNull {
                    collection.optJSONObject(it)?.optString("query")
                }
            } catch (_: Exception) { emptyList() }
        }

    /**
     * Resolve a SoundCloud track to a directly-playable audio stream URL.
     */
    suspend fun resolveStreamUrl(scTrackId: String): String? {
        val id = clientId ?: discoverClientId() ?: return null
        val trackId = scTrackId.removePrefix("sc_")
        return withContext(Dispatchers.IO) {
            try {
                // Try media URL resolution
                val url = "$SC_API/tracks/$trackId?client_id=$id"
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                val json = com.beatdrop.kt.youtube.okHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    JSONObject(resp.body!!.string())
                }

                // Check for direct downloadable URL
                json.optString("downloadable").let { if (it == "true") json.optString("download_url") else null }
                    ?.let { return@withContext "${it}?client_id=$id" }

                // Check for stream URL
                json.optString("stream_url").let { if (it.isNotBlank()) "${it}?client_id=$id" else null }
                    ?.let { return@withContext it }

                // Try media transcodings
                val transcodings = json.optJSONObject("media")?.optJSONArray("transcodings")
                if (transcodings != null) {
                    for (i in 0 until transcodings.length()) {
                        val tc = transcodings.optJSONObject(i) ?: continue
                        val format = tc.optJSONObject("format")?.optString("protocol") ?: ""
                        if (format == "progressive" || format == "hls") {
                            val tcUrl = tc.optJSONObject("url")?.optString("url") ?: continue
                            val resolved = resolveRedirect("$tcUrl?client_id=$id")
                            if (resolved != null) return@withContext resolved
                        }
                    }
                }
                null
            } catch (e: Exception) {
                DebugLog.w("soundcloud", "resolve failed: ${e.message}")
                null
            }
        }
    }

    private fun resolveRedirect(url: String): String? {
        return try {
            com.beatdrop.kt.youtube.okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: return null)
                json.optString("url").ifBlank { null }
            }
        } catch (_: Exception) { null }
    }

    private fun formatDuration(secs: Int): String {
        if (secs <= 0) return "0:00"
        val m = secs / 60; val s = secs % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
