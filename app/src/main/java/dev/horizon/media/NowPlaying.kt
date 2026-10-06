package dev.horizon.media

import android.graphics.Bitmap

/** A snapshot of what another app's media session is playing. */
data class NowPlaying(
    val packageName: String,
    val appLabel: String?,
    val title: String,
    val artist: String?,
    val art: Bitmap?,
    /** Track length, or 0 when the app doesn't report one (e.g. live streams). */
    val durationMs: Long,
    val isPlaying: Boolean,
    /** Position reported by the app at [positionUpdatedAt] (elapsedRealtime millis). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    /** Whether the app accepts seeking (some live streams don't). */
    val canSeek: Boolean = false,
) {
    /** Extrapolates the playback position to [nowElapsed], clamped to the track length. */
    fun positionAt(nowElapsed: Long): Long {
        val elapsed = if (isPlaying) ((nowElapsed - positionUpdatedAt) * playbackSpeed).toLong() else 0L
        val position = (positionMs + elapsed).coerceAtLeast(0L)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }
}

/** Formats a track time as m:ss, or h:mm:ss for long tracks. */
fun formatTrackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = (totalSeconds % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}
