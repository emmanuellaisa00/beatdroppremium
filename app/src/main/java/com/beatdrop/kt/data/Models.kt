package com.beatdrop.kt.data

import android.net.Uri

/**
 * Port of the RN Track type.
 * artworkOverride: used for YouTube tracks where albumId has no MediaStore entry.
 */
data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val data: String?,          // file path — used for sibling .lrc lookup
    val dateAdded: Long,
    val artworkOverride: String? = null,  // YouTube thumbnail or downloaded art path
) {
    val artworkUri: Uri
        get() = if (!artworkOverride.isNullOrBlank()) Uri.parse(artworkOverride)
                else Uri.parse("content://media/external/audio/albumart/$albumId")

    val isYoutube: Boolean get() = id.startsWith("yt_") || id.startsWith("dl_")
}

data class AlbumGroup(val album: String, val artist: String, val artworkUri: Uri, val tracks: List<Track>)
data class ArtistGroup(val artist: String, val trackCount: Int, val tracks: List<Track>)

enum class SortMode(val label: String) {
    TITLE_ASC("Title A–Z"),
    TITLE_DESC("Title Z–A"),
    ARTIST("Artist"),
    RECENT("Recently added"),
}
