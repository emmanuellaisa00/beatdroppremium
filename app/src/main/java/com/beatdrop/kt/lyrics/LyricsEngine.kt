package com.beatdrop.kt.lyrics

import android.content.Context
import com.beatdrop.kt.data.Track

/**
 * Unified lyrics fetcher with four-tier fallback — never returns empty for a
 * non-instrumental track:
 *   1. Sidecar .lrc next to audio file
 *   2. App cache .lrc (written after any previous online fetch)
 *   3. LrcLib.net (synced + plain timed)
 *   4. Auto-generated placeholder timed from title/artist (never fails)
 *
 * Any result from tiers 2 or 3 is automatically written to the app cache so
 * the next play is instant and offline-safe.
 */
object LyricsEngine {

    fun fetch(ctx: Context, track: Track): List<LyricLine> {
        // Tier 1 — user-provided sidecar .lrc
        LrcParser.findAndParse(track).takeIf { it.isNotEmpty() }?.let { return it }

        // Tier 2 — previously cached online fetch
        LyricsCache.read(ctx, track)?.let { return it }

        // Tier 3 — LrcLib.net (free, no key)
        val online = LrcLibProvider.fetch(track)
        if (online.isNotEmpty()) {
            LyricsCache.write(ctx, track, online)
            return online
        }

        // Tier 4 — generated placeholder from metadata (never fails)
        return generatePlaceholder(track)
    }

    /** Split title+artist into words and create evenly-spaced timed lines so
     *  the lyrics UI is never blank and the user sees *something* synced. */
    private fun generatePlaceholder(track: Track): List<LyricLine> {
        val raw = "${track.title} — ${track.artist}"
        val words = raw.split(Regex("""[\s\-–—]+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf(LyricLine(0L, raw))

        val durSec = (track.durationMs / 1000).coerceAtLeast(30).toInt()
        val introMs = 1500L
        val outroMs = 1500L
        val availMs = (durSec * 1000L) - introMs - outroMs
        val perWord = availMs / words.size

        val out = ArrayList<LyricLine>(words.size)
        var cursor = introMs
        for (w in words) {
            out.add(LyricLine(cursor, w))
            cursor += perWord
        }
        return out
    }
}
