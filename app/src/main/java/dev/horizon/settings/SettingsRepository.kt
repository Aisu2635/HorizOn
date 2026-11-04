package dev.horizon.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The clock styles a user can pick from the controls. */
enum class ClockFace(val label: String) {
    Digital("Digital"),
    Analog("Analog"),
    Flip("Flip"),
    ;

    companion object {
        val Default = Digital

        /** Unknown or missing values (e.g. a face removed in a later version) fall back to the default. */
        fun fromKey(key: String?): ClockFace = entries.firstOrNull { it.name == key } ?: Default
    }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** User preferences, stored on device with DataStore. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val clockFace: Flow<ClockFace> = store.data.map { ClockFace.fromKey(it[CLOCK_FACE]) }

    suspend fun setClockFace(face: ClockFace) {
        store.edit { it[CLOCK_FACE] = face.name }
    }

    /** Whether the user tapped "Not now" on the music access card. */
    val musicPromptDismissed: Flow<Boolean> = store.data.map { it[MUSIC_PROMPT_DISMISSED] ?: false }

    suspend fun setMusicPromptDismissed(dismissed: Boolean) {
        store.edit { it[MUSIC_PROMPT_DISMISSED] = dismissed }
    }

    /** Whether to mirror Google Maps' turn-by-turn directions. Off until the user turns it on. */
    val showNavigation: Flow<Boolean> = store.data.map { it[SHOW_NAVIGATION] ?: false }

    /** Turning directions on or off is an answer to the "show directions here?" prompt too. */
    suspend fun setShowNavigation(show: Boolean) {
        store.edit {
            it[SHOW_NAVIGATION] = show
            it[NAV_PROMPT_DISMISSED] = true
        }
    }

    /** Whether the user has answered the "show directions here?" prompt (or chosen in the controls). */
    val navPromptDismissed: Flow<Boolean> = store.data.map { it[NAV_PROMPT_DISMISSED] ?: false }

    suspend fun setNavPromptDismissed(dismissed: Boolean) {
        store.edit { it[NAV_PROMPT_DISMISSED] = dismissed }
    }

    /** HorizOn+ only: whether to look up lyrics online. Off until the user agrees. */
    val showLyrics: Flow<Boolean> = store.data.map { it[SHOW_LYRICS] ?: false }

    /** Turning lyrics on or off also answers the "show lyrics?" card. */
    suspend fun setShowLyrics(show: Boolean) {
        store.edit {
            it[SHOW_LYRICS] = show
            it[LYRICS_PROMPT_DISMISSED] = true
        }
    }

    /** Whether the user has answered the "show lyrics?" card. */
    val lyricsPromptDismissed: Flow<Boolean> = store.data.map { it[LYRICS_PROMPT_DISMISSED] ?: false }

    suspend fun setLyricsPromptDismissed(dismissed: Boolean) {
        store.edit { it[LYRICS_PROMPT_DISMISSED] = dismissed }
    }

    private companion object {        val CLOCK_FACE = stringPreferencesKey("clock_face")
        val MUSIC_PROMPT_DISMISSED = booleanPreferencesKey("music_prompt_dismissed")
        val SHOW_NAVIGATION = booleanPreferencesKey("show_navigation")
        val NAV_PROMPT_DISMISSED = booleanPreferencesKey("nav_prompt_dismissed")
        val SHOW_LYRICS = booleanPreferencesKey("show_lyrics")
        val LYRICS_PROMPT_DISMISSED = booleanPreferencesKey("lyrics_prompt_dismissed")
    }
}
