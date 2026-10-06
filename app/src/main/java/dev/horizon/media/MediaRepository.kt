package dev.horizon.media

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/** Album art is shown at ~120dp; anything larger just wastes memory. */
private const val MAX_ART_PX = 512

/**
 * Watches other apps' media sessions (Spotify, YouTube Music, etc.) and exposes the one
 * worth showing as [nowPlaying]. Also forwards transport controls to that session.
 */
class MediaRepository(context: Context) {
    private val app = context.applicationContext
    private val sessionManager = app.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(app, MediaListenerService::class.java)

    @Volatile
    private var active: MediaController? = null

    /** True when the user has granted notification access, which media sessions require. */
    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    /** Opens the system screen where the user grants notification access. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Fallback when the detail screen isn't available (some OEM builds). */
    fun accessListSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** The session to show, or null when nothing is playing or paused (or access is missing). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowPlaying: Flow<NowPlaying?> = callbackFlow<SessionSnapshot?> {
        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
        var controllers: List<MediaController> = emptyList()

        fun publish() {
            val chosen = pickSession(controllers)
            active = chosen
            trySend(chosen?.let(::snapshot))
        }

        fun track(list: List<MediaController>) {
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            callbacks.clear()
            controllers = list
            list.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onSessionDestroyed() = publish()
                }
                controller.registerCallback(callback, Handler(Looper.getMainLooper()))
                callbacks[controller] = callback
            }
            publish()
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener, listenerComponent, Handler(Looper.getMainLooper()),
            )
            track(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            // Access not granted (or revoked); nothing to show.
            trySend(null)
        }

        awaitClose {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            active = null
        }
    }
        // Session callbacks arrive on the main thread; keep all bookkeeping there too.
        .flowOn(Dispatchers.Main)
        .conflate()
        .mapLatest { it?.withLoadedArt() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun playPause() {
        val controller = active ?: return
        if (controller.playbackState?.state.isPlayingState()) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        active?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        active?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        active?.transportControls?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /**
     * Opens the app that owns the shown session: its own "now playing" screen when it provides
     * one, otherwise its launcher entry. Returns false if neither could be opened.
     */
    fun openPlayerApp(activity: Activity): Boolean {
        val controller = active ?: return false
        controller.sessionActivity?.let { pending ->
            try {
                pending.send(activity, 0, null, null, null, null, pendingIntentStartOptions())
                return true
            } catch (_: PendingIntent.CanceledException) {
                // Fall through to the launcher intent.
            }
        }
        val launch = activity.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return false
        return try {
            activity.startActivity(launch)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Android 14+ only lets another app's PendingIntent start an activity if the sender opts in.
     * We are the visible app, so allow it.
     */
    private fun pendingIntentStartOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
            .toBundle()
        else -> null
    }

    private fun snapshot(controller: MediaController): SessionSnapshot {
        val metadata = controller.metadata
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        val artUri = metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }
        val nowPlaying = NowPlaying(
            packageName = controller.packageName,
            appLabel = appLabel(controller.packageName),
            title = metadata?.text(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "Unknown track",
            artist = metadata?.text(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            art = metadata?.let {
                it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            },
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            isPlaying = state?.state.isPlayingState(),
            positionMs = state?.position ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0L,
            playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
        return SessionSnapshot(nowPlaying, artUri)
    }

    private fun appLabel(packageName: String): String? = try {
        val pm = app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        null
    }

    // The art last handed to the UI, and what it was made from. Every session update carries a
    // fresh copy of the art bitmap, so without this the UI would see "new" art (and fade it,
    // and re-tint the card) several times per track change.
    private var artTrack: String? = null
    private var artSource: String? = null
    private var artShown: Bitmap? = null

    /**
     * Resolves the art to show: reuses the previous bitmap while the track and its art are
     * unchanged (or while an update arrives without art), otherwise loads a local URI if needed
     * and caps the size. Called sequentially from [nowPlaying]'s mapLatest.
     */
    private fun SessionSnapshot.withLoadedArt(): NowPlaying {
        val track = "${nowPlaying.packageName}|${nowPlaying.title}|${nowPlaying.artist}"
        val embedded = nowPlaying.art
        val source = when {
            embedded != null -> "bitmap:${embedded.width}x${embedded.height}"
            artUri != null -> "uri:$artUri"
            else -> null
        }
        val sameTrack = track == artTrack
        val art = when {
            sameTrack && (source == null || source == artSource) -> artShown
            else -> (embedded ?: artUri?.let(::loadLocalArt))?.capped()
        }
        if (!sameTrack || source != null) {
            artTrack = track
            artSource = source
            artShown = art
        }
        return nowPlaying.copy(art = art)
    }

    private fun loadLocalArt(uri: String): Bitmap? {
        val parsed = uri.toUri()
        // No internet permission: only content:// and android.resource:// art can be read.
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT && parsed.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE) return null
        return try {
            app.contentResolver.openInputStream(parsed)?.use(BitmapFactory::decodeStream)
        } catch (_: Exception) {
            null
        }
    }
}

/** A session snapshot plus where to fetch its art from, before the art is loaded off the main thread. */
private data class SessionSnapshot(val nowPlaying: NowPlaying, val artUri: String?)

private fun MediaMetadata.text(key: String): String? = getText(key)?.toString()?.takeIf { it.isNotBlank() }

private fun Int?.isPlayingState(): Boolean =
    this == PlaybackState.STATE_PLAYING || this == PlaybackState.STATE_BUFFERING

private fun Bitmap.capped(): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= MAX_ART_PX) return this
    val ratio = MAX_ART_PX.toFloat() / largest
    return scale((width * ratio).toInt(), (height * ratio).toInt())
}

/**
 * Picks the session to show. Sessions arrive most-recently-active first, so prefer the first
 * one that is playing; otherwise the first paused one that has metadata.
 */
private fun pickSession(controllers: List<MediaController>): MediaController? =
    controllers.firstOrNull { it.playbackState?.state.isPlayingState() }
        ?: controllers.firstOrNull {
            it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED
        }
