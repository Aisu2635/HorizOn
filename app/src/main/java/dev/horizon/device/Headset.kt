package dev.horizon.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** A connected Bluetooth headset. [percent] is null when the headset doesn't report its battery. */
data class Headset(val name: String, val percent: Int?)

// Not in the public SDK, but sent by the system to apps holding BLUETOOTH_CONNECT.
private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

private val BluetoothOutputTypes = buildSet {
    add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
    add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
}

/** Whether we may read Bluetooth device details (the "Nearby devices" permission on Android 12+). */
fun Context.hasBluetoothPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/**
 * The connected Bluetooth audio output, or null. Whether one is connected comes from
 * AudioManager, which needs no permission. The actual device (name, battery) comes from the
 * Bluetooth audio profiles and needs [hasBluetoothPermission]; without it the name is generic
 * and the battery unknown. (AudioDeviceInfo can't identify it: its address is anonymized and
 * its product name is often the phone's own model.)
 */
@SuppressLint("MissingPermission") // Every Bluetooth call is guarded by hasBluetoothPermission().
fun Context.headsetStatus(): Flow<Headset?> = callbackFlow {
    val audio = getSystemService(AudioManager::class.java)
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    val handler = Handler(Looper.getMainLooper())
    // Latest level per device address from the system's battery broadcasts.
    val reportedLevels = mutableMapOf<String, Int>()
    // Connected-device lists per audio profile, filled in once each proxy connects.
    val proxies = mutableMapOf<Int, BluetoothProfile>()

    fun publish() {
        val hasOutput = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in BluetoothOutputTypes }
        val device = if (hasBluetoothPermission()) {
            proxies.values.firstNotNullOfOrNull { proxy -> runCatching { proxy.connectedDevices.firstOrNull() }.getOrNull() }
        } else {
            null
        }
        if (!hasOutput && device == null) {
            trySend(null)
            return
        }
        val name = device?.let { runCatching { it.displayName() }.getOrNull() } ?: "Headphones"
        val percent = device?.let { reportedLevels[it.address] ?: it.batteryLevelOrNull() }
        trySend(Headset(name, percent))
    }

    val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            publish()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            publish()
        }
    }
    if (hasBluetoothPermission() && adapter != null) {
        AudioProfiles.forEach { adapter.getProfileProxy(this@headsetStatus, profileListener, it) }
    }

    val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publish()
    }
    audio.registerAudioDeviceCallback(audioCallback, handler)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {
                val device = intent.bluetoothDevice()
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                if (device != null && level in 0..100) reportedLevels[device.address] = level
            }
            publish()
        }
    }
    val filter = IntentFilter().apply {
        addAction(ACTION_BATTERY_LEVEL_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
    }
    ContextCompat.registerReceiver(this@headsetStatus, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

    publish()
    awaitClose {
        audio.unregisterAudioDeviceCallback(audioCallback)
        unregisterReceiver(receiver)
        proxies.forEach { (profile, proxy) -> adapter?.closeProfileProxy(profile, proxy) }
    }
}.distinctUntilChanged()

/** Profiles a headset can be connected through; LE Audio (API 33+) for newer earbuds. */
private val AudioProfiles = buildList {
    add(BluetoothProfile.A2DP)
    add(BluetoothProfile.HEADSET)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
}

/** The name the user gave the device (alias, API 30+), falling back to its advertised name. */
@SuppressLint("MissingPermission")
private fun BluetoothDevice.displayName(): String? =
    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias else null) ?: name

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

/**
 * BluetoothDevice.getBatteryLevel() is hidden from the SDK but callable by apps with
 * BLUETOOTH_CONNECT; it's what battery widgets use. Returns null if unknown or unavailable.
 */
@SuppressLint("DiscouragedPrivateApi")
private fun BluetoothDevice.batteryLevelOrNull(): Int? = runCatching {
    BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(this) as Int
}.getOrNull()?.takeIf { it in 0..100 }
