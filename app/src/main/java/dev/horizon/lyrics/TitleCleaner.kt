package dev.horizon.lyrics

/** Title and artist as a lyrics database would list them. */
data class SongName(val title: String, val artist: String?)

/**
 * Turns what music apps report into a searchable song name. YouTube Music in particular often
 * reports video titles ("Artist - Song (Official Video)") with a channel as the artist.
 */
object TitleCleaner {
    private val NOISE = Regex(
        """\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|remaster(ed)?|feat\.?|ft\.|from|explicit|hd|4k|m/?v|full song|prod\.?)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val DASH_REMASTER = Regex("""\s+-\s+[^-]*\bremaster(ed)?\b.*$""", RegexOption.IGNORE_CASE)
    private val TRAILING_FEAT = Regex("""\s+(feat\.?|ft\.)\s.*$""", RegexOption.IGNORE_CASE)
    private val CHANNEL_SUFFIX = Regex("""\s*(-\s*Topic|VEVO)$""", RegexOption.IGNORE_CASE)
    private val QUOTES = Regex("""^["'“”‘’]+|["'“”‘’]+$""")

    fun clean(title: String, artist: String?): SongName {
        var t = title.replace(NOISE, "").replace(DASH_REMASTER, "").replace(TRAILING_FEAT, "")
        var a = artist?.replace(CHANNEL_SUFFIX, "")?.replace(TRAILING_FEAT, "")?.trim()?.takeIf { it.isNotEmpty() }

        // "Artist - Title" in the title field: trust it when the artist field is missing, a channel,
        // or the same name the title starts with.
        val dash = t.indexOf(" - ")
        if (dash > 0) {
            val left = t.substring(0, dash).trim()
            val right = t.substring(dash + 3).trim()
            val channel = artist != null && CHANNEL_SUFFIX.containsMatchIn(artist)
            if (a == null || channel || left.equals(a, ignoreCase = true)) {
                a = left
                t = right
            }
        }
        t = t.trim().replace(QUOTES, "").replace(Regex("\\s+"), " ").trim()
        return SongName(t.ifEmpty { title.trim() }, a)
    }
}
