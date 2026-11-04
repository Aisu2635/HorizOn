package dev.horizon.lyrics

/** HorizOn+: lyrics come from lrclib.net, once the user turns them on. */
object LyricsSources {
    const val ONLINE = true

    fun create(): LyricsSource? = LrcLibSource()
}
