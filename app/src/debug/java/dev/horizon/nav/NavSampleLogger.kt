package dev.horizon.nav

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

private const val TAG = "HorizOnNav"

/** Logcat drops lines longer than about 4000 characters, so long samples are split. */
private const val LOG_CHUNK = 3_000

/**
 * Debug builds only (Nav_plan.md, milestone N0). Records every notification from
 * [NAV_PACKAGES] to Logcat (tag `HorizOnNav`) and to `files/nav_samples/` in app storage,
 * so we can learn exactly what Google Maps puts in its turn-by-turn notification:
 *
 *     adb logcat -s HorizOnNav
 *     adb exec-out run-as dev.horizon cat files/nav_samples/samples.jsonl
 *
 * Large icons (the turn arrows) are saved once each as PNGs under `files/nav_samples/icons/`.
 * Samples contain street names and destinations; review them before sharing.
 */
internal object NavSampleLogger {
    private val io = Executors.newSingleThreadExecutor()

    fun onConnected(service: NotificationListenerService) {
        val active = try {
            service.activeNotifications ?: emptyArray()
        } catch (_: SecurityException) {
            emptyArray()
        }
        val nav = active.filter { it.packageName in NAV_PACKAGES }
        Log.i(TAG, "Listener connected, ${nav.size} navigation notification(s) active")
        nav.forEach { record(service, "active", it) }
    }

    fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification) {
        if (sbn.packageName in NAV_PACKAGES) record(service, "posted", sbn)
    }

    fun onRemoved(service: NotificationListenerService, sbn: StatusBarNotification) {
        if (sbn.packageName !in NAV_PACKAGES) return
        val json = header("removed", sbn)
        io.execute { write(service, json) }
    }

    private fun record(service: NotificationListenerService, event: String, sbn: StatusBarNotification) {
        val json = try {
            sample(service, event, sbn)
        } catch (e: Exception) {
            header(event, sbn).put("error", e.toString())
        }
        val icon = try {
            sbn.notification.getLargeIcon()?.loadDrawable(service)?.toBitmap()
        } catch (_: Exception) {
            null
        }
        io.execute {
            if (icon != null) json.put("largeIconFile", saveIcon(service, icon))
            write(service, json)
        }
    }

    private fun header(event: String, sbn: StatusBarNotification): JSONObject = JSONObject()
        .put("event", event)
        .put("time", Instant.now().toString())
        .put("sdk", Build.VERSION.SDK_INT)
        .put("package", sbn.packageName)
        .put("key", sbn.key)
        .put("id", sbn.id)
        .put("tag", sbn.tag ?: JSONObject.NULL)

    private fun sample(service: NotificationListenerService, event: String, sbn: StatusBarNotification): JSONObject {
        val n = sbn.notification
        return header(event, sbn)
            .put("channel", n.channelId ?: JSONObject.NULL)
            .put("category", n.category ?: JSONObject.NULL)
            .put("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
            .put("foregroundService", n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            .put("onlyAlertOnce", n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            .put("hasContentIntent", n.contentIntent != null)
            .put("actions", JSONArray(n.actions.orEmpty().map { it.title?.toString() ?: JSONObject.NULL }))
            .put("extras", describe(n.extras))
            .put("customViews", customViews(service, n))
    }

    /**
     * Some apps draw their notification with custom RemoteViews instead of the standard text
     * fields. Inflate any we find and collect their visible text, so we know where the data is.
     */
    @Suppress("DEPRECATION")
    private fun customViews(service: NotificationListenerService, n: Notification): JSONObject {
        val result = JSONObject()
        listOf(
            "contentView" to n.contentView,
            "bigContentView" to n.bigContentView,
            "headsUpContentView" to n.headsUpContentView,
        ).forEach { (name, views) -> if (views != null) result.put(name, viewTexts(service, views)) }
        return result
    }

    private fun viewTexts(service: NotificationListenerService, views: RemoteViews): Any = try {
        val root = views.apply(service, FrameLayout(service))
        JSONArray(mutableListOf<String>().also { collectTexts(root, it) })
    } catch (e: Exception) {
        "could not inflate: $e"
    }

    private fun collectTexts(view: View, out: MutableList<String>) {
        when (view) {
            is TextView -> view.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
            is ViewGroup -> for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
        }
    }

    @Suppress("DEPRECATION") // Bundle.get(String): we want every value whatever its type.
    private fun describe(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is CharSequence -> value.toString()
        is Boolean, is Int, is Long -> value
        is Number -> value.toString()
        is Bundle -> JSONObject().also { json -> value.keySet().sorted().forEach { json.put(it, describe(value.get(it))) } }
        is Bitmap -> "Bitmap ${value.width}x${value.height}"
        is Icon -> "Icon"
        is Array<*> -> JSONArray(value.map(::describe))
        is Collection<*> -> JSONArray(value.map(::describe))
        is IntArray -> JSONArray(value.toList())
        is LongArray -> JSONArray(value.toList())
        else -> value.javaClass.name
    }

    private fun sampleDir(service: NotificationListenerService) = File(service.filesDir, "nav_samples")

    /** Saves [icon] once per distinct image and returns its path relative to nav_samples/. Runs on [io]. */
    private fun saveIcon(service: NotificationListenerService, icon: Bitmap): String {
        val pixels = IntArray(icon.width * icon.height)
        icon.getPixels(pixels, 0, icon.width, 0, 0, icon.width, icon.height)
        val hash = pixels.contentHashCode()
        val name = "icons/${Integer.toHexString(hash)}.png"
        // Checked on disk rather than remembered, so clearing nav_samples/ brings icons back.
        val file = File(sampleDir(service), name)
        if (!file.exists()) {
            try {
                file.parentFile?.mkdirs()
                file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not save icon $name", e)
            }
        }
        return name
    }

    /** Appends [json] to samples.jsonl and Logcat. Runs on [io]. */
    private fun write(service: NotificationListenerService, json: JSONObject) {
        val line = json.toString()
        line.chunked(LOG_CHUNK).forEachIndexed { i, part -> Log.i(TAG, if (i == 0) part else "… $part") }
        try {
            val dir = sampleDir(service).apply { mkdirs() }
            File(dir, "samples.jsonl").appendText(line + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "Could not write sample", e)
        }
    }
}
