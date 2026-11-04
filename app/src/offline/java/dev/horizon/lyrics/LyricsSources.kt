package dev.horizon.lyrics

/** The offline build has no internet, so no lyrics: the lyrics page shows the visualizer. */
object LyricsSources {
    const val ONLINE = false

    fun create(): LyricsSource? = null
}
