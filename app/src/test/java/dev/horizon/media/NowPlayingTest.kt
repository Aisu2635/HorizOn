package dev.horizon.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private val base = NowPlaying(
        packageName = "com.example.music",
        appLabel = "Music",
        title = "Song",
        artist = "Artist",
        art = null,
        durationMs = 200_000,
        isPlaying = true,
        positionMs = 60_000,
        positionUpdatedAt = 1_000,
        playbackSpeed = 1f,
        canSkipPrevious = true,
        canSkipNext = true,
    )

    @Test fun `playing position advances with time`() = assertEquals(65_000L, base.positionAt(6_000))
    @Test fun `paused position stays put`() = assertEquals(60_000L, base.copy(isPlaying = false).positionAt(6_000))
    @Test fun `playback speed scales progress`() = assertEquals(70_000L, base.copy(playbackSpeed = 2f).positionAt(6_000))
    @Test fun `position never passes the end`() = assertEquals(200_000L, base.positionAt(1_000_000))
    @Test fun `unknown duration is not clamped`() = assertEquals(1_059_000L, base.copy(durationMs = 0).positionAt(1_000_000))

    @Test fun `track time formats`() {
        assertEquals("0:00", formatTrackTime(0))
        assertEquals("1:23", formatTrackTime(83_400))
        assertEquals("3:48", formatTrackTime(228_000))
        assertEquals("1:02:05", formatTrackTime(3_725_000))
    }
}
