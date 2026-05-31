package com.beatdrop.kt.youtube

/**
 * Online search result — mirrors YoutubeSearchResult from YoutubeService.ts.
 * Added durationSecs + isLive compared to the original shell.
 */
data class OnlineResult(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val durationText: String,
    val durationSecs: Int = 0,
    val isLive: Boolean = false,
)

/** Pluggable search backend — the UI only talks to this interface. */
interface SearchProvider {
    suspend fun search(query: String): List<OnlineResult>
    suspend fun suggestions(query: String): List<String> = emptyList()
}

object NotConfiguredProvider : SearchProvider {
    override suspend fun search(query: String): List<OnlineResult> = emptyList()
}

/**
 * Production search backend — calls YouTube Innertube /search directly.
 * No API key, no quota. Mirrors searchYoutube() from YoutubeService.ts.
 */
class InnertubeSearchProvider : SearchProvider {
    override suspend fun search(query: String): List<OnlineResult> =
        searchYoutube(query)

    override suspend fun suggestions(query: String): List<String> =
        getSearchSuggestions(query)
}

/** Injection point. Set in BeatDropApp.onCreate(). */
object OnlineSearch {
    @Volatile var provider: SearchProvider = NotConfiguredProvider
    val isConfigured: Boolean get() = provider !== NotConfiguredProvider
}
