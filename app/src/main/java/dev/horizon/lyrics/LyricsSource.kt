package dev.horizon.lyrics

/** What we know about the song being played. */
data class LyricsRequest(
    val packageName: String,
    val title: String,
    val artist: String?,
    val album: String?,
    /** 0 when the app reports no length. */
    val durationMs: Long,
)

sealed interface LyricsResult {
    data class Synced(val lyrics: Lyrics) : LyricsResult

    /** The song has no words; show the visualizer. */
    data object Instrumental : LyricsResult

    /** No time-synced lyrics for this song. */
    data object NotFound : LyricsResult

    /** Couldn't ask (no network, server down). Not cached, so it is tried again next time. */
    data object Unavailable : LyricsResult
}

/** Somewhere lyrics come from. HorizOn+ uses LRCLIB; the offline build has none. */
fun interface LyricsSource {
    suspend fun find(request: LyricsRequest): LyricsResult
}
