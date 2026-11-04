package dev.horizon.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun `time tags in their usual forms`() {
        val lyrics = LrcParser.parse("[00:01.50]One two\n[00:03]Three\n[1:02.345]Four")
        assertEquals(listOf(1_500L, 3_000L, 62_345L), lyrics.lines.map { it.timeMs })
        assertEquals("One two", lyrics.lines[0].text)
    }

    @Test fun `a line with several times repeats`() {
        val lyrics = LrcParser.parse("[00:10.00][00:40.00]Chorus line\n[00:20.00]Verse")
        assertEquals(listOf("Chorus line", "Verse", "Chorus line"), lyrics.lines.map { it.text })
    }

    @Test fun `metadata and untimed lines are ignored`() {
        val lyrics = LrcParser.parse("[ar:Someone]\n[ti:Song]\nplain text\n[00:05.00]Sung")
        assertEquals(1, lyrics.lines.size)
    }

    @Test fun `positive offset shows lyrics earlier`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:02.00]Hi")
        assertEquals(1_500L, lyrics.lines.single().timeMs)
    }

    @Test fun `empty line is a break`() {
        val lyrics = LrcParser.parse("[00:01.00]Words here\n[00:05.00]\n[00:09.00]More")
        assertTrue(lyrics.lines[1].words.isEmpty())
    }

    @Test fun `enhanced lrc times each word`() {
        val line = LrcParser.parse("[00:01.00]<00:01.00>Hel <00:01.40>lo <00:02.00>world").lines.single()
        assertEquals(listOf(1_000L, 1_400L, 2_000L), line.words.map { it.timeMs })
    }
}

class TimedWordsTest {
    private val lyrics = LrcParser.parse("[00:10.00]a b c d\n[00:14.00]e\n[00:30.00]f g")

    @Test fun `words are spread across their line`() {
        val words = lyrics.timedWords()
        // 4 s to the next line, words fill 85% of it: 0, 850, 1700, 2550 ms in.
        assertEquals(listOf(10_000L, 10_850L, 11_700L, 12_550L), words.take(4).map { it.timeMs })
    }

    @Test fun `long gaps are not stretched`() {
        val words = lyrics.timedWords()
        // "f g" is the last line, so it gets at most 6 s.
        assertEquals(30_000L, words[5].timeMs)
        assertEquals(30_000L + 2_550L, words[6].timeMs)
    }

    @Test fun `count of sung words at a position`() {
        val words = lyrics.timedWords()
        assertEquals(0, words.countAt(9_999))
        assertEquals(1, words.countAt(10_000))
        assertEquals(5, words.countAt(20_000))
        assertEquals(7, words.countAt(99_000))
    }

    @Test fun `intro waits for the first word`() {
        val words = lyrics.timedWords()
        assertEquals(LyricsWait(LyricsWait.Kind.Intro, 8_000), words.waitAt(2_000))
        assertNull(words.waitAt(9_000)) // within the lead-in
    }

    @Test fun `a long break waits after its last line has been read`() {
        val words = lyrics.timedWords()
        // "e" at 14 s, then nothing until "f" at 30 s.
        assertNull(words.waitAt(12_000))
        assertNull(words.waitAt(17_000))
        assertEquals(LyricsWait(LyricsWait.Kind.Break, 9_000), words.waitAt(21_000))
    }

    @Test fun `the outro starts once the last line has been read`() {
        val words = lyrics.timedWords()
        // Last word "g" at 32.55 s.
        assertNull(words.waitAt(35_000))
        assertEquals(LyricsWait(LyricsWait.Kind.Outro, null), words.waitAt(39_000))
    }

    @Test fun `enhanced times never run backwards`() {
        val words = LrcParser.parse("[00:05.00]<00:05.00>a <00:04.00>b").timedWords()
        assertEquals(listOf(5_000L, 5_000L), words.map { it.timeMs })
    }
}

class TitleCleanerTest {
    @Test fun `video labels are dropped`() =
        assertEquals(SongName("Song Title", "Artist"), TitleCleaner.clean("Song Title (Official Video)", "Artist"))

    @Test fun `channel artist gives way to the title's artist`() =
        assertEquals(SongName("Song", "Artist"), TitleCleaner.clean("Artist - Song [Official Music Video]", "ArtistVEVO"))

    @Test fun `topic channels lose their suffix`() =
        assertEquals(SongName("Kesariya", "Arijit Singh"), TitleCleaner.clean("Kesariya (From \"Brahmastra\")", "Arijit Singh - Topic"))

    @Test fun `featured artists are dropped`() {
        assertEquals("Song", TitleCleaner.clean("Song (feat. X)", "A").title)
        assertEquals("Song", TitleCleaner.clean("Song feat. X", "A").title)
    }

    @Test fun `remaster suffix is dropped`() =
        assertEquals("Bohemian Rhapsody", TitleCleaner.clean("Bohemian Rhapsody - Remastered 2011", "Queen").title)

    @Test fun `artist found in the title when missing`() =
        assertEquals(SongName("Song", "Artist"), TitleCleaner.clean("Artist - Song", null))

    @Test fun `a dash that is part of the title stays`() =
        assertEquals(SongName("Hello - Live", "Adele"), TitleCleaner.clean("Hello - Live", "Adele"))

    @Test fun `no artist stays no artist`() = assertNull(TitleCleaner.clean("Song", null).artist)
}
