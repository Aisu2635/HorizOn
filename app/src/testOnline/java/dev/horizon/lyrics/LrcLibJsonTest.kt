package dev.horizon.lyrics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Response shapes as documented by lrclib.net; no network in tests. */
class LrcLibJsonTest {
    private fun record(duration: Double, synced: String?, instrumental: Boolean = false) = JSONObject()
        .put("trackName", "Song")
        .put("duration", duration)
        .put("instrumental", instrumental)
        .put("plainLyrics", "Plain words")
        .put("syncedLyrics", synced ?: JSONObject.NULL)

    @Test fun `synced lyrics are parsed`() {
        val result = LrcLibJson.result(record(200.0, "[00:01.00]Hello there"))
        assertTrue(result is LyricsResult.Synced)
        assertEquals("Hello there", (result as LyricsResult.Synced).lyrics.lines.single().text)
    }

    @Test fun `plain lyrics only count as not found`() =
        assertEquals(LyricsResult.NotFound, LrcLibJson.result(record(200.0, null)))

    @Test fun `instrumental is reported`() =
        assertEquals(LyricsResult.Instrumental, LrcLibJson.result(record(200.0, null, instrumental = true)))

    @Test fun `search picks the closest length with synced lyrics`() {
        val records = JSONArray()
            .put(record(250.0, "[00:01.00]Wrong recording"))
            .put(record(201.5, null))
            .put(record(199.0, "[00:01.00]Right one"))
        val result = LrcLibJson.bestOf(records, 200.0) as LyricsResult.Synced
        assertEquals("Right one", result.lyrics.lines.single().text)
    }

    @Test fun `search with nothing close finds nothing`() =
        assertNull(LrcLibJson.bestOf(JSONArray().put(record(300.0, "[00:01.00]Far off")), 200.0))
}
