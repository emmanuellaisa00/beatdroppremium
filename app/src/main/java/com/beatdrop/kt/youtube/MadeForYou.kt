package com.beatdrop.kt.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Curated "Made for You" playlist hub for the Discover screen.
 *
 * Without a logged-in YouTube Music account we can't fetch true
 * personalised feeds (`FEmusic_home`, "Daily Mix", "My Supermix" require
 * an authenticated `SAPISIDHASH`). What we *can* fetch with the open
 * Innertube `BROWSE` endpoint:
 *
 *   • Any **public** YT Music playlist by `playlistId`
 *     (delegated to YouTubePlaylist.fetchPlaylist)
 *
 * So we hand-pick ~10 long-standing public playlists that cover the
 * dominant musical territories, present them as cards in the Discover
 * screen ("Made for You" / "Spotify Mixes" UX), and the first few tracks
 * of each playlist auto-populate the carousel preview rows.
 *
 * The list is small on purpose — every entry costs one Innertube round-trip
 * on first load, after which `cachedPlaylists` keeps them in RAM. A weekly
 * refresh on cold-launch matches how the playlists themselves rotate.
 */
object MadeForYou {

    /**
     * Curated card. `subtitle` is shown under the title in the carousel
     * tile; `accentHex` tints the gradient corner badge so the cards
     * look distinct from each other (matches Spotify's coloured mix cards).
     */
    data class FeaturedPlaylist(
        val playlistId: String,
        val title: String,
        val subtitle: String,
        val accentHex: Long,
    )

    /**
     * Public YT Music playlist IDs. These are long-standing curated lists
     * that YouTube maintains; the music inside them rotates while the
     * playlist URL stays stable.
     */
    val featured: List<FeaturedPlaylist> = listOf(
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_kFQXdnqMaQCVx2wpUM4ZfbsGCDibZtkJk",
            title = "Today's Hits",
            subtitle = "The biggest songs right now",
            accentHex = 0xFFE53935,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFY",
            title = "Pop Essentials",
            subtitle = "Pop that defined the decade",
            accentHex = 0xFFEC407A,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_lf_e1YL1tj49v2EOe2T9DPyl7c5niCXEU",
            title = "Hip-Hop Now",
            subtitle = "Today's biggest rap",
            accentHex = 0xFFFFB300,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_kqM3hL3WkD3-_uJZ9_GJhMHnHFhQ8Wfys",
            title = "R&B Heat",
            subtitle = "Smooth, soulful, current",
            accentHex = 0xFF8E24AA,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_mHAEb33pqvgdtuxsemicZNu-5w6rLRweo",
            title = "Afrobeats",
            subtitle = "The sound of Lagos & beyond",
            accentHex = 0xFF00897B,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_kuo_NioExeUmw07tFKBQAJrJ9CbCAGRng",
            title = "Latin Heat",
            subtitle = "Reggaeton, Latin pop, more",
            accentHex = 0xFFFB8C00,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_lBNUleBV3zykOmstKvU13l4Bn8gKvxXKs",
            title = "Workout Mix",
            subtitle = "High-energy fuel",
            accentHex = 0xFFD81B60,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_lBNUleBV3zykOmstKvU13l4Bn8gKvxXKs",
            title = "Chill Vibes",
            subtitle = "Slow it down",
            accentHex = 0xFF26A69A,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_lEvc3sQuk9OuczyKBcFRzeOroOXfqxYW0",
            title = "Sleep & Focus",
            subtitle = "Quiet instrumental",
            accentHex = 0xFF5C6BC0,
        ),
        FeaturedPlaylist(
            playlistId = "RDCLAK5uy_kFOyVe-iAqOZsdpYZJX3RKvqRtsBjGzpY",
            title = "Throwbacks",
            subtitle = "Hits from yesterday",
            accentHex = 0xFF6D4C41,
        ),
    )

    /**
     * Fetch the first [previewTracks] tracks of every curated playlist
     * in parallel. Failed fetches are returned as empty `videos` lists
     * (the caller renders them as a placeholder card rather than dropping
     * the whole tile).
     */
    suspend fun fetchAll(previewTracks: Int = 4): List<PlaylistPreview> = coroutineScope {
        withContext(Dispatchers.IO) {
            featured.map { pl ->
                async {
                    val info = runCatching {
                        YouTubePlaylist.fetchPlaylist(pl.playlistId, maxItems = previewTracks * 4)
                    }.getOrNull()
                    PlaylistPreview(
                        meta = pl,
                        coverUrl = info?.videos?.firstOrNull()?.thumbnailUrl,
                        tracks = info?.videos.orEmpty().take(previewTracks),
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * A single playlist tile ready for rendering on Discover.
     *
     *  • `meta`     — the curated metadata (title, subtitle, accent).
     *  • `coverUrl` — first track's thumbnail, used as the tile cover
     *                 (already passed through `upgradeThumbnailUrl`).
     *  • `tracks`   — preview list (first N tracks), surfaced as the
     *                 "tap-to-play" carousel under the tile.
     */
    data class PlaylistPreview(
        val meta: FeaturedPlaylist,
        val coverUrl: String?,
        val tracks: List<OnlineResult>,
    )
}
