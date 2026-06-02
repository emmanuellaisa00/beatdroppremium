package com.beatdrop.kt.playback

import com.beatdrop.kt.data.Track
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * AutoMixEngine — Tier-2 next-track picker for Auto-DJ.
 *
 * Given the currently-playing track, the available library, the user's listening
 * history (play counts, liked set, recently-played ring), and a per-track
 * features cache (BPM + Camelot key), it returns the BEST next track to mix into.
 *
 * Scoring weights (tuned for music-listening, not radio):
 *
 *   +30   same artist (you tend to listen to artists in chunks)
 *   +20   same album
 *   +10   duration within ±20% of current
 *   +0..40  play-count bonus (log-scaled — favour your top tracks)
 *   +25   in your "liked" set
 *   +5    added to library in the last 30 days
 *   +0..20 BPM proximity (max bonus at ±2 BPM; falls off Gaussian, σ=10)
 *   +0..15 Camelot key compatibility (1.0=same key → +15, 0.7 → +10, etc.)
 *   −∞    in the recently-played ring (last 5 tracks) → never pick
 *
 * Returns null if the library is empty or only contains recently-played tracks.
 */
object AutoMixEngine {

    /** Public scoring entrypoint — pure function, testable. */
    fun pickNext(
        current: Track,
        library: List<Track>,
        likedIds: Set<String>,
        playCounts: Map<String, Int>,
        recentlyPlayedIds: Set<String>,    // current + last N played, excluded
        featuresById: Map<String, TrackAnalyzer.TrackFeatures>,
        now: Long = System.currentTimeMillis(),
    ): Track? {
        if (library.isEmpty()) return null
        val curFeat = featuresById[current.id]
        val maxPlays = (playCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)

        val candidates = library.asSequence()
            .filter { it.id != current.id }
            .filter { it.id !in recentlyPlayedIds }
            .filter { it.durationMs in 30_000..1_800_000 }   // 30s..30min, skip extremes

        var best: Track? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (t in candidates) {
            val s = score(current, t, curFeat, featuresById[t.id],
                          likedIds, playCounts, maxPlays, now)
            if (s > bestScore) { bestScore = s; best = t }
        }
        return best ?: library.firstOrNull { it.id != current.id && it.id !in recentlyPlayedIds }
    }

    fun score(
        cur: Track,
        cand: Track,
        curFeat: TrackAnalyzer.TrackFeatures?,
        candFeat: TrackAnalyzer.TrackFeatures?,
        likedIds: Set<String>,
        playCounts: Map<String, Int>,
        maxPlays: Int,
        now: Long,
    ): Float {
        var s = 0f
        // Artist/album affinity
        if (cand.artist.equals(cur.artist, ignoreCase = true)) s += 30f
        if (cand.albumId == cur.albumId && cur.albumId != 0L) s += 20f
        // Duration proximity (±20% sweet spot, hard cap on extremes)
        val ratio = cand.durationMs.toDouble() / cur.durationMs.coerceAtLeast(1).toDouble()
        if (ratio in 0.8..1.25) s += 10f
        // Liked
        if (cand.id in likedIds) s += 25f
        // Play count — log-scaled bonus (max 40 for your most-played track)
        val plays = playCounts[cand.id] ?: 0
        if (plays > 0) {
            s += 40f * (ln(1.0 + plays).toFloat() / ln(1.0 + maxPlays).toFloat())
        }
        // Recently added (last 30 days) — surface new music
        val ageDays = (now - cand.dateAdded) / (1000.0 * 60 * 60 * 24)
        if (ageDays in 0.0..30.0) s += 5f
        // BPM proximity — Gaussian bonus (peaks at same BPM)
        if (curFeat != null && candFeat != null) {
            val dBpm = abs(curFeat.bpm - candFeat.bpm)
            // Also reward octave-matched tempos (half/double-time mixes work)
            val dHalf = abs(curFeat.bpm * 2 - candFeat.bpm)
            val dDouble = abs(curFeat.bpm - candFeat.bpm * 2)
            val effectiveDelta = minOf(dBpm, dHalf, dDouble)
            val sigma = 10.0
            s += (20.0 * exp(-(effectiveDelta * effectiveDelta) / (2 * sigma * sigma))).toFloat()
            // Key compatibility via Camelot
            s += 15f * TrackAnalyzer.camelotScore(curFeat.keyCamelot, candFeat.keyCamelot)
        }
        return s
    }
}
