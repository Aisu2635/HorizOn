package dev.horizon.nav

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps takes its notification down while Maps itself is on screen and puts it back when you
 * leave. Waiting this long before clearing avoids a flash of the clock on the way back.
 */
private const val REMOVE_GRACE_MS = 3_000L

/** Not a platform constant before Android 16 (`Notification.EXTRA_SHORT_CRITICAL_TEXT`). */
private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

/** What the navigation app is showing now, ready for the UI. */
data class NavState(
    /** The notification's key, so its removal can be matched. */
    val key: String,
    val packageName: String,
    val info: NavInfo,
    /** The app's maneuver arrow: white on transparent, tint it to taste. */
    val maneuverIcon: Bitmap?,
    /** Opens the navigation app's own screen. */
    val openIntent: PendingIntent?,
)

/**
 * Process-wide holder for the mirrored navigation state. Written by the notification listener
 * (on the main thread), read by the UI. Kept in memory only.
 */
object NavRepository {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    /**
     * Whether a navigation app is navigating right now, known from the notification's package,
     * category and ongoing flag alone. Tracked even while directions are off (no text is read),
     * so the screen can offer to turn them on.
     */
    private val _navigating = MutableStateFlow(false)
    val navigating: StateFlow<Boolean> = _navigating.asStateFlow()
    private val navigatingKeys = mutableSetOf<String>()

    private val main = Handler(Looper.getMainLooper())
    private val clearPending = Runnable { _state.value = null }

    /** Records whether the notification [key] is an ongoing navigation notification. */
    fun setNavigating(key: String, navigating: Boolean) {
        if (navigating) navigatingKeys.add(key) else navigatingKeys.remove(key)
        _navigating.value = navigatingKeys.isNotEmpty()
    }

    fun post(next: NavState) {
        main.removeCallbacks(clearPending)
        // Maps re-sends the same arrow about once a second; keep one bitmap so the UI doesn't redraw it.
        val previousIcon = _state.value?.maneuverIcon
        val icon = next.maneuverIcon
        _state.value = if (previousIcon != null && icon != null && icon.sameAs(previousIcon)) {
            next.copy(maneuverIcon = previousIcon)
        } else {
            next
        }
    }

    fun removed(key: String) {
        setNavigating(key, false)
        if (_state.value?.key != key) return
        main.removeCallbacks(clearPending)
        main.postDelayed(clearPending, REMOVE_GRACE_MS)
    }

    /** Drops the directions shown, e.g. when the user turns them off. */
    fun clear() {
        main.removeCallbacks(clearPending)
        _state.value = null
    }

    /** Forgets everything, e.g. when the listener disconnects. */
    fun reset() {
        clear()
        navigatingKeys.clear()
        _navigating.value = false
    }
}

/** Whether this is an ongoing navigation notification, from its package, category and flags only. */
internal fun StatusBarNotification.isNavigation(): Boolean =
    NavParser.isNavigation(packageName, notification.category, notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

/**
 * Reads a navigation notification into a [NavState], or returns null for anything else. The
 * package, category and ongoing flag are checked before any text is read.
 */
internal fun StatusBarNotification.toNavState(context: Context): NavState? {
    if (!isNavigation()) return null
    val n = notification
    val extras = n.extras
    val fields = NavFields(
        packageName = packageName,
        category = n.category,
        isOngoing = true,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        shortCriticalText = extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT)?.toString(),
        progress = extras.getInt(Notification.EXTRA_PROGRESS),
        progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX),
    )
    val info = NavParser.parse(fields) ?: return null
    val icon = try {
        n.getLargeIcon()?.loadDrawable(context)?.toBitmap()
    } catch (_: Exception) {
        null
    }
    return NavState(key, packageName, info, icon, n.contentIntent)
}
