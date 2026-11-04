package dev.horizon.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import dev.horizon.DeskActivity
import dev.horizon.lyrics.LrcParser
import dev.horizon.lyrics.LyricsRepository
import dev.horizon.lyrics.LyricsRequest
import dev.horizon.lyrics.LyricsResult
import dev.horizon.lyrics.LyricsSource

/**
 * Debug builds only. Starts a fake media session with a few demo tracks, then opens the desk
 * screen, so the player can be tested and screenshotted without a real music app. Its songs get
 * made-up lyrics (one is instrumental), and two have a near-black and a greyscale cover to test
 * album colours:
 *
 *     adb shell am start -n dev.horizon/.debug.DemoMediaActivity
 */
class DemoMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LyricsRepository.demo = DemoLyrics
        DemoSession.start(applicationContext)
        startActivity(Intent(this, DeskActivity::class.java))
        finish()
    }
}

private object DemoSession {
    private data class Track(val title: String, val artist: String, val durationMs: Long, val from: Int, val to: Int)

    private val tracks = listOf(
        Track("Midnight Drive", "Horizon Demo", 228_000, 0xFFF2A65A.toInt(), 0xFF3B1A12.toInt()),
        Track("Blue Hour", "The Placeholders", 194_000, 0xFF5AA9F2.toInt(), 0xFF10203B.toInt()),
        Track("Green Room Sessions", "Test Pattern Orchestra", 251_000, 0xFF7FD18B.toInt(), 0xFF12301A.toInt()),
        Track("Night Shift", "The Placeholders", 203_000, 0xFF1E2740.toInt(), 0xFF04060C.toInt()),
        Track("Paper Moon", "Horizon Demo", 187_000, 0xFFE6E6E6.toInt(), 0xFF121212.toInt()),
    )

    private var session: MediaSession? = null
    private var index = 0
    private var playing = true
    private var position = 0L
    private var updatedAt = 0L

    fun start(context: Context) {
        if (session != null) return
        session = MediaSession(context, "HorizOnDemo").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = setPlaying(true)
                override fun onPause() = setPlaying(false)
                override fun onSkipToNext() = select(index + 1)
                override fun onSkipToPrevious() = select(index - 1)
            })
            isActive = true
        }
        select(0, startAt = 83_000)
    }

    private fun currentPosition(): Long =
        if (playing) position + (SystemClock.elapsedRealtime() - updatedAt) else position

    private fun setPlaying(value: Boolean) {
        position = currentPosition()
        playing = value
        publishState()
    }

    private fun select(newIndex: Int, startAt: Long = 0) {
        index = Math.floorMod(newIndex, tracks.size)
        val track = tracks[index]
        position = startAt
        playing = true
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art(track))
                .build(),
        )
        publishState()
    }

    private fun publishState() {
        updatedAt = SystemClock.elapsedRealtime()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    position,
                    if (playing) 1f else 0f,
                    updatedAt,
                )
                .build(),
        )
    }

    private fun art(track: Track): Bitmap {
        val size = 400
        return createBitmap(size, size).also { bitmap ->
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), track.from, track.to, Shader.TileMode.CLAMP)
            }
            Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        }
    }
}

/** Made-up lyrics for the demo songs, line-synced every 4.4 s for the whole song. */
private object DemoLyrics : LyricsSource {
    private val verses = listOf(
        "Streetlights hum a quiet tune",
        "I trace your name across the moon",
        "Every signal finds its way",
        "back to you at the end of day",
        "Wires glowing, hearts in sync",
        "closer than we used to think",
        "",
        "Turn the dial and let it play",
        "static fading into grey",
    )

    override suspend fun find(request: LyricsRequest): LyricsResult {
        if (request.title == "Green Room Sessions") return LyricsResult.Instrumental
        val lrc = buildString {
            var t = 2_000L
            var i = 0
            while (t < request.durationMs) {
                val ms = t % 1_000 / 10
                append("[%02d:%02d.%02d]".format(java.util.Locale.ROOT, t / 60_000, t / 1_000 % 60, ms))
                append(verses[i % verses.size]).append('\n')
                t += 4_400
                i++
            }
        }
        return LyricsResult.Synced(LrcParser.parse(lrc))
    }
}
