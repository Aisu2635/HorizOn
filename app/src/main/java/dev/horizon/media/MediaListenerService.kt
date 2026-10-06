package dev.horizon.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.horizon.nav.NAV_PACKAGES
import dev.horizon.nav.NavRepository
import dev.horizon.nav.NavSampleLogger
import dev.horizon.nav.toNavState
import dev.horizon.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Exists so Android lets us see other apps' media sessions
 * (MediaSessionManager.getActiveSessions requires an enabled notification listener).
 *
 * Only when the user turns on directions does it read anything: the ongoing turn-by-turn
 * notification from Google Maps, which is mirrored into [NavRepository] and kept in memory
 * only. Every other notification is ignored by package name before its content is touched.
 * Debug builds also hand Maps notifications to [NavSampleLogger] (Nav_plan.md, milestone N0).
 *
 * Don't rename this class: notification access is granted per component name, so a rename
 * would silently revoke every user's grant.
 */
class MediaListenerService : NotificationListenerService() {
    /** Lives while the system has us connected. Callbacks and this scope both run on the main thread. */
    private var scope: CoroutineScope? = null
    private var showNavigation = false

    override fun onListenerConnected() {
        NavSampleLogger.onConnected(this)
        scope?.cancel()
        scope = MainScope().apply {
            launch {
                SettingsRepository(this@MediaListenerService).showNavigation.collect { enabled ->
                    showNavigation = enabled
                    if (enabled) mirrorActiveNavigation() else NavRepository.clear()
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        stop()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NavSampleLogger.onPosted(this, sbn)
        if (showNavigation && sbn.packageName in NAV_PACKAGES) sbn.toNavState(this)?.let(NavRepository::post)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NavSampleLogger.onRemoved(this, sbn)
        if (sbn.packageName in NAV_PACKAGES) NavRepository.removed(sbn.key)
    }

    /** Picks up navigation that was already running when we connected or the setting was turned on. */
    private fun mirrorActiveNavigation() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (_: SecurityException) {
            emptyArray()
        }
        active.firstNotNullOfOrNull { if (it.packageName in NAV_PACKAGES) it.toNavState(this) else null }
            ?.let(NavRepository::post)
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        showNavigation = false
        NavRepository.clear()
    }
}
