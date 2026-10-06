package dev.horizon.media

import android.service.notification.NotificationListenerService

/**
 * Exists only so Android lets us see other apps' media sessions
 * (MediaSessionManager.getActiveSessions requires an enabled notification listener).
 * It deliberately overrides nothing: notifications are never read or stored.
 */
class MediaListenerService : NotificationListenerService()
