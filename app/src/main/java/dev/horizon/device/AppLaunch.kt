package dev.horizon.device

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle

/**
 * Opens another app's screen: its own [screen] (e.g. "now playing" or the navigation view) when
 * it provides one, otherwise its launcher entry. Returns false if neither could be opened.
 */
fun Activity.openOtherApp(packageName: String, screen: PendingIntent?): Boolean {
    if (screen != null) {
        try {
            screen.send(this, 0, null, null, null, null, pendingIntentStartOptions())
            return true
        } catch (_: PendingIntent.CanceledException) {
            // Fall through to the launcher intent.
        }
    }
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return try {
        startActivity(launch)
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
