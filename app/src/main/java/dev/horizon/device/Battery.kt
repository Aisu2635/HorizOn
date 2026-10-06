package dev.horizon.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class BatteryStatus(val percent: Int, val pluggedIn: Boolean)

/** Battery level and charger state; emits the current value right away (sticky broadcast). */
fun Context.batteryStatus(): Flow<BatteryStatus> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.toBatteryStatus())
        }
    }
    ContextCompat.registerReceiver(
        this@batteryStatus,
        receiver,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )?.let { trySend(it.toBatteryStatus()) }
    awaitClose { unregisterReceiver(receiver) }
}.distinctUntilChanged()

private fun Intent.toBatteryStatus(): BatteryStatus {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    return BatteryStatus(percent, plugged)
}
