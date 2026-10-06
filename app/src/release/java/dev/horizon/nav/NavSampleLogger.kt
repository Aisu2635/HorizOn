package dev.horizon.nav

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Release builds: does nothing. Notifications are never read. The debug version records samples. */
@Suppress("UNUSED_PARAMETER")
internal object NavSampleLogger {
    fun onConnected(service: NotificationListenerService) = Unit

    fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification) = Unit

    fun onRemoved(service: NotificationListenerService, sbn: StatusBarNotification) = Unit
}
