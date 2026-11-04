package dev.horizon.lyrics

/** Songs whose lyrics (or lack of them) are remembered. In memory only: no listening history on disk. */
private const val CACHE_SIZE = 50

/**
 * Finds lyrics for the song playing, through this build's [LyricsSources] (none in the offline
 * build), remembering recent answers for this session.
 */
object LyricsRepository {
    private val source: LyricsSource? = LyricsSources.create()

    /** HorizOn+: lyrics can be looked up online. */
    val online: Boolean get() = LyricsSources.ONLINE

    /** Debug builds plug demo lyrics in here, answered only for the demo player's own songs. */
    @Volatile
    var demo: LyricsSource? = null

    private val cache = object : LinkedHashMap<String, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>?) = size > CACHE_SIZE
    }

    /**
     * Lyrics for [request]. [enabled] is the user's Lyrics switch: nothing is looked up online
     * while it is off. [ownPackage] is this app's package, whose demo songs get demo lyrics.
     */
    suspend fun find(request: LyricsRequest, ownPackage: String, enabled: Boolean): LyricsResult {
        demo?.let { if (request.packageName == ownPackage) return it.find(request) }
        val lookup = source?.takeIf { enabled } ?: return LyricsResult.NotFound
        val key = "${request.title}|${request.artist}|${request.durationMs / 1000}"
        synchronized(cache) { cache[key] }?.let { return it }
        val result = lookup.find(request)
        if (result != LyricsResult.Unavailable) synchronized(cache) { cache[key] = result }
        return result
    }
}
