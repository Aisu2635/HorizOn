package dev.horizon.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

private const val BASE = "https://lrclib.net/api"

/** LRCLIB asks apps to identify themselves. */
private const val USER_AGENT = "HorizOn+ (https://github.com/Aisu2635/HorizOn)"
private const val TIMEOUT_MS = 5_000

/** How far a search result's length may be from the song's and still count as the same recording. */
private const val DURATION_SLACK_S = 3.0

/**
 * Time-synced lyrics from lrclib.net, a free, open lyrics database. Sends only the song's title,
 * artist, album and length. Plain (untimed) lyrics count as not found: the web needs timing.
 */
class LrcLibSource : LyricsSource {
    override suspend fun find(request: LyricsRequest): LyricsResult = withContext(Dispatchers.IO) {
        val song = TitleCleaner.clean(request.title, request.artist)
        val seconds = (request.durationMs / 1000).takeIf { it > 0 }
        try {
            // An exact match first, then a search.
            val exact = if (song.artist != null) {
                get(
                    "get",
                    "track_name" to song.title,
                    "artist_name" to song.artist,
                    "album_name" to request.album,
                    "duration" to seconds?.toString(),
                )?.let { LrcLibJson.result(JSONObject(it)) }
            } else {
                null
            }
            if (exact != null && exact != LyricsResult.NotFound) return@withContext exact
            val found = get("search", "track_name" to song.title, "artist_name" to song.artist)
                ?.let { LrcLibJson.bestOf(JSONArray(it), seconds?.toDouble()) }
            found ?: exact ?: LyricsResult.NotFound
        } catch (_: IOException) {
            LyricsResult.Unavailable
        } catch (_: JSONException) {
            LyricsResult.NotFound
        }
    }

    /** The response body, or null for 404 (not in the database). */
    private fun get(path: String, vararg params: Pair<String, String?>): String? {
        val query = params.mapNotNull { (k, v) -> v?.let { "$k=${URLEncoder.encode(it, "UTF-8")}" } }.joinToString("&")
        val connection = URL("$BASE/$path?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("LRCLIB answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** Reading LRCLIB's answers. Kept apart from the network so it can be tested. */
object LrcLibJson {
    /** One record from `/api/get`. */
    fun result(record: JSONObject): LyricsResult {
        if (record.optBoolean("instrumental")) return LyricsResult.Instrumental
        if (record.isNull("syncedLyrics")) return LyricsResult.NotFound
        val synced = record.optString("syncedLyrics").takeIf { it.isNotBlank() } ?: return LyricsResult.NotFound
        val lyrics = LrcParser.parse(synced)
        return if (lyrics.lines.isEmpty()) LyricsResult.NotFound else LyricsResult.Synced(lyrics)
    }

    /**
     * The best of `/api/search`'s records: synced lyrics with the closest length (within a few
     * seconds when the length is known). Null when none fits.
     */
    fun bestOf(records: JSONArray, durationS: Double?): LyricsResult? {
        val usable = (0 until records.length()).map { records.getJSONObject(it) }
            .filter { durationS == null || abs(it.optDouble("duration", Double.NaN) - durationS) <= DURATION_SLACK_S }
        val synced = usable
            .sortedBy { if (durationS == null) 0.0 else abs(it.optDouble("duration") - durationS) }
            .map(::result)
            .firstOrNull { it is LyricsResult.Synced }
        return synced ?: usable.firstOrNull()?.let(::result)?.takeIf { it == LyricsResult.Instrumental }
    }
}
