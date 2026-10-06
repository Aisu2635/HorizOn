package dev.horizon.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.horizon.nav.NavSampleLogger

/**
 * Exists so Android lets us see other apps' media sessions
 * (MediaSessionManager.getActiveSessions requires an enabled notification listener).
 *
 * In release builds notifications are never read or stored. Debug builds hand navigation
 * notifications (Google Maps only) to [NavSampleLogger] so their format can be studied;
 * see Nav_plan.md, milestone N0.
 *
 * Don't rename this class: notification access is granted per component name, so a rename
 * would silently revoke every user's grant.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() = NavSampleLogger.onConnected(this)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) NavSampleLogger.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) NavSampleLogger.onRemoved(this, sbn)
    }
}
